package lv.xenonfix.config;

import java.time.Clock;
import java.util.Map;
import java.util.Set;

import lv.xenonfix.admin.AdminUserRepository;
import lv.xenonfix.audit.AuditEventType;
import lv.xenonfix.audit.AuditService;
import lv.xenonfix.auth.AuthenticatedAdmin;
import lv.xenonfix.auth.Authorities;
import lv.xenonfix.auth.SessionPolicyFilter;
import lv.xenonfix.ratelimit.RateLimitFilter;
import lv.xenonfix.ratelimit.RateLimiter;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
class SecurityConfig {

	private static final String RESERVATIONS_PATH = "/api/store/reservations";

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository securityContextRepository,
			AuditService audit, AdminUserRepository admins, AppProperties properties, Clock clock) throws Exception {
		http
			.securityContext((context) -> context.securityContextRepository(securityContextRepository))
			// Ends sessions of disabled admins and sessions past their absolute timeout
			.addFilterAfter(new SessionPolicyFilter(admins, audit, properties, clock),
					SecurityContextHolderFilter.class)
			// Token in a readable XSRF-TOKEN cookie, which Angular sends back in X-XSRF-TOKEN
			// Reservations are anonymous and use no login or session, so a forged request gains
			// nothing that anyone could not do by filling in the form
			.csrf((csrf) -> csrf.spa().ignoringRequestMatchers(RESERVATIONS_PATH))
			.authorizeHttpRequests((auth) -> auth
				.requestMatchers("/error").permitAll()
				.requestMatchers(HttpMethod.GET, "/api/health", "/uploads/**").permitAll()
				.requestMatchers(HttpMethod.GET, "/api/store/products", "/api/store/products/*", "/api/store/sitemap")
				.permitAll()
				.requestMatchers(HttpMethod.POST, RESERVATIONS_PATH).permitAll()
				.requestMatchers(HttpMethod.GET, "/api/admin/auth/csrf").permitAll()
				.requestMatchers(HttpMethod.POST, "/api/admin/auth/login").permitAll()
				// Reachable before the password is changed; everything else needs the full role
				.requestMatchers("/api/admin/me", "/api/admin/me/password").authenticated()
				.requestMatchers("/api/admin/**").hasAuthority(Authorities.ADMIN)
				// New public endpoints must be allowed above on purpose
				.anyRequest().denyAll())
			.exceptionHandling((exceptions) -> exceptions
				.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.logout((logout) -> logout
				.logoutUrl("/api/admin/auth/logout")
				.addLogoutHandler((request, response, authentication) -> {
					if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedAdmin admin) {
						audit.record(AuditEventType.LOGGED_OUT, admin.id(), request.getRemoteAddr(), null);
					}
				})
				.logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
			.headers((headers) -> headers
				.contentSecurityPolicy((csp) -> csp.policyDirectives("default-src 'self'; frame-ancestors 'none'"))
				.httpStrictTransportSecurity((hsts) -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000)));
		return http.build();
	}

	@Bean
	SecurityContextRepository securityContextRepository() {
		return new HttpSessionSecurityContextRepository();
	}

	/**
	 * Argon2id. Hashes carry an {id} prefix, so the algorithm can be changed later and old
	 * hashes are upgraded on the next login.
	 */
	@Bean
	PasswordEncoder passwordEncoder() {
		return new DelegatingPasswordEncoder("argon2",
				Map.of("argon2", Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()));
	}

	/** Before Spring Security, so rejected requests cost almost nothing. */
	@Bean
	FilterRegistrationBean<RateLimitFilter> authRateLimitFilter(RateLimiter rateLimiter, AppProperties properties) {
		return rateLimit("authRateLimitFilter", new RateLimitFilter(rateLimiter, properties.rateLimit(),
				Set.of("/api/admin/auth/login", "/api/admin/me/password"), "auth:"));
	}

	@Bean
	FilterRegistrationBean<RateLimitFilter> reservationRateLimitFilter(RateLimiter rateLimiter,
			AppProperties properties) {
		return rateLimit("reservationRateLimitFilter", new RateLimitFilter(rateLimiter,
				properties.reservation().rateLimit(), Set.of(RESERVATIONS_PATH), "reservation:"));
	}

	/** Each needs its own name, or the server would only register the first of this class. */
	private static FilterRegistrationBean<RateLimitFilter> rateLimit(String name, RateLimitFilter filter) {
		var registration = new FilterRegistrationBean<>(filter);
		registration.setName(name);
		registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
		return registration;
	}

}
