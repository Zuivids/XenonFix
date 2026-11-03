package lv.xenonfix.health;

import lv.xenonfix.health.dto.HealthResponse;
import lv.xenonfix.health.dto.HealthResponse.Status;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class HealthController {

	private final HealthService healthService;

	HealthController(HealthService healthService) {
		this.healthService = healthService;
	}

	@GetMapping("/api/health")
	ResponseEntity<HealthResponse> health() {
		HealthResponse health = this.healthService.check();
		HttpStatus status = (health.status() == Status.UP) ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
		return ResponseEntity.status(status).body(health);
	}

}
