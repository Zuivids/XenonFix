package lv.xenonfix;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import lv.xenonfix.admin.AdminUser;
import lv.xenonfix.admin.AdminUserRepository;
import lv.xenonfix.auth.mfa.Base32;
import lv.xenonfix.auth.mfa.Totp;

import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Creates admins and logs them in through the real API, like the frontend does. Each instance
 * uses its own client IP, so tests do not share rate limits.
 */
public class TestAdmins {

	public static final String PASSWORD = "correct horse battery";

	/** Each admin's authenticator key, as their phone would have it. Shared, like the database. */
	private static final Map<String, byte[]> SECRETS = new ConcurrentHashMap<>();

	private final MockMvc mvc;

	private final AdminUserRepository admins;

	private final PasswordEncoder passwordEncoder;

	private final Clock clock;

	private final String ip = randomIp();

	public TestAdmins(MockMvc mvc, AdminUserRepository admins, PasswordEncoder passwordEncoder, Clock clock) {
		this.mvc = mvc;
		this.admins = admins;
		this.passwordEncoder = passwordEncoder;
		this.clock = clock;
	}

	/** A new admin who has their own password; returns the email. */
	public String create() {
		return create(false);
	}

	public String create(boolean passwordChangeRequired) {
		String email = "admin-" + UUID.randomUUID() + "@example.com";
		this.admins.save(new AdminUser(email, this.passwordEncoder.encode(PASSWORD), "Test", passwordChangeRequired,
				this.clock.instant()));
		return email;
	}

	/** A new admin, logged in. */
	public MockHttpSession loggedIn() throws Exception {
		return login(create());
	}

	/** Both steps of the login; sets up the authenticator app the first time, as the panel does. */
	public MockHttpSession login(String email) throws Exception {
		return login(email, PASSWORD);
	}

	public MockHttpSession login(String email, String password) throws Exception {
		MockHttpSession session = new MockHttpSession();
		nextCodeStep();
		String response = attemptLogin(session, email, password, this.ip).andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		secondStep(session, email, JsonPath.read(response, "$.status"));
		return session;
	}

	/**
	 * The second step, after a password step that answered {@code status}: a code from the
	 * app, or setting up the app if the admin has none.
	 */
	public void secondStep(MockHttpSession session, String email, String status) throws Exception {
		if ("MFA_SETUP_REQUIRED".equals(status)) {
			String setup = this.mvc.perform(get("/api/admin/me/mfa/setup").session(session))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();
			byte[] secret = Base32.decode(JsonPath.read(setup, "$.secret"));
			SECRETS.put(email, secret);
			sendCode(session, "/api/admin/me/mfa/setup", currentCode(email)).andExpect(status().isOk());
		}
		else {
			sendCode(session, "/api/admin/auth/mfa", currentCode(email)).andExpect(status().isOk());
		}
	}

	/** A request with a code, whose outcome the test checks itself. */
	public ResultActions sendCode(MockHttpSession session, String path, String code) throws Exception {
		return this.mvc.perform(post(path).with(browser()).session(session)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"code\":\"" + code + "\"}"));
	}

	/** The code the admin's app shows now. */
	public String currentCode(String email) {
		return Totp.code(SECRETS.get(email), Totp.step(this.clock.instant()));
	}

	public static byte[] secretOf(String email) {
		return SECRETS.get(email);
	}

	/** For a test that set up the app itself. */
	public static void remember(String email, byte[] secret) {
		SECRETS.put(email, secret);
	}

	/**
	 * Moves the test clock on to the next 30-second step, so the next code is a new one: the
	 * backend refuses a code that was already used.
	 */
	public void nextCodeStep() {
		if (this.clock instanceof MutableClock mutable) {
			mutable.advance(Duration.ofSeconds(Totp.STEP_SECONDS));
		}
	}

	/** A login request whose outcome the test checks itself. */
	public ResultActions attemptLogin(MockHttpSession session, String email, String password) throws Exception {
		return attemptLogin(session, email, password, this.ip);
	}

	public ResultActions attemptLogin(MockHttpSession session, String email, String password, String ip)
			throws Exception {
		return this.mvc.perform(post("/api/admin/auth/login").with(browser(ip)).session(session)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
	}

	public long id(String email) {
		return this.admins.findByEmail(email).orElseThrow().getId();
	}

	public RequestPostProcessor browser() {
		return browser(this.ip);
	}

	/** Adds a CSRF token the way the frontend does, sent from the given client IP. */
	public RequestPostProcessor browser(String ip) {
		return (request) -> {
			try {
				Cookie token = this.mvc.perform(get("/api/admin/auth/csrf"))
					.andReturn()
					.getResponse()
					.getCookie("XSRF-TOKEN");
				request.setCookies(token);
				request.addHeader("X-XSRF-TOKEN", token.getValue());
				request.setRemoteAddr(ip);
				return request;
			}
			catch (Exception ex) {
				throw new IllegalStateException(ex);
			}
		};
	}

	public static String randomIp() {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		return "10." + random.nextInt(256) + "." + random.nextInt(256) + "." + random.nextInt(1, 255);
	}

}
