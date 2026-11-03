package lv.xenonfix.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import lv.xenonfix.admin.AdminUser;
import lv.xenonfix.auth.dto.ChangePasswordRequest;
import lv.xenonfix.auth.dto.LoginRequest;
import lv.xenonfix.auth.dto.LoginResponse;
import lv.xenonfix.auth.dto.MeResponse;

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

	@PostMapping("/api/admin/auth/login")
	LoginResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
			HttpServletResponse response) {
		Authentication authentication = this.authService.login(body.email(), body.password(),
				request.getRemoteAddr());
		this.loginSession.establish(authentication, request, response);
		AuthenticatedAdmin admin = (AuthenticatedAdmin) authentication.getPrincipal();
		return new LoginResponse(LoginResponse.Status.AUTHENTICATED, me(admin));
	}

	@GetMapping("/api/admin/me")
	MeResponse me(@AuthenticationPrincipal AuthenticatedAdmin admin) {
		AdminUser user = this.authService.find(admin.id());
		return new MeResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.isPasswordChangeRequired());
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

}
