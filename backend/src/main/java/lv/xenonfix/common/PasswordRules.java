package lv.xenonfix.common;

public final class PasswordRules {

	public static final int MIN_LENGTH = 12;

	/** Hashing a very long password costs the server more than it adds in security. */
	public static final int MAX_LENGTH = 128;

	private PasswordRules() {
	}

}
