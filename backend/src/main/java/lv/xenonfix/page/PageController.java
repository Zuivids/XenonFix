package lv.xenonfix.page;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import lv.xenonfix.auth.AuthenticatedAdmin;
import lv.xenonfix.common.ApiException;
import lv.xenonfix.page.content.Image;
import lv.xenonfix.page.dto.PageResponse;
import lv.xenonfix.page.dto.PageSummaryResponse;
import lv.xenonfix.page.dto.PageUpdateRequest;
import lv.xenonfix.product.ProductLanguage;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
class PageController {

	private final PageService service;

	PageController(PageService service) {
		this.service = service;
	}

	/**
	 * A page in one language, as the site shows it. The answer is the page's own content, whose
	 * shape the records in lv.xenonfix.page.content define.
	 */
	@GetMapping("/api/pages/{page}")
	Object publicPage(@PathVariable String page, @RequestParam(defaultValue = "lv") ProductLanguage lang) {
		return this.service.publicContent(key(page), lang.name());
	}

	@GetMapping("/api/admin/pages")
	List<PageSummaryResponse> list() {
		return this.service.list();
	}

	@GetMapping("/api/admin/pages/{page}")
	PageResponse get(@PathVariable String page) {
		return this.service.get(key(page));
	}

	@PutMapping("/api/admin/pages/{page}")
	PageResponse save(@PathVariable String page, @Valid @RequestBody PageUpdateRequest body,
			@AuthenticationPrincipal AuthenticatedAdmin admin, HttpServletRequest request) {
		return this.service.save(key(page), body.content(), body.version(), admin.id(), request.getRemoteAddr());
	}

	@PostMapping("/api/admin/pages/images")
	@ResponseStatus(HttpStatus.CREATED)
	Image upload(@RequestParam MultipartFile file) {
		return this.service.upload(file);
	}

	private static PageKey key(String page) {
		return PageKey.fromPath(page).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PAGE_NOT_FOUND"));
	}

}
