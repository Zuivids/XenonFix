package lv.xenonfix.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;

import com.jayway.jsonpath.JsonPath;
import lv.xenonfix.IntegrationTest;
import lv.xenonfix.MutableClock;
import lv.xenonfix.TestAdmins;
import lv.xenonfix.admin.AdminUserRepository;
import lv.xenonfix.auth.mfa.Base32;
import lv.xenonfix.auth.mfa.Totp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class MfaTests {

	private static final String SETUP = "/api/admin/me/mfa/setup";

	private static final String VERIFY = "/api/admin/auth/mfa";

	private static final String RECOVERY_CODES = "/api/admin/me/mfa/recovery-codes";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private AdminUserRepository admins;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private MutableClock clock;

	@Autowired
	private JdbcClient jdbcClient;

	private TestAdmins testAdmins;

	@BeforeEach
	void setUp() {
		this.testAdmins = new TestAdmins(this.mvc, this.admins, this.passwordEncoder, this.clock);
	}

	@Test
	void setupShowsAQrCodeAndTheSameKeyUntilProven() throws Exception {
		String email = this.testAdmins.create();
		MockHttpSession session = passwordOnly(email);

		String first = setupDetails(session);
		String again = setupDetails(session);

		String secret = JsonPath.read(first, "$.secret");
		assertThat(secret).matches("([A-Z2-7]{4} )+[A-Z2-7]{1,4}");
		assertThat(JsonPath.<String>read(again, "$.secret")).isEqualTo(secret);
		// What a phone reads from the QR code
		assertThat(decodeQr(JsonPath.read(first, "$.qrCode"))).isEqualTo("otpauth://totp/XenonFix%3A"
				+ email.replace("@", "%40") + "?secret=" + secret.replace(" ", "")
				+ "&issuer=XenonFix&algorithm=SHA1&digits=6&period=30");
	}

	private static String decodeQr(String dataUri) throws Exception {
		assertThat(dataUri).startsWith("data:image/png;base64,");
		byte[] png = java.util.Base64.getDecoder().decode(dataUri.substring("data:image/png;base64,".length()));
		java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(png));
		int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
		var source = new com.google.zxing.RGBLuminanceSource(image.getWidth(), image.getHeight(), pixels);
		return new com.google.zxing.qrcode.QRCodeReader()
			.decode(new com.google.zxing.BinaryBitmap(new com.google.zxing.common.HybridBinarizer(source)))
			.getText();
	}

	@Test
	void setupNeedsARightCodeAndGivesTenBackupCodesOnce() throws Exception {
		String email = this.testAdmins.create();
		MockHttpSession session = passwordOnly(email);
		byte[] secret = Base32.decode(JsonPath.read(setupDetails(session), "$.secret"));

		this.testAdmins.sendCode(session, SETUP, wrongCode(secret)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_CODE"));
		this.mvc.perform(get("/api/admin/admins").session(session)).andExpect(status().isForbidden());

		String done = this.testAdmins.sendCode(session, SETUP, code(secret))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.codes.length()").value(10))
			.andExpect(jsonPath("$.admin.step").value("READY"))
			.andReturn()
			.getResponse()
			.getContentAsString();

		this.mvc.perform(get("/api/admin/admins").session(session)).andExpect(status().isOk());
		this.mvc.perform(get(RECOVERY_CODES).session(session)).andExpect(jsonPath("$.left").value(10));
		// Setting up again is not possible once done
		this.mvc.perform(get(SETUP).session(session)).andExpect(status().isForbidden());
		List<String> codes = JsonPath.read(done, "$.codes");
		long id = this.testAdmins.id(email);
		assertThat(audit("MFA_ENABLED", id)).isEqualTo(1);
		assertThat(this.jdbcClient.sql("SELECT COUNT(*) FROM admin_recovery_code WHERE code_hash IN (:codes)")
			.param("codes", codes)
			.query(Long.class)
			.single()).as("backup codes are stored only as hashes").isZero();
	}

	@Test
	void theKeyIsStoredEncrypted() throws Exception {
		String email = this.testAdmins.create();
		this.testAdmins.login(email);

		byte[] stored = this.jdbcClient.sql("SELECT mfa_secret FROM admin_user WHERE email = ?")
			.param(email)
			.query(byte[].class)
			.single();

		byte[] secret = TestAdmins.secretOf(email);
		assertThat(stored).isNotEqualTo(secret).hasSizeGreaterThan(secret.length);
		assertThat(new String(stored, java.nio.charset.StandardCharsets.ISO_8859_1))
			.doesNotContain(new String(secret, java.nio.charset.StandardCharsets.ISO_8859_1));
	}

	@Test
	void anAdminWithTheAppNeedsTheCode() throws Exception {
		String email = this.testAdmins.create();
		this.testAdmins.login(email);
		this.testAdmins.nextCodeStep();
		MockHttpSession session = new MockHttpSession();

		this.testAdmins.attemptLogin(session, email, TestAdmins.PASSWORD)
			.andExpect(jsonPath("$.status").value("MFA_REQUIRED"));
		this.mvc.perform(get("/api/admin/me").session(session)).andExpect(jsonPath("$.step").value("MFA_REQUIRED"));
		this.mvc.perform(get("/api/admin/admins").session(session)).andExpect(status().isForbidden());
		this.mvc.perform(get(SETUP).session(session)).andExpect(status().isForbidden());
		String sessionIdBefore = session.getId();

		this.testAdmins.sendCode(session, VERIFY, this.testAdmins.currentCode(email))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("AUTHENTICATED"))
			.andExpect(jsonPath("$.admin.step").value("READY"));

		assertThat(session.getId()).isNotEqualTo(sessionIdBefore);
		this.mvc.perform(get("/api/admin/admins").session(session)).andExpect(status().isOk());
	}

	@Test
	void aCodeWorksOnlyOnce() throws Exception {
		String email = this.testAdmins.create();
		this.testAdmins.login(email);
		this.testAdmins.nextCodeStep();
		String code = this.testAdmins.currentCode(email);
		MockHttpSession first = passwordOnly(email);
		this.testAdmins.sendCode(first, VERIFY, code).andExpect(status().isOk());

		MockHttpSession second = passwordOnly(email);

		this.testAdmins.sendCode(second, VERIFY, code).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_CODE"));
		this.mvc.perform(get("/api/admin/admins").session(second)).andExpect(status().isForbidden());
	}

	@Test
	void aBackupCodeWorksOnceInsteadOfTheApp() throws Exception {
		String email = this.testAdmins.create();
		List<String> codes = setUpAndGetBackupCodes(email);
		long id = this.testAdmins.id(email);

		MockHttpSession session = passwordOnly(email);
		this.testAdmins.sendCode(session, VERIFY, codes.get(3).toLowerCase()).andExpect(status().isOk());
		this.mvc.perform(get("/api/admin/admins").session(session)).andExpect(status().isOk());
		this.mvc.perform(get(RECOVERY_CODES).session(session)).andExpect(jsonPath("$.left").value(9));
		assertThat(lastDetails("LOGIN_SUCCEEDED", id)).isEqualTo("ar rezerves kodu, atlikuši 9");

		MockHttpSession again = passwordOnly(email);
		this.testAdmins.sendCode(again, VERIFY, codes.get(3)).andExpect(status().isBadRequest());
	}

	@Test
	void wrongCodesCountTowardsTheLockout() throws Exception {
		String email = this.testAdmins.create();
		this.testAdmins.login(email);
		String attacker = TestAdmins.randomIp();
		long id = this.testAdmins.id(email);

		for (int i = 0; i < 5; i++) {
			MockHttpSession session = new MockHttpSession();
			this.testAdmins.attemptLogin(session, email, TestAdmins.PASSWORD, attacker).andExpect(status().isOk());
			this.mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(VERIFY)
				.with(this.testAdmins.browser(attacker))
				.session(session)
				.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
				.content("{\"code\":\"000000\"}")).andExpect(status().isBadRequest());
		}

		// Blocked from that address, even with the right password
		this.testAdmins.attemptLogin(new MockHttpSession(), email, TestAdmins.PASSWORD, attacker)
			.andExpect(status().isUnauthorized());
		assertThat(audit("MFA_FAILED", id)).isGreaterThanOrEqualTo(5);
		assertThat(audit("BLOCKED_FOR_IP", id)).isEqualTo(1);
		assertThat(this.jdbcClient.sql("SELECT COUNT(*) FROM audit_log WHERE details LIKE '%000000%'")
			.query(Long.class)
			.single()).isZero();
		// The admin's own address still works
		this.testAdmins.login(email);
	}

	@Test
	void theSecondStepMustFollowWithinFiveMinutes() throws Exception {
		String email = this.testAdmins.create();
		this.testAdmins.login(email);
		MockHttpSession session = passwordOnly(email);

		this.clock.advance(Duration.ofMinutes(5));

		this.mvc.perform(get("/api/admin/me").session(session)).andExpect(status().isUnauthorized());
		this.testAdmins.sendCode(session, VERIFY, this.testAdmins.currentCode(email))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void newBackupCodesNeedACurrentCodeAndReplaceTheOldOnes() throws Exception {
		String email = this.testAdmins.create();
		List<String> old = setUpAndGetBackupCodes(email);
		MockHttpSession session = this.testAdmins.login(email);

		this.testAdmins.sendCode(session, RECOVERY_CODES, "000000").andExpect(status().isBadRequest());
		this.testAdmins.nextCodeStep();
		String body = this.testAdmins.sendCode(session, RECOVERY_CODES, this.testAdmins.currentCode(email))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.codes.length()").value(10))
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(JsonPath.<List<String>>read(body, "$.codes")).doesNotContainAnyElementsOf(old);
		MockHttpSession withOld = passwordOnly(email);
		this.testAdmins.sendCode(withOld, VERIFY, old.getFirst()).andExpect(status().isBadRequest());
		assertThat(audit("MFA_RECOVERY_CODES_RENEWED", this.testAdmins.id(email))).isEqualTo(1);
	}

	@Test
	void aTemporaryPasswordIsChangedAfterTheSecondStep() throws Exception {
		String email = this.testAdmins.create(true);
		MockHttpSession session = passwordOnly(email);

		// Not before the second step
		this.mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/admin/me/password")
			.with(this.testAdmins.browser())
			.session(session)
			.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
			.content("{\"currentPassword\":\"" + TestAdmins.PASSWORD + "\",\"newPassword\":\"a brand new password\"}"))
			.andExpect(status().isForbidden());

		this.testAdmins.secondStep(session, email, "MFA_SETUP_REQUIRED");

		this.mvc.perform(get("/api/admin/me").session(session))
			.andExpect(jsonPath("$.step").value("PASSWORD_CHANGE_REQUIRED"));
		this.mvc.perform(get("/api/admin/admins").session(session)).andExpect(status().isForbidden());
	}

	private MockHttpSession passwordOnly(String email) throws Exception {
		MockHttpSession session = new MockHttpSession();
		this.testAdmins.attemptLogin(session, email, TestAdmins.PASSWORD).andExpect(status().isOk());
		return session;
	}

	private String setupDetails(MockHttpSession session) throws Exception {
		return this.mvc.perform(get(SETUP).session(session))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
	}

	private List<String> setUpAndGetBackupCodes(String email) throws Exception {
		MockHttpSession session = passwordOnly(email);
		byte[] secret = Base32.decode(JsonPath.read(setupDetails(session), "$.secret"));
		TestAdmins.remember(email, secret);
		String body = this.testAdmins.sendCode(session, SETUP, code(secret))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$.codes");
	}

	private String code(byte[] secret) {
		return Totp.code(secret, Totp.step(this.clock.instant()));
	}

	/** A code that is none of the accepted ones around now. */
	private String wrongCode(byte[] secret) {
		long step = Totp.step(this.clock.instant());
		for (int candidate = 0;; candidate++) {
			String code = "%06d".formatted(candidate);
			if (Totp.matchingStep(secret, code, this.clock.instant()).isEmpty()
					&& !code.equals(Totp.code(secret, step))) {
				return code;
			}
		}
	}

	private long audit(String eventType, long adminId) {
		return this.jdbcClient.sql("SELECT COUNT(*) FROM audit_log WHERE event_type = ? AND admin_id = ?")
			.params(eventType, adminId)
			.query(Long.class)
			.single();
	}

	private String lastDetails(String eventType, long adminId) {
		return this.jdbcClient
			.sql("SELECT details FROM audit_log WHERE event_type = ? AND admin_id = ? ORDER BY id DESC LIMIT 1")
			.params(eventType, adminId)
			.query(String.class)
			.single();
	}

}
