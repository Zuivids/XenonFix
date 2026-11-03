package lv.xenonfix.auth.dto;

/**
 * @param status what happens next; with MFA, a value such as MFA_REQUIRED fits here
 * @param admin the logged-in admin
 */
public record LoginResponse(Status status, MeResponse admin) {

	public enum Status {

		AUTHENTICATED

	}

}
