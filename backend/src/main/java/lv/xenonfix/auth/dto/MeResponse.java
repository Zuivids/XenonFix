package lv.xenonfix.auth.dto;

/**
 * @param step where the session is in the login, so the panel can show the right page
 */
public record MeResponse(long id, String email, String displayName, boolean passwordChangeRequired, Step step) {

	public enum Step {

		MFA_REQUIRED, MFA_SETUP_REQUIRED, PASSWORD_CHANGE_REQUIRED, READY

	}

}
