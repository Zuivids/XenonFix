package lv.xenonfix.auth.mfa;

import java.io.ByteArrayOutputStream;

/** RFC 4648 Base32, how authenticator apps take their key. */
public final class Base32 {

	private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

	private Base32() {
	}

	/** Without padding, as the otpauth address and the apps expect. */
	public static String encode(byte[] data) {
		StringBuilder out = new StringBuilder((data.length * 8 + 4) / 5);
		int buffer = 0;
		int bits = 0;
		for (byte value : data) {
			buffer = (buffer << 8) | (value & 0xff);
			bits += 8;
			while (bits >= 5) {
				out.append(ALPHABET.charAt((buffer >>> (bits - 5)) & 0x1f));
				bits -= 5;
			}
		}
		if (bits > 0) {
			out.append(ALPHABET.charAt((buffer << (5 - bits)) & 0x1f));
		}
		return out.toString();
	}

	/** Ignores case, spaces and padding. */
	public static byte[] decode(String text) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		int buffer = 0;
		int bits = 0;
		for (char c : text.toUpperCase().toCharArray()) {
			if (c == ' ' || c == '=' || c == '-') {
				continue;
			}
			int value = ALPHABET.indexOf(c);
			if (value < 0) {
				throw new IllegalArgumentException("Not Base32");
			}
			buffer = (buffer << 5) | value;
			bits += 5;
			if (bits >= 8) {
				out.write((buffer >>> (bits - 8)) & 0xff);
				bits -= 8;
			}
		}
		return out.toByteArray();
	}

}
