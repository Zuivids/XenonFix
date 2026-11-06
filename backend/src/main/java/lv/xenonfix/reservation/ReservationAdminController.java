package lv.xenonfix.reservation;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import lv.xenonfix.auth.AuthenticatedAdmin;
import lv.xenonfix.reservation.ReservationAdminService.Actor;
import lv.xenonfix.reservation.dto.ReservationDetailResponse;
import lv.xenonfix.reservation.dto.ReservationNoteRequest;
import lv.xenonfix.reservation.dto.ReservationPageResponse;
import lv.xenonfix.reservation.dto.ReservationStatusRequest;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/reservations")
class ReservationAdminController {

	private final ReservationAdminService service;

	ReservationAdminController(ReservationAdminService service) {
		this.service = service;
	}

	@GetMapping
	ReservationPageResponse list(@RequestParam(required = false) ReservationStatus status,
			@RequestParam(required = false) String search, @RequestParam(defaultValue = "0") int page) {
		return this.service.list(status, search, page);
	}

	@GetMapping("/{id}")
	ReservationDetailResponse get(@PathVariable long id) {
		return this.service.get(id);
	}

	@PostMapping("/{id}/status")
	ReservationDetailResponse changeStatus(@PathVariable long id, @Valid @RequestBody ReservationStatusRequest body,
			@AuthenticationPrincipal AuthenticatedAdmin admin, HttpServletRequest request) {
		return this.service.changeStatus(id, body.status(), body.version(), actor(admin, request));
	}

	@PutMapping("/{id}/note")
	ReservationDetailResponse changeNote(@PathVariable long id, @Valid @RequestBody ReservationNoteRequest body,
			@AuthenticationPrincipal AuthenticatedAdmin admin, HttpServletRequest request) {
		return this.service.changeNote(id, body.note(), body.version(), actor(admin, request));
	}

	private static Actor actor(AuthenticatedAdmin admin, HttpServletRequest request) {
		return new Actor(admin.id(), request.getRemoteAddr());
	}

}
