package lv.xenonfix.product.dto;

import java.util.List;

public record StoreProductPageResponse(List<StoreProductSummaryResponse> items, int page, int totalPages) {
}
