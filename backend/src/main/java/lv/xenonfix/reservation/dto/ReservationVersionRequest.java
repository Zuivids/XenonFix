package lv.xenonfix.reservation.dto;

import jakarta.validation.constraints.NotNull;

/**
 * @param version the version the admin was looking at
 */
public record ReservationVersionRequest(@NotNull Long version) {
}
