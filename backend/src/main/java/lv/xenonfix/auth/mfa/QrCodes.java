package lv.xenonfix.auth.mfa;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;
import java.util.Map;

import javax.imageio.ImageIO;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

/** QR codes as PNG data addresses, which the admin page shows as an image. */
public final class QrCodes {

	private static final int SIZE = 240;

	private QrCodes() {
	}

	public static String pngDataUri(String text) {
		try {
			BitMatrix matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, SIZE, SIZE,
					Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 2));
			BufferedImage image = new BufferedImage(matrix.getWidth(), matrix.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
			for (int y = 0; y < matrix.getHeight(); y++) {
				for (int x = 0; x < matrix.getWidth(); x++) {
					image.setRGB(x, y, matrix.get(x, y) ? 0x000000 : 0xffffff);
				}
			}
			ByteArrayOutputStream png = new ByteArrayOutputStream();
			ImageIO.write(image, "png", png);
			return "data:image/png;base64," + Base64.getEncoder().encodeToString(png.toByteArray());
		}
		catch (WriterException ex) {
			throw new IllegalStateException("Could not make the QR code", ex);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
