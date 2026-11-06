package lv.xenonfix.auth.dto;

/**
 * @param status what happens next
 * @param admin the admin, with the step their session is at
 */
public record LoginResponse(Status status, MeResponse admin) {

	public enum Status {

		/** Both steps done. */
		AUTHENTICATED,

		/** The password was right; the code from the authenticator app must follow. */
		MFA_REQUIRED,

		/** The password was right; the admin must set up the authenticator app first. */
		MFA_SETUP_REQUIRED

	}

}
