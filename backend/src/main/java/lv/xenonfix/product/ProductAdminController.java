package lv.xenonfix.product;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import lv.xenonfix.auth.AuthenticatedAdmin;
import lv.xenonfix.product.ProductAdminService.Actor;
import lv.xenonfix.product.dto.ImageOrderRequest;
import lv.xenonfix.product.dto.ProductPageResponse;
import lv.xenonfix.product.dto.ProductRequest;
import lv.xenonfix.product.dto.ProductResponse;
import lv.xenonfix.product.dto.ProductStatusRequest;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/products")
class ProductAdminController {

	private final ProductAdminService service;

	ProductAdminController(ProductAdminService service) {
		this.service = service;
	}

	@GetMapping
	ProductPageResponse list(@RequestParam(required = false) ProductStatus status,
			@RequestParam(required = false) String search, @RequestParam(defaultValue = "0") int page) {
		return this.service.list(status, search, page);
	}

	@GetMapping("/{id}")
	ProductResponse get(@PathVariable long id) {
		return this.service.get(id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	ProductResponse create(@Valid @RequestBody ProductRequest body, @AuthenticationPrincipal AuthenticatedAdmin admin,
			HttpServletRequest request) {
		return this.service.create(body, actor(admin, request));
	}

	@PutMapping("/{id}")
	ProductResponse update(@PathVariable long id, @Valid @RequestBody ProductRequest body,
			@AuthenticationPrincipal AuthenticatedAdmin admin, HttpServletRequest request) {
		return this.service.update(id, body, actor(admin, request));
	}

	@PostMapping("/{id}/status")
	ProductResponse changeStatus(@PathVariable long id, @Valid @RequestBody ProductStatusRequest body,
			@AuthenticationPrincipal AuthenticatedAdmin admin, HttpServletRequest request) {
		return this.service.changeStatus(id, body.status(), actor(admin, request));
	}

	@PostMapping("/{id}/images")
	@ResponseStatus(HttpStatus.CREATED)
	ProductResponse addImage(@PathVariable long id, @RequestParam MultipartFile file,
			@AuthenticationPrincipal AuthenticatedAdmin admin, HttpServletRequest request) {
		return this.service.addImage(id, file, actor(admin, request));
	}

	@PutMapping("/{id}/images/order")
	ProductResponse orderImages(@PathVariable long id, @Valid @RequestBody ImageOrderRequest body,
			@AuthenticationPrincipal AuthenticatedAdmin admin, HttpServletRequest request) {
		return this.service.orderImages(id, body.imageIds(), actor(admin, request));
	}

	@DeleteMapping("/{id}/images/{imageId}")
	ProductResponse removeImage(@PathVariable long id, @PathVariable long imageId,
			@AuthenticationPrincipal AuthenticatedAdmin admin, HttpServletRequest request) {
		return this.service.removeImage(id, imageId, actor(admin, request));
	}

	private static Actor actor(AuthenticatedAdmin admin, HttpServletRequest request) {
		return new Actor(admin.id(), request.getRemoteAddr());
	}

}
