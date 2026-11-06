package lv.xenonfix.page.dto;

import java.time.Instant;
import java.util.Map;

/**
 * @param content the page with all three languages, as the panel edits it
 */
public record PageResponse(String page, Map<String, Object> content, long version, Instant updatedAt,
		String updatedBy) {
}
