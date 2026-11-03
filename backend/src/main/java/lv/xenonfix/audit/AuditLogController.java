package lv.xenonfix.audit;

import lv.xenonfix.audit.dto.AuditLogPageResponse;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AuditLogController {

	private final AuditService auditService;

	AuditLogController(AuditService auditService) {
		this.auditService = auditService;
	}

	@GetMapping("/api/admin/audit-log")
	AuditLogPageResponse auditLog(@RequestParam(defaultValue = "0") int page) {
		return this.auditService.page(page);
	}

}
