package lv.xenonfix.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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

}
