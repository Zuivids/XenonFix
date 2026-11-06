package lv.xenonfix.product;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.MetadataException;
import com.drew.metadata.exif.ExifIFD0Directory;
import lv.xenonfix.common.ApiException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Turns an uploaded file into the JPEG photos the site serves. The file is judged by what it
 * contains, not by its name. Writing a new image also leaves out everything else the file
 * carried, including the GPS position that phones store in photos.
 */
@Component
public class ImageProcessor {

	private static final Set<String> ACCEPTED_FORMATS = Set.of("jpeg", "png");

	/** A larger image would need too much memory to decode. */
	private static final long MAX_PIXELS = 60_000_000;

	private static final int MAX_SIZE = 1600;

	private static final int THUMBNAIL_SIZE = 600;

	private static final float JPEG_QUALITY = 0.85f;

	/** Also used for the photos of the editable pages. */
	public Processed process(byte[] file) {
		BufferedImage upright;
		try {
			upright = rotateUpright(decode(file), orientation(file));
		}
		catch (IOException ex) {
			throw invalidImage();
		}
		BufferedImage full = fitInside(upright, MAX_SIZE);
		return new Processed(encode(full), encode(fitInside(upright, THUMBNAIL_SIZE)), full.getWidth(),
				full.getHeight());
	}

	private static BufferedImage decode(byte[] file) throws IOException {
		try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(file))) {
			Iterator<ImageReader> readers = (input != null) ? ImageIO.getImageReaders(input) : null;
			if (readers == null || !readers.hasNext()) {
				throw invalidImage();
			}
			ImageReader reader = readers.next();
			try {
				if (!ACCEPTED_FORMATS.contains(reader.getFormatName().toLowerCase(Locale.ROOT))) {
					throw invalidImage();
				}
				reader.setInput(input, true, true);
				if ((long) reader.getWidth(0) * reader.getHeight(0) > MAX_PIXELS) {
					throw new ApiException(HttpStatus.BAD_REQUEST, "IMAGE_TOO_LARGE");
				}
				return reader.read(0);
			}
			finally {
				reader.dispose();
			}
		}
	}

	/** The EXIF orientation, or 1 (already upright) when the file has none. */
	private static int orientation(byte[] file) {
		try {
			var directory = ImageMetadataReader.readMetadata(new ByteArrayInputStream(file))
				.getFirstDirectoryOfType(ExifIFD0Directory.class);
			if (directory != null && directory.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
				return directory.getInt(ExifIFD0Directory.TAG_ORIENTATION);
			}
		}
		catch (ImageProcessingException | MetadataException | IOException ex) {
			// Unreadable metadata is no reason to refuse a photo that decodes
		}
		return 1;
	}

	private static BufferedImage rotateUpright(BufferedImage image, int orientation) {
		int width = image.getWidth();
		int height = image.getHeight();
		AffineTransform transform = switch (orientation) {
			case 2 -> new AffineTransform(-1, 0, 0, 1, width, 0);
			case 3 -> new AffineTransform(-1, 0, 0, -1, width, height);
			case 4 -> new AffineTransform(1, 0, 0, -1, 0, height);
			case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
			case 6 -> new AffineTransform(0, 1, -1, 0, height, 0);
			case 7 -> new AffineTransform(0, -1, -1, 0, height, width);
			case 8 -> new AffineTransform(0, -1, 1, 0, 0, width);
			default -> new AffineTransform();
		};
		boolean swapsSides = orientation >= 5 && orientation <= 8;
		return draw(image, swapsSides ? height : width, swapsSides ? width : height, transform);
	}

	/** Reduces in steps of half at most, which keeps fine detail that one big step would lose. */
	private static BufferedImage fitInside(BufferedImage image, int size) {
		BufferedImage result = image;
		int longest = Math.max(image.getWidth(), image.getHeight());
		double remaining = (double) longest / size;
		while (remaining > 1) {
			double step = Math.max(1 / remaining, 0.5);
			int width = Math.max(1, (int) Math.round(result.getWidth() * step));
			int height = Math.max(1, (int) Math.round(result.getHeight() * step));
			result = draw(result, width, height,
					AffineTransform.getScaleInstance((double) width / result.getWidth(),
							(double) height / result.getHeight()));
			remaining *= step;
			if (remaining < 1.001) {
				break;
			}
		}
		return result;
	}

	/** Draws onto white, because JPEG has no transparency for a PNG's clear areas. */
	private static BufferedImage draw(BufferedImage source, int width, int height, AffineTransform transform) {
		BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = target.createGraphics();
		try {
			graphics.setColor(Color.WHITE);
			graphics.fillRect(0, 0, width, height);
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			graphics.drawImage(source, transform, null);
		}
		finally {
			graphics.dispose();
		}
		return target;
	}

	private static byte[] encode(BufferedImage image) {
		ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
		try (var bytes = new ByteArrayOutputStream(); var output = new MemoryCacheImageOutputStream(bytes)) {
			ImageWriteParam parameters = writer.getDefaultWriteParam();
			parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			parameters.setCompressionQuality(JPEG_QUALITY);
			writer.setOutput(output);
			writer.write(null, new IIOImage(image, null, null), parameters);
			output.flush();
			return bytes.toByteArray();
		}
		catch (IOException ex) {
			throw new IllegalStateException("Could not encode a photo", ex);
		}
		finally {
			writer.dispose();
		}
	}

	private static ApiException invalidImage() {
		return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IMAGE");
	}

	public record Processed(byte[] full, byte[] thumbnail, int width, int height) {
	}

}
