package lv.xenonfix;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A clock that tests move forward by hand. */
public class MutableClock extends Clock {

	private volatile Instant now;

	MutableClock(Instant now) {
		this.now = now;
	}

	public void advance(Duration duration) {
		this.now = this.now.plus(duration);
	}

	@Override
	public Instant instant() {
		return this.now;
	}

	@Override
	public ZoneId getZone() {
		return ZoneOffset.UTC;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		return this;
	}

}
