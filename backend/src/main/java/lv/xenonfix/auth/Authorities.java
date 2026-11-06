package lv.xenonfix.auth;

import java.util.Collection;
import java.util.List;

import lv.xenonfix.admin.AdminUser;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;

/**
 * What a session may do, by the step of the login it is at. Being logged in is not enough
 * for the admin features: only a session that has done both steps and has its own password
 * gets {@link #ADMIN}. SecurityConfig decides which endpoint needs which.
 */
public final class Authorities {

	public static final String ADMIN = "ROLE_ADMIN";

	/** The password was right; the authenticator code must follow. */
	public static final String MFA_PENDING = "MFA_PENDING";

	/** The password was right; the admin must set up the authenticator app first. */
	public static final String MFA_SETUP = "MFA_SETUP";

	/** Both steps done. Enough to change a temporary password, not yet for the rest. */
	public static final String MFA_DONE = "MFA_DONE";

	private Authorities() {
	}

	/** After the password, before the second step. */
	static List<GrantedAuthority> afterPassword(AdminUser admin) {
		return AuthorityUtils.createAuthorityList(admin.isMfaEnabled() ? MFA_PENDING : MFA_SETUP);
	}

	/** After the second step. A temporary password must still be changed before the rest. */
	static List<GrantedAuthority> afterSecondStep(AdminUser admin) {
		return admin.isPasswordChangeRequired() ? AuthorityUtils.createAuthorityList(MFA_DONE)
				: AuthorityUtils.createAuthorityList(MFA_DONE, ADMIN);
	}

	static boolean has(Collection<? extends GrantedAuthority> authorities, String authority) {
		return authorities.stream().anyMatch((granted) -> authority.equals(granted.getAuthority()));
	}

}
