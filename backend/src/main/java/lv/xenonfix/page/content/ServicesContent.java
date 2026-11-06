package lv.xenonfix.page.content;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param warrantyLink the text of the button to the warranty terms
 */
public record ServicesContent(@Valid @NotNull Seo seo, @Valid @NotNull Text eyebrow, @Valid @NotNull Text heading,
		@Valid @NotNull Text lead, @Valid @NotNull @Size(min = 1, max = 40) List<@Valid @NotNull Service> services,
		@Valid @NotNull Text warrantyLink) {

	public record Service(@Valid @NotNull Text title, @Valid @NotNull Text text) {
	}

}
