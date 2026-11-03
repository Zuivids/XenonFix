package lv.xenonfix.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import lv.xenonfix.IntegrationTest;
import lv.xenonfix.MutableClock;
import lv.xenonfix.TestAdmins;
import lv.xenonfix.admin.AdminUserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
class AuthTests {

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
	void adminFeaturesNeedALogin() throws Exception {
		this.mvc.perform(get("/api/admin/me")).andExpect(status().isUnauthorized());
		this.mvc.perform(get("/api/admin/admins")).andExpect(status().isUnauthorized());
		this.mvc.perform(get("/api/admin/audit-log")).andExpect(status().isUnauthorized());
	}

	@Test
	void unknownPathsAreClosedByDefault() throws Exception {
		this.mvc.perform(get("/api/something-new")).andExpect(status().isUnauthorized());
	}

	@Test
	void publicEndpointsStayPublic() throws Exception {
		this.mvc.perform(get("/api/health")).andExpect(status().isOk());
	}

	@Test
	void loginGivesAccessAndChangesTheSessionId() throws Exception {
		String email = this.testAdmins.create();
		MockHttpSession session = new MockHttpSession();
		String sessionIdBefore = session.getId();

		this.testAdmins.attemptLogin(session, email, TestAdmins.PASSWORD)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("AUTHENTICATED"))
			.andExpect(jsonPath("$.admin.email").value(email));

		assertThat(session.getId()).isNotEqualTo(sessionIdBefore);
		this.mvc.perform(get("/api/admin/me").session(session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value(email));
		this.mvc.perform(get("/api/admin/admins").session(session)).andExpect(status().isOk());
	}

	@Test
	void emailIsNotCaseSensitive() throws Exception {
		String email = this.testAdmins.create();

		this.testAdmins.attemptLogin(new MockHttpSession(), email.toUpperCase(), TestAdmins.PASSWORD)
			.andExpect(status().isOk());
	}

	@Test
	void wrongPasswordAndUnknownEmailLookTheSame() throws Exception {
		String email = this.testAdmins.create();

		String wrongPassword = this.testAdmins.attemptLogin(new MockHttpSession(), email, "wrong password here")
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		String unknownEmail = this.testAdmins
			.attemptLogin(new MockHttpSession(), "nobody@example.com", "wrong password here")
			.andExpect(status().isUnauthorized())
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(unknownEmail).isEqualTo(wrongPassword);
	}

	@Test
	void postsNeedACsrfToken() throws Exception {
		String email = this.testAdmins.create();

		this.mvc
			.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"" + TestAdmins.PASSWORD + "\"}"))
			.andExpect(status().isForbidden());
	}

	@Test
	void tooManyFailuresBlockOnlyThatAddress() throws Exception {
		String email = this.testAdmins.create();
		String attacker = TestAdmins.randomIp();
		for (int i = 0; i < 5; i++) {
			this.testAdmins.attemptLogin(new MockHttpSession(), email, "wrong password here", attacker)
				.andExpect(status().isUnauthorized());
		}

		this.testAdmins.attemptLogin(new MockHttpSession(), email, TestAdmins.PASSWORD, attacker)
			.andExpect(status().isUnauthorized());
		this.testAdmins.attemptLogin(new MockHttpSession(), email, TestAdmins.PASSWORD, TestAdmins.randomIp())
			.andExpect(status().isOk());
	}

	@Test
	void failuresFromManyAddressesLockTheAccountForAWhile() throws Exception {
		String email = this.testAdmins.create();
		for (int i = 0; i < 20; i++) {
			this.testAdmins.attemptLogin(new MockHttpSession(), email, "wrong password here", TestAdmins.randomIp())
				.andExpect(status().isUnauthorized());
		}

		this.testAdmins.attemptLogin(new MockHttpSession(), email, TestAdmins.PASSWORD, TestAdmins.randomIp())
			.andExpect(status().isUnauthorized());

		this.clock.advance(Duration.ofMinutes(16));
		this.testAdmins.attemptLogin(new MockHttpSession(), email, TestAdmins.PASSWORD, TestAdmins.randomIp())
			.andExpect(status().isOk());
	}

	@Test
	void tooManyRequestsFromOneAddressAreRejected() throws Exception {
		String ip = TestAdmins.randomIp();
		for (int i = 0; i < 20; i++) {
			this.testAdmins.attemptLogin(new MockHttpSession(), "nobody@example.com", "wrong password here", ip)
				.andExpect(status().isUnauthorized());
		}

		this.testAdmins.attemptLogin(new MockHttpSession(), "nobody@example.com", "wrong password here", ip)
			.andExpect(status().isTooManyRequests())
			.andExpect(header().exists("Retry-After"));
	}

	@Test
	void temporaryPasswordMustBeChangedBeforeAnythingElse() throws Exception {
		String email = this.testAdmins.create(true);
		MockHttpSession session = this.testAdmins.login(email);

		this.mvc.perform(get("/api/admin/me").session(session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.passwordChangeRequired").value(true));
		this.mvc.perform(get("/api/admin/admins").session(session)).andExpect(status().isForbidden());

		changePassword(session, TestAdmins.PASSWORD, "a brand new password").andExpect(status().isNoContent());

		this.mvc.perform(get("/api/admin/me").session(session))
			.andExpect(jsonPath("$.passwordChangeRequired").value(false));
		this.mvc.perform(get("/api/admin/admins").session(session)).andExpect(status().isOk());
		this.testAdmins.attemptLogin(new MockHttpSession(), email, TestAdmins.PASSWORD)
			.andExpect(status().isUnauthorized());
		this.testAdmins.attemptLogin(new MockHttpSession(), email, "a brand new password").andExpect(status().isOk());
	}

	@Test
	void changingThePasswordChecksTheCurrentOneAndTheRules() throws Exception {
		MockHttpSession session = this.testAdmins.loggedIn();

		changePassword(session, "not the current one", "a brand new password").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("WRONG_CURRENT_PASSWORD"));
		changePassword(session, TestAdmins.PASSWORD, TestAdmins.PASSWORD).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("SAME_PASSWORD"));
		changePassword(session, TestAdmins.PASSWORD, "too short").andExpect(status().isBadRequest());
	}

	@Test
	void changingThePasswordEndsOtherSessionsButNotThisOne() throws Exception {
		String email = this.testAdmins.create();
		MockHttpSession thisSession = this.testAdmins.login(email);
		MockHttpSession otherSession = this.testAdmins.login(email);

		changePassword(thisSession, TestAdmins.PASSWORD, "a brand new password").andExpect(status().isNoContent());

		this.mvc.perform(get("/api/admin/me").session(thisSession)).andExpect(status().isOk());
		this.mvc.perform(get("/api/admin/me").session(otherSession)).andExpect(status().isUnauthorized());
	}

	@Test
	void sessionsEndAfterTheAbsoluteTimeout() throws Exception {
		MockHttpSession session = this.testAdmins.loggedIn();

		this.clock.advance(Duration.ofHours(7));
		this.mvc.perform(get("/api/admin/me").session(session)).andExpect(status().isOk());

		this.clock.advance(Duration.ofHours(2));
		this.mvc.perform(get("/api/admin/me").session(session)).andExpect(status().isUnauthorized());
	}

	@Test
	void logoutEndsTheSession() throws Exception {
		MockHttpSession session = this.testAdmins.loggedIn();

		this.mvc.perform(post("/api/admin/auth/logout").with(this.testAdmins.browser()).session(session))
			.andExpect(status().isNoContent());

		this.mvc.perform(get("/api/admin/me").session(new MockHttpSession())).andExpect(status().isUnauthorized());
		assertThat(session.isInvalid()).isTrue();
	}

	@Test
	void loginAndFailuresAreAuditedWithoutThePassword() throws Exception {
		String email = this.testAdmins.create();
		this.testAdmins.attemptLogin(new MockHttpSession(), email, "wrong password here")
			.andExpect(status().isUnauthorized());
		this.testAdmins.attemptLogin(new MockHttpSession(), email, TestAdmins.PASSWORD).andExpect(status().isOk());
		long id = this.testAdmins.id(email);

		assertThat(count("LOGIN_FAILED", id)).isEqualTo(1);
		assertThat(count("LOGIN_SUCCEEDED", id)).isEqualTo(1);
		assertThat(this.jdbcClient.sql("SELECT COUNT(*) FROM audit_log WHERE details LIKE '%wrong password%'")
			.query(Long.class)
			.single()).isZero();
	}

	@Test
	void passwordIsStoredAsAnArgon2Hash() {
		String email = this.testAdmins.create();

		assertThat(this.admins.findByEmail(email).orElseThrow().getPasswordHash()).startsWith("{argon2}");
	}

	private ResultActions changePassword(MockHttpSession session, String current, String next) throws Exception {
		return this.mvc.perform(post("/api/admin/me/password").with(this.testAdmins.browser()).session(session)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}"));
	}

	private long count(String eventType, long adminId) {
		return this.jdbcClient.sql("SELECT COUNT(*) FROM audit_log WHERE event_type = ? AND admin_id = ?")
			.params(eventType, adminId)
			.query(Long.class)
			.single();
	}

}
