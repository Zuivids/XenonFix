package lv.xenonfix.audit;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;

/** Append-only event. Never updated or deleted by the application. */
@Entity
@Immutable
@Table(name = "audit_log")
public class AuditLogEntry {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "event_type", nullable = false, length = 50)
	private AuditEventType eventType;

	@Column(name = "admin_id")
	private Long adminId;

	@Column(name = "target_type", length = 50)
	private String targetType;

	@Column(name = "target_id", length = 100)
	private String targetId;

	@Column(name = "ip_address", length = 45)
	private String ipAddress;

	@Column(length = 500)
	private String details;

	protected AuditLogEntry() {
	}

	AuditLogEntry(Instant occurredAt, AuditEventType eventType, Long adminId, String targetType, String targetId,
			String ipAddress, String details) {
		this.occurredAt = occurredAt;
		this.eventType = eventType;
		this.adminId = adminId;
		this.targetType = targetType;
		this.targetId = targetId;
		this.ipAddress = ipAddress;
		this.details = details;
	}

}
