package lv.xenonfix.auth;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/**
 * The logged-in admin, kept in the HTTP session.
 *
 * @param authenticatedAt when the session logged in; decides the absolute timeout
 * @param securityVersion the account's security version at login
 */
public record AuthenticatedAdmin(long id, String email, Instant authenticatedAt,
		long securityVersion) implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

}
