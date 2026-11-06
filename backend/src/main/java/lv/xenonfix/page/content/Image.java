package lv.xenonfix.page.content;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * A photo on a page, the same in every language: one of the site's own photos or one uploaded in
 * the panel. Any other address is refused, so a page can only show photos from this site.
 */
public record Image(@NotNull @Pattern(regexp = SRC) String src, @Positive int width, @Positive int height) {

	public static final String UPLOADS_PREFIX = "/uploads/pages/";

	static final String SRC = "/(images/[a-z0-9-]{1,60}|uploads/pages/[0-9a-f-]{36})\\.jpg";

}
