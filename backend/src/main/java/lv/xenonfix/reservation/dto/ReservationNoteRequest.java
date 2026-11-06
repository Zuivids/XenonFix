package lv.xenonfix.reservation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param note empty removes the note
 * @param version the version the admin was looking at
 */
public record ReservationNoteRequest(@Size(max = 2000) String note, @NotNull Long version) {
}
