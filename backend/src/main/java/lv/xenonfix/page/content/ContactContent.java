package lv.xenonfix.page.content;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** The Kontakti page's own texts; the details on it come from {@link SiteInfoContent}. */
public record ContactContent(@Valid @NotNull Seo seo, @Valid @NotNull Text heading, @Valid @NotNull Text phoneHeading,
		@Valid @NotNull Text addressHeading, @Valid @NotNull Text emailHeading, @Valid @NotNull Text hoursHeading) {
}
