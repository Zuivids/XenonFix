package lv.xenonfix.page.content;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A text in the three languages. Latvian is required; an empty Russian or English text shows
 * the Latvian one on the site.
 */
public record Text(@NotBlank @Size(max = MAX) String lv, @Size(max = MAX) String ru, @Size(max = MAX) String en) {

	public static final int MAX = 3000;

	/** In this language, or in Latvian if it has none. */
	public String in(String language) {
		String text = switch (language) {
			case "ru" -> this.ru;
			case "en" -> this.en;
			default -> this.lv;
		};
		return (text == null || text.isBlank()) ? this.lv : text;
	}

}
