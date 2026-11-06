package lv.xenonfix.product.dto;

import java.time.Instant;

/** A published product's page, for the sitemap the frontend server makes. */
public record StoreSitemapEntry(String slug, Instant updatedAt) {
}
