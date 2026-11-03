package lv.xenonfix.product;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

final class TestImages {

	private TestImages() {
	}

	/** A photo whose top-left quarter is red, so tests can see how it was turned. */
	static byte[] encoded(String format, int width, int height) throws IOException {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = image.createGraphics();
		graphics.setColor(Color.BLUE);
		graphics.fillRect(0, 0, width, height);
		graphics.setColor(Color.RED);
		graphics.fillRect(0, 0, width / 2, height / 2);
		graphics.dispose();
		var bytes = new ByteArrayOutputStream();
		ImageIO.write(image, format, bytes);
		return bytes.toByteArray();
	}

	/** Adds the EXIF block a phone writes: just the rotation, right after the JPEG start. */
	static byte[] withOrientation(byte[] jpeg, int orientation) {
		byte[] exif = { (byte) 0xFF, (byte) 0xE1, 0, 34, 'E', 'x', 'i', 'f', 0, 0, 'M', 'M', 0, 42, 0, 0, 0, 8, 0, 1,
				0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, (byte) orientation, 0, 0, 0, 0, 0, 0 };
		byte[] result = new byte[jpeg.length + exif.length];
		System.arraycopy(jpeg, 0, result, 0, 2);
		System.arraycopy(exif, 0, result, 2, exif.length);
		System.arraycopy(jpeg, 2, result, 2 + exif.length, jpeg.length - 2);
		return result;
	}

}
