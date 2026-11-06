package lv.xenonfix.page;

import java.util.Arrays;
import java.util.Optional;

import lv.xenonfix.page.content.AboutContent;
import lv.xenonfix.page.content.ContactContent;
import lv.xenonfix.page.content.HomeContent;
import lv.xenonfix.page.content.ServicesContent;
import lv.xenonfix.page.content.SiteInfoContent;
import lv.xenonfix.page.content.WorksContent;

/**
 * The editable pages, each with the shape of its content. {@code name} is how addresses and
 * the starting content files (resources/pages) call it.
 */
public enum PageKey {

	HOME("home", HomeContent.class), ABOUT("about", AboutContent.class), SERVICES("services", ServicesContent.class),
	WORKS("works", WorksContent.class), CONTACT("contact", ContactContent.class),
	SITE_INFO("site-info", SiteInfoContent.class);

	private final String path;

	private final Class<? extends Record> contentType;

	PageKey(String path, Class<? extends Record> contentType) {
		this.path = path;
		this.contentType = contentType;
	}

	public String path() {
		return this.path;
	}

	public Class<? extends Record> contentType() {
		return this.contentType;
	}

	public static Optional<PageKey> fromPath(String path) {
		return Arrays.stream(values()).filter((key) -> key.path.equals(path)).findFirst();
	}

}
