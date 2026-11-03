package lv.xenonfix.auth;

import java.util.List;

import lv.xenonfix.admin.AdminUser;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;

/**
 * What a session may do. Being logged in is not enough for the admin features: a session
 * with a password that must be changed gets no authorities. MFA fits here later: grant
 * {@link #ADMIN} only after the second factor.
 */
public final class Authorities {

	public static final String ADMIN = "ROLE_ADMIN";

	private Authorities() {
	}

	static List<GrantedAuthority> of(AdminUser admin) {
		return admin.isPasswordChangeRequired() ? List.of() : AuthorityUtils.createAuthorityList(ADMIN);
	}

}
