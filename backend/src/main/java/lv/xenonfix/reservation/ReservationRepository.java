package lv.xenonfix.reservation;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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

}
