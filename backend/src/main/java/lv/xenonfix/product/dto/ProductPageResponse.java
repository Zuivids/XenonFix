package lv.xenonfix.product.dto;

import java.util.List;

public record ProductPageResponse(List<ProductSummaryResponse> items, int page, int totalPages) {
}
