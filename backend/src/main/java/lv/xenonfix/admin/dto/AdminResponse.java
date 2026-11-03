package lv.xenonfix.admin.dto;

import java.time.Instant;

import lv.xenonfix.admin.AdminStatus;

public record AdminResponse(long id, String email, String displayName, AdminStatus status, boolean locked,
		boolean passwordChangeRequired, Instant lastLoginAt, Instant createdAt) {
}
