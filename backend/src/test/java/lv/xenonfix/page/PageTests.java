package lv.xenonfix.page;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import lv.xenonfix.IntegrationTest;
import lv.xenonfix.MutableClock;
import lv.xenonfix.RecordingMailSender;
import lv.xenonfix.TestAdmins;
import lv.xenonfix.admin.AdminUserRepository;
import lv.xenonfix.config.AppProperties;
import lv.xenonfix.product.TestImages;
import lv.xenonfix.product.TestProducts;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
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
class PageTests {

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
	private PageService pageService;

	@Autowired
	private AppProperties properties;

	@Autowired
	private RecordingMailSender mailSender;

	private TestAdmins testAdmins;

	private MockHttpSession session;

	/** The pages as they were, put back after each test, as the tests share one database. */
	private List<Map<String, Object>> savedPages;

	@BeforeEach
	void setUp() throws Exception {
		this.savedPages = this.jdbcClient.sql("SELECT page_key, content FROM page").query().listOfRows();
		this.testAdmins = new TestAdmins(this.mvc, this.admins, this.passwordEncoder, this.clock);
		this.session = this.testAdmins.loggedIn();
	}

	@AfterEach
	void restorePages() {
		for (Map<String, Object> page : this.savedPages) {
			this.jdbcClient.sql("UPDATE page SET content = ? WHERE page_key = ?")
				.params(page.get("content"), page.get("page_key"))
				.update();
		}
	}

	@Test
	void everyPageStartsWithTodaysContentAndNeedsNoLogin() throws Exception {
		this.mvc.perform(get("/api/pages/home").param("lang", "ru"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.heading").value("Ремонт передних автомобильных фар"))
			.andExpect(jsonPath("$.tiles.length()").value(6))
			.andExpect(jsonPath("$.tiles[0].image.src").value("/images/service-glass.jpg"))
			.andExpect(jsonPath("$.serviceCount").value(14));
		this.mvc.perform(get("/api/pages/about")).andExpect(jsonPath("$.heading").value("Par mums"));
		this.mvc.perform(get("/api/pages/services").param("lang", "en")).andExpect(jsonPath("$.services.length()").value(14));
		this.mvc.perform(get("/api/pages/works")).andExpect(jsonPath("$.photos.length()").value(6));
		this.mvc.perform(get("/api/pages/contact").param("lang", "en")).andExpect(jsonPath("$.heading").value("Contact"));
		this.mvc.perform(get("/api/pages/site-info"))
			.andExpect(jsonPath("$.phone").value("+371 288 03 119"))
			.andExpect(jsonPath("$.hours[0].time").value("9:00–17:00"));
		this.mvc.perform(get("/api/pages/nothing")).andExpect(status().isNotFound());
		this.mvc.perform(get("/api/pages/home").param("lang", "de")).andExpect(status().isBadRequest());
	}

	@Test
	void changesNeedAnAdminAndATokenAndAreAudited() throws Exception {
		this.mvc.perform(get("/api/admin/pages")).andExpect(status().isUnauthorized());
		this.mvc.perform(put("/api/admin/pages/home").session(this.session)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}")).andExpect(status().isForbidden());

		this.mvc.perform(get("/api/admin/pages").session(this.session))
			.andExpect(jsonPath("$[*].page", Matchers.contains("home", "about", "services", "works", "contact", "site-info")));
		DocumentContext page = adminPage("about");
		page.set("$.content.heading.lv", "Par mums jauns");

		save("about", page).andExpect(status().isOk()).andExpect(jsonPath("$.updatedBy").value("Test"));

		this.mvc.perform(get("/api/pages/about")).andExpect(jsonPath("$.heading").value("Par mums jauns"));
		assertThat(this.jdbcClient.sql("SELECT details FROM audit_log WHERE event_type = 'PAGE_UPDATED' AND target_id = 'about'")
			.query(String.class)
			.list()).contains("Par mums");
	}

	@Test
	void anEmptyTranslationShowsTheLatvianText() throws Exception {
		DocumentContext page = adminPage("works");
		page.set("$.content.heading.ru", "");
		page.set("$.content.heading.en", null);
		save("works", page).andExpect(status().isOk());

		this.mvc.perform(get("/api/pages/works").param("lang", "ru")).andExpect(jsonPath("$.heading").value("Mūsu darbi"));
		this.mvc.perform(get("/api/pages/works").param("lang", "en")).andExpect(jsonPath("$.heading").value("Mūsu darbi"));
	}

	@Test
	void listsCanGrowShrinkAndBeReordered() throws Exception {
		DocumentContext page = adminPage("services");
		List<Object> services = page.read("$.content.services");
		Object first = services.removeFirst();
		services.add(first);
		services.removeFirst();
		page.set("$.content.services", services);

		save("services", page).andExpect(status().isOk());

		String lastTitle = JsonPath.read(first, "$.title.lv");
		this.mvc.perform(get("/api/pages/services"))
			.andExpect(jsonPath("$.services.length()").value(13))
			.andExpect(jsonPath("$.services[12].title").value(lastTitle));
		this.mvc.perform(get("/api/pages/home")).andExpect(jsonPath("$.serviceCount").value(13));
	}

	@Test
	void contentIsCheckedAgainstThePageShape() throws Exception {
		DocumentContext noLatvian = adminPage("home");
		noLatvian.set("$.content.tiles[2].title.lv", " ");
		save("home", noLatvian).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_PAGE_CONTENT"))
			.andExpect(jsonPath("$.field").value("tiles[2].title.lv"));

		DocumentContext unknownField = adminPage("home");
		unknownField.put("$.content", "script", "<script>");
		save("home", unknownField).andExpect(status().isBadRequest());

		DocumentContext foreignPhoto = adminPage("home");
		foreignPhoto.set("$.content.heroImage.src", "https://example.com/photo.jpg");
		save("home", foreignPhoto).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.field").value("heroImage.src"));

		DocumentContext noTiles = adminPage("home");
		noTiles.set("$.content.tiles", List.of());
		save("home", noTiles).andExpect(status().isBadRequest());

		DocumentContext badPhone = adminPage("site-info");
		badPhone.set("$.content.phone", "zvaniet");
		save("site-info", badPhone).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("phone"));

		this.mvc.perform(get("/api/pages/home")).andExpect(jsonPath("$.heading").value("Auto priekšējo lukturu remonts"));
	}

	@Test
	void anOldVersionIsRefused() throws Exception {
		DocumentContext page = adminPage("contact");
		page.set("$.content.heading.lv", "Pirmais");
		save("contact", page).andExpect(status().isOk());

		page.set("$.content.heading.lv", "Otrais");
		save("contact", page).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CHANGED_BY_ANOTHER"));

		this.mvc.perform(get("/api/pages/contact")).andExpect(jsonPath("$.heading").value("Pirmais"));
	}

	@Test
	void photosAreUploadedAndDeletedWhenNoLongerUsed() throws Exception {
		Map<String, Object> first = upload();
		Map<String, Object> second = upload();
		Path firstFile = file(first);
		assertThat(firstFile).exists();
		assertThat(first.get("src").toString()).matches("/uploads/pages/[0-9a-f-]{36}\\.jpg");

		DocumentContext page = adminPage("about");
		page.set("$.content.managerImage", first);
		save("about", page).andExpect(status().isOk());
		this.mvc.perform(get("/api/pages/about")).andExpect(jsonPath("$.managerImage.src").value(first.get("src")));
		this.mvc.perform(get(first.get("src").toString())).andExpect(status().isOk());

		page = adminPage("about");
		page.set("$.content.managerImage", second);
		save("about", page).andExpect(status().isOk());

		assertThat(firstFile).doesNotExist();
		assertThat(file(second)).exists();
	}

	@Test
	void photosNeverSavedIntoAPageAreDeletedAfterADay() throws Exception {
		Path unsaved = file(upload());

		this.pageService.deleteUnsavedUploads();
		assertThat(unsaved).exists();

		this.clock.advance(Duration.ofDays(2));
		this.pageService.deleteUnsavedUploads();
		assertThat(unsaved).doesNotExist();
	}

	@Test
	void theStartingContentNeverReplacesAnEdit() throws Exception {
		DocumentContext page = adminPage("works");
		page.set("$.content.intro.lv", "Rediģēts");
		save("works", page).andExpect(status().isOk());

		this.pageService.run(null);

		this.mvc.perform(get("/api/pages/works")).andExpect(jsonPath("$.intro").value("Rediģēts"));
	}

	@Test
	void emailsUseTheEditedContactDetails() throws Exception {
		DocumentContext info = adminPage("site-info");
		info.set("$.content.phone", "+371 200 00 999");
		info.set("$.content.street", "Jaunā iela 1");
		save("site-info", info).andExpect(status().isOk());
		TestProducts products = new TestProducts(this.mvc, this.testAdmins, this.session);
		TestProducts.Created product = products.published(5);

		String reference = JsonPath.read(this.mvc.perform(post("/api/store/reservations").with((request) -> {
			request.setRemoteAddr(TestAdmins.randomIp());
			return request;
		}).contentType(MediaType.APPLICATION_JSON).content("""
				{"slug":"%s","quantity":1,"name":"Anna","phone":"+371 20000000","email":"anna@example.com",\
				"comment":"","language":"lv","privacyAccepted":true,"website":""}""".formatted(product.slug())))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString(), "$.reference");

		await().atMost(Duration.ofSeconds(10)).until(() -> this.mailSender.sentWith(reference).size() == 2);
		var customer = this.mailSender.sentWith(reference).stream()
			.filter((email) -> email.to().equals("anna@example.com"))
			.findFirst()
			.orElseThrow();
		assertThat(customer.body()).contains("+371 200 00 999", "Jaunā iela 1").doesNotContain("288 03 119");
	}

	private DocumentContext adminPage(String page) throws Exception {
		return JsonPath.parse(this.mvc.perform(get("/api/admin/pages/" + page).session(this.session))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString());
	}

	private ResultActions save(String page, DocumentContext current) throws Exception {
		String body = JsonPath.parse(Map.of("content", current.read("$.content"), "version", current.read("$.version")))
			.jsonString();
		return this.mvc.perform(put("/api/admin/pages/" + page).with(this.testAdmins.browser())
			.session(this.session)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private Map<String, Object> upload() throws Exception {
		String body = this.mvc
			.perform(multipart("/api/admin/pages/images")
				.file(new MockMultipartFile("file", "photo.png", "image/png", TestImages.encoded("png", 300, 200)))
				.with(this.testAdmins.browser())
				.session(this.session))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.width").value(300))
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$");
	}

	private Path file(Map<String, Object> image) {
		return this.properties.uploadsPath()
			.toAbsolutePath()
			.resolve(image.get("src").toString().substring("/uploads/".length()));
	}

}
