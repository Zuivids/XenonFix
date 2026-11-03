package lv.xenonfix.product.dto;

import jakarta.validation.constraints.Size;

public record ProductTextsDto(@Size(max = 200) String name, @Size(max = 5000) String description) {
}
