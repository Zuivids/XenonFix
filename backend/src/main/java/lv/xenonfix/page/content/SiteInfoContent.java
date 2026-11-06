package lv.xenonfix.page.content;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The shop's details, shown on Kontakti, in the header and footer, on the home page and in the
 * emails. The phone and WhatsApp links and the map links are made from these.
 */
public record SiteInfoContent(@NotBlank @Pattern(regexp = PHONE) String phone, @NotBlank @Pattern(regexp = PHONE) String whatsapp,
		@NotBlank @Email @Size(max = 254) String email, @NotBlank @Size(max = 100) String street,
		@NotBlank @Size(max = 100) String city, @Valid @NotNull @Size(max = 7) List<@Valid @NotNull Hours> hours,
		@NotBlank @Pattern(regexp = URL) String instagramUrl, @NotBlank @Pattern(regexp = URL) String facebookUrl,
		@NotBlank @Size(max = 100) String companyName, @NotBlank @Size(max = 40) String registrationNumber,
		@NotBlank @Size(max = 40) String vatNumber, @NotBlank @Size(max = 40) String bankAccount) {

	/** As people write phone numbers: digits, spaces, a leading plus, brackets and dashes. */
	static final String PHONE = "\\+?[0-9][0-9 ()-]{5,24}";

	static final String URL = "https://[^\\s\"<>]{3,300}";

	/** A row of the opening hours, such as "Pirmdiena – piektdiena" and "9:00–17:00". */
	public record Hours(@Valid @NotNull Text day, @Valid @NotNull Text time) {
	}

	/** For tel: links: only the digits and the leading plus. */
	public static String dialable(String phone) {
		return phone.replaceAll("[^+0-9]", "");
	}

}
