package lv.xenonfix.product.dto;

import jakarta.validation.constraints.NotNull;

import lv.xenonfix.product.ProductStatus;

public record ProductStatusRequest(@NotNull ProductStatus status) {
}
