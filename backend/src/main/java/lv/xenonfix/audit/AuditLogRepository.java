package lv.xenonfix.audit;

import lv.xenonfix.audit.dto.AuditLogResponse;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface AuditLogRepository extends JpaRepository<AuditLogEntry, Long> {

	@Query(value = """
			select new lv.xenonfix.audit.dto.AuditLogResponse(e.occurredAt, e.eventType, a.email, e.targetType,
				e.targetId, e.ipAddress, e.details)
			from AuditLogEntry e left join lv.xenonfix.admin.AdminUser a on a.id = e.adminId
			order by e.occurredAt desc, e.id desc""", countQuery = "select count(e) from AuditLogEntry e")
	Page<AuditLogResponse> findNewestFirst(Pageable pageable);

}
