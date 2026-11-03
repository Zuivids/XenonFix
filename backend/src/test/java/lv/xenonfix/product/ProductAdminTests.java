package lv.xenonfix.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import lv.xenonfix.IntegrationTest;
import lv.xenonfix.MutableClock;
import lv.xenonfix.TestAdmins;
import lv.xenonfix.admin.AdminUserRepository;
import lv.xenonfix.config.AppProperties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
class ProductAdminTests {

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
	private AppProperties properties;

	private TestAdmins testAdmins;

	private MockHttpSession session;

	@BeforeEach
	void setUp() throws Exception {
		this.testAdmins = new TestAdmins(this.mvc, this.admins, this.passwordEncoder, this.clock);
		this.session = this.testAdmins.loggedIn();
	}

	@Test
	void productsNeedAnAdminLogin() throws Exception {
		this.mvc.perform(get("/api/admin/products")).andExpect(status().isUnauthorized());
		String email = this.testAdmins.create(true);
		this.mvc.perform(get("/api/admin/products").session(this.testAdmins.login(email)))
			.andExpect(status().isForbidden());
	}

	@Test
	void createsADraftWithAnAddressFromTheLatvianName() throws Exception {
		String name = "Škoda Octavia " + unique() + " lukturis";

		create(body(name, "", "450.00", "null", 3)).andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("DRAFT"))
			.andExpect(jsonPath("$.slug").value(Slugs.of(name)))
			.andExpect(jsonPath("$.price").value(450.00))
			.andExpect(jsonPath("$.stockQuantity").value(3))
			.andExpect(jsonPath("$.translations.lv.name").value(name))
			.andExpect(jsonPath("$.images").isEmpty());
	}

	@Test
	void sameNameGetsADifferentAddress() throws Exception {
		String name = "Audi " + unique();

		String first = slug(create(body(name, "", "100", "null", 1)));
		String second = slug(create(body(name, "", "100", "null", 1)));

		assertThat(second).isEqualTo(first + "-2");
	}

	@Test
	void aChosenAddressMustBeFreeAndWellFormed() throws Exception {
		String slug = "audi-" + unique();
		create(body("Audi", slug, "100", "null", 1)).andExpect(status().isCreated());

		create(body("Audi", slug, "100", "null", 1)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("SLUG_TAKEN"));
		create(body("Audi", "Nav Labs!", "100", "null", 1)).andExpect(status().isBadRequest());
	}

	@Test
	void validatesThePrices() throws Exception {
		create(body("Audi", "", "0", "null", 1)).andExpect(status().isBadRequest());
		create(body("Audi", "", "10.123", "null", 1)).andExpect(status().isBadRequest());
		create(body("Audi", "", "100", "100", 1)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("SALE_PRICE_NOT_LOWER"));
		create(body("Audi", "", "100", "90.50", 1)).andExpect(status().isCreated())
			.andExpect(jsonPath("$.salePrice").value(90.50));
	}

	@Test
	void stockCannotBeNegative() throws Exception {
		create(body("Audi", "", "100", "null", -1)).andExpect(status().isBadRequest());
	}

	@Test
	void theLatvianNameIsRequiredAndOtherLanguagesAreOptional() throws Exception {
		create("""
				{"price":100,"stockQuantity":1,"translations":{"ru":{"name":"Фара"}}}""")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("LATVIAN_NAME_REQUIRED"));
		create("""
				{"price":100,"stockQuantity":1,"translations":{"lv":{"name":"Lukturis"},"ru":{"name":"","description":"Текст"}}}""")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("TRANSLATION_NEEDS_NAME"));
		create("""
				{"price":100,"stockQuantity":1,"translations":{"lv":{"name":"Lukturis"},"ru":{"name":"Передняя фара","description":"Текст"},"en":{"name":""}}}""")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.translations.ru.name").value("Передняя фара"))
			.andExpect(jsonPath("$.translations.en").doesNotExist());
	}

	@Test
	void updatesTheProductAndRecordsStockChanges() throws Exception {
		long id = id(create(body("Audi " + unique(), "", "100", "null", 3)));

		update(id, body("Audi jauns", "", "120", "99", 5, 0)).andExpect(status().isOk())
			.andExpect(jsonPath("$.translations.lv.name").value("Audi jauns"))
			.andExpect(jsonPath("$.price").value(120))
			.andExpect(jsonPath("$.salePrice").value(99))
			.andExpect(jsonPath("$.stockQuantity").value(5))
			.andExpect(jsonPath("$.version").value(1));

		assertThat(audit("PRODUCT_STOCK_CHANGED", id)).containsExactly("Audi jauns: 3 → 5");
	}

	@Test
	void aSecondAdminCannotOverwriteChangesTheyHaveNotSeen() throws Exception {
		long id = id(create(body("Audi " + unique(), "", "100", "null", 3)));
		update(id, body("Pirmais", "", "100", "null", 3, 0)).andExpect(status().isOk());

		update(id, body("Otrais", "", "100", "null", 3, 0)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CHANGED_BY_ANOTHER"));

		this.mvc.perform(get("/api/admin/products/" + id).session(this.session))
			.andExpect(jsonPath("$.translations.lv.name").value("Pirmais"));
	}

	@Test
	void translationsCanBeRemovedAgain() throws Exception {
		long id = id(create("""
				{"price":100,"stockQuantity":1,"translations":{"lv":{"name":"Lukturis"},"ru":{"name":"Фара"}}}"""));

		update(id, """
				{"price":100,"stockQuantity":1,"version":0,"translations":{"lv":{"name":"Lukturis"},"ru":{"name":""}}}""")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.translations.ru").doesNotExist());
	}

	@Test
	void publishingNeedsAPhoto() throws Exception {
		long id = id(create(body("Audi " + unique(), "", "100", "null", 1)));

		changeStatus(id, "PUBLISHED").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PUBLISH_NEEDS_IMAGE"));

		upload(id, TestImages.encoded("jpeg", 800, 600)).andExpect(status().isCreated());
		changeStatus(id, "PUBLISHED").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLISHED"));
		assertThat(audit("PRODUCT_STATUS_CHANGED", id)).singleElement()
			.asString()
			.endsWith("melnraksts → publicēts");
	}

	@Test
	void anUploadedPhotoIsServedFromDiskAndCachedForLong() throws Exception {
		long id = id(create(body("Audi " + unique(), "", "100", "null", 1)));

		String response = upload(id, TestImages.encoded("jpeg", 3000, 2000)).andExpect(status().isCreated())
			.andExpect(jsonPath("$.images[0].width").value(1600))
			.andExpect(jsonPath("$.images[0].height").value(1067))
			.andReturn().getResponse().getContentAsString();

		String url = JsonPath.read(response, "$.images[0].url");
		String thumbnailUrl = JsonPath.read(response, "$.images[0].thumbnailUrl");
		assertThat(url).startsWith("/uploads/products/" + id + "/").endsWith(".jpg");
		this.mvc.perform(get(url))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.IMAGE_JPEG))
			.andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("max-age=31536000")));
		this.mvc.perform(get(thumbnailUrl)).andExpect(status().isOk());
		assertThat(uploaded(url)).exists();
		assertThat(this.jdbcClient.sql("SELECT path FROM product_image WHERE product_id = ?")
			.param(id).query(String.class).single()).isEqualTo(url.substring("/uploads/".length()));
	}

	@Test
	void refusesFilesThatAreNotPhotos() throws Exception {
		long id = id(create(body("Audi " + unique(), "", "100", "null", 1)));

		upload(id, "<html><script>alert(1)</script></html>".getBytes()).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_IMAGE"));
	}

	@Test
	void photosCanBeOrdered() throws Exception {
		long id = id(create(body("Audi " + unique(), "", "100", "null", 1)));
		upload(id, TestImages.encoded("jpeg", 100, 100)).andExpect(status().isCreated());
		String response = upload(id, TestImages.encoded("jpeg", 100, 100)).andReturn().getResponse().getContentAsString();
		long first = ((Number) JsonPath.read(response, "$.images[0].id")).longValue();
		long second = ((Number) JsonPath.read(response, "$.images[1].id")).longValue();

		order(id, "[" + second + "," + first + "]").andExpect(status().isOk())
			.andExpect(jsonPath("$.images[0].id").value(second));

		this.mvc.perform(get("/api/admin/products/" + id).session(this.session))
			.andExpect(jsonPath("$.images[0].id").value(second));
		order(id, "[" + first + "]").andExpect(status().isBadRequest());
	}

	@Test
	void removingAPhotoDeletesItsFilesButNotThePublishedProductsLast() throws Exception {
		long id = id(create(body("Audi " + unique(), "", "100", "null", 1)));
		upload(id, TestImages.encoded("jpeg", 100, 100)).andExpect(status().isCreated());
		String response = upload(id, TestImages.encoded("jpeg", 100, 100)).andReturn().getResponse().getContentAsString();
		long first = ((Number) JsonPath.read(response, "$.images[0].id")).longValue();
		long second = ((Number) JsonPath.read(response, "$.images[1].id")).longValue();
		String url = JsonPath.read(response, "$.images[0].url");
		changeStatus(id, "PUBLISHED").andExpect(status().isOk());

		removeImage(id, first).andExpect(status().isOk()).andExpect(jsonPath("$.images.length()").value(1));
		assertThat(uploaded(url)).doesNotExist();

		removeImage(id, second).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("LAST_IMAGE_OF_PUBLISHED"));
	}

	@Test
	void listsFindsAndHidesArchivedProducts() throws Exception {
		String word = unique();
		long id = id(create("""
				{"partNumber":"8T0.941.032C","price":100,"stockQuantity":1,"translations":{"lv":{"name":"Audi %s"}}}"""
			.formatted(word)));

		this.mvc.perform(get("/api/admin/products").param("search", word.toUpperCase()).session(this.session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].name").value("Audi " + word))
			.andExpect(jsonPath("$.items[0].partNumber").value("8T0.941.032C"));

		changeStatus(id, "ARCHIVED").andExpect(status().isOk());

		this.mvc.perform(get("/api/admin/products").param("search", word).session(this.session))
			.andExpect(jsonPath("$.items").isEmpty());
		this.mvc.perform(get("/api/admin/products").param("search", word).param("status", "ARCHIVED").session(this.session))
			.andExpect(jsonPath("$.items.length()").value(1));
	}

	@Test
	void aMissingProductIsNotFound() throws Exception {
		this.mvc.perform(get("/api/admin/products/999999999").session(this.session)).andExpect(status().isNotFound());
	}

	private static String unique() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

	private static String body(String name, String slug, String price, String salePrice, int stock) {
		return "{\"slug\":\"%s\",\"price\":%s,\"salePrice\":%s,\"stockQuantity\":%d,\"translations\":{\"lv\":{\"name\":\"%s\"}}}"
			.formatted(slug, price, salePrice, stock, name);
	}

	private static String body(String name, String slug, String price, String salePrice, int stock, long version) {
		return body(name, slug, price, salePrice, stock).replaceFirst("^\\{", "{\"version\":" + version + ",");
	}

	private ResultActions create(String body) throws Exception {
		return this.mvc.perform(post("/api/admin/products").with(this.testAdmins.browser()).session(this.session)
			.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions update(long id, String body) throws Exception {
		return this.mvc.perform(put("/api/admin/products/" + id).with(this.testAdmins.browser()).session(this.session)
			.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions changeStatus(long id, String status) throws Exception {
		return this.mvc.perform(post("/api/admin/products/" + id + "/status").with(this.testAdmins.browser())
			.session(this.session).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + status + "\"}"));
	}

	private ResultActions upload(long id, byte[] file) throws Exception {
		return this.mvc.perform(multipart("/api/admin/products/" + id + "/images")
			.file(new MockMultipartFile("file", "photo.jpg", "image/jpeg", file))
			.with(this.testAdmins.browser()).session(this.session));
	}

	private ResultActions order(long id, String imageIds) throws Exception {
		return this.mvc.perform(put("/api/admin/products/" + id + "/images/order").with(this.testAdmins.browser())
			.session(this.session).contentType(MediaType.APPLICATION_JSON).content("{\"imageIds\":" + imageIds + "}"));
	}

	private ResultActions removeImage(long id, long imageId) throws Exception {
		return this.mvc.perform(delete("/api/admin/products/" + id + "/images/" + imageId)
			.with(this.testAdmins.browser()).session(this.session));
	}

	private static long id(ResultActions created) throws Exception {
		return ((Number) JsonPath.read(created.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
	}

	private static String slug(ResultActions created) throws Exception {
		return JsonPath.read(created.andReturn().getResponse().getContentAsString(), "$.slug");
	}

	private Path uploaded(String url) {
		return this.properties.uploadsPath().resolve(url.substring("/uploads/".length()));
	}

	private java.util.List<String> audit(String eventType, long productId) {
		return this.jdbcClient
			.sql("SELECT details FROM audit_log WHERE event_type = ? AND target_type = 'PRODUCT' AND target_id = ?")
			.params(eventType, Long.toString(productId))
			.query(String.class)
			.list();
	}

}
