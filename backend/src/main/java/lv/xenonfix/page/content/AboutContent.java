package lv.xenonfix.page.content;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param points the numbered statements under the heading
 */
public record AboutContent(@Valid @NotNull Seo seo, @Valid @NotNull Text heading, @Valid @NotNull Text lead,
		@Valid @NotNull @Size(max = 12) List<@Valid @NotNull Text> points, @Valid @NotNull Text goalHeading,
		@Valid @NotNull @Size(max = 10) List<@Valid @NotNull Text> goal, @Valid @NotNull Image managerImage,
		@Valid @NotNull Text managerName, @Valid @NotNull Text managerRole,
		@Valid @NotNull @Size(max = 10) List<@Valid @NotNull Text> manager) {
}
