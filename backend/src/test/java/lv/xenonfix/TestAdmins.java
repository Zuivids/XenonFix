package lv.xenonfix;

import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import jakarta.servlet.http.Cookie;
import lv.xenonfix.admin.AdminUser;
import lv.xenonfix.admin.AdminUserRepository;

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

	public MockHttpSession login(String email) throws Exception {
		MockHttpSession session = new MockHttpSession();
		attemptLogin(session, email, PASSWORD, this.ip).andExpect(status().isOk());
		return session;
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
