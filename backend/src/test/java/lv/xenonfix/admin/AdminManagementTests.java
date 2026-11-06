package lv.xenonfix.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import lv.xenonfix.IntegrationTest;
import lv.xenonfix.MutableClock;
import lv.xenonfix.TestAdmins;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
class AdminManagementTests {

	private static final String TEMPORARY_PASSWORD = "a temporary password";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private AdminUserRepository admins;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private MutableClock clock;

	@Autowired
	private AdminRecoveryCodeRepository recoveryCodes;

	private TestAdmins testAdmins;

	@BeforeEach
	void setUp() {
		this.testAdmins = new TestAdmins(this.mvc, this.admins, this.passwordEncoder, this.clock);
	}

	@Test
	void anAdminAddsAnotherWhoMustChangeThePassword() throws Exception {
		MockHttpSession session = this.testAdmins.loggedIn();
		String email = "new-" + UUID.randomUUID() + "@example.com";

		create(session, email, TEMPORARY_PASSWORD).andExpect(status().isCreated())
			.andExpect(jsonPath("$.email").value(email))
			.andExpect(jsonPath("$.passwordChangeRequired").value(true))
			.andExpect(jsonPath("$.passwordHash").doesNotExist());

		this.mvc.perform(get("/api/admin/admins").session(session))
			.andExpect(jsonPath("$[*].email", hasItem(email)));
		this.testAdmins.attemptLogin(new MockHttpSession(), email, TEMPORARY_PASSWORD)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.admin.passwordChangeRequired").value(true));
	}

	@Test
	void anEmailCanBelongToOneAdminOnly() throws Exception {
		MockHttpSession session = this.testAdmins.loggedIn();
		String email = this.testAdmins.create();

		create(session, email.toUpperCase(), TEMPORARY_PASSWORD).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
	}

	@Test
	void aNewAdminNeedsAValidEmailAndALongEnoughPassword() throws Exception {
		MockHttpSession session = this.testAdmins.loggedIn();

		create(session, "not-an-email", TEMPORARY_PASSWORD).andExpect(status().isBadRequest());
		create(session, "new-" + UUID.randomUUID() + "@example.com", "too short").andExpect(status().isBadRequest());
	}

	@Test
	void disablingEndsSessionsAndBlocksLoginUntilEnabled() throws Exception {
		MockHttpSession session = this.testAdmins.loggedIn();
		String email = this.testAdmins.create();
		MockHttpSession otherSession = this.testAdmins.login(email);
		long id = this.testAdmins.id(email);

		action(session, id, "disable").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DISABLED"));

		this.mvc.perform(get("/api/admin/me").session(otherSession)).andExpect(status().isUnauthorized());
		this.testAdmins.attemptLogin(new MockHttpSession(), email, TestAdmins.PASSWORD)
			.andExpect(status().isUnauthorized());

		action(session, id, "enable").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));

		this.testAdmins.attemptLogin(new MockHttpSession(), email, TestAdmins.PASSWORD).andExpect(status().isOk());
	}

	@Test
	void nobodyCanDisableOrResetThemselves() throws Exception {
		String email = this.testAdmins.create();
		MockHttpSession session = this.testAdmins.login(email);
		long id = this.testAdmins.id(email);

		action(session, id, "disable").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CANNOT_CHANGE_SELF"));
		resetPassword(session, id).andExpect(status().isConflict());
	}

	@Test
	void aResetEndsSessionsAndRequiresANewPassword() throws Exception {
		MockHttpSession session = this.testAdmins.loggedIn();
		String email = this.testAdmins.create();
		MockHttpSession otherSession = this.testAdmins.login(email);

		resetPassword(session, this.testAdmins.id(email)).andExpect(status().isOk())
			.andExpect(jsonPath("$.passwordChangeRequired").value(true));

		this.mvc.perform(get("/api/admin/me").session(otherSession)).andExpect(status().isUnauthorized());
		this.testAdmins.attemptLogin(new MockHttpSession(), email, TEMPORARY_PASSWORD).andExpect(status().isOk());
	}

	@Test
	void resettingTheAppSetupEndsSessionsAndAsksForANewSetup() throws Exception {
		MockHttpSession session = this.testAdmins.loggedIn();
		String email = this.testAdmins.create();
		MockHttpSession otherSession = this.testAdmins.login(email);
		long id = this.testAdmins.id(email);
		this.mvc.perform(get("/api/admin/admins").session(session))
			.andExpect(jsonPath("$[?(@.id == " + id + ")].mfaEnabled").value(true));

		action(session, id, "reset-mfa").andExpect(status().isOk()).andExpect(jsonPath("$.mfaEnabled").value(false));

		this.mvc.perform(get("/api/admin/me").session(otherSession)).andExpect(status().isUnauthorized());
		this.testAdmins.attemptLogin(new MockHttpSession(), email, TestAdmins.PASSWORD)
			.andExpect(jsonPath("$.status").value("MFA_SETUP_REQUIRED"));
		// A new key, and the old one no longer works
		this.testAdmins.login(email);
		assertThat(this.recoveryCodes.countByAdminIdAndUsedAtIsNull(id)).isEqualTo(10);
	}

	@Test
	void nobodyCanResetTheirOwnAppSetup() throws Exception {
		String email = this.testAdmins.create();
		MockHttpSession session = this.testAdmins.login(email);

		action(session, this.testAdmins.id(email), "reset-mfa").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CANNOT_CHANGE_SELF"));
	}

	@Test
	void aMissingAdminIsNotFound() throws Exception {
		MockHttpSession session = this.testAdmins.loggedIn();

		action(session, 999_999_999, "disable").andExpect(status().isNotFound());
	}

	@Test
	void theAuditLogShowsWhoDidWhat() throws Exception {
		String actorEmail = this.testAdmins.create();
		MockHttpSession session = this.testAdmins.login(actorEmail);
		String email = "new-" + UUID.randomUUID() + "@example.com";
		create(session, email, TEMPORARY_PASSWORD).andExpect(status().isCreated());

		String body = this.mvc.perform(get("/api/admin/audit-log").session(session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.items[0].eventType").value("ADMIN_CREATED"))
			.andExpect(jsonPath("$.items[0].adminEmail").value(actorEmail))
			.andExpect(jsonPath("$.items[0].targetType").value("ADMIN"))
			.andExpect(jsonPath("$.items[0].details").value(email))
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(body).doesNotContain(TEMPORARY_PASSWORD);
	}

	private ResultActions create(MockHttpSession session, String email, String password) throws Exception {
		return this.mvc.perform(post("/api/admin/admins").with(this.testAdmins.browser()).session(session)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"" + email + "\",\"displayName\":\"New\",\"password\":\"" + password + "\"}"));
	}

	private ResultActions action(MockHttpSession session, long id, String action) throws Exception {
		return this.mvc.perform(
				post("/api/admin/admins/" + id + "/" + action).with(this.testAdmins.browser()).session(session));
	}

	private ResultActions resetPassword(MockHttpSession session, long id) throws Exception {
		return this.mvc.perform(post("/api/admin/admins/" + id + "/reset-password").with(this.testAdmins.browser())
			.session(session)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"password\":\"" + TEMPORARY_PASSWORD + "\"}"));
	}

}
