package lv.xenonfix.audit;

import java.time.Clock;

import lv.xenonfix.audit.dto.AuditLogPageResponse;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

	private static final int PAGE_SIZE = 50;

	private final AuditLogRepository repository;

	private final Clock clock;

	AuditService(AuditLogRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	/**
	 * Records an event in the caller's transaction (or a new one if there is none). Never
	 * pass passwords or other secrets as details.
	 */
	@Transactional
	public void record(AuditEventType type, Long adminId, String ipAddress, String details) {
		recordOnTarget(type, adminId, null, null, ipAddress, details);
	}

	/** Records an admin's action on something, for example another admin's account. */
	@Transactional
	public void recordOnTarget(AuditEventType type, Long adminId, String targetType, String targetId, String ipAddress,
			String details) {
		this.repository.save(
				new AuditLogEntry(this.clock.instant(), type, adminId, targetType, targetId, ipAddress, details));
	}

	@Transactional(readOnly = true)
	AuditLogPageResponse page(int page) {
		var result = this.repository.findNewestFirst(PageRequest.of(Math.max(0, page), PAGE_SIZE));
		return new AuditLogPageResponse(result.getContent(), result.getNumber(), result.getTotalPages());
	}

}
