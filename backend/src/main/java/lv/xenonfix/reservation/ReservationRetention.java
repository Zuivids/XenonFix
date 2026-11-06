package lv.xenonfix.reservation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import lv.xenonfix.audit.AuditEventType;
import lv.xenonfix.audit.AuditService;
import lv.xenonfix.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Erases the customer's details from cancelled and completed reservations once the retention
 * period has passed since their last change, as the privacy policy promises. Pending and
 * confirmed reservations are never touched, however old.
 */
@Component
class ReservationRetention {

	private static final Logger logger = LoggerFactory.getLogger(ReservationRetention.class);

	private final ReservationRepository reservations;

	private final AuditService audit;

	private final Clock clock;

	private final Duration retention;

	ReservationRetention(ReservationRepository reservations, AuditService audit, Clock clock,
			AppProperties properties) {
		this.reservations = reservations;
		this.audit = audit;
		this.clock = clock;
		this.retention = properties.reservation().retention();
	}

	/** Every night at 03:30 shop time, when nobody is likely to be working on reservations. */
	@Scheduled(cron = "0 30 3 * * *", zone = "Europe/Riga")
	@Transactional
	public void run() {
		Instant now = this.clock.instant();
		int count = this.reservations.anonymizeFinishedBefore(now.minus(this.retention), now);
		if (count > 0) {
			// No admin: the system did it. Only the count, never whose details they were.
			this.audit.record(AuditEventType.RESERVATIONS_EXPIRED, null, null,
					"Automātiski dzēsti klientu dati " + count + " rezervācijām, kas nav mainītas "
							+ this.retention.toDays() + " dienas");
			logger.info("Anonymized {} reservations past the retention period", count);
		}
	}

}
