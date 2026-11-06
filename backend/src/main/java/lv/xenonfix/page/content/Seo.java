package lv.xenonfix.page.content;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * @param title the browser tab and search result title
 * @param description the search result text
 */
public record Seo(@Valid @NotNull Text title, @Valid @NotNull Text description) {
}
