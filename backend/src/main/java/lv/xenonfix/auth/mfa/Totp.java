package lv.xenonfix.auth.mfa;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.OptionalLong;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * The codes of authenticator apps (TOTP, RFC 6238): HMAC-SHA1, 30-second steps, 6 digits, the
 * values every app uses by default.
 */
public final class Totp {

	public static final int STEP_SECONDS = 30;

	private static final int DIGITS = 6;

	private static final int MODULUS = 1_000_000;

	/** Steps either side that are still accepted, for a phone clock that is slightly off. */
	private static final int ALLOWED_DRIFT = 1;

	private Totp() {
	}

	public static long step(Instant time) {
		return Math.floorDiv(time.getEpochSecond(), STEP_SECONDS);
	}

	/** The code for one time step, with leading zeros. */
	public static String code(byte[] secret, long step) {
		try {
			Mac mac = Mac.getInstance("HmacSHA1");
			mac.init(new SecretKeySpec(secret, "HmacSHA1"));
			byte[] hash = mac.doFinal(longBytes(step));
			// Dynamic truncation, RFC 4226 section 5.3
			int offset = hash[hash.length - 1] & 0x0f;
			int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
					| ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
			return String.format("%0" + DIGITS + "d", binary % MODULUS);
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("HmacSHA1 is part of every Java runtime", ex);
		}
	}

	/**
	 * @return the time step whose code this is, near {@code now}, or empty if it is none of them
	 */
	public static OptionalLong matchingStep(byte[] secret, String code, Instant now) {
		if (code == null || !code.matches("\\d{" + DIGITS + "}")) {
			return OptionalLong.empty();
		}
		long current = step(now);
		for (long step = current - ALLOWED_DRIFT; step <= current + ALLOWED_DRIFT; step++) {
			// Compared in constant time, so timing does not reveal how many digits were right
			if (MessageDigest.isEqual(code(secret, step).getBytes(), code.getBytes())) {
				return OptionalLong.of(step);
			}
		}
		return OptionalLong.empty();
	}

	private static byte[] longBytes(long value) {
		byte[] bytes = new byte[8];
		for (int i = 7; i >= 0; i--) {
			bytes[i] = (byte) value;
			value >>>= 8;
		}
		return bytes;
	}

}
