package lv.xenonfix.reservation;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface ReservationRepository extends JpaRepository<Reservation, Long> {

	/**
	 * @param status only this status, or all of them
	 * @param search lowercase text with % wildcards, matched against the customer and product
	 * @param id also finds this reservation, for a search by its number
	 */
	@Query("""
			select r from Reservation r
			where (:status is null or r.status = :status)
				and (:search is null or lower(r.customerName) like :search or lower(r.customerPhone) like :search
					or lower(r.customerEmail) like :search or lower(r.productName) like :search or r.id = :id)
			order by r.createdAt desc, r.id desc""")
	Page<Reservation> search(ReservationStatus status, String search, Long id, Pageable pageable);

	/**
	 * Anonymizes finished reservations not changed since {@code before}, in one statement. The
	 * version goes up, so an admin's page opened earlier cannot save over it.
	 * @return how many were anonymized
	 */
	@Modifying(clearAutomatically = true)
	@Query("""
			update Reservation r
			set r.customerName = null, r.customerPhone = null, r.customerEmail = null, r.comment = null,
				r.adminNote = null, r.anonymizedAt = :now, r.updatedAt = :now, r.version = r.version + 1
			where r.anonymizedAt is null and r.updatedAt < :before
				and r.status in (lv.xenonfix.reservation.ReservationStatus.CANCELLED,
					lv.xenonfix.reservation.ReservationStatus.COMPLETED)""")
	int anonymizeFinishedBefore(Instant before, Instant now);

}
