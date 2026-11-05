package lv.xenonfix.product;

import java.math.BigDecimal;
import java.time.Clock;

import lv.xenonfix.common.ApiException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Takes stock for other features, so they need nothing else from inside this package. */
@Service
public class ProductStock {

	private final ProductRepository products;

	private final Clock clock;

	ProductStock(ProductRepository products, Clock clock) {
		this.products = products;
		this.clock = clock;
	}

	/**
	 * Takes the quantity in the caller's transaction, so it goes back if the caller fails.
	 * @return the product as it is now, for the caller to keep
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public TakenProduct take(String slug, int quantity, ProductLanguage language) {
		if (this.products.takeStock(slug, quantity, this.clock.instant()) == 0) {
			throw this.products.existsBySlugAndStatus(slug, ProductStatus.PUBLISHED)
					? new ApiException(HttpStatus.CONFLICT, "OUT_OF_STOCK")
					: new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND");
		}
		Product product = this.products.findBySlugAndStatus(slug, ProductStatus.PUBLISHED).orElseThrow();
		BigDecimal unitPrice = (product.getSalePrice() != null) ? product.getSalePrice() : product.getPrice();
		return new TakenProduct(product.getId(), product.getTranslations().get(ProductLanguage.lv).name(),
				ProductPublicService.texts(product, language).name(), product.getPartNumber(), unitPrice);
	}

	/**
	 * @param latvianName the name for the shop
	 * @param name the name in the language asked for, or the Latvian one if not translated
	 */
	public record TakenProduct(long id, String latvianName, String name, String partNumber, BigDecimal unitPrice) {
	}

}
