package lv.xenonfix.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.jayway.jsonpath.JsonPath;
import lv.xenonfix.IntegrationTest;
import lv.xenonfix.MutableClock;
import lv.xenonfix.RecordingMailSender;
import lv.xenonfix.TestAdmins;
import lv.xenonfix.admin.AdminUserRepository;
import lv.xenonfix.product.TestProducts;

import org.junit.jupiter.api.AfterEach;
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
class ReservationTests {

	private static final Duration EMAIL_WAIT = Duration.ofSeconds(10);

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

	@Autowired
	private RecordingMailSender mailSender;

	private TestAdmins testAdmins;

	private MockHttpSession session;

	private TestProducts products;

	@BeforeEach
	void setUp() throws Exception {
		this.testAdmins = new TestAdmins(this.mvc, this.admins, this.passwordEncoder, this.clock);
		this.session = this.testAdmins.loggedIn();
		this.products = new TestProducts(this.mvc, this.testAdmins, this.session);
	}

	@AfterEach
	void mailWorksAgain() {
		this.mailSender.setFailing(false);
	}

	@Test
	void savesTheReservationAndTakesTheStock() throws Exception {
		TestProducts.Created product = this.products.published("Audi " + TestProducts.unique(), 5, "80.50", "");

		String reference = reference(reserve(body(product.slug(), 2, "lv")).andExpect(status().isCreated()));

		assertThat(reference).matches("XF-\\d{6,}");
		assertThat(stock(product.id())).isEqualTo(3);
		var saved = this.jdbcClient.sql("""
				SELECT product_name, unit_price, quantity, customer_name, customer_phone, customer_email, comment,
					language, status
				FROM reservation WHERE product_id = ?""").param(product.id()).query().singleRow();
		assertThat(saved).containsEntry("product_name", product.name())
			.containsEntry("quantity", 2)
			.containsEntry("customer_name", "Anna Bērziņa")
			.containsEntry("customer_phone", "+371 20000000")
			.containsEntry("customer_email", "anna@example.com")
			.containsEntry("comment", "Lūdzu, zvaniet pēc 15:00")
			.containsEntry("language", "lv")
			.containsEntry("status", "PENDING");
		assertThat(saved.get("unit_price").toString()).isEqualTo("80.50");
	}

	@Test
	void needsNoCsrfTokenButOtherRequestsStillDo() throws Exception {
		TestProducts.Created product = this.products.published(1);

		reserve(body(product.slug(), 1, "lv")).andExpect(status().isCreated());

		this.mvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isForbidden());
		this.mvc.perform(post("/api/store/products").contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isForbidden());
	}

	@Test
	void reservationsCannotBeReadBack() throws Exception {
		this.mvc.perform(get("/api/store/reservations")).andExpect(status().isUnauthorized());
		this.mvc.perform(get("/api/store/reservations/1")).andExpect(status().isUnauthorized());
	}

	@Test
	void neverSellsMoreThanThereIsWhenManyReserveAtOnce() throws Exception {
		TestProducts.Created product = this.products.published(3);
		int customers = 8;
		var start = new CountDownLatch(1);

		List<Integer> statuses = new ArrayList<>();
		try (var executor = Executors.newFixedThreadPool(customers)) {
			List<Future<Integer>> results = new ArrayList<>();
			for (int i = 0; i < customers; i++) {
				results.add(executor.submit(() -> {
					start.await();
					return reserve(body(product.slug(), 1, "lv")).andReturn().getResponse().getStatus();
				}));
			}
			start.countDown();
			for (Future<Integer> result : results) {
				statuses.add(result.get());
			}
		}

		assertThat(statuses).filteredOn((status) -> status == 201).hasSize(3);
		assertThat(statuses).filteredOn((status) -> status == 409).hasSize(5);
		assertThat(stock(product.id())).isZero();
		assertThat(reservationCount(product.id())).isEqualTo(3);
	}

	@Test
	void refusesMoreThanIsInStockAndLeavesTheStockAlone() throws Exception {
		TestProducts.Created product = this.products.published(1);

		reserve(body(product.slug(), 2, "lv")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("OUT_OF_STOCK"));

		assertThat(stock(product.id())).isEqualTo(1);
		assertThat(reservationCount(product.id())).isZero();
	}

	@Test
	void anAdminFormOpenedBeforeAReservationCannotPutTheOldStockBack() throws Exception {
		TestProducts.Created product = this.products.published(3);
		long versionInTheForm = version(product.id());

		reserve(body(product.slug(), 1, "lv")).andExpect(status().isCreated());

		this.mvc
			.perform(put("/api/admin/products/" + product.id()).with(this.testAdmins.browser())
				.session(this.session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"version\":%d,\"price\":100,\"stockQuantity\":3,\"translations\":{\"lv\":{\"name\":\"Cits\"}}}"
					.formatted(versionInTheForm)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CHANGED_BY_ANOTHER"));
		assertThat(stock(product.id())).isEqualTo(2);
	}

	@Test
	void onlyPublishedProductsCanBeReserved() throws Exception {
		TestProducts.Created draft = this.products.draft("Melnraksts " + TestProducts.unique(), 5, "null", "");
		TestProducts.Created archived = this.products.published(5);
		this.products.changeStatus(archived.id(), "ARCHIVED");

		reserve(body(draft.slug(), 1, "lv")).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
		reserve(body(archived.slug(), 1, "lv")).andExpect(status().isNotFound());
		reserve(body("nav-tada-" + TestProducts.unique(), 1, "lv")).andExpect(status().isNotFound());
		assertThat(stock(draft.id())).isEqualTo(5);
		assertThat(stock(archived.id())).isEqualTo(5);
	}

	@Test
	void checksTheForm() throws Exception {
		TestProducts.Created product = this.products.published(50);
		String good = body(product.slug(), 1, "lv");

		reserve(good.replace("\"privacyAccepted\":true", "\"privacyAccepted\":false")).andExpect(status().isBadRequest());
		reserve(good.replace("\"privacyAccepted\":true,", "")).andExpect(status().isBadRequest());
		reserve(good.replace("\"website\":\"\"", "\"website\":\"http://spam.example\"")).andExpect(status().isBadRequest());
		reserve(good.replace("Anna Bērziņa", " ")).andExpect(status().isBadRequest());
		reserve(good.replace("Anna Bērziņa", "Anna\\r\\nBcc: x@example.com")).andExpect(status().isBadRequest());
		reserve(good.replace("+371 20000000", "zvani man")).andExpect(status().isBadRequest());
		reserve(good.replace("Anna@Example.com", "nav-epasts")).andExpect(status().isBadRequest());
		reserve(good.replace("\"language\":\"lv\"", "\"language\":\"de\"")).andExpect(status().isBadRequest());
		reserve(body(product.slug(), 0, "lv")).andExpect(status().isBadRequest());
		reserve(body(product.slug(), 11, "lv")).andExpect(status().isBadRequest());

		assertThat(stock(product.id())).isEqualTo(50);
		assertThat(reservationCount(product.id())).isZero();
	}

	@Test
	void limitsReservationsFromOneAddress() throws Exception {
		TestProducts.Created product = this.products.published(50);
		String ip = TestAdmins.randomIp();
		String body = body(product.slug(), 1, "lv");

		for (int i = 0; i < 5; i++) {
			reserveFrom(ip, body).andExpect(status().isCreated());
		}

		reserveFrom(ip, body).andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
		reserveFrom(TestAdmins.randomIp(), body).andExpect(status().isCreated());
		assertThat(stock(product.id())).isEqualTo(44);
	}

	@Test
	void emailsTheShopAndTheCustomerInTheirLanguage() throws Exception {
		TestProducts.Created product = this.products.published("Audi " + TestProducts.unique(), 5, "null",
				",\"ru\":{\"name\":\"Фара\"}");

		String reference = reference(reserve(body(product.slug(), 2, "ru")));

		await().atMost(EMAIL_WAIT).until(() -> this.mailSender.sentWith(reference).size() == 2);
		var emails = this.mailSender.sentWith(reference);
		var toShop = emails.stream().filter((email) -> email.to().equals("info@xenonfix.lv")).findFirst().orElseThrow();
		var toCustomer = emails.stream().filter((email) -> email.to().equals("anna@example.com")).findFirst().orElseThrow();

		assertThat(toShop.subject()).startsWith("Jauna rezervācija");
		assertThat(toShop.replyTo()).isEqualTo("anna@example.com");
		assertThat(toShop.body()).contains(product.name(), "+371 20000000", "anna@example.com", "200.00 EUR",
				"Lūdzu, zvaniet pēc 15:00");
		assertThat(toCustomer.subject()).startsWith("Ваше резервирование");
		assertThat(toCustomer.body()).contains("Anna Bērziņa", "Фара", "200.00 EUR", reference);
		assertThat(toCustomer.from()).isEqualTo("info@xenonfix.lv");
	}

	@Test
	void aRefusedReservationSendsNoEmail() throws Exception {
		TestProducts.Created product = this.products.published("Vienīgais " + TestProducts.unique(), 1, "null", "");
		reserve(body(product.slug(), 1, "lv")).andExpect(status().isCreated());

		reserve(body(product.slug(), 1, "lv")).andExpect(status().isConflict());

		// The two emails of the first reservation, and none from the refused one
		await().during(Duration.ofSeconds(1))
			.atMost(EMAIL_WAIT)
			.until(() -> this.mailSender.sentWith("XF-")
				.stream()
				.filter((email) -> email.body().contains(product.name()))
				.count() == 2);
		assertThat(reservationCount(product.id())).isEqualTo(1);
	}

	@Test
	void aMailProblemDoesNotLoseTheReservation() throws Exception {
		this.mailSender.setFailing(true);
		TestProducts.Created product = this.products.published(5);

		reserve(body(product.slug(), 1, "lv")).andExpect(status().isCreated());

		assertThat(reservationCount(product.id())).isEqualTo(1);
		assertThat(stock(product.id())).isEqualTo(4);
	}

	@Test
	void theAuditLogHasTheReservationButNotTheCustomer() throws Exception {
		TestProducts.Created product = this.products.published("Audi " + TestProducts.unique(), 5, "null", "");

		String reference = reference(reserve(body(product.slug(), 2, "lv")));

		String id = Long.toString(Long.parseLong(reference.substring("XF-".length())));
		List<String> details = this.jdbcClient
			.sql("SELECT details FROM audit_log WHERE event_type = 'RESERVATION_CREATED' AND target_id = ? AND admin_id IS NULL")
			.param(id)
			.query(String.class)
			.list();
		assertThat(details).containsExactly(reference + ": " + product.name() + " × 2");
	}

	private static String body(String slug, int quantity, String language) {
		return """
				{"slug":"%s","quantity":%d,"name":"Anna Bērziņa","phone":"+371 20000000","email":"Anna@Example.com",\
				"comment":"Lūdzu, zvaniet pēc 15:00","language":"%s","privacyAccepted":true,"website":""}"""
			.formatted(slug, quantity, language);
	}

	/** From an address of its own, so tests do not share the limit. */
	private ResultActions reserve(String body) throws Exception {
		return reserveFrom(TestAdmins.randomIp(), body);
	}

	private ResultActions reserveFrom(String ip, String body) throws Exception {
		return this.mvc.perform(post("/api/store/reservations").with((request) -> {
			request.setRemoteAddr(ip);
			return request;
		}).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private static String reference(ResultActions created) throws Exception {
		return JsonPath.read(created.andReturn().getResponse().getContentAsString(), "$.reference");
	}

	private int stock(long productId) {
		return this.jdbcClient.sql("SELECT stock_quantity FROM product WHERE id = ?")
			.param(productId)
			.query(Integer.class)
			.single();
	}

	private long version(long productId) {
		return this.jdbcClient.sql("SELECT version FROM product WHERE id = ?")
			.param(productId)
			.query(Long.class)
			.single();
	}

	private int reservationCount(long productId) {
		return this.jdbcClient.sql("SELECT COUNT(*) FROM reservation WHERE product_id = ?")
			.param(productId)
			.query(Integer.class)
			.single();
	}

}
