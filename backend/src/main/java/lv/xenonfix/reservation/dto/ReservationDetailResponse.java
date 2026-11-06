package lv.xenonfix.reservation.dto;

import java.math.BigDecimal;
import java.time.Instant;

import lv.xenonfix.product.ProductLanguage;
import lv.xenonfix.reservation.ReservationStatus;

public record ReservationDetailResponse(long id, String reference, long productId, String productName,
		String partNumber, BigDecimal unitPrice, int quantity, BigDecimal total, String customerName,
		String customerPhone, String customerEmail, String comment, String adminNote, ProductLanguage language,
		ReservationStatus status, Instant createdAt, Instant updatedAt, long version) {
}
