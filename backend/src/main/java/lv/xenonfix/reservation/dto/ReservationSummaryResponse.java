package lv.xenonfix.reservation.dto;

import java.math.BigDecimal;
import java.time.Instant;

import lv.xenonfix.reservation.ReservationStatus;

public record ReservationSummaryResponse(long id, String reference, String productName, int quantity, BigDecimal total,
		String customerName, String customerPhone, ReservationStatus status, Instant createdAt) {
}
