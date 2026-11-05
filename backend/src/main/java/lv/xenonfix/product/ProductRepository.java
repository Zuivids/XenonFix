package lv.xenonfix.product;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface ProductRepository extends JpaRepository<Product, Long> {

	boolean existsBySlug(String slug);

	boolean existsBySlugAndIdNot(String slug, long id);

	/**
	 * @param status only this status; without one, everything except archived products
	 * @param search lowercase text with % wildcards, matched against name, part number and slug
	 */
	@Query("""
			select distinct p from Product p left join p.translations t
			where ((:status is null and p.status <> lv.xenonfix.product.ProductStatus.ARCHIVED) or p.status = :status)
				and (:search is null or lower(t.name) like :search or lower(p.partNumber) like :search
					or lower(p.slug) like :search)
			order by p.updatedAt desc, p.id desc""")
	Page<Product> search(ProductStatus status, String search, Pageable pageable);

	Page<Product> findByStatusOrderByCreatedAtDescIdDesc(ProductStatus status, Pageable pageable);

	Optional<Product> findBySlugAndStatus(String slug, ProductStatus status);

	boolean existsBySlugAndStatus(String slug, ProductStatus status);

	/**
	 * Takes stock in one statement, so the database decides who gets the last one when several
	 * people reserve at once. The version goes up because an admin's product form saves an
	 * absolute stock number: a form opened before this update must be refused, or it would put
	 * the old stock back.
	 * @return the number of products changed: 0 means not for sale or not enough in stock
	 */
	@Modifying(clearAutomatically = true)
	@Query("""
			update Product p
			set p.stockQuantity = p.stockQuantity - :quantity, p.version = p.version + 1, p.updatedAt = :now
			where p.slug = :slug and p.status = lv.xenonfix.product.ProductStatus.PUBLISHED
				and p.stockQuantity >= :quantity""")
	int takeStock(String slug, int quantity, Instant now);

}
