package lv.xenonfix.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lv.xenonfix.config.AppProperties.RateLimit;

import org.springframework.stereotype.Component;

/**
 * Token bucket per key. A bucket holds up to {@code limit} tokens, refills at {@code limit}
 * per {@code window}, and each request takes one token. This allows short bursts while
 * capping the average rate.
 *
 * <p>
 * State is in memory, so limits are per application instance and reset on restart. That
 * is fine while the app runs as a single instance.
 */
@Component
public class RateLimiter {

	/** Upper bound on tracked keys, so many distinct IPs cannot exhaust memory. */
	private static final int MAX_KEYS = 100_000;

	private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
		.maximumSize(MAX_KEYS)
		// Idle buckets are full again long before this, so dropping them changes nothing
		.expireAfterAccess(Duration.ofHours(2))
		.build();

	private final Clock clock;

	RateLimiter(Clock clock) {
		this.clock = clock;
	}

	/** Takes a token if one is available. */
	public Decision tryAcquire(String key, RateLimit rule) {
		Bucket bucket = this.buckets.get(key, (k) -> new Bucket(rule.limit(), this.clock.instant()));
		return bucket.tryAcquire(rule, this.clock.instant());
	}

	/**
	 * @param allowed whether the request may proceed
	 * @param retryAfter when not allowed, how long until the next token is available
	 */
	public record Decision(boolean allowed, Duration retryAfter) {
	}

	private static final class Bucket {

		private double tokens;

		private Instant lastRefill;

		Bucket(int capacity, Instant now) {
			this.tokens = capacity;
			this.lastRefill = now;
		}

		synchronized Decision tryAcquire(RateLimit rule, Instant now) {
			double perNano = (double) rule.limit() / rule.window().toNanos();
			long elapsed = Math.max(0, Duration.between(this.lastRefill, now).toNanos());
			this.tokens = Math.min(rule.limit(), this.tokens + elapsed * perNano);
			this.lastRefill = now;
			if (this.tokens >= 1) {
				this.tokens -= 1;
				return new Decision(true, Duration.ZERO);
			}
			long nanosUntilToken = (long) Math.ceil((1 - this.tokens) / perNano);
			return new Decision(false, Duration.ofNanos(nanosUntilToken));
		}

	}

}
