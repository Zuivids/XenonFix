package lv.xenonfix.auth.mfa;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TotpTests {

	/** The SHA-1 key of RFC 6238, appendix B. */
	private static final byte[] RFC_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

	/** RFC 6238 appendix B gives 8 digits; apps use the last 6. */
	@ParameterizedTest
	@CsvSource({ "59, 94287082", "1111111109, 07081804", "1111111111, 14050471", "1234567890, 89005924",
			"2000000000, 69279037", "20000000000, 65353130" })
	void matchesTheRfcTestValues(long seconds, String eightDigits) {
		long step = Totp.step(Instant.ofEpochSecond(seconds));

		assertThat(Totp.code(RFC_SECRET, step)).isEqualTo(eightDigits.substring(2));
	}

	@Test
	void acceptsOneStepEitherSideAndNoMore() {
		Instant now = Instant.ofEpochSecond(1_234_567_890);
		long step = Totp.step(now);

		assertThat(Totp.matchingStep(RFC_SECRET, Totp.code(RFC_SECRET, step), now)).hasValue(step);
		assertThat(Totp.matchingStep(RFC_SECRET, Totp.code(RFC_SECRET, step - 1), now)).hasValue(step - 1);
		assertThat(Totp.matchingStep(RFC_SECRET, Totp.code(RFC_SECRET, step + 1), now)).hasValue(step + 1);
		assertThat(Totp.matchingStep(RFC_SECRET, Totp.code(RFC_SECRET, step - 2), now)).isEmpty();
		assertThat(Totp.matchingStep(RFC_SECRET, Totp.code(RFC_SECRET, step + 2), now)).isEmpty();
	}

	@Test
	void refusesWhatIsNotSixDigits() {
		Instant now = Instant.ofEpochSecond(59);

		assertThat(Totp.matchingStep(RFC_SECRET, null, now)).isEmpty();
		assertThat(Totp.matchingStep(RFC_SECRET, "", now)).isEmpty();
		assertThat(Totp.matchingStep(RFC_SECRET, "94287082", now)).isEmpty();
		assertThat(Totp.matchingStep(RFC_SECRET, "28708", now)).isEmpty();
		assertThat(Totp.matchingStep(RFC_SECRET, "abcdef", now)).isEmpty();
	}

	@Test
	void base32RoundTripsAndMatchesTheRfc() {
		// RFC 4648 section 10
		assertThat(Base32.encode("foobar".getBytes(StandardCharsets.US_ASCII))).isEqualTo("MZXW6YTBOI");
		assertThat(Base32.decode("mzxw 6ytb oi")).isEqualTo("foobar".getBytes(StandardCharsets.US_ASCII));
		assertThat(Base32.decode(Base32.encode(RFC_SECRET))).isEqualTo(RFC_SECRET);
	}

	@Test
	void recoveryCodesAreUniqueAndTypedLoosely() {
		var codes = RecoveryCodes.generate();

		assertThat(codes).hasSize(10).doesNotHaveDuplicates().allMatch((code) -> code.matches("[A-Z2-9]{5}-[A-Z2-9]{5}"));
		String code = codes.getFirst();
		assertThat(RecoveryCodes.hashOfTyped(" " + code.toLowerCase().replace("-", " ") + " "))
			.isEqualTo(RecoveryCodes.hashOfTyped(code));
		assertThat(RecoveryCodes.hashOfTyped("123456")).isNull();
		assertThat(RecoveryCodes.hashOfTyped("OOOOO-IIIII")).isNull();
	}

}
