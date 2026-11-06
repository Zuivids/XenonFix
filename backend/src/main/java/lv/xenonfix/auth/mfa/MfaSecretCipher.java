package lv.xenonfix.auth.mfa;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import lv.xenonfix.config.AppProperties;

import org.springframework.stereotype.Component;

/**
 * Encrypts the admins' authenticator keys before they go into the database, with AES-GCM and
 * the key in {@code APP_MFA_KEY}. A copy of the database alone is then not enough to make
 * codes. Without a valid key the application does not start.
 */
@Component
public class MfaSecretCipher {

	private static final int KEY_BYTES = 32;

	private static final int IV_BYTES = 12;

	private static final int TAG_BITS = 128;

	private final SecretKey key;

	private final SecureRandom random = new SecureRandom();

	MfaSecretCipher(AppProperties properties) {
		this.key = parseKey(properties.mfa() != null ? properties.mfa().key() : null);
	}

	/** @return the IV followed by the encrypted key and its tag */
	public byte[] encrypt(byte[] secret) {
		byte[] iv = new byte[IV_BYTES];
		this.random.nextBytes(iv);
		try {
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, this.key, new GCMParameterSpec(TAG_BITS, iv));
			byte[] encrypted = cipher.doFinal(secret);
			return ByteBuffer.allocate(IV_BYTES + encrypted.length).put(iv).put(encrypted).array();
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("Could not encrypt the authenticator key", ex);
		}
	}

	public byte[] decrypt(byte[] stored) {
		try {
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, this.key, new GCMParameterSpec(TAG_BITS, stored, 0, IV_BYTES));
			return cipher.doFinal(stored, IV_BYTES, stored.length - IV_BYTES);
		}
		catch (GeneralSecurityException ex) {
			// A different APP_MFA_KEY than the one the key was stored with
			throw new IllegalStateException("Could not decrypt an authenticator key: APP_MFA_KEY has changed", ex);
		}
	}

	/** Never includes the value in the message, so it cannot end up in a log. */
	private static SecretKey parseKey(String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalStateException("APP_MFA_KEY is not set. It must be " + KEY_BYTES
					+ " random bytes in Base64, for example from: openssl rand -base64 32");
		}
		byte[] bytes;
		try {
			bytes = Base64.getDecoder().decode(value.strip());
		}
		catch (IllegalArgumentException ex) {
			throw new IllegalStateException("APP_MFA_KEY is not valid Base64");
		}
		if (bytes.length != KEY_BYTES) {
			throw new IllegalStateException("APP_MFA_KEY must be " + KEY_BYTES + " bytes, it has " + bytes.length);
		}
		SecretKey key = new SecretKeySpec(bytes, "AES");
		Arrays.fill(bytes, (byte) 0);
		return key;
	}

}
