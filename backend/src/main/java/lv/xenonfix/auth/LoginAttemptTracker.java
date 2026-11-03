package lv.xenonfix.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lv.xenonfix.config.AppProperties;

import org.springframework.stereotype.Component;

/**
 * Counts failed login attempts per account and IP address. After too many failures that
 * IP is blocked for that account for a while. An attacker who keeps guessing blocks only
 * their own address, while the real admin can still log in from theirs.
 *
 * <p>
 * Kept in memory (per instance, reset on restart). The account-wide lock in the database
 * is the durable second line of defence.
 */
@Component
public class LoginAttemptTracker {

	private static final int MAX_KEYS = 100_000;

	private final Cache<String, Attempts> attempts;

	private final AppProperties.Lockout lockout;

	private final Clock clock;

	LoginAttemptTracker(AppProperties properties, Clock clock) {
		this.lockout = properties.lockout();
		this.clock = clock;
		this.attempts = Caffeine.newBuilder()
			.maximumSize(MAX_KEYS)
			// Failures older than this no longer count
			.expireAfterWrite(this.lockout.duration())
			.build();
	}

	boolean isBlocked(long adminId, String ip) {
		Attempts current = this.attempts.getIfPresent(key(adminId, ip));
		return current != null && current.blockedUntil() != null
				&& current.blockedUntil().isAfter(this.clock.instant());
	}

	/** @return {@code true} if this failure blocked the IP for the account */
	boolean recordFailure(long adminId, String ip) {
		Instant now = this.clock.instant();
		Duration duration = this.lockout.duration();
		Attempts updated = this.attempts.asMap().compute(key(adminId, ip), (k, previous) -> {
			int failures = (previous == null || previous.blockedUntil() != null) ? 1 : previous.failures() + 1;
			Instant blockedUntil = failures >= this.lockout.maxFailedAttemptsPerIp() ? now.plus(duration) : null;
			return new Attempts(failures, blockedUntil);
		});
		return updated.blockedUntil() != null;
	}

	void reset(long adminId, String ip) {
		this.attempts.invalidate(key(adminId, ip));
	}

	/** Lifts the per-IP blocks of an account on all addresses. */
	public void resetAll(long adminId) {
		String prefix = adminId + "|";
		this.attempts.asMap().keySet().removeIf((key) -> key.startsWith(prefix));
	}

	private static String key(long adminId, String ip) {
		return adminId + "|" + ip;
	}

	private record Attempts(int failures, Instant blockedUntil) {
	}

}
