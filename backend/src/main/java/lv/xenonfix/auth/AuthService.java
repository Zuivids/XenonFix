package lv.xenonfix.auth;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import lv.xenonfix.admin.AdminRecoveryCode;
import lv.xenonfix.admin.AdminRecoveryCodeRepository;
import lv.xenonfix.admin.AdminUser;
import lv.xenonfix.admin.AdminUserRepository;
import lv.xenonfix.admin.Emails;
import lv.xenonfix.audit.AuditEventType;
import lv.xenonfix.audit.AuditService;
import lv.xenonfix.auth.dto.MfaSetupResponse;
import lv.xenonfix.auth.mfa.Base32;
import lv.xenonfix.auth.mfa.MfaSecretCipher;
import lv.xenonfix.auth.mfa.QrCodes;
import lv.xenonfix.auth.mfa.RecoveryCodes;
import lv.xenonfix.auth.mfa.Totp;
import lv.xenonfix.common.ApiException;
import lv.xenonfix.config.AppProperties;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The two steps of logging in: the password, then a code from the authenticator app (or a
 * backup code), or setting up the app if the admin has none yet. Failures of either step count
 * towards the same lockouts.
 */
@Service
class AuthService {

	private static final String ISSUER = "XenonFix";

	/** 160 bits, the length RFC 4226 recommends for HMAC-SHA1. */
	private static final int SECRET_BYTES = 20;

	private final AdminUserRepository admins;

	private final AdminRecoveryCodeRepository recoveryCodes;

	private final PasswordEncoder passwordEncoder;

	private final LoginAttemptTracker attempts;

	private final AuditService audit;

	private final MfaSecretCipher cipher;

	private final AppProperties.Lockout lockout;

	private final Clock clock;

	private final SecureRandom random = new SecureRandom();

	/** Compared against when the email is unknown, so the response time does not reveal it. */
	private final String dummyPasswordHash;

	AuthService(AdminUserRepository admins, AdminRecoveryCodeRepository recoveryCodes, PasswordEncoder passwordEncoder,
			LoginAttemptTracker attempts, AuditService audit, MfaSecretCipher cipher, AppProperties properties,
			Clock clock) {
		this.admins = admins;
		this.recoveryCodes = recoveryCodes;
		this.passwordEncoder = passwordEncoder;
		this.attempts = attempts;
		this.audit = audit;
		this.cipher = cipher;
		this.lockout = properties.lockout();
		this.clock = clock;
		this.dummyPasswordHash = passwordEncoder.encode("timing-equalization-only");
	}

	/**
	 * The first step: checks email and password. Every failure looks the same to the client, so
	 * it reveals neither who is an admin nor who is locked out. Failed attempts are committed
	 * (not rolled back) so the lockout counter works.
	 *
	 * <p>
	 * A right password does not reset the failure counts: only the second step does, or
	 * someone who knows the password could keep guessing codes.
	 * @return a session that may only do the second step
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
			registerFailure(AuditEventType.LOGIN_FAILED, admin, ipAddress, now, null);
			throw invalidCredentials();
		}
		if (this.passwordEncoder.upgradeEncoding(admin.getPasswordHash())) {
			admin.upgradePasswordHash(this.passwordEncoder.encode(password), now);
		}
		return authentication(admin, Authorities.afterPassword(admin), now);
	}

	/**
	 * The second step, with a code from the app or a backup code.
	 * @param pending the session after the password
	 */
	@Transactional(noRollbackFor = ApiException.class)
	Authentication verifySecondStep(AuthenticatedAdmin pending, String code, String ipAddress) {
		Instant now = this.clock.instant();
		AdminUser admin = this.admins.findById(pending.id()).orElseThrow();
		checkNotBlocked(admin, ipAddress, now);
		String how;
		if (Totp.matchingStep(secretOf(admin), code, now).isPresent()) {
			useAppCode(admin, code, ipAddress, now);
			how = "ar lietotnes kodu";
		}
		else {
			AdminRecoveryCode recoveryCode = findRecoveryCode(admin, code)
				.orElseThrow(() -> failure(admin, ipAddress, now, null));
			recoveryCode.use(now);
			how = "ar rezerves kodu, atlikuši " + this.recoveryCodes.countByAdminIdAndUsedAtIsNull(admin.getId());
		}
		completeLogin(admin, ipAddress, now, how);
		return authentication(admin, Authorities.afterSecondStep(admin), pending.authenticatedAt());
	}

	/** A new key for setting up the app, shown as a QR code and as text. Not stored until proven. */
	byte[] newSecret() {
		byte[] secret = new byte[SECRET_BYTES];
		this.random.nextBytes(secret);
		return secret;
	}

	MfaSetupResponse setupDetails(String email, byte[] secret) {
		String key = Base32.encode(secret);
		String label = URLEncoder.encode(ISSUER + ":" + email, StandardCharsets.UTF_8).replace("+", "%20");
		String address = "otpauth://totp/" + label + "?secret=" + key + "&issuer=" + ISSUER
				+ "&algorithm=SHA1&digits=6&period=" + Totp.STEP_SECONDS;
		return new MfaSetupResponse(QrCodes.pngDataUri(address), key.replaceAll("(.{4})(?!$)", "$1 "));
	}

	/**
	 * The second step for an admin without the app: the code proves the app was set up with
	 * this key. Saves the key and makes the backup codes.
	 */
	@Transactional(noRollbackFor = ApiException.class)
	SetupResult completeSetup(AuthenticatedAdmin pending, byte[] secret, String code, String ipAddress) {
		Instant now = this.clock.instant();
		AdminUser admin = this.admins.findById(pending.id()).orElseThrow();
		checkNotBlocked(admin, ipAddress, now);
		if (admin.isMfaEnabled()) {
			throw new ApiException(HttpStatus.CONFLICT, "MFA_ALREADY_ENABLED");
		}
		// A wrong code here guesses nothing, as the admin already has the key, so it is not counted
		OptionalLong step = Totp.matchingStep(secret, code, now);
		if (step.isEmpty()) {
			throw invalidCode();
		}
		admin.enableMfa(this.cipher.encrypt(secret), step.getAsLong(), now);
		List<String> codes = replaceRecoveryCodes(admin, now);
		this.audit.record(AuditEventType.MFA_ENABLED, admin.getId(), ipAddress, null);
		completeLogin(admin, ipAddress, now, "iestatīta lietotne");
		return new SetupResult(authentication(admin, Authorities.afterSecondStep(admin), pending.authenticatedAt()),
				codes);
	}

	/** New backup codes for a logged-in admin, with a current app code. The old ones stop working. */
	@Transactional(noRollbackFor = ApiException.class)
	List<String> renewRecoveryCodes(long adminId, String code, String ipAddress) {
		Instant now = this.clock.instant();
		AdminUser admin = this.admins.findById(adminId).orElseThrow();
		checkNotBlocked(admin, ipAddress, now);
		if (Totp.matchingStep(secretOf(admin), code, now).isEmpty()) {
			throw failure(admin, ipAddress, now, "jauni rezerves kodi");
		}
		useAppCode(admin, code, ipAddress, now);
		this.attempts.reset(admin.getId(), ipAddress);
		List<String> codes = replaceRecoveryCodes(admin, now);
		this.audit.record(AuditEventType.MFA_RECOVERY_CODES_RENEWED, admin.getId(), ipAddress, null);
		return codes;
	}

	@Transactional(readOnly = true)
	long recoveryCodesLeft(long adminId) {
		return this.recoveryCodes.countByAdminIdAndUsedAtIsNull(adminId);
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
		// Only reachable after both steps of the login
		return authentication(admin, Authorities.afterSecondStep(admin), this.clock.instant());
	}

	@Transactional(readOnly = true)
	AdminUser find(long adminId) {
		return this.admins.findById(adminId).orElseThrow();
	}

	/** The app code was right; refuses it if it, or a later one, was already used. */
	private void useAppCode(AdminUser admin, String code, String ipAddress, Instant now) {
		long step = Totp.matchingStep(secretOf(admin), code, now).orElseThrow();
		if (!admin.useMfaStep(step, now)) {
			throw failure(admin, ipAddress, now, "kods jau izmantots");
		}
	}

	private Optional<AdminRecoveryCode> findRecoveryCode(AdminUser admin, String code) {
		String hash = RecoveryCodes.hashOfTyped(code);
		return (hash == null) ? Optional.empty()
				: this.recoveryCodes.findByAdminIdAndCodeHashAndUsedAtIsNull(admin.getId(), hash);
	}

	private List<String> replaceRecoveryCodes(AdminUser admin, Instant now) {
		this.recoveryCodes.deleteAllOf(admin.getId());
		List<String> codes = RecoveryCodes.generate();
		for (String code : codes) {
			this.recoveryCodes.save(new AdminRecoveryCode(admin.getId(), RecoveryCodes.hashOfTyped(code), now));
		}
		return codes;
	}

	private void completeLogin(AdminUser admin, String ipAddress, Instant now, String how) {
		admin.recordLogin(now);
		this.attempts.reset(admin.getId(), ipAddress);
		this.audit.record(AuditEventType.LOGIN_SUCCEEDED, admin.getId(), ipAddress, how);
	}

	private byte[] secretOf(AdminUser admin) {
		if (!admin.isMfaEnabled()) {
			// Reset by another admin in the meantime
			throw invalidCode();
		}
		return this.cipher.decrypt(admin.getMfaSecret());
	}

	/** A lockout from the first step also stops the second, even with the right code. */
	private void checkNotBlocked(AdminUser admin, String ipAddress, Instant now) {
		String reason = blockReason(admin, ipAddress, now);
		if (reason != null) {
			this.audit.record(AuditEventType.MFA_FAILED, admin.getId(), ipAddress, reason);
			throw invalidCode();
		}
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

	private ApiException failure(AdminUser admin, String ipAddress, Instant now, String details) {
		registerFailure(AuditEventType.MFA_FAILED, admin, ipAddress, now, details);
		return invalidCode();
	}

	private void registerFailure(AuditEventType type, AdminUser admin, String ipAddress, Instant now, String details) {
		this.audit.record(type, admin.getId(), ipAddress, details);
		if (this.attempts.recordFailure(admin.getId(), ipAddress)) {
			this.audit.record(AuditEventType.BLOCKED_FOR_IP, admin.getId(), ipAddress,
					"uz " + this.lockout.duration().toMinutes() + " min");
		}
		if (admin.registerFailedAttempt(this.lockout.maxFailedAttempts(), this.lockout.duration(), now)) {
			this.audit.record(AuditEventType.ACCOUNT_LOCKED, admin.getId(), ipAddress,
					"uz " + this.lockout.duration().toMinutes() + " min");
		}
	}

	private Authentication authentication(AdminUser admin, Collection<? extends GrantedAuthority> authorities,
			Instant authenticatedAt) {
		AuthenticatedAdmin principal = new AuthenticatedAdmin(admin.getId(), admin.getEmail(), authenticatedAt,
				admin.getSecurityVersion());
		return UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities);
	}

	private static ApiException invalidCredentials() {
		return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
	}

	/** 400, not 401: the session is still valid for another try. */
	private static ApiException invalidCode() {
		return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CODE");
	}

	record SetupResult(Authentication authentication, List<String> recoveryCodes) {
	}

}
