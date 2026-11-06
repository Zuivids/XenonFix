package lv.xenonfix.auth;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import lv.xenonfix.admin.AdminSessionState;
import lv.xenonfix.admin.AdminStatus;
import lv.xenonfix.admin.AdminUserRepository;
import lv.xenonfix.audit.AuditEventType;
import lv.xenonfix.audit.AuditService;
import lv.xenonfix.config.AppProperties;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Ends a logged-in session when it is no longer allowed. Runs on every request right after
 * the session's login is loaded:
 * <ul>
 * <li>the account was disabled since login;</li>
 * <li>the account's password changed or was reset, or its authenticator app was reset
 * (security version);</li>
 * <li>the session is older than the absolute timeout;</li>
 * <li>the second step of the login was not done in time.</li>
 * </ul>
 * The request then continues as a logged-out request, so protected endpoints answer 401
 * and public ones still work.
 */
public class SessionPolicyFilter extends OncePerRequestFilter {

	private final AdminUserRepository admins;

	private final AuditService audit;

	private final AppProperties.Session sessionProperties;

	private final Duration mfaPendingTimeout;

	private final Clock clock;

	private final SecurityContextHolderStrategy holderStrategy = SecurityContextHolder.getContextHolderStrategy();

	public SessionPolicyFilter(AdminUserRepository admins, AuditService audit, AppProperties properties, Clock clock) {
		this.admins = admins;
		this.audit = audit;
		this.sessionProperties = properties.session();
		this.mfaPendingTimeout = properties.mfa().pendingTimeout();
		this.clock = clock;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Authentication authentication = this.holderStrategy.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedAdmin admin) {
			String reason = endReason(admin, authentication);
			if (reason != null) {
				end(request);
				this.audit.record(AuditEventType.SESSION_ENDED, admin.id(), request.getRemoteAddr(), reason);
			}
		}
		chain.doFilter(request, response);
	}

	/** @return why the session must end, or {@code null} if it may continue */
	private String endReason(AuthenticatedAdmin admin, Authentication authentication) {
		Instant now = this.clock.instant();
		if (!now.isBefore(admin.authenticatedAt().plus(this.sessionProperties.absolute()))) {
			return "beidzies maksimālais sesijas laiks";
		}
		if (!Authorities.has(authentication.getAuthorities(), Authorities.MFA_DONE)
				&& !now.isBefore(admin.authenticatedAt().plus(this.mfaPendingTimeout))) {
			return "otrais solis netika pabeigts laikā";
		}
		// One primary-key lookup per request; keeps changes to an account effective immediately
		Optional<AdminSessionState> state = this.admins.findSessionState(admin.id());
		if (state.isEmpty() || state.get().status() != AdminStatus.ACTIVE) {
			return "konts atslēgts";
		}
		if (state.get().securityVersion() != admin.securityVersion()) {
			// The password was changed or reset, or the authenticator app was reset
			return "mainīta parole vai divpakāpju pieslēgšanās";
		}
		return null;
	}

	private void end(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session != null) {
			session.invalidate();
		}
		this.holderStrategy.clearContext();
	}

}
