package lv.xenonfix.admin;

import java.time.Clock;
import java.time.Instant;

import lv.xenonfix.audit.AuditEventType;
import lv.xenonfix.audit.AuditService;
import lv.xenonfix.common.PasswordRules;
import lv.xenonfix.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Creates the first admin from {@code app.bootstrap-admin} when the application starts and
 * there are no admins yet. After that, admins add each other in the admin panel and these
 * settings are ignored.
 */
@Component
class AdminBootstrap implements ApplicationRunner {

	private static final Logger logger = LoggerFactory.getLogger(AdminBootstrap.class);

	private final AppProperties.BootstrapAdmin properties;

	private final AdminUserRepository admins;

	private final PasswordEncoder passwordEncoder;

	private final AuditService audit;

	private final Clock clock;

	AdminBootstrap(AppProperties properties, AdminUserRepository admins, PasswordEncoder passwordEncoder,
			AuditService audit, Clock clock) {
		this.properties = properties.bootstrapAdmin();
		this.admins = admins;
		this.passwordEncoder = passwordEncoder;
		this.audit = audit;
		this.clock = clock;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (this.admins.count() > 0) {
			return;
		}
		if (!StringUtils.hasText(this.properties.email()) || !StringUtils.hasText(this.properties.password())) {
			logger.warn("There are no admins, and APP_ADMIN_EMAIL and APP_ADMIN_PASSWORD are not set");
			return;
		}
		if (this.properties.password().length() < PasswordRules.MIN_LENGTH) {
			throw new IllegalStateException(
					"APP_ADMIN_PASSWORD must have at least " + PasswordRules.MIN_LENGTH + " characters");
		}
		Instant now = this.clock.instant();
		AdminUser admin = this.admins.save(new AdminUser(Emails.normalize(this.properties.email()),
				this.passwordEncoder.encode(this.properties.password()), "Administrators",
				this.properties.passwordChangeRequired(), now));
		this.audit.recordOnTarget(AuditEventType.ADMIN_CREATED, null, "ADMIN", admin.getId().toString(), null,
				"pirmais administrators, izveidots startējot");
		logger.info("Created the first admin");
	}

}
