package lv.xenonfix.page.dto;

import java.time.Instant;

/**
 * @param updatedBy the admin who last saved it, or empty for the starting content
 */
public record PageSummaryResponse(String page, Instant updatedAt, String updatedBy) {
}
