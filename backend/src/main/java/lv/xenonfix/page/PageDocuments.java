package lv.xenonfix.page;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import lv.xenonfix.common.ApiException;
import lv.xenonfix.page.content.Image;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Reads, checks and writes the pages' JSON. Strict on purpose: a field the page's record does
 * not know, or a value of the wrong type, is refused, so the stored JSON always has the shape
 * the site expects.
 */
@Component
class PageDocuments {

	private static final Set<String> LANGUAGES = Set.of("lv", "ru", "en");

	private final JsonMapper mapper = JsonMapper.builder()
		.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
		.enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
		.build();

	private final Validator validator;

	PageDocuments(Validator validator) {
		this.validator = validator;
	}

	/**
	 * Checks content sent by the panel against the page's shape and rules.
	 * @return the same content as stored JSON
	 */
	String checked(PageKey key, Map<String, Object> content) {
		Record parsed;
		try {
			parsed = this.mapper.convertValue(content, key.contentType());
		}
		catch (JacksonException | IllegalArgumentException ex) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE_CONTENT");
		}
		Set<ConstraintViolation<Record>> violations = this.validator.validate(parsed);
		if (!violations.isEmpty()) {
			ApiException error = new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE_CONTENT");
			// Where the first problem is, such as tiles[2].title.lv, so the panel can point at it
			error.getBody()
				.setProperty("field", violations.stream().map((v) -> v.getPropertyPath().toString()).sorted().findFirst()
					.orElse(""));
			throw error;
		}
		return this.mapper.writeValueAsString(parsed);
	}

	/** The content as the panel edits it, with all three languages. */
	@SuppressWarnings("unchecked")
	Map<String, Object> asMap(String json) {
		return this.mapper.readValue(json, LinkedHashMap.class);
	}

	<T> T read(String json, Class<T> type) {
		return this.mapper.readValue(json, type);
	}

	/** Whether stored JSON still fits its page's shape, for example after a change to the code. */
	boolean fits(PageKey key, String json) {
		try {
			return this.validator.validate(this.mapper.readValue(json, key.contentType())).isEmpty();
		}
		catch (JacksonException ex) {
			return false;
		}
	}

	/** The content in one language: each text becomes that language's string, or the Latvian one. */
	Object inLanguage(String json, String language) {
		return resolve(asMap(json), language);
	}

	/** The uploaded photos a page uses, as paths under the uploads folder. */
	Set<String> uploadPaths(String json) {
		Set<String> paths = new LinkedHashSet<>();
		collectUploads(asMap(json), paths);
		return paths;
	}

	@SuppressWarnings("unchecked")
	private static Object resolve(Object value, String language) {
		if (value instanceof Map<?, ?> map) {
			if (map.keySet().equals(LANGUAGES)) {
				Object text = map.get(language);
				return (text instanceof String s && !s.isBlank()) ? s : map.get("lv");
			}
			Map<String, Object> resolved = new LinkedHashMap<>();
			((Map<String, Object>) map).forEach((name, item) -> resolved.put(name, resolve(item, language)));
			return resolved;
		}
		if (value instanceof List<?> list) {
			List<Object> resolved = new ArrayList<>(list.size());
			list.forEach((item) -> resolved.add(resolve(item, language)));
			return resolved;
		}
		return value;
	}

	private static void collectUploads(Object value, Set<String> paths) {
		if (value instanceof Map<?, ?> map) {
			if (map.get("src") instanceof String src && src.startsWith(Image.UPLOADS_PREFIX)) {
				paths.add(src.substring("/uploads/".length()));
			}
			map.values().forEach((item) -> collectUploads(item, paths));
		}
		else if (value instanceof List<?> list) {
			list.forEach((item) -> collectUploads(item, paths));
		}
	}

}
