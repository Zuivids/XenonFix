package lv.xenonfix.product;

import java.text.Normalizer;
import java.util.Locale;

final class Slugs {

	private static final String FALLBACK = "produkts";

	private Slugs() {
	}

	/** An address from a Latvian name: "Škoda Octavia (2020) lukturis" is "skoda-octavia-2020-lukturis". */
	static String of(String text) {
		String withoutMarks = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		String slug = withoutMarks.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
		return slug.isEmpty() ? FALLBACK : slug;
	}

}
