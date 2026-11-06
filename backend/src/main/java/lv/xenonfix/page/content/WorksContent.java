package lv.xenonfix.page.content;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record WorksContent(@Valid @NotNull Seo seo, @Valid @NotNull Text heading, @Valid @NotNull Text intro,
		@Valid @NotNull @Size(max = 60) List<@Valid @NotNull Photo> photos) {

	/** A gallery photo; the caption also describes it for screen readers. */
	public record Photo(@Valid @NotNull Image image, @Valid @NotNull Text caption) {
	}

}
