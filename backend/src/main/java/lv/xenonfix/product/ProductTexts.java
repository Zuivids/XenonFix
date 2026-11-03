package lv.xenonfix.product;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public record ProductTexts(@Column(nullable = false, length = 200) String name,
		@Column(length = 5000) String description) {
}
