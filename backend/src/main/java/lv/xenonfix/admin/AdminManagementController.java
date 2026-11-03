package lv.xenonfix.admin;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import lv.xenonfix.admin.dto.AdminResponse;
import lv.xenonfix.admin.dto.CreateAdminRequest;
import lv.xenonfix.admin.dto.ResetPasswordRequest;
import lv.xenonfix.auth.AuthenticatedAdmin;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/admins")
class AdminManagementController {

	private final AdminManagementService service;

	AdminManagementController(AdminManagementService service) {
		this.service = service;
	}

	@GetMapping
	List<AdminResponse> list() {
		return this.service.list();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	AdminResponse create(@Valid @RequestBody CreateAdminRequest body, @AuthenticationPrincipal AuthenticatedAdmin actor,
			HttpServletRequest request) {
		return this.service.create(body, actor, request.getRemoteAddr());
	}

	@PostMapping("/{id}/disable")
	AdminResponse disable(@PathVariable long id, @AuthenticationPrincipal AuthenticatedAdmin actor,
			HttpServletRequest request) {
		return this.service.disable(id, actor, request.getRemoteAddr());
	}

	@PostMapping("/{id}/enable")
	AdminResponse enable(@PathVariable long id, @AuthenticationPrincipal AuthenticatedAdmin actor,
			HttpServletRequest request) {
		return this.service.enable(id, actor, request.getRemoteAddr());
	}

	@PostMapping("/{id}/reset-password")
	AdminResponse resetPassword(@PathVariable long id, @Valid @RequestBody ResetPasswordRequest body,
			@AuthenticationPrincipal AuthenticatedAdmin actor, HttpServletRequest request) {
		return this.service.resetPassword(id, body.password(), actor, request.getRemoteAddr());
	}

}
