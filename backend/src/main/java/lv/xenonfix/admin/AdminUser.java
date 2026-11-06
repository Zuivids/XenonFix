package lv.xenonfix.admin;

import java.time.Duration;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "admin_user")
public class AdminUser {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 320)
	private String email;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Column(name = "display_name", nullable = false, length = 100)
	private String displayName;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AdminStatus status;

	@Column(name = "password_change_required", nullable = false)
	private boolean passwordChangeRequired;

	/** The authenticator key, encrypted (MfaSecretCipher); empty until the admin sets up the app. */
	@Column(name = "mfa_secret", length = 128)
	private byte[] mfaSecret;

	@Column(name = "mfa_enabled_at")
	private Instant mfaEnabledAt;

	/** The time step of the last code used, so no code works twice. */
	@Column(name = "mfa_last_step")
	private Long mfaLastStep;

	@Column(name = "failed_login_count", nullable = false)
	private int failedLoginCount;

	@Column(name = "locked_until")
	private Instant lockedUntil;

	@Column(name = "security_version", nullable = false)
	private long securityVersion;

	@Column(name = "last_login_at")
	private Instant lastLoginAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	private long version;

	protected AdminUser() {
	}

	public AdminUser(String email, String passwordHash, String displayName, boolean passwordChangeRequired,
			Instant now) {
		this.email = email;
		this.passwordHash = passwordHash;
		this.displayName = displayName;
		this.status = AdminStatus.ACTIVE;
		this.passwordChangeRequired = passwordChangeRequired;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public boolean isLocked(Instant now) {
		return this.lockedUntil != null && this.lockedUntil.isAfter(now);
	}

	public boolean canLogIn(Instant now) {
		return this.status == AdminStatus.ACTIVE && !isLocked(now);
	}

	/**
	 * Records a failed attempt and locks the account once the limit is reached.
	 * @return {@code true} if this failure locked the account
	 */
	public boolean registerFailedAttempt(int maxAttempts, Duration lockDuration, Instant now) {
		this.failedLoginCount++;
		this.updatedAt = now;
		if (this.failedLoginCount >= maxAttempts) {
			this.lockedUntil = now.plus(lockDuration);
			this.failedLoginCount = 0;
			return true;
		}
		return false;
	}

	public void recordLogin(Instant now) {
		this.failedLoginCount = 0;
		this.lockedUntil = null;
		this.lastLoginAt = now;
		this.updatedAt = now;
	}

	/** The admin set their own password: other open sessions end, and no change is pending. */
	public void changeOwnPassword(String passwordHash, Instant now) {
		this.passwordHash = passwordHash;
		this.passwordChangeRequired = false;
		endSessions(now);
	}

	/** Someone else chose the password, so the admin must replace it at next login. */
	public void resetPassword(String passwordHash, Instant now) {
		this.passwordHash = passwordHash;
		this.passwordChangeRequired = true;
		unlock();
		endSessions(now);
	}

	/** Same password in a newer hash format. */
	public void upgradePasswordHash(String passwordHash, Instant now) {
		this.passwordHash = passwordHash;
		this.updatedAt = now;
	}

	/** Blocks login and ends the admin's open sessions on their next request. */
	public void disable(Instant now) {
		this.status = AdminStatus.DISABLED;
		endSessions(now);
	}

	public void enable(Instant now) {
		this.status = AdminStatus.ACTIVE;
		unlock();
		this.updatedAt = now;
	}

	/** @param step the step of the code that proved the app works, which cannot be used again */
	public void enableMfa(byte[] encryptedSecret, long step, Instant now) {
		this.mfaSecret = encryptedSecret;
		this.mfaEnabledAt = now;
		this.mfaLastStep = step;
		this.updatedAt = now;
	}

	/**
	 * Accepts a code's time step if it is newer than the last one used.
	 * @return {@code false} if that code, or a later one, was already used
	 */
	public boolean useMfaStep(long step, Instant now) {
		if (this.mfaLastStep != null && step <= this.mfaLastStep) {
			return false;
		}
		this.mfaLastStep = step;
		this.updatedAt = now;
		return true;
	}

	/** For a lost phone: the admin sets up the app again at next login, and open sessions end. */
	public void resetMfa(Instant now) {
		this.mfaSecret = null;
		this.mfaEnabledAt = null;
		this.mfaLastStep = null;
		unlock();
		endSessions(now);
	}

	public boolean isMfaEnabled() {
		return this.mfaSecret != null;
	}

	public byte[] getMfaSecret() {
		return this.mfaSecret;
	}

	private void unlock() {
		this.failedLoginCount = 0;
		this.lockedUntil = null;
	}

	private void endSessions(Instant now) {
		this.securityVersion++;
		this.updatedAt = now;
	}

	public Long getId() {
		return this.id;
	}

	public String getEmail() {
		return this.email;
	}

	public String getPasswordHash() {
		return this.passwordHash;
	}

	public String getDisplayName() {
		return this.displayName;
	}

	public AdminStatus getStatus() {
		return this.status;
	}

	public boolean isPasswordChangeRequired() {
		return this.passwordChangeRequired;
	}

	public long getSecurityVersion() {
		return this.securityVersion;
	}

	public Instant getLastLoginAt() {
		return this.lastLoginAt;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

}
