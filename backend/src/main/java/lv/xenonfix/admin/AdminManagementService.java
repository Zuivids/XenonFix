package lv.xenonfix.admin;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import lv.xenonfix.admin.dto.AdminResponse;
import lv.xenonfix.admin.dto.CreateAdminRequest;
import lv.xenonfix.audit.AuditEventType;
import lv.xenonfix.audit.AuditService;
import lv.xenonfix.auth.AuthenticatedAdmin;
import lv.xenonfix.auth.LoginAttemptTracker;
import lv.xenonfix.common.ApiException;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AdminManagementService {

	private static final String TARGET_TYPE = "ADMIN";

	private final AdminUserRepository admins;

	private final PasswordEncoder passwordEncoder;

	private final LoginAttemptTracker attempts;

	private final AuditService audit;

	private final Clock clock;

	AdminManagementService(AdminUserRepository admins, PasswordEncoder passwordEncoder, LoginAttemptTracker attempts,
			AuditService audit, Clock clock) {
		this.admins = admins;
		this.passwordEncoder = passwordEncoder;
		this.attempts = attempts;
		this.audit = audit;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	List<AdminResponse> list() {
		Instant now = this.clock.instant();
		return this.admins.findAllByOrderByEmailAsc().stream().map((admin) -> toResponse(admin, now)).toList();
	}

	/** The new admin gets the password from the creator and must replace it at first login. */
	@Transactional
	AdminResponse create(CreateAdminRequest request, AuthenticatedAdmin actor, String ipAddress) {
		String email = Emails.normalize(request.email());
		if (this.admins.existsByEmail(email)) {
			throw new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN");
		}
		Instant now = this.clock.instant();
		AdminUser admin = this.admins.save(new AdminUser(email, this.passwordEncoder.encode(request.password()),
				request.displayName().strip(), true, now));
		recordAction(AuditEventType.ADMIN_CREATED, actor, admin, ipAddress);
		return toResponse(admin, now);
	}

	@Transactional
	AdminResponse disable(long id, AuthenticatedAdmin actor, String ipAddress) {
		// Nobody can disable themselves, so at least one active admin always remains
		AdminUser admin = findOther(id, actor);
		Instant now = this.clock.instant();
		admin.disable(now);
		recordAction(AuditEventType.ADMIN_DISABLED, actor, admin, ipAddress);
		return toResponse(admin, now);
	}

	@Transactional
	AdminResponse enable(long id, AuthenticatedAdmin actor, String ipAddress) {
		AdminUser admin = findOther(id, actor);
		Instant now = this.clock.instant();
		admin.enable(now);
		this.attempts.resetAll(admin.getId());
		recordAction(AuditEventType.ADMIN_ENABLED, actor, admin, ipAddress);
		return toResponse(admin, now);
	}

	/** For a forgotten password: the other admin sets a temporary one, which must be replaced. */
	@Transactional
	AdminResponse resetPassword(long id, String password, AuthenticatedAdmin actor, String ipAddress) {
		AdminUser admin = findOther(id, actor);
		Instant now = this.clock.instant();
		admin.resetPassword(this.passwordEncoder.encode(password), now);
		this.attempts.resetAll(admin.getId());
		recordAction(AuditEventType.ADMIN_PASSWORD_RESET, actor, admin, ipAddress);
		return toResponse(admin, now);
	}

	private AdminUser findOther(long id, AuthenticatedAdmin actor) {
		if (id == actor.id()) {
			throw new ApiException(HttpStatus.CONFLICT, "CANNOT_CHANGE_SELF");
		}
		return this.admins.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ADMIN_NOT_FOUND"));
	}

	private void recordAction(AuditEventType type, AuthenticatedAdmin actor, AdminUser target, String ipAddress) {
		this.audit.recordOnTarget(type, actor.id(), TARGET_TYPE, target.getId().toString(), ipAddress,
				target.getEmail());
	}

	private static AdminResponse toResponse(AdminUser admin, Instant now) {
		return new AdminResponse(admin.getId(), admin.getEmail(), admin.getDisplayName(), admin.getStatus(),
				admin.isLocked(now), admin.isPasswordChangeRequired(), admin.getLastLoginAt(), admin.getCreatedAt());
	}

}
