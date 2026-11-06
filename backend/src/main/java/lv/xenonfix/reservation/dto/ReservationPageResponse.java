package lv.xenonfix.reservation.dto;

import java.util.List;

public record ReservationPageResponse(List<ReservationSummaryResponse> items, int page, int totalPages) {
}
