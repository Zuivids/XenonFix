package lv.xenonfix.product;

import lv.xenonfix.product.dto.StoreProductPageResponse;
import lv.xenonfix.product.dto.StoreProductResponse;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/store/products")
class ProductPublicController {

	private final ProductPublicService service;

	ProductPublicController(ProductPublicService service) {
		this.service = service;
	}

	@GetMapping
	StoreProductPageResponse list(@RequestParam(defaultValue = "lv") ProductLanguage lang,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "24") int size) {
		return this.service.list(lang, page, size);
	}

	@GetMapping("/{slug}")
	StoreProductResponse get(@PathVariable String slug, @RequestParam(defaultValue = "lv") ProductLanguage lang) {
		return this.service.get(slug, lang);
	}

}
