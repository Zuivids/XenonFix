package lv.xenonfix.reservation.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lv.xenonfix.product.ProductLanguage;

/**
 * @param language the language of the page, for the messages the customer gets
 * @param website a field people never see and bots fill in, so it must stay empty
 */
public record ReservationRequest(@NotBlank @Size(max = 200) String slug, @NotNull @Min(1) @Max(10) Integer quantity,
		@NotBlank @Size(max = 100) @Pattern(regexp = "[^\\p{Cntrl}]*") String name,
		@NotBlank @Size(max = 30) @Pattern(regexp = "\\s*\\+?[0-9][0-9 ()-]{5,28}\\s*") String phone,
		@NotBlank @Email @Size(max = 254) String email, @Size(max = 1000) String comment,
		@NotNull ProductLanguage language, @NotNull @AssertTrue Boolean privacyAccepted,
		@Size(max = 0) String website) {
}
