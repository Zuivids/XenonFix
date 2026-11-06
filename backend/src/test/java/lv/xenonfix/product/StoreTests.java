package lv.xenonfix.product;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import lv.xenonfix.IntegrationTest;
import lv.xenonfix.MutableClock;
import lv.xenonfix.TestAdmins;
import lv.xenonfix.admin.AdminUserRepository;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class StoreTests {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private AdminUserRepository admins;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private MutableClock clock;

	private TestProducts products;

	@BeforeEach
	void setUp() throws Exception {
		TestAdmins testAdmins = new TestAdmins(this.mvc, this.admins, this.passwordEncoder, this.clock);
		this.products = new TestProducts(this.mvc, testAdmins, testAdmins.loggedIn());
	}

	@Test
	void needsNoLogin() throws Exception {
		TestProducts.Created product = this.products.published(1);

		this.mvc.perform(get("/api/store/products")).andExpect(status().isOk());
		this.mvc.perform(get("/api/store/products/" + product.slug())).andExpect(status().isOk());
	}

	@Test
	void theRestOfTheStoreAddressesStayClosed() throws Exception {
		this.mvc.perform(get("/api/store/reservations")).andExpect(status().isUnauthorized());
		this.mvc.perform(get("/api/store/other")).andExpect(status().isUnauthorized());
	}

	@Test
	void showsThePublishedProductWithoutAdminDetails() throws Exception {
		TestProducts.Created product = this.products.published("Audi " + TestProducts.unique(), 3, "90.50", "");

		this.mvc.perform(get("/api/store/products/" + product.slug()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value(product.name()))
			.andExpect(jsonPath("$.description").value("Apraksts"))
			.andExpect(jsonPath("$.price").value(100))
			.andExpect(jsonPath("$.salePrice").value(90.50))
			.andExpect(jsonPath("$.available").value(true))
			.andExpect(jsonPath("$.images[0].url").value(Matchers.startsWith("/uploads/products/")))
			.andExpect(jsonPath("$.stockQuantity").doesNotExist())
			.andExpect(jsonPath("$.version").doesNotExist())
			.andExpect(jsonPath("$.status").doesNotExist())
			.andExpect(jsonPath("$.id").doesNotExist());
	}

	@Test
	void draftsAndArchivedProductsAreNotShown() throws Exception {
		TestProducts.Created draft = this.products.draft("Melnraksts " + TestProducts.unique(), 1, "null", "");
		TestProducts.Created archived = this.products.published(1);
		this.products.changeStatus(archived.id(), "ARCHIVED");

		this.mvc.perform(get("/api/store/products/" + draft.slug()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
		this.mvc.perform(get("/api/store/products/" + archived.slug())).andExpect(status().isNotFound());
		this.mvc.perform(get("/api/store/products?size=48"))
			.andExpect(jsonPath("$.items[*].slug", Matchers.not(Matchers.hasItem(draft.slug()))))
			.andExpect(jsonPath("$.items[*].slug", Matchers.not(Matchers.hasItem(archived.slug()))));
	}

	@Test
	void anUntranslatedLanguageShowsLatvian() throws Exception {
		String name = "Luktura " + TestProducts.unique();
		TestProducts.Created product = this.products.published(name, 1, "null", ",\"ru\":{\"name\":\"Фара\"}");

		this.mvc.perform(get("/api/store/products/" + product.slug()).param("lang", "ru"))
			.andExpect(jsonPath("$.name").value("Фара"));
		this.mvc.perform(get("/api/store/products/" + product.slug()).param("lang", "en"))
			.andExpect(jsonPath("$.name").value(name))
			.andExpect(jsonPath("$.description").value("Apraksts"));
	}

	@Test
	void anOutOfStockProductIsShownAsNotAvailable() throws Exception {
		TestProducts.Created product = this.products.published(0);

		this.mvc.perform(get("/api/store/products/" + product.slug())).andExpect(jsonPath("$.available").value(false));
	}

	@Test
	void listsTheNewestFirstInPagesOfALimitedSize() throws Exception {
		this.clock.advance(java.time.Duration.ofSeconds(1));
		TestProducts.Created older = this.products.published(1);
		this.clock.advance(java.time.Duration.ofSeconds(1));
		TestProducts.Created newer = this.products.published(1);

		this.mvc.perform(get("/api/store/products").param("size", "2"))
			.andExpect(jsonPath("$.items.length()").value(2))
			.andExpect(jsonPath("$.items[0].slug").value(newer.slug()))
			.andExpect(jsonPath("$.items[1].slug").value(older.slug()))
			.andExpect(jsonPath("$.items[0].thumbnailUrl").value(Matchers.startsWith("/uploads/products/")))
			.andExpect(jsonPath("$.items[0].stockQuantity").doesNotExist());
		this.mvc.perform(get("/api/store/products").param("size", "100000")).andExpect(status().isOk());
		this.mvc.perform(get("/api/store/products").param("lang", "de")).andExpect(status().isBadRequest());
	}

	@Test
	void theSitemapListsOnlyPublishedProducts() throws Exception {
		TestProducts.Created published = this.products.published(0);
		TestProducts.Created draft = this.products.draft("Melnraksts " + TestProducts.unique(), 1, "null", "");
		TestProducts.Created archived = this.products.published(1);
		this.products.changeStatus(archived.id(), "ARCHIVED");

		this.mvc.perform(get("/api/store/sitemap"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].slug", Matchers.hasItem(published.slug())))
			.andExpect(jsonPath("$[*].slug", Matchers.not(Matchers.hasItem(draft.slug()))))
			.andExpect(jsonPath("$[*].slug", Matchers.not(Matchers.hasItem(archived.slug()))))
			.andExpect(jsonPath("$[0].updatedAt").exists())
			.andExpect(jsonPath("$[0].stockQuantity").doesNotExist());
	}

}
