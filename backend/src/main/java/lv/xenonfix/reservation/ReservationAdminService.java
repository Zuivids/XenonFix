package lv.xenonfix.reservation;

import java.time.Clock;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lv.xenonfix.audit.AuditEventType;
import lv.xenonfix.audit.AuditService;
import lv.xenonfix.common.ApiException;
import lv.xenonfix.product.ProductStock;
import lv.xenonfix.reservation.dto.ReservationDetailResponse;
import lv.xenonfix.reservation.dto.ReservationPageResponse;
import lv.xenonfix.reservation.dto.ReservationSummaryResponse;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
class ReservationAdminService {

	private static final String TARGET_TYPE = "RESERVATION";

	private static final int PAGE_SIZE = 50;

	/** "XF-000123" or just "123". */
	private static final Pattern REFERENCE = Pattern.compile("(?:xf-)?0*(\\d{1,18})");

	private final ReservationRepository reservations;

	private final ProductStock stock;

	private final AuditService audit;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	ReservationAdminService(ReservationRepository reservations, ProductStock stock, AuditService audit,
			ApplicationEventPublisher events, Clock clock) {
		this.reservations = reservations;
		this.stock = stock;
		this.audit = audit;
		this.events = events;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	ReservationPageResponse list(ReservationStatus status, String search, int page) {
		String text = StringUtils.hasText(search) ? search.strip().toLowerCase(Locale.ROOT) : null;
		Matcher reference = (text != null) ? REFERENCE.matcher(text) : null;
		Long id = (reference != null && reference.matches()) ? Long.valueOf(reference.group(1)) : null;
		var result = this.reservations.search(status, (text != null) ? "%" + text + "%" : null, id,
				PageRequest.of(Math.max(0, page), PAGE_SIZE));
		return new ReservationPageResponse(
				result.getContent().stream().map(ReservationAdminService::toSummary).toList(), result.getNumber(),
				result.getTotalPages());
	}

	@Transactional(readOnly = true)
	ReservationDetailResponse get(long id) {
		return toDetail(find(id));
	}

	/**
	 * Any status can become any other. Only a cancelled reservation does not hold its stock, so
	 * stock moves only on the way into or out of that status. All of it happens or none of it:
	 * reopening without enough stock leaves the status as it was. The reservation is saved
	 * first, so of two admins changing it at once only one gets as far as moving stock.
	 */
	@Transactional
	ReservationDetailResponse changeStatus(long id, ReservationStatus status, long version, Actor actor) {
		Reservation reservation = find(id);
		checkVersion(reservation, version);
		ReservationStatus oldStatus = reservation.getStatus();
		if (status == oldStatus) {
			return toDetail(reservation);
		}
		reservation.changeStatus(status, this.clock.instant());
		reservation = this.reservations.saveAndFlush(reservation);
		String stockChange = moveStock(reservation, oldStatus, status);
		record(AuditEventType.RESERVATION_STATUS_CHANGED, reservation, actor, reservation.getReference() + ": "
				+ statusName(oldStatus) + " → " + statusName(status) + stockChange);
		if (customerHearsAbout(oldStatus, status)) {
			this.events.publishEvent(new ReservationStatusChanged(reservation.getReference(), status,
					this.stock.nameFor(reservation.getProductId(), reservation.getLanguage()),
					reservation.getQuantity(), reservation.getCustomerName(), reservation.getCustomerEmail(),
					reservation.getLanguage()));
		}
		return toDetail(reservation);
	}

	@Transactional
	ReservationDetailResponse changeNote(long id, String note, long version, Actor actor) {
		Reservation reservation = find(id);
		checkVersion(reservation, version);
		String newNote = StringUtils.hasText(note) ? note.strip() : null;
		if (Objects.equals(newNote, reservation.getAdminNote())) {
			return toDetail(reservation);
		}
		reservation.changeAdminNote(newNote, this.clock.instant());
		reservation = this.reservations.saveAndFlush(reservation);
		// Only that it changed: the text may hold personal details
		record(AuditEventType.RESERVATION_NOTE_CHANGED, reservation, actor, reservation.getReference());
		return toDetail(reservation);
	}

	/** @return what to add to the audit entry */
	private String moveStock(Reservation reservation, ReservationStatus oldStatus, ReservationStatus status) {
		if (status == ReservationStatus.CANCELLED) {
			this.stock.giveBack(reservation.getProductId(), reservation.getQuantity());
			return ", atgriezti " + reservation.getQuantity() + " gab.";
		}
		if (oldStatus == ReservationStatus.CANCELLED) {
			this.stock.takeAgain(reservation.getProductId(), reservation.getQuantity());
			return ", atkal paņemti " + reservation.getQuantity() + " gab.";
		}
		return "";
	}

	/** Changes from completed correct a mistake or record a return, which the shop handles in person. */
	private static boolean customerHearsAbout(ReservationStatus oldStatus, ReservationStatus status) {
		return switch (status) {
			case CONFIRMED -> oldStatus == ReservationStatus.PENDING || oldStatus == ReservationStatus.CANCELLED;
			case CANCELLED -> oldStatus == ReservationStatus.PENDING || oldStatus == ReservationStatus.CONFIRMED;
			case PENDING, COMPLETED -> false;
		};
	}

	private Reservation find(long id) {
		return this.reservations.findById(id)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RESERVATION_NOT_FOUND"));
	}

	private static void checkVersion(Reservation reservation, long version) {
		if (version != reservation.getVersion()) {
			throw new ApiException(HttpStatus.CONFLICT, "CHANGED_BY_ANOTHER");
		}
	}

	private void record(AuditEventType type, Reservation reservation, Actor actor, String details) {
		this.audit.recordOnTarget(type, actor.adminId(), TARGET_TYPE, reservation.getId().toString(),
				actor.ipAddress(), details);
	}

	/** The audit log is read in the Latvian admin panel. */
	private static String statusName(ReservationStatus status) {
		return switch (status) {
			case PENDING -> "gaida";
			case CONFIRMED -> "apstiprināta";
			case CANCELLED -> "atcelta";
			case COMPLETED -> "pabeigta";
		};
	}

	private static ReservationSummaryResponse toSummary(Reservation reservation) {
		return new ReservationSummaryResponse(reservation.getId(), reservation.getReference(),
				reservation.getProductName(), reservation.getQuantity(), reservation.getTotal(),
				reservation.getCustomerName(), reservation.getCustomerPhone(), reservation.getStatus(),
				reservation.getCreatedAt());
	}

	private static ReservationDetailResponse toDetail(Reservation reservation) {
		return new ReservationDetailResponse(reservation.getId(), reservation.getReference(),
				reservation.getProductId(), reservation.getProductName(), reservation.getPartNumber(),
				reservation.getUnitPrice(), reservation.getQuantity(), reservation.getTotal(),
				reservation.getCustomerName(), reservation.getCustomerPhone(), reservation.getCustomerEmail(),
				reservation.getComment(), reservation.getAdminNote(), reservation.getLanguage(),
				reservation.getStatus(), reservation.getCreatedAt(), reservation.getUpdatedAt(),
				reservation.getVersion());
	}

	record Actor(long adminId, String ipAddress) {
	}

}
