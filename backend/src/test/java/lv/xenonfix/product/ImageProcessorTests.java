package lv.xenonfix.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

import lv.xenonfix.common.ApiException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ImageProcessorTests {

	private final ImageProcessor processor = new ImageProcessor();

	@ParameterizedTest
	@ValueSource(strings = { "jpeg", "png" })
	void acceptsJpegAndPngAndWritesJpeg(String format) throws IOException {
		var result = this.processor.process(TestImages.encoded(format, 800, 600));

		assertThat(result.width()).isEqualTo(800);
		assertThat(result.height()).isEqualTo(600);
		assertThat(result.full()).startsWith((byte) 0xFF, (byte) 0xD8);
		assertThat(decode(result.full()).getWidth()).isEqualTo(800);
	}

	@Test
	void reducesLargePhotosAndMakesAThumbnail() throws IOException {
		var result = this.processor.process(TestImages.encoded("jpeg", 4000, 3000));

		assertThat(result.width()).isEqualTo(1600);
		assertThat(result.height()).isEqualTo(1200);
		BufferedImage thumbnail = decode(result.thumbnail());
		assertThat(thumbnail.getWidth()).isEqualTo(600);
		assertThat(thumbnail.getHeight()).isEqualTo(450);
	}

	@Test
	void doesNotEnlargeSmallPhotos() throws IOException {
		var result = this.processor.process(TestImages.encoded("jpeg", 300, 200));

		assertThat(decode(result.thumbnail()).getWidth()).isEqualTo(300);
	}

	@Test
	void turnsAPhoneLandscapePhotoUpright() throws IOException {
		byte[] rotated = TestImages.withOrientation(TestImages.encoded("jpeg", 800, 400), 6);

		var result = this.processor.process(rotated);

		assertThat(result.width()).isEqualTo(400);
		assertThat(result.height()).isEqualTo(800);
		// Orientation 6 means turn 90° clockwise, so the red top-left ends up top-right
		BufferedImage image = decode(result.full());
		assertThat(isRed(image.getRGB(300, 100))).isTrue();
		assertThat(isRed(image.getRGB(100, 100))).isFalse();
	}

	@Test
	void leavesNoExifInTheResult() throws IOException {
		byte[] rotated = TestImages.withOrientation(TestImages.encoded("jpeg", 200, 100), 6);

		var result = this.processor.process(rotated);

		assertThat(new String(result.full(), StandardCharsets.ISO_8859_1)).doesNotContain("Exif");
	}

	@Test
	void judgesAFileByItsContent() {
		byte[] notAnImage = "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8);

		assertThatThrownBy(() -> this.processor.process(notAnImage)).isInstanceOf(ApiException.class);
	}

	@Test
	void refusesOtherImageFormats() throws IOException {
		byte[] gif = TestImages.encoded("gif", 100, 100);

		assertThatThrownBy(() -> this.processor.process(gif)).isInstanceOf(ApiException.class);
	}

	private static BufferedImage decode(byte[] bytes) throws IOException {
		return ImageIO.read(new ByteArrayInputStream(bytes));
	}

	private static boolean isRed(int rgb) {
		return ((rgb >> 16) & 0xFF) > 150 && (rgb & 0xFF) < 100;
	}

}
