package lv.xenonfix.page.content;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record HomeContent(@Valid @NotNull Seo seo, @Valid @NotNull Text eyebrow, @Valid @NotNull Text heading,
		@Valid @NotNull Text intro, @Valid @NotNull Image heroImage, @Valid @NotNull Text heroImageAlt,
		@Valid @NotNull @Size(max = 4) List<@Valid @NotNull Stat> stats, @Valid @NotNull Text servicesEyebrow,
		@Valid @NotNull Text servicesHeading, @Valid @NotNull Text allServices,
		@Valid @NotNull @Size(min = 1, max = 12) List<@Valid @NotNull Tile> tiles, @Valid @NotNull Text storeEyebrow,
		@Valid @NotNull Text storeHeading, @Valid @NotNull Text ctaHeading, @Valid @NotNull Text ctaText,
		@Valid @NotNull @Size(max = 10) List<@Valid @NotNull Text> reasons) {

	/** A number with its label, such as "10 gadu pieredze". */
	public record Stat(@NotBlank @Size(max = 10) String value, @Valid @NotNull Text label) {
	}

	/** A service tile with a photo, linking to the services page. */
	public record Tile(@Valid @NotNull Image image, @Valid @NotNull Text title, @Valid @NotNull Text text) {
	}

}
