package lv.xenonfix.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

import lv.xenonfix.admin.AdminUser;
import lv.xenonfix.auth.dto.ChangePasswordRequest;
import lv.xenonfix.auth.dto.LoginRequest;
import lv.xenonfix.auth.dto.LoginResponse;
import lv.xenonfix.auth.dto.MeResponse;
import lv.xenonfix.auth.dto.MfaCodeRequest;
import lv.xenonfix.auth.dto.MfaSetupResponse;
import lv.xenonfix.auth.dto.RecoveryCodesLeftResponse;
import lv.xenonfix.auth.dto.RecoveryCodesResponse;
import lv.xenonfix.common.ApiException;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AuthController {

	/** The key being set up, kept in the session until a code proves the app has it. */
	private static final String SETUP_SECRET = AuthController.class.getName() + ".setupSecret";

	private final AuthService authService;

	private final LoginSession loginSession;

	AuthController(AuthService authService, LoginSession loginSession) {
		this.authService = authService;
		this.loginSession = loginSession;
	}

	/** Sets the {@code XSRF-TOKEN} cookie. The admin panel calls this before logging in. */
	@GetMapping("/api/admin/auth/csrf")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void csrf(CsrfToken token) {
		// Reading the token makes Spring Security write the cookie
		token.getToken();
	}

	/** The first step. The session may then only do the second one. */
	@PostMapping("/api/admin/auth/login")
	LoginResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
			HttpServletResponse response) {
		Authentication authentication = this.authService.login(body.email(), body.password(),
				request.getRemoteAddr());
		this.loginSession.establish(authentication, request, response);
		LoginResponse.Status status = Authorities.has(authentication.getAuthorities(), Authorities.MFA_PENDING)
				? LoginResponse.Status.MFA_REQUIRED : LoginResponse.Status.MFA_SETUP_REQUIRED;
		return new LoginResponse(status, me(authentication));
	}

	/** The second step, for an admin who has the app: its code or a backup code. */
	@PostMapping("/api/admin/auth/mfa")
	LoginResponse verifyMfa(@Valid @RequestBody MfaCodeRequest body, @AuthenticationPrincipal AuthenticatedAdmin admin,
			HttpServletRequest request, HttpServletResponse response) {
		Authentication authentication = this.authService.verifySecondStep(admin, body.code(), request.getRemoteAddr());
		this.loginSession.establish(authentication, request, response);
		return new LoginResponse(LoginResponse.Status.AUTHENTICATED, me(authentication));
	}

	/** The second step, for an admin without the app: the key to set it up with. */
	@GetMapping("/api/admin/me/mfa/setup")
	MfaSetupResponse mfaSetup(@AuthenticationPrincipal AuthenticatedAdmin admin, HttpSession session) {
		// The same key when the page is opened again, so a key already scanned keeps working
		byte[] secret = (byte[]) session.getAttribute(SETUP_SECRET);
		if (secret == null) {
			secret = this.authService.newSecret();
			session.setAttribute(SETUP_SECRET, secret);
		}
		return this.authService.setupDetails(admin.email(), secret);
	}

	@PostMapping("/api/admin/me/mfa/setup")
	RecoveryCodesResponse completeMfaSetup(@Valid @RequestBody MfaCodeRequest body,
			@AuthenticationPrincipal AuthenticatedAdmin admin, HttpServletRequest request,
			HttpServletResponse response) {
		HttpSession session = request.getSession();
		byte[] secret = (byte[]) session.getAttribute(SETUP_SECRET);
		if (secret == null) {
			throw new ApiException(HttpStatus.CONFLICT, "MFA_SETUP_NOT_STARTED");
		}
		AuthService.SetupResult result = this.authService.completeSetup(admin, secret, body.code(),
				request.getRemoteAddr());
		session.removeAttribute(SETUP_SECRET);
		this.loginSession.establish(result.authentication(), request, response);
		return new RecoveryCodesResponse(result.recoveryCodes(), me(result.authentication()));
	}

	@GetMapping("/api/admin/me/mfa/recovery-codes")
	RecoveryCodesLeftResponse recoveryCodesLeft(@AuthenticationPrincipal AuthenticatedAdmin admin) {
		return new RecoveryCodesLeftResponse(this.authService.recoveryCodesLeft(admin.id()));
	}

	@PostMapping("/api/admin/me/mfa/recovery-codes")
	RecoveryCodesResponse renewRecoveryCodes(@Valid @RequestBody MfaCodeRequest body, Authentication authentication,
			HttpServletRequest request) {
		AuthenticatedAdmin admin = (AuthenticatedAdmin) authentication.getPrincipal();
		return new RecoveryCodesResponse(
				this.authService.renewRecoveryCodes(admin.id(), body.code(), request.getRemoteAddr()),
				me(authentication));
	}

	@GetMapping("/api/admin/me")
	MeResponse me(Authentication authentication) {
		AuthenticatedAdmin admin = (AuthenticatedAdmin) authentication.getPrincipal();
		AdminUser user = this.authService.find(admin.id());
		return new MeResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.isPasswordChangeRequired(),
				step(authentication, user));
	}

	@PostMapping("/api/admin/me/password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void changePassword(@Valid @RequestBody ChangePasswordRequest body,
			@AuthenticationPrincipal AuthenticatedAdmin admin, HttpServletRequest request,
			HttpServletResponse response) {
		Authentication authentication = this.authService.changePassword(admin.id(), body.currentPassword(),
				body.newPassword(), request.getRemoteAddr());
		this.loginSession.establish(authentication, request, response);
	}

	private static MeResponse.Step step(Authentication authentication, AdminUser user) {
		var authorities = authentication.getAuthorities();
		if (Authorities.has(authorities, Authorities.MFA_PENDING)) {
			return MeResponse.Step.MFA_REQUIRED;
		}
		if (Authorities.has(authorities, Authorities.MFA_SETUP)) {
			return MeResponse.Step.MFA_SETUP_REQUIRED;
		}
		return user.isPasswordChangeRequired() ? MeResponse.Step.PASSWORD_CHANGE_REQUIRED : MeResponse.Step.READY;
	}

}
