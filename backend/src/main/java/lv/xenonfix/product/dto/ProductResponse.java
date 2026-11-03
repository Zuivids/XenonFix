package lv.xenonfix.product.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import lv.xenonfix.product.ProductLanguage;
import lv.xenonfix.product.ProductStatus;

public record ProductResponse(long id, String slug, String partNumber, BigDecimal price, BigDecimal salePrice,
		int stockQuantity, ProductStatus status, Map<ProductLanguage, ProductTextsDto> translations,
		List<ProductImageResponse> images, long version, Instant createdAt, Instant updatedAt) {
}
