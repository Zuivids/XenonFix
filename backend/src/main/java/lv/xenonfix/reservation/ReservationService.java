package lv.xenonfix.reservation;

import java.time.Clock;

import lv.xenonfix.admin.Emails;
import lv.xenonfix.audit.AuditEventType;
import lv.xenonfix.audit.AuditService;
import lv.xenonfix.product.ProductStock;
import lv.xenonfix.product.ProductStock.TakenProduct;
import lv.xenonfix.reservation.dto.ReservationRequest;
import lv.xenonfix.reservation.dto.ReservationResponse;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
class ReservationService {

	private static final String TARGET_TYPE = "RESERVATION";

	private final ReservationRepository reservations;

	private final ProductStock stock;

	private final AuditService audit;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	ReservationService(ReservationRepository reservations, ProductStock stock, AuditService audit,
			ApplicationEventPublisher events, Clock clock) {
		this.reservations = reservations;
		this.stock = stock;
		this.audit = audit;
		this.events = events;
		this.clock = clock;
	}

	/** The stock, the reservation and its audit entry are saved together or not at all. */
	@Transactional
	ReservationResponse create(ReservationRequest request, String ipAddress) {
		TakenProduct product = this.stock.take(request.slug(), request.quantity(), request.language());
		String email = Emails.normalize(request.email());
		String comment = StringUtils.hasText(request.comment()) ? request.comment().strip() : null;
		Reservation reservation = this.reservations.saveAndFlush(new Reservation(product.id(), product.latvianName(),
				product.partNumber(), product.unitPrice(), request.quantity(), request.name().strip(),
				request.phone().strip(), email, comment, request.language(), this.clock.instant()));
		String reference = reservation.getReference();
		// In Latvian like the rest of the log, and without the customer's details
		this.audit.recordOnTarget(AuditEventType.RESERVATION_CREATED, null, TARGET_TYPE,
				reservation.getId().toString(), ipAddress,
				reference + ": " + product.latvianName() + " × " + request.quantity());
		this.events.publishEvent(new ReservationCreated(reference, product.name(), product.latvianName(),
				product.partNumber(), product.unitPrice(), request.quantity(), request.name().strip(),
				request.phone().strip(), email, comment, request.language()));
		return new ReservationResponse(reference);
	}

}
