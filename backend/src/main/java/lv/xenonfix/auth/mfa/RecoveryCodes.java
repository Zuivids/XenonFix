package lv.xenonfix.auth.mfa;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

/**
 * One-time backup codes such as {@code K7QF2-M9XPD}: 10 random characters, 50 bits. Stored
 * only as SHA-256 hashes. That is enough for random codes of this length, as every wrong
 * attempt also counts towards the lockout.
 */
public final class RecoveryCodes {

	public static final int COUNT = 10;

	/** Without 0, 1, I and O, which are easy to mix up on paper. */
	private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

	private static final int LENGTH = 10;

	private static final SecureRandom RANDOM = new SecureRandom();

	private RecoveryCodes() {
	}

	public static List<String> generate() {
		return IntStream.range(0, COUNT).mapToObj((i) -> generateOne()).toList();
	}

	/**
	 * @return the hash of what the admin typed, or {@code null} if it cannot be a backup code;
	 * case, spaces and the dash do not matter
	 */
	public static String hashOfTyped(String typed) {
		if (typed == null) {
			return null;
		}
		String normalized = typed.toUpperCase(Locale.ROOT).replaceAll("[\\s-]", "");
		if (normalized.length() != LENGTH || !normalized.chars().allMatch((c) -> ALPHABET.indexOf(c) >= 0)) {
			return null;
		}
		return hash(normalized);
	}

	private static String generateOne() {
		StringBuilder code = new StringBuilder(LENGTH + 1);
		for (int i = 0; i < LENGTH; i++) {
			if (i == LENGTH / 2) {
				code.append('-');
			}
			code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
		}
		return code.toString();
	}

	private static String hash(String normalized) {
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.US_ASCII)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is part of every Java runtime", ex);
		}
	}

}
