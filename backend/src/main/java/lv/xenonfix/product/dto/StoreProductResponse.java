package lv.xenonfix.product.dto;

import java.math.BigDecimal;
import java.util.List;

public record StoreProductResponse(String slug, String name, String description, String partNumber, BigDecimal price,
		BigDecimal salePrice, boolean available, List<StoreImageResponse> images) {
}
