package lv.xenonfix.product;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SlugsTests {

	@Test
	void makesAnAddressFromALatvianName() {
		assertThat(Slugs.of("Škoda Octavia (2020) aizmugurējais lukturis"))
			.isEqualTo("skoda-octavia-2020-aizmugurejais-lukturis");
		assertThat(Slugs.of("Ģērbonis ķēde ļoti ņem")).isEqualTo("gerbonis-kede-loti-nem");
	}

	@Test
	void hasAnAddressEvenWithoutLatinLetters() {
		assertThat(Slugs.of("Передняя фара")).isEqualTo("produkts");
	}

}
