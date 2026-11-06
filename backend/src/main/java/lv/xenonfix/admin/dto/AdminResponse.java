package lv.xenonfix.admin.dto;

import java.time.Instant;

import lv.xenonfix.admin.AdminStatus;

/**
 * @param mfaEnabled whether the admin has set up the authenticator app
 */
public record AdminResponse(long id, String email, String displayName, AdminStatus status, boolean locked,
		boolean passwordChangeRequired, boolean mfaEnabled, Instant lastLoginAt, Instant createdAt) {
}
