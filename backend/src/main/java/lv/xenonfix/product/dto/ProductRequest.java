package lv.xenonfix.product.dto;

import java.math.BigDecimal;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lv.xenonfix.product.ProductLanguage;

/**
 * @param slug the page address; empty means it is made from the Latvian name
 * @param version when updating, the version the admin was looking at
 */
public record ProductRequest(@Pattern(regexp = "|[a-z0-9]+(-[a-z0-9]+)*") @Size(max = 200) String slug,
		@Size(max = 200) String partNumber,
		@NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal price,
		@DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal salePrice,
		@NotNull @Min(0) @Max(1_000_000) Integer stockQuantity,
		@NotNull Map<ProductLanguage, @Valid ProductTextsDto> translations, Long version) {
}
