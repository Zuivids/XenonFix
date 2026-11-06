package lv.xenonfix.config;

import java.nio.file.Path;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app")
public record AppProperties(Path uploadsPath, BootstrapAdmin bootstrapAdmin, Lockout lockout, RateLimit rateLimit,
		Session session, Reservation reservation, Mail mail, Mfa mfa) {

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

	/**
	 * @param retention how long after its last change a cancelled or completed reservation keeps
	 * the customer's details; the privacy policy states the same period
	 */
	public record Reservation(RateLimit rateLimit, Duration retention) {
	}

	/**
	 * @param from the address emails are sent from
	 * @param shop where the shop's copies go
	 */
	public record Mail(String from, String shop) {
	}

	/**
	 * @param key encrypts the authenticator keys in the database: 32 random bytes in Base64
	 * @param pendingTimeout how long a session may wait at the second step of the login
	 */
	public record Mfa(String key, Duration pendingTimeout) {
	}

}
