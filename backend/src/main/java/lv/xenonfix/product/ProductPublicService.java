package lv.xenonfix.product;

import lv.xenonfix.common.ApiException;
import lv.xenonfix.product.dto.StoreImageResponse;
import lv.xenonfix.product.dto.StoreProductPageResponse;
import lv.xenonfix.product.dto.StoreProductResponse;
import lv.xenonfix.product.dto.StoreProductSummaryResponse;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** What customers see: published products only, and never the stock number. */
@Service
class ProductPublicService {

	private static final String UPLOADS_URL = "/uploads/";

	private static final int MAX_PAGE_SIZE = 48;

	private final ProductRepository products;

	ProductPublicService(ProductRepository products) {
		this.products = products;
	}

	@Transactional(readOnly = true)
	StoreProductPageResponse list(ProductLanguage language, int page, int size) {
		var request = PageRequest.of(Math.max(0, page), Math.clamp(size, 1, MAX_PAGE_SIZE));
		var result = this.products.findByStatusOrderByCreatedAtDescIdDesc(ProductStatus.PUBLISHED, request);
		return new StoreProductPageResponse(
				result.getContent().stream().map((product) -> toSummary(product, language)).toList(),
				result.getNumber(), result.getTotalPages());
	}

	@Transactional(readOnly = true)
	StoreProductResponse get(String slug, ProductLanguage language) {
		Product product = this.products.findBySlugAndStatus(slug, ProductStatus.PUBLISHED)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND"));
		ProductTexts texts = texts(product, language);
		return new StoreProductResponse(product.getSlug(), texts.name(), texts.description(), product.getPartNumber(),
				product.getPrice(), product.getSalePrice(), isAvailable(product),
				product.getImages()
					.stream()
					.map((image) -> new StoreImageResponse(UPLOADS_URL + image.getPath(),
							UPLOADS_URL + image.getThumbnailPath(), image.getWidth(), image.getHeight()))
					.toList());
	}

	/** Latvian is required for every product, so it is what an untranslated language shows. */
	static ProductTexts texts(Product product, ProductLanguage language) {
		var translations = product.getTranslations();
		return translations.getOrDefault(language, translations.get(ProductLanguage.lv));
	}

	private static StoreProductSummaryResponse toSummary(Product product, ProductLanguage language) {
		String thumbnailUrl = product.getImages()
			.stream()
			.findFirst()
			.map((image) -> UPLOADS_URL + image.getThumbnailPath())
			.orElse(null);
		return new StoreProductSummaryResponse(product.getSlug(), texts(product, language).name(), product.getPrice(),
				product.getSalePrice(), isAvailable(product), thumbnailUrl);
	}

	private static boolean isAvailable(Product product) {
		return product.getStockQuantity() > 0;
	}

}
