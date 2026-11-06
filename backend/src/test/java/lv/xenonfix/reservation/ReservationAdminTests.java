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

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
class ReservationAdminTests {

	private static final Duration EMAIL_WAIT = Duration.ofSeconds(10);

	private static final Duration NO_EMAIL_WAIT = Duration.ofMillis(400);

	private static final String BASE = "/api/admin/reservations";

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

	@Autowired
	private ReservationRetention retention;

	private TestAdmins testAdmins;

	private MockHttpSession session;

	private TestProducts products;

	@BeforeEach
	void setUp() throws Exception {
		this.testAdmins = new TestAdmins(this.mvc, this.admins, this.passwordEncoder, this.clock);
		this.session = this.testAdmins.loggedIn();
		this.products = new TestProducts(this.mvc, this.testAdmins, this.session);
	}

	@Test
	void needsAnAdminLogin() throws Exception {
		this.mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
		this.mvc.perform(get(BASE + "/1")).andExpect(status().isUnauthorized());
		String mustChangePassword = this.testAdmins.create(true);
		this.mvc.perform(get(BASE).session(this.testAdmins.login(mustChangePassword)))
			.andExpect(status().isForbidden());
	}

	@Test
	void changesNeedACsrfToken() throws Exception {
		this.mvc.perform(post(BASE + "/1/status").session(this.session)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\":\"CONFIRMED\",\"version\":0}")).andExpect(status().isForbidden());
	}

	@Test
	void showsAllTheDetails() throws Exception {
		TestProducts.Created product = this.products.published("Audi " + TestProducts.unique(), 5, "80.50", "");
		long id = reserve(product, 2, "ru");

		this.mvc.perform(get(BASE + "/" + id).session(this.session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.reference").value(reference(id)))
			.andExpect(jsonPath("$.productId").value(product.id()))
			.andExpect(jsonPath("$.productName").value(product.name()))
			.andExpect(jsonPath("$.unitPrice").value(80.50))
			.andExpect(jsonPath("$.quantity").value(2))
			.andExpect(jsonPath("$.total").value(161.00))
			.andExpect(jsonPath("$.customerName").value("Anna Bērziņa"))
			.andExpect(jsonPath("$.customerPhone").value("+371 20000000"))
			.andExpect(jsonPath("$.customerEmail").value("anna@example.com"))
			.andExpect(jsonPath("$.comment").value("Zvaniet pēc 15:00"))
			.andExpect(jsonPath("$.language").value("ru"))
			.andExpect(jsonPath("$.status").value("PENDING"))
			.andExpect(jsonPath("$.adminNote").doesNotExist());
	}

	@Test
	void aMissingReservationIsNotFound() throws Exception {
		this.mvc.perform(get(BASE + "/999999999").session(this.session))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
	}

	@Test
	void listsTheNewestFirst() throws Exception {
		TestProducts.Created product = this.products.published(5);
		long first = reserve(product, 1, "lv");
		this.clock.advance(Duration.ofSeconds(1));
		long second = reserve(product, 1, "lv");

		this.mvc.perform(get(BASE).session(this.session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].id").value(second))
			.andExpect(jsonPath("$.items[1].id").value(first))
			.andExpect(jsonPath("$.items[0].customerName").value("Anna Bērziņa"))
			.andExpect(jsonPath("$.items[0].total").value(100));
	}

	@Test
	void filtersByStatus() throws Exception {
		TestProducts.Created product = this.products.published(5);
		long pending = reserve(product, 1, "lv");
		long confirmed = reserve(product, 1, "lv");
		change(confirmed, "CONFIRMED").andExpect(status().isOk());

		this.mvc.perform(get(BASE).param("status", "CONFIRMED").session(this.session))
			.andExpect(jsonPath("$.items[*].id", Matchers.hasItem((int) confirmed)))
			.andExpect(jsonPath("$.items[*].id", Matchers.not(Matchers.hasItem((int) pending))))
			.andExpect(jsonPath("$.items[*].status", Matchers.everyItem(Matchers.is("CONFIRMED"))));
	}

	@Test
	void searchesByCustomerProductAndNumber() throws Exception {
		TestProducts.Created product = this.products.published("Unikāls " + TestProducts.unique(), 5, "null", "");
		long id = reserve(product, 1, "lv");

		for (String search : List.of(product.name().toUpperCase(), "bērziņa", "anna@example", "20000000", reference(id),
				reference(id).toLowerCase(), Long.toString(id))) {
			this.mvc.perform(get(BASE).param("search", search).session(this.session))
				.andExpect(jsonPath("$.items[*].id", Matchers.hasItem((int) id)));
		}
		this.mvc.perform(get(BASE).param("search", "nekas-tāds-nav-" + TestProducts.unique()).session(this.session))
			.andExpect(jsonPath("$.items").isEmpty());
	}

	/**
	 * Starts with 5 in stock and reserves 2, leaving 3. {@code stock} is what the change to
	 * {@code to} should leave, and {@code email} a word in the customer's new email, if any.
	 */
	@ParameterizedTest(name = "{0} to {1}")
	@CsvSource(nullValues = "none", textBlock = """
			PENDING,   CONFIRMED, 3, apstiprināta
			PENDING,   CANCELLED, 5, atcelta
			PENDING,   COMPLETED, 3, none
			CONFIRMED, PENDING,   3, none
			CONFIRMED, CANCELLED, 5, atcelta
			CONFIRMED, COMPLETED, 3, none
			CANCELLED, PENDING,   3, none
			CANCELLED, CONFIRMED, 3, apstiprināta
			CANCELLED, COMPLETED, 3, none
			COMPLETED, PENDING,   3, none
			COMPLETED, CONFIRMED, 3, none
			COMPLETED, CANCELLED, 5, none
			""")
	void movesStockAndEmailsAsTheRulesSay(String from, String to, int stock, String email) throws Exception {
		TestProducts.Created product = this.products.published(5);
		long id = reserve(product, 2, "lv");
		String reference = reference(id);
		await().atMost(EMAIL_WAIT).until(() -> emails(reference).size() == 2);
		if (!from.equals("PENDING")) {
			change(id, from).andExpect(status().isOk());
		}
		int emailsBefore = 2 + ((from.equals("CONFIRMED") || from.equals("CANCELLED")) ? 1 : 0);
		await().atMost(EMAIL_WAIT).until(() -> emails(reference).size() == emailsBefore);

		change(id, to).andExpect(status().isOk()).andExpect(jsonPath("$.status").value(to));

		assertThat(stock(product.id())).isEqualTo(stock);
		if (email != null) {
			await().atMost(EMAIL_WAIT).until(() -> emails(reference).size() == emailsBefore + 1);
			var sent = emails(reference).getLast();
			assertThat(sent.to()).isEqualTo("anna@example.com");
			assertThat(sent.subject()).contains(email);
			assertThat(sent.body()).contains("Anna Bērziņa", reference, product.name());
		}
		else {
			await().during(NO_EMAIL_WAIT).atMost(EMAIL_WAIT).until(() -> emails(reference).size() == emailsBefore);
		}
	}

	@Test
	void emailsInTheCustomersLanguage() throws Exception {
		TestProducts.Created product = this.products.published("Audi " + TestProducts.unique(), 5, "null",
				",\"ru\":{\"name\":\"Фара\"}");
		long id = reserve(product, 1, "ru");
		String reference = reference(id);

		change(id, "CONFIRMED").andExpect(status().isOk());

		await().atMost(EMAIL_WAIT).until(() -> emails(reference).stream().anyMatch((email) -> email.subject().contains("подтверждено")));
		var sent = emails(reference).stream().filter((email) -> email.subject().contains("подтверждено")).findFirst().orElseThrow();
		assertThat(sent.body()).contains("Фара", "подтверждено");
	}

	@Test
	void reopeningWithoutEnoughStockIsRefusedAndChangesNothing() throws Exception {
		TestProducts.Created product = this.products.published(1);
		long cancelled = reserve(product, 1, "lv");
		change(cancelled, "CANCELLED").andExpect(status().isOk());
		reserve(product, 1, "lv");
		assertThat(stock(product.id())).isZero();

		change(cancelled, "PENDING").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("OUT_OF_STOCK"));

		assertThat(stock(product.id())).isZero();
		this.mvc.perform(get(BASE + "/" + cancelled).session(this.session))
			.andExpect(jsonPath("$.status").value("CANCELLED"));
		assertThat(audit("RESERVATION_STATUS_CHANGED", cancelled)).hasSize(1);
	}

	@Test
	void anArchivedProductWithStockCanBeReopened() throws Exception {
		TestProducts.Created product = this.products.published(3);
		long id = reserve(product, 2, "lv");
		change(id, "CANCELLED").andExpect(status().isOk());
		this.products.changeStatus(product.id(), "ARCHIVED");

		change(id, "CONFIRMED").andExpect(status().isOk());

		assertThat(stock(product.id())).isEqualTo(1);
	}

	@Test
	void anAdminFormOpenedBeforeTheStockMovedCannotOverwriteIt() throws Exception {
		TestProducts.Created product = this.products.published(3);
		long id = reserve(product, 1, "lv");
		long versionInTheForm = this.jdbcClient.sql("SELECT version FROM product WHERE id = ?")
			.param(product.id())
			.query(Long.class)
			.single();

		change(id, "CANCELLED").andExpect(status().isOk());

		this.mvc
			.perform(put("/api/admin/products/" + product.id()).with(this.testAdmins.browser())
				.session(this.session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"version\":%d,\"price\":100,\"stockQuantity\":2,\"translations\":{\"lv\":{\"name\":\"Cits\"}}}"
					.formatted(versionInTheForm)))
			.andExpect(status().isConflict());
		assertThat(stock(product.id())).isEqualTo(3);
	}

	@Test
	void twoAdminsCancellingAtOnceReturnTheStockOnce() throws Exception {
		TestProducts.Created product = this.products.published(5);
		long id = reserve(product, 2, "lv");
		long version = version(id);
		var start = new CountDownLatch(1);

		List<Integer> statuses = new ArrayList<>();
		try (var executor = Executors.newFixedThreadPool(4)) {
			List<Future<Integer>> results = new ArrayList<>();
			for (int i = 0; i < 4; i++) {
				results.add(executor.submit(() -> {
					start.await();
					return changeAt(id, "CANCELLED", version).andReturn().getResponse().getStatus();
				}));
			}
			start.countDown();
			for (Future<Integer> result : results) {
				statuses.add(result.get());
			}
		}

		assertThat(statuses).filteredOn((status) -> status == 200).hasSize(1);
		assertThat(statuses).filteredOn((status) -> status == 409).hasSize(3);
		assertThat(stock(product.id())).isEqualTo(5);
		assertThat(audit("RESERVATION_STATUS_CHANGED", id)).hasSize(1);
	}

	@Test
	void anOldVersionIsRefused() throws Exception {
		TestProducts.Created product = this.products.published(5);
		long id = reserve(product, 1, "lv");
		long old = version(id);
		change(id, "CONFIRMED").andExpect(status().isOk());

		changeAt(id, "CANCELLED", old).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CHANGED_BY_ANOTHER"));
		note(id, "Teksts", old).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CHANGED_BY_ANOTHER"));

		assertThat(stock(product.id())).isEqualTo(4);
	}

	@Test
	void theSameStatusChangesNothing() throws Exception {
		TestProducts.Created product = this.products.published(5);
		long id = reserve(product, 1, "lv");
		String reference = reference(id);
		await().atMost(EMAIL_WAIT).until(() -> emails(reference).size() == 2);
		change(id, "CANCELLED").andExpect(status().isOk());
		await().atMost(EMAIL_WAIT).until(() -> emails(reference).size() == 3);

		change(id, "CANCELLED").andExpect(status().isOk());

		assertThat(stock(product.id())).isEqualTo(5);
		assertThat(audit("RESERVATION_STATUS_CHANGED", id)).hasSize(1);
		await().during(NO_EMAIL_WAIT).atMost(EMAIL_WAIT).until(() -> emails(reference).size() == 3);
	}

	@Test
	void validatesTheRequests() throws Exception {
		long id = reserve(this.products.published(5), 1, "lv");

		send(post(BASE + "/" + id + "/status"), "{\"status\":\"NAV_TADS\",\"version\":0}")
			.andExpect(status().isBadRequest());
		send(post(BASE + "/" + id + "/status"), "{\"status\":\"CONFIRMED\"}").andExpect(status().isBadRequest());
		note(id, "x".repeat(2001), version(id)).andExpect(status().isBadRequest());
	}

	@Test
	void savesAndClearsTheNote() throws Exception {
		long id = reserve(this.products.published(5), 1, "lv");

		note(id, "  Zvanīju, atnāks piektdien  ", version(id)).andExpect(status().isOk())
			.andExpect(jsonPath("$.adminNote").value("Zvanīju, atnāks piektdien"));
		this.mvc.perform(get(BASE + "/" + id).session(this.session))
			.andExpect(jsonPath("$.adminNote").value("Zvanīju, atnāks piektdien"));

		note(id, "   ", version(id)).andExpect(status().isOk()).andExpect(jsonPath("$.adminNote").doesNotExist());
	}

	@Test
	void theAuditLogHasTheChangesButNoPersonalDetailsOrNoteText() throws Exception {
		TestProducts.Created product = this.products.published(5);
		long id = reserve(product, 2, "lv");
		String reference = reference(id);

		note(id, "Slepena piezīme", version(id)).andExpect(status().isOk());
		note(id, "Slepena piezīme", version(id)).andExpect(status().isOk());
		change(id, "CANCELLED").andExpect(status().isOk());
		change(id, "PENDING").andExpect(status().isOk());

		assertThat(audit("RESERVATION_NOTE_CHANGED", id)).containsExactly(reference);
		assertThat(audit("RESERVATION_STATUS_CHANGED", id)).containsExactlyInAnyOrder(
				reference + ": gaida → atcelta, atgriezti 2 gab.", reference + ": atcelta → gaida, atkal paņemti 2 gab.");
		int adminCount = this.jdbcClient
			.sql("SELECT COUNT(*) FROM audit_log WHERE target_id = ? AND target_type = 'RESERVATION' AND admin_id IS NOT NULL")
			.param(Long.toString(id))
			.query(Integer.class)
			.single();
		assertThat(adminCount).isEqualTo(3);
	}

	@Test
	void theNoteIsNeverEmailedToTheCustomer() throws Exception {
		long id = reserve(this.products.published(5), 1, "lv");
		String reference = reference(id);
		note(id, "Slepena piezīme", version(id)).andExpect(status().isOk());

		change(id, "CONFIRMED").andExpect(status().isOk());

		await().atMost(EMAIL_WAIT).until(() -> emails(reference).size() == 3);
		assertThat(emails(reference)).noneMatch((email) -> email.body().contains("Slepena piezīme"));
	}

	@Test
	void anonymizingErasesTheCustomerAndKeepsTheRest() throws Exception {
		TestProducts.Created product = this.products.published(5);
		long id = reserve(product, 2, "lv");
		note(id, "Slepena piezīme", version(id)).andExpect(status().isOk());
		change(id, "COMPLETED").andExpect(status().isOk());

		anonymize(id, version(id)).andExpect(status().isOk())
			.andExpect(jsonPath("$.anonymizedAt").exists())
			.andExpect(jsonPath("$.customerName").doesNotExist());

		var row = this.jdbcClient
			.sql("SELECT customer_name, customer_phone, customer_email, comment, admin_note, quantity, status"
					+ " FROM reservation WHERE id = ?")
			.param(id)
			.query()
			.singleRow();
		assertThat(row).containsEntry("customer_name", null)
			.containsEntry("customer_phone", null)
			.containsEntry("customer_email", null)
			.containsEntry("comment", null)
			.containsEntry("admin_note", null)
			.containsEntry("quantity", 2)
			.containsEntry("status", "COMPLETED");
		assertThat(stock(product.id())).isEqualTo(3);
		assertThat(audit("RESERVATION_ANONYMIZED", id)).containsExactly(reference(id));
		this.mvc.perform(get(BASE).param("search", reference(id)).session(this.session))
			.andExpect(jsonPath("$.items[0].anonymized").value(true))
			.andExpect(jsonPath("$.items[0].customerName").doesNotExist());
	}

	@ParameterizedTest
	@CsvSource({ "PENDING", "CONFIRMED" })
	void onlyFinishedReservationsCanBeAnonymized(String status) throws Exception {
		long id = reserve(this.products.published(5), 1, "lv");
		if (!status.equals("PENDING")) {
			change(id, status).andExpect(status().isOk());
		}

		anonymize(id, version(id)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RESERVATION_ACTIVE"));

		this.mvc.perform(get(BASE + "/" + id).session(this.session))
			.andExpect(jsonPath("$.customerName").value("Anna Bērziņa"));
	}

	@Test
	void anAnonymizedReservationCannotBeChanged() throws Exception {
		TestProducts.Created product = this.products.published(5);
		long id = reserve(product, 1, "lv");
		change(id, "CANCELLED").andExpect(status().isOk());
		anonymize(id, version(id)).andExpect(status().isOk());

		change(id, "CONFIRMED").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RESERVATION_ANONYMIZED"));
		note(id, "Teksts", version(id)).andExpect(status().isConflict());
		anonymize(id, version(id)).andExpect(status().isConflict());

		assertThat(stock(product.id())).isEqualTo(5);
	}

	@Test
	void anonymizingWithAnOldVersionIsRefused() throws Exception {
		long id = reserve(this.products.published(5), 1, "lv");
		long old = version(id);
		change(id, "CANCELLED").andExpect(status().isOk());

		anonymize(id, old).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CHANGED_BY_ANOTHER"));
		send(post(BASE + "/" + id + "/anonymize"), "{}").andExpect(status().isBadRequest());
	}

	@Test
	void theNightlyJobAnonymizesOnlyOldFinishedReservations() throws Exception {
		TestProducts.Created product = this.products.published(10);
		long oldCompleted = reserve(product, 1, "lv");
		long oldCancelled = reserve(product, 1, "lv");
		long oldPending = reserve(product, 1, "lv");
		long oldConfirmed = reserve(product, 1, "lv");
		long recentCompleted = reserve(product, 1, "lv");
		change(oldCompleted, "COMPLETED").andExpect(status().isOk());
		change(oldCancelled, "CANCELLED").andExpect(status().isOk());
		change(oldConfirmed, "CONFIRMED").andExpect(status().isOk());
		change(recentCompleted, "COMPLETED").andExpect(status().isOk());
		// The period is 183 days; a day either side, so time zones cannot matter
		for (long id : List.of(oldCompleted, oldCancelled, oldPending, oldConfirmed)) {
			setUpdatedDaysAgo(id, 185);
		}
		setUpdatedDaysAgo(recentCompleted, 181);
		int stockBefore = stock(product.id());

		this.retention.run();

		assertThat(customerName(oldCompleted)).isNull();
		assertThat(customerName(oldCancelled)).isNull();
		assertThat(customerName(oldPending)).isEqualTo("Anna Bērziņa");
		assertThat(customerName(oldConfirmed)).isEqualTo("Anna Bērziņa");
		assertThat(customerName(recentCompleted)).isEqualTo("Anna Bērziņa");
		assertThat(stock(product.id())).isEqualTo(stockBefore);
		List<String> expired = this.jdbcClient
			.sql("SELECT details FROM audit_log WHERE event_type = 'RESERVATIONS_EXPIRED' AND admin_id IS NULL"
					+ " ORDER BY id DESC LIMIT 1")
			.query(String.class)
			.list();
		assertThat(expired).singleElement().asString().contains("dati 2 rezervācijām").doesNotContain("Anna");

		this.retention.run();
		assertThat(customerName(oldPending)).isEqualTo("Anna Bērziņa");
	}

	private ResultActions anonymize(long id, long version) throws Exception {
		return send(post(BASE + "/" + id + "/anonymize"), "{\"version\":%d}".formatted(version));
	}

	private void setUpdatedDaysAgo(long id, int days) {
		this.jdbcClient.sql("UPDATE reservation SET updated_at = ? WHERE id = ?")
			.params(java.sql.Timestamp.from(this.clock.instant().minus(Duration.ofDays(days))), id)
			.update();
	}

	private String customerName(long id) {
		return this.jdbcClient.sql("SELECT customer_name FROM reservation WHERE id = ?")
			.param(id)
			.query((row, number) -> row.getString(1))
			.list()
			.getFirst();
	}

	private long reserve(TestProducts.Created product, int quantity, String language) throws Exception {
		String body = """
				{"slug":"%s","quantity":%d,"name":"Anna Bērziņa","phone":"+371 20000000","email":"anna@example.com",\
				"comment":"Zvaniet pēc 15:00","language":"%s","privacyAccepted":true,"website":""}"""
			.formatted(product.slug(), quantity, language);
		String response = this.mvc.perform(post("/api/store/reservations").with((request) -> {
			request.setRemoteAddr(TestAdmins.randomIp());
			return request;
		}).contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String reference = JsonPath.read(response, "$.reference");
		return Long.parseLong(reference.substring("XF-".length()));
	}

	private static String reference(long id) {
		return "XF-%06d".formatted(id);
	}

	private ResultActions change(long id, String status) throws Exception {
		return changeAt(id, status, version(id));
	}

	private ResultActions changeAt(long id, String status, long version) throws Exception {
		return send(post(BASE + "/" + id + "/status"), "{\"status\":\"%s\",\"version\":%d}".formatted(status, version));
	}

	private ResultActions note(long id, String note, long version) throws Exception {
		return send(put(BASE + "/" + id + "/note"), "{\"note\":\"%s\",\"version\":%d}".formatted(note, version));
	}

	private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
			String body) throws Exception {
		return this.mvc.perform(request.with(this.testAdmins.browser())
			.session(this.session)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private long version(long id) {
		return this.jdbcClient.sql("SELECT version FROM reservation WHERE id = ?")
			.param(id)
			.query(Long.class)
			.single();
	}

	private int stock(long productId) {
		return this.jdbcClient.sql("SELECT stock_quantity FROM product WHERE id = ?")
			.param(productId)
			.query(Integer.class)
			.single();
	}

	private List<RecordingMailSender.Email> emails(String reference) {
		return this.mailSender.sentWith(reference);
	}

	private List<String> audit(String eventType, long id) {
		return this.jdbcClient
			.sql("SELECT details FROM audit_log WHERE event_type = ? AND target_type = 'RESERVATION' AND target_id = ?")
			.params(eventType, Long.toString(id))
			.query(String.class)
			.list();
	}

}
