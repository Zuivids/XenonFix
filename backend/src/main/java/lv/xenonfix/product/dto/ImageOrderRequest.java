package lv.xenonfix.product.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;

public record ImageOrderRequest(@NotNull List<@NotNull Long> imageIds) {
}
