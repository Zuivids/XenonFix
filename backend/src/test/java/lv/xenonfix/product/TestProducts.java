package lv.xenonfix.product;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import lv.xenonfix.TestAdmins;

import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/** Makes products through the real admin API, like the panel does. */
public class TestProducts {

	private final MockMvc mvc;

	private final TestAdmins admins;

	private final MockHttpSession session;

	public TestProducts(MockMvc mvc, TestAdmins admins, MockHttpSession session) {
		this.mvc = mvc;
		this.admins = admins;
		this.session = session;
	}

	/** A published product in Latvian only, at 100 EUR. */
	public Created published(int stock) throws Exception {
		return published("Lukturis " + unique(), stock, "null", "");
	}

	/**
	 * @param salePrice as JSON, for example {@code "90.50"} or {@code "null"}
	 * @param otherTranslations more of the translations object, for example
	 * {@code ,"ru":{"name":"Фара"}}
	 */
	public Created published(String name, int stock, String salePrice, String otherTranslations) throws Exception {
		Created product = draft(name, stock, salePrice, otherTranslations);
		this.mvc
			.perform(multipart("/api/admin/products/" + product.id() + "/images")
				.file(new MockMultipartFile("file", "photo.jpg", "image/jpeg", TestImages.encoded("jpeg", 100, 100)))
				.with(this.admins.browser())
				.session(this.session))
			.andExpect(status().isCreated());
		changeStatus(product.id(), "PUBLISHED");
		return product;
	}

	public Created draft(String name, int stock, String salePrice, String otherTranslations) throws Exception {
		String body = "{\"price\":100,\"salePrice\":%s,\"stockQuantity\":%d,\"translations\":{\"lv\":{\"name\":\"%s\",\"description\":\"Apraksts\"}%s}}"
			.formatted(salePrice, stock, name, otherTranslations);
		String response = this.mvc
			.perform(post("/api/admin/products").with(this.admins.browser())
				.session(this.session)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return new Created(((Number) JsonPath.read(response, "$.id")).longValue(), JsonPath.read(response, "$.slug"),
				name);
	}

	public void changeStatus(long id, String newStatus) throws Exception {
		this.mvc
			.perform(post("/api/admin/products/" + id + "/status").with(this.admins.browser())
				.session(this.session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"" + newStatus + "\"}"))
			.andExpect(status().isOk());
	}

	public static String unique() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

	public record Created(long id, String slug, String name) {
	}

}
