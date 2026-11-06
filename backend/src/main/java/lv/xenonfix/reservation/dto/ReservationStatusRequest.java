package lv.xenonfix.reservation.dto;

import jakarta.validation.constraints.NotNull;

import lv.xenonfix.reservation.ReservationStatus;

/**
 * @param version the version the admin was looking at
 */
public record ReservationStatusRequest(@NotNull ReservationStatus status, @NotNull Long version) {
}
