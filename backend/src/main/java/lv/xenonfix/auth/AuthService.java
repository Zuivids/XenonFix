package lv.xenonfix.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import lv.xenonfix.admin.AdminUser;
import lv.xenonfix.admin.AdminUserRepository;
import lv.xenonfix.admin.Emails;
import lv.xenonfix.audit.AuditEventType;
import lv.xenonfix.audit.AuditService;
import lv.xenonfix.common.ApiException;
import lv.xenonfix.config.AppProperties;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AuthService {

	private final AdminUserRepository admins;

	private final PasswordEncoder passwordEncoder;

	private final LoginAttemptTracker attempts;

	private final AuditService audit;

	private final AppProperties.Lockout lockout;

	private final Clock clock;

	/** Compared against when the email is unknown, so the response time does not reveal it. */
	private final String dummyPasswordHash;

	AuthService(AdminUserRepository admins, PasswordEncoder passwordEncoder, LoginAttemptTracker attempts,
			AuditService audit, AppProperties properties, Clock clock) {
		this.admins = admins;
		this.passwordEncoder = passwordEncoder;
		this.attempts = attempts;
		this.audit = audit;
		this.lockout = properties.lockout();
		this.clock = clock;
		this.dummyPasswordHash = passwordEncoder.encode("timing-equalization-only");
	}

	/**
	 * Checks email and password. Every failure looks the same to the client, so it reveals
	 * neither who is an admin nor who is locked out. Failed attempts are committed (not
	 * rolled back) so the lockout counter works.
	 */
	@Transactional(noRollbackFor = ApiException.class)
	Authentication login(String email, String password, String ipAddress) {
		Instant now = this.clock.instant();
		Optional<AdminUser> found = this.admins.findByEmail(Emails.normalize(email));
		if (found.isEmpty()) {
			this.passwordEncoder.matches(password, this.dummyPasswordHash);
			// Not the typed email: people sometimes type their password into that field
			this.audit.record(AuditEventType.LOGIN_FAILED, null, ipAddress, "nezināms konts");
			throw invalidCredentials();
		}
		AdminUser admin = found.get();
		boolean passwordMatches = this.passwordEncoder.matches(password, admin.getPasswordHash());
		String blockReason = blockReason(admin, ipAddress, now);
		if (blockReason != null) {
			this.audit.record(AuditEventType.LOGIN_FAILED, admin.getId(), ipAddress, blockReason);
			throw invalidCredentials();
		}
		if (!passwordMatches) {
			registerFailure(admin, ipAddress, now);
			throw invalidCredentials();
		}
		if (this.passwordEncoder.upgradeEncoding(admin.getPasswordHash())) {
			admin.upgradePasswordHash(this.passwordEncoder.encode(password), now);
		}
		// With MFA, this is where a second step would start instead of the login completing
		admin.recordLogin(now);
		this.attempts.reset(admin.getId(), ipAddress);
		this.audit.record(AuditEventType.LOGIN_SUCCEEDED, admin.getId(), ipAddress, null);
		return authentication(admin);
	}

	/**
	 * Changes the admin's own password and ends their other sessions.
	 * @return the authentication for this session, which stays logged in
	 */
	@Transactional(noRollbackFor = ApiException.class)
	Authentication changePassword(long adminId, String currentPassword, String newPassword, String ipAddress) {
		AdminUser admin = this.admins.findById(adminId).orElseThrow();
		if (!this.passwordEncoder.matches(currentPassword, admin.getPasswordHash())) {
			this.audit.record(AuditEventType.PASSWORD_CHANGE_FAILED, adminId, ipAddress, null);
			throw new ApiException(HttpStatus.BAD_REQUEST, "WRONG_CURRENT_PASSWORD");
		}
		if (this.passwordEncoder.matches(newPassword, admin.getPasswordHash())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "SAME_PASSWORD");
		}
		admin.changeOwnPassword(this.passwordEncoder.encode(newPassword), this.clock.instant());
		this.audit.record(AuditEventType.PASSWORD_CHANGED, adminId, ipAddress, null);
		return authentication(admin);
	}

	@Transactional(readOnly = true)
	AdminUser find(long adminId) {
		return this.admins.findById(adminId).orElseThrow();
	}

	/** @return why the admin may not log in from this IP right now, or {@code null} */
	private String blockReason(AdminUser admin, String ipAddress, Instant now) {
		if (!admin.canLogIn(now)) {
			return "konts bloķēts vai atslēgts";
		}
		if (this.attempts.isBlocked(admin.getId(), ipAddress)) {
			return "bloķēts no šīs IP adreses";
		}
		return null;
	}

	private void registerFailure(AdminUser admin, String ipAddress, Instant now) {
		this.audit.record(AuditEventType.LOGIN_FAILED, admin.getId(), ipAddress, null);
		if (this.attempts.recordFailure(admin.getId(), ipAddress)) {
			this.audit.record(AuditEventType.BLOCKED_FOR_IP, admin.getId(), ipAddress,
					"uz " + this.lockout.duration().toMinutes() + " min");
		}
		if (admin.registerFailedAttempt(this.lockout.maxFailedAttempts(), this.lockout.duration(), now)) {
			this.audit.record(AuditEventType.ACCOUNT_LOCKED, admin.getId(), ipAddress,
					"uz " + this.lockout.duration().toMinutes() + " min");
		}
	}

	private Authentication authentication(AdminUser admin) {
		AuthenticatedAdmin principal = new AuthenticatedAdmin(admin.getId(), admin.getEmail(), this.clock.instant(),
				admin.getSecurityVersion());
		return UsernamePasswordAuthenticationToken.authenticated(principal, null, Authorities.of(admin));
	}

	private static ApiException invalidCredentials() {
		return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
	}

}
