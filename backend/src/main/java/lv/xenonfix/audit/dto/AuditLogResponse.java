package lv.xenonfix.audit.dto;

import java.time.Instant;

import lv.xenonfix.audit.AuditEventType;

public record AuditLogResponse(Instant occurredAt, AuditEventType eventType, String adminEmail, String targetType,
		String targetId, String ipAddress, String details) {
}
