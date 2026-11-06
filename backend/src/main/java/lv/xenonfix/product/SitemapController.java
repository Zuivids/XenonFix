package lv.xenonfix.product;

import java.util.List;

import lv.xenonfix.product.dto.StoreSitemapEntry;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The published products' addresses. Apart from /api/store/products, where "sitemap" would
 * be taken for a product's address.
 */
@RestController
class SitemapController {

	private final ProductRepository products;

	SitemapController(ProductRepository products) {
		this.products = products;
	}

	@GetMapping("/api/store/sitemap")
	@Transactional(readOnly = true)
	List<StoreSitemapEntry> sitemap() {
		return this.products.findSitemapEntries();
	}

}
