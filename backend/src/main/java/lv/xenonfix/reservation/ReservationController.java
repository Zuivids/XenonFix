package lv.xenonfix.reservation;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import lv.xenonfix.reservation.dto.ReservationRequest;
import lv.xenonfix.reservation.dto.ReservationResponse;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ReservationController {

	private final ReservationService service;

	ReservationController(ReservationService service) {
		this.service = service;
	}

	@PostMapping("/api/store/reservations")
	@ResponseStatus(HttpStatus.CREATED)
	ReservationResponse create(@Valid @RequestBody ReservationRequest body, HttpServletRequest request) {
		return this.service.create(body, request.getRemoteAddr());
	}

}
