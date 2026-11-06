package lv.xenonfix.reservation.dto;

import java.math.BigDecimal;
import java.time.Instant;

import lv.xenonfix.product.ProductLanguage;
import lv.xenonfix.reservation.ReservationStatus;

/**
 * @param anonymizedAt when the customer's details were erased; they are all empty after that
 */
public record ReservationDetailResponse(long id, String reference, long productId, String productName,
		String partNumber, BigDecimal unitPrice, int quantity, BigDecimal total, String customerName,
		String customerPhone, String customerEmail, String comment, String adminNote, ProductLanguage language,
		ReservationStatus status, Instant createdAt, Instant updatedAt, Instant anonymizedAt, long version) {
}
