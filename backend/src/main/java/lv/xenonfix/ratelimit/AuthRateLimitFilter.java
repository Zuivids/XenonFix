package lv.xenonfix.ratelimit;

import java.io.IOException;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lv.xenonfix.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

/**
 * Limits requests that check a password, per client IP.
 *
 * <p>
 * Uses the connection's remote address. Behind a reverse proxy this is the proxy's
 * address until forwarded headers are configured; until then every client would share one
 * limit.
 */
public class AuthRateLimitFilter extends OncePerRequestFilter {

	private static final Logger logger = LoggerFactory.getLogger(AuthRateLimitFilter.class);

	private static final Set<String> LIMITED_PATHS = Set.of("/api/admin/auth/login", "/api/admin/me/password");

	private static final String PROBLEM_BODY = """
			{"type":"about:blank","title":"Too Many Requests","status":429,"code":"TOO_MANY_REQUESTS"}""";

	private final RateLimiter rateLimiter;

	private final AppProperties.RateLimit rule;

	public AuthRateLimitFilter(RateLimiter rateLimiter, AppProperties properties) {
		this.rateLimiter = rateLimiter;
		this.rule = properties.rateLimit();
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !"POST".equals(request.getMethod()) || !LIMITED_PATHS.contains(path(request));
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String ip = request.getRemoteAddr();
		RateLimiter.Decision decision = this.rateLimiter.tryAcquire(ip, this.rule);
		if (decision.allowed()) {
			chain.doFilter(request, response);
			return;
		}
		logger.warn("Rate limit exceeded for {} from {}", path(request), ip);
		response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
		// Round up, so the client never retries a moment too early
		long seconds = Math.max(1, (decision.retryAfter().toMillis() + 999) / 1000);
		response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(seconds));
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.getWriter().write(PROBLEM_BODY);
	}

	/**
	 * Decoded path without ";" parameters, as Spring MVC sees it. The raw request URI would
	 * let "/api/admin/auth/%6Cogin" slip past the limit and still reach the login endpoint.
	 */
	private static String path(HttpServletRequest request) {
		return UrlPathHelper.defaultInstance.getPathWithinApplication(request);
	}

}
