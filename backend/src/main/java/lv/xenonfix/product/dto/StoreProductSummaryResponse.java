package lv.xenonfix.product.dto;

import java.math.BigDecimal;

public record StoreProductSummaryResponse(String slug, String name, BigDecimal price, BigDecimal salePrice,
		boolean available, String thumbnailUrl) {
}
