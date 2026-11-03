package lv.xenonfix.product.dto;

import java.math.BigDecimal;
import java.time.Instant;

import lv.xenonfix.product.ProductStatus;

/** A row in the admin product list. */
public record ProductSummaryResponse(long id, String name, String slug, String partNumber, BigDecimal price,
		BigDecimal salePrice, int stockQuantity, ProductStatus status, String thumbnailUrl, Instant updatedAt) {
}
