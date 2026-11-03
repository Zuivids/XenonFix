package lv.xenonfix.audit.dto;

import java.util.List;

public record AuditLogPageResponse(List<AuditLogResponse> items, int page, int totalPages) {
}
