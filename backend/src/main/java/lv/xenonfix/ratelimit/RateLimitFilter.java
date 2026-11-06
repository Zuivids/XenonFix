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
 * Limits POST requests to the given paths, per client IP.
 *
 * <p>
 * Uses the request's remote address. On the server that is the visitor's, taken from
 * X-Forwarded-For by Tomcat (see application-prod.yml); without that every visitor would have
 * Caddy's address and share one limit.
 */
public class RateLimitFilter extends OncePerRequestFilter {

	private static final Logger logger = LoggerFactory.getLogger(RateLimitFilter.class);

	private static final String PROBLEM_BODY = """
			{"type":"about:blank","title":"Too Many Requests","status":429,"code":"TOO_MANY_REQUESTS"}""";

	private final RateLimiter rateLimiter;

	private final AppProperties.RateLimit rule;

	private final Set<String> paths;

	private final String keyPrefix;

	/**
	 * @param keyPrefix keeps this filter's counts apart from other filters' for the same IP
	 */
	public RateLimitFilter(RateLimiter rateLimiter, AppProperties.RateLimit rule, Set<String> paths, String keyPrefix) {
		this.rateLimiter = rateLimiter;
		this.rule = rule;
		this.paths = paths;
		this.keyPrefix = keyPrefix;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !"POST".equals(request.getMethod()) || !this.paths.contains(path(request));
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String ip = request.getRemoteAddr();
		RateLimiter.Decision decision = this.rateLimiter.tryAcquire(this.keyPrefix + ip, this.rule);
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
