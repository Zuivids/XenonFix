package lv.xenonfix.product;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import lv.xenonfix.audit.AuditEventType;
import lv.xenonfix.audit.AuditService;
import lv.xenonfix.common.ApiException;
import lv.xenonfix.product.dto.ProductImageResponse;
import lv.xenonfix.product.dto.ProductPageResponse;
import lv.xenonfix.product.dto.ProductRequest;
import lv.xenonfix.product.dto.ProductResponse;
import lv.xenonfix.product.dto.ProductSummaryResponse;
import lv.xenonfix.product.dto.ProductTextsDto;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
class ProductAdminService {

	private static final String TARGET_TYPE = "PRODUCT";

	private static final String UPLOADS_URL = "/uploads/";

	private static final int PAGE_SIZE = 50;

	private static final int MAX_IMAGES = 20;

	private final ProductRepository products;

	private final ImageProcessor imageProcessor;

	private final ProductImageStorage imageStorage;

	private final AuditService audit;

	private final Clock clock;

	ProductAdminService(ProductRepository products, ImageProcessor imageProcessor, ProductImageStorage imageStorage,
			AuditService audit, Clock clock) {
		this.products = products;
		this.imageProcessor = imageProcessor;
		this.imageStorage = imageStorage;
		this.audit = audit;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	ProductPageResponse list(ProductStatus status, String search, int page) {
		String pattern = StringUtils.hasText(search) ? "%" + search.strip().toLowerCase(Locale.ROOT) + "%" : null;
		var result = this.products.search(status, pattern, PageRequest.of(Math.max(0, page), PAGE_SIZE));
		return new ProductPageResponse(result.getContent().stream().map(ProductAdminService::toSummary).toList(),
				result.getNumber(), result.getTotalPages());
	}

	@Transactional(readOnly = true)
	ProductResponse get(long id) {
		return toResponse(find(id));
	}

	@Transactional
	ProductResponse create(ProductRequest request, Actor actor) {
		Map<ProductLanguage, ProductTexts> translations = translations(request);
		checkSalePrice(request);
		String slug = StringUtils.hasText(request.slug()) ? requireFreeSlug(request.slug())
				: freeSlugFor(translations.get(ProductLanguage.lv).name());
		Instant now = this.clock.instant();
		Product product = new Product(slug, now);
		product.update(slug, partNumber(request), request.price(), request.salePrice(), request.stockQuantity(),
				translations, now);
		product = this.products.saveAndFlush(product);
		record(AuditEventType.PRODUCT_CREATED, product, actor, describe(product));
		return toResponse(product);
	}

	@Transactional
	ProductResponse update(long id, ProductRequest request, Actor actor) {
		Product product = find(id);
		if (request.version() == null || request.version() != product.getVersion()) {
			throw new ApiException(HttpStatus.CONFLICT, "CHANGED_BY_ANOTHER");
		}
		Map<ProductLanguage, ProductTexts> translations = translations(request);
		checkSalePrice(request);
		String slug = StringUtils.hasText(request.slug()) ? request.slug() : product.getSlug();
		if (this.products.existsBySlugAndIdNot(slug, id)) {
			throw new ApiException(HttpStatus.CONFLICT, "SLUG_TAKEN");
		}
		int oldStock = product.getStockQuantity();
		product.update(slug, partNumber(request), request.price(), request.salePrice(), request.stockQuantity(),
				translations, this.clock.instant());
		product = this.products.saveAndFlush(product);
		record(AuditEventType.PRODUCT_UPDATED, product, actor, describe(product));
		if (oldStock != product.getStockQuantity()) {
			record(AuditEventType.PRODUCT_STOCK_CHANGED, product, actor,
					describe(product) + ": " + oldStock + " → " + product.getStockQuantity());
		}
		return toResponse(product);
	}

	@Transactional
	ProductResponse changeStatus(long id, ProductStatus status, Actor actor) {
		Product product = find(id);
		if (status == ProductStatus.PUBLISHED && product.getImages().isEmpty()) {
			throw new ApiException(HttpStatus.CONFLICT, "PUBLISH_NEEDS_IMAGE");
		}
		if (status != product.getStatus()) {
			ProductStatus oldStatus = product.getStatus();
			product.changeStatus(status, this.clock.instant());
			product = this.products.saveAndFlush(product);
			record(AuditEventType.PRODUCT_STATUS_CHANGED, product, actor,
					describe(product) + ": " + statusName(oldStatus) + " → " + statusName(status));
		}
		return toResponse(product);
	}

	@Transactional
	ProductResponse addImage(long id, MultipartFile file, Actor actor) {
		Product product = find(id);
		if (product.getImages().size() >= MAX_IMAGES) {
			throw new ApiException(HttpStatus.CONFLICT, "TOO_MANY_IMAGES");
		}
		ImageProcessor.Processed processed;
		try {
			processed = this.imageProcessor.process(file.getBytes());
		}
		catch (IOException ex) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IMAGE");
		}
		ProductImageStorage.Stored stored = this.imageStorage.save(id, processed);
		deleteIfRolledBack(stored);
		product.addImage(stored.path(), stored.thumbnailPath(), processed.width(), processed.height(),
				this.clock.instant());
		product = this.products.saveAndFlush(product);
		record(AuditEventType.PRODUCT_IMAGE_ADDED, product, actor, describe(product));
		return toResponse(product);
	}

	@Transactional
	ProductResponse removeImage(long id, long imageId, Actor actor) {
		Product product = find(id);
		ProductImage image = product.findImage(imageId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "IMAGE_NOT_FOUND"));
		if (product.getStatus() == ProductStatus.PUBLISHED && product.getImages().size() == 1) {
			throw new ApiException(HttpStatus.CONFLICT, "LAST_IMAGE_OF_PUBLISHED");
		}
		product.removeImage(image, this.clock.instant());
		deleteAfterCommit(image);
		product = this.products.saveAndFlush(product);
		record(AuditEventType.PRODUCT_IMAGE_REMOVED, product, actor, describe(product));
		return toResponse(product);
	}

	@Transactional
	ProductResponse orderImages(long id, List<Long> imageIds, Actor actor) {
		Product product = find(id);
		boolean sameImages = imageIds.size() == product.getImages().size()
				&& new HashSet<>(imageIds).equals(new HashSet<>(product.getImages().stream().map(ProductImage::getId).toList()));
		if (!sameImages) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IMAGE_ORDER");
		}
		product.orderImages(imageIds, this.clock.instant());
		return toResponse(this.products.saveAndFlush(product));
	}

	private Product find(long id) {
		return this.products.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND"));
	}

	/**
	 * The Latvian name is required. A language with neither name nor description is simply
	 * not translated yet, and the shop will show Latvian for it.
	 */
	private static Map<ProductLanguage, ProductTexts> translations(ProductRequest request) {
		Map<ProductLanguage, ProductTexts> result = new EnumMap<>(ProductLanguage.class);
		request.translations().forEach((language, texts) -> {
			String name = (texts != null && texts.name() != null) ? texts.name().strip() : "";
			String description = (texts != null && StringUtils.hasText(texts.description()))
					? texts.description().strip() : null;
			if (name.isEmpty() && description != null) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "TRANSLATION_NEEDS_NAME");
			}
			if (!name.isEmpty()) {
				result.put(language, new ProductTexts(name, description));
			}
		});
		if (!result.containsKey(ProductLanguage.lv)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "LATVIAN_NAME_REQUIRED");
		}
		return result;
	}

	private static void checkSalePrice(ProductRequest request) {
		BigDecimal salePrice = request.salePrice();
		if (salePrice != null && salePrice.compareTo(request.price()) >= 0) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "SALE_PRICE_NOT_LOWER");
		}
	}

	private static String partNumber(ProductRequest request) {
		return StringUtils.hasText(request.partNumber()) ? request.partNumber().strip() : null;
	}

	private String requireFreeSlug(String slug) {
		if (this.products.existsBySlug(slug)) {
			throw new ApiException(HttpStatus.CONFLICT, "SLUG_TAKEN");
		}
		return slug;
	}

	private String freeSlugFor(String name) {
		String base = Slugs.of(name);
		base = base.substring(0, Math.min(base.length(), 190));
		String slug = base;
		for (int number = 2; this.products.existsBySlug(slug); number++) {
			slug = base + "-" + number;
		}
		return slug;
	}

	/** Files are not part of the database transaction, so they are cleaned up by hand. */
	private void deleteIfRolledBack(ProductImageStorage.Stored stored) {
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

			@Override
			public void afterCompletion(int status) {
				if (status != STATUS_COMMITTED) {
					ProductAdminService.this.imageStorage.delete(stored.path());
					ProductAdminService.this.imageStorage.delete(stored.thumbnailPath());
				}
			}

		});
	}

	private void deleteAfterCommit(ProductImage image) {
		String path = image.getPath();
		String thumbnailPath = image.getThumbnailPath();
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

			@Override
			public void afterCommit() {
				ProductAdminService.this.imageStorage.delete(path);
				ProductAdminService.this.imageStorage.delete(thumbnailPath);
			}

		});
	}

	private void record(AuditEventType type, Product product, Actor actor, String details) {
		this.audit.recordOnTarget(type, actor.adminId(), TARGET_TYPE, product.getId().toString(), actor.ipAddress(),
				details);
	}

	private static String describe(Product product) {
		return product.getTranslations().get(ProductLanguage.lv).name();
	}

	/** The audit log is read in the Latvian admin panel. */
	private static String statusName(ProductStatus status) {
		return switch (status) {
			case DRAFT -> "melnraksts";
			case PUBLISHED -> "publicēts";
			case ARCHIVED -> "arhivēts";
		};
	}

	private static ProductSummaryResponse toSummary(Product product) {
		String thumbnailUrl = product.getImages().stream().findFirst()
			.map((image) -> UPLOADS_URL + image.getThumbnailPath())
			.orElse(null);
		return new ProductSummaryResponse(product.getId(), describe(product), product.getSlug(),
				product.getPartNumber(), product.getPrice(), product.getSalePrice(), product.getStockQuantity(),
				product.getStatus(), thumbnailUrl, product.getUpdatedAt());
	}

	private static ProductResponse toResponse(Product product) {
		Map<ProductLanguage, ProductTextsDto> translations = new EnumMap<>(ProductLanguage.class);
		product.getTranslations()
			.forEach((language, texts) -> translations.put(language,
					new ProductTextsDto(texts.name(), texts.description())));
		List<ProductImageResponse> images = product.getImages().stream()
			.map((image) -> new ProductImageResponse(image.getId(), UPLOADS_URL + image.getPath(),
					UPLOADS_URL + image.getThumbnailPath(), image.getWidth(), image.getHeight()))
			.toList();
		return new ProductResponse(product.getId(), product.getSlug(), product.getPartNumber(), product.getPrice(),
				product.getSalePrice(), product.getStockQuantity(), product.getStatus(), translations, images,
				product.getVersion(), product.getCreatedAt(), product.getUpdatedAt());
	}

	record Actor(long adminId, String ipAddress) {
	}

}
