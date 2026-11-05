package lv.xenonfix.config;

import java.nio.file.Path;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app")
public record AppProperties(Path uploadsPath, BootstrapAdmin bootstrapAdmin, Lockout lockout, RateLimit rateLimit,
		Session session, Reservation reservation, Mail mail) {

	/**
	 * @param email email of the first admin; empty means no admin is created
	 * @param password its password
	 * @param passwordChangeRequired whether it must be changed at first login
	 */
	public record BootstrapAdmin(String email, String password, boolean passwordChangeRequired) {
	}

	public record Lockout(int maxFailedAttemptsPerIp, int maxFailedAttempts, Duration duration) {
	}

	public record RateLimit(int limit, Duration window) {
	}

	public record Session(Duration idle, Duration absolute) {
	}

	public record Reservation(RateLimit rateLimit) {
	}

	/**
	 * @param from the address emails are sent from
	 * @param shop where the shop's copies go
	 */
	public record Mail(String from, String shop) {
	}

}
