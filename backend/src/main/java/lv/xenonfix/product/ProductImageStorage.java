package lv.xenonfix.product;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import lv.xenonfix.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

/**
 * The photo files under the uploads folder. The paths are made here, never from what the
 * client sent, and the random names let a photo be cached forever.
 */
@Component
class ProductImageStorage {

	private static final Logger logger = LoggerFactory.getLogger(ProductImageStorage.class);

	private final Path root;

	ProductImageStorage(AppProperties properties) {
		this.root = properties.uploadsPath().toAbsolutePath().normalize();
	}

	Stored save(long productId, ImageProcessor.Processed image) {
		String name = "products/" + productId + "/" + UUID.randomUUID();
		String path = name + ".jpg";
		String thumbnailPath = name + "-thumb.jpg";
		try {
			Files.createDirectories(this.root.resolve("products").resolve(Long.toString(productId)));
			Files.write(this.root.resolve(path), image.full());
			Files.write(this.root.resolve(thumbnailPath), image.thumbnail());
		}
		catch (IOException ex) {
			delete(path);
			delete(thumbnailPath);
			throw new UncheckedIOException("Could not save a product photo", ex);
		}
		return new Stored(path, thumbnailPath);
	}

	/** A file that cannot be deleted is only wasted space, so this does not fail the request. */
	void delete(String path) {
		try {
			Files.deleteIfExists(this.root.resolve(path));
		}
		catch (IOException ex) {
			logger.warn("Could not delete the upload {}", path, ex);
		}
	}

	record Stored(String path, String thumbnailPath) {
	}

}
