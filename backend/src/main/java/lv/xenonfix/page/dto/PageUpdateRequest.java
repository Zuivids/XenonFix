package lv.xenonfix.page.dto;

import java.util.Map;

import jakarta.validation.constraints.NotNull;

/**
 * @param content the whole page; checked against the page's own shape (PageDocuments)
 * @param version the version the admin was editing
 */
public record PageUpdateRequest(@NotNull Map<String, Object> content, @NotNull Long version) {
}
