package lv.xenonfix.page;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import lv.xenonfix.admin.AdminUser;
import lv.xenonfix.admin.AdminUserRepository;
import lv.xenonfix.audit.AuditEventType;
import lv.xenonfix.audit.AuditService;
import lv.xenonfix.common.ApiException;
import lv.xenonfix.config.AppProperties;
import lv.xenonfix.page.content.Image;
import lv.xenonfix.page.content.ServicesContent;
import lv.xenonfix.page.content.SiteInfoContent;
import lv.xenonfix.page.dto.PageResponse;
import lv.xenonfix.page.dto.PageSummaryResponse;
import lv.xenonfix.product.ImageProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PageService implements ApplicationRunner {

	private static final Logger logger = LoggerFactory.getLogger(PageService.class);

	private static final String TARGET_TYPE = "PAGE";

	/** An uploaded photo not saved into a page by then is deleted. */
	private static final Duration UNSAVED_UPLOAD_AGE = Duration.ofDays(1);

	private final PageRepository pages;

	private final PageUploadRepository uploads;

	private final PageDocuments documents;

	private final ImageProcessor imageProcessor;

	private final AdminUserRepository admins;

	private final AuditService audit;

	private final Clock clock;

	private final Path uploadsRoot;

	PageService(PageRepository pages, PageUploadRepository uploads, PageDocuments documents,
			ImageProcessor imageProcessor, AdminUserRepository admins, AuditService audit, AppProperties properties,
			Clock clock) {
		this.pages = pages;
		this.uploads = uploads;
		this.documents = documents;
		this.imageProcessor = imageProcessor;
		this.admins = admins;
		this.audit = audit;
		this.clock = clock;
		this.uploadsRoot = properties.uploadsPath().toAbsolutePath().normalize();
	}

	/**
	 * Gives each page without a row its starting content from resources/pages. An edited page is
	 * never replaced, so this is safe on every start.
	 */
	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		for (PageKey key : PageKey.values()) {
			if (!this.pages.existsById(key)) {
				String json = startingContent(key);
				if (!this.documents.fits(key, json)) {
					throw new IllegalStateException("resources/pages/" + key.path() + ".json does not fit " + key);
				}
				this.pages.save(new Page(key, json, this.clock.instant()));
				logger.info("Page {} created from its starting content", key.path());
			}
		}
	}

	/** A page as the site shows it, in one language. */
	@Transactional(readOnly = true)
	public Object publicContent(PageKey key, String language) {
		Object content = this.documents.inLanguage(find(key).getContent(), language);
		if (key == PageKey.HOME && content instanceof Map<?, ?> map) {
			// The home page's "all services" tile shows how many the services page lists
			@SuppressWarnings("unchecked")
			Map<String, Object> home = new LinkedHashMap<>((Map<String, Object>) map);
			home.put("serviceCount", this.documents
				.read(find(PageKey.SERVICES).getContent(), ServicesContent.class)
				.services()
				.size());
			return home;
		}
		return content;
	}

	/** The shop's details, for the emails. */
	@Transactional(readOnly = true)
	public SiteInfoContent siteInfo() {
		return this.documents.read(find(PageKey.SITE_INFO).getContent(), SiteInfoContent.class);
	}

	@Transactional(readOnly = true)
	List<PageSummaryResponse> list() {
		return Arrays.stream(PageKey.values())
			.map(this::find)
			.map((page) -> new PageSummaryResponse(page.getKey().path(), page.getUpdatedAt(), editorName(page)))
			.toList();
	}

	@Transactional(readOnly = true)
	PageResponse get(PageKey key) {
		return toResponse(find(key));
	}

	/**
	 * Replaces the whole page. Photos it no longer uses are deleted, unless another page still
	 * uses them.
	 */
	@Transactional
	PageResponse save(PageKey key, Map<String, Object> content, long version, long adminId, String ipAddress) {
		Page page = find(key);
		if (page.getVersion() != version) {
			throw new ApiException(HttpStatus.CONFLICT, "CHANGED_BY_ANOTHER");
		}
		String json = this.documents.checked(key, content);
		Set<String> before = this.documents.uploadPaths(page.getContent());
		page.change(json, adminId, this.clock.instant());
		page = this.pages.saveAndFlush(page);
		before.removeAll(usedUploads());
		deleteUploads(before);
		this.audit.recordOnTarget(AuditEventType.PAGE_UPDATED, adminId, TARGET_TYPE, key.path(), ipAddress,
				pageName(key));
		return toResponse(page);
	}

	/** A photo for a page. It is kept only if a page is saved with it (see {@link #deleteUnsavedUploads}). */
	@Transactional
	Image upload(MultipartFile file) {
		ImageProcessor.Processed processed;
		try {
			processed = this.imageProcessor.process(file.getBytes());
		}
		catch (IOException ex) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IMAGE");
		}
		String path = "pages/" + UUID.randomUUID() + ".jpg";
		try {
			Files.createDirectories(this.uploadsRoot.resolve("pages"));
			Files.write(this.uploadsRoot.resolve(path), processed.full());
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Could not save a page photo", ex);
		}
		afterRollback(() -> deleteFile(path));
		this.uploads.save(new PageUpload(path, this.clock.instant()));
		return new Image("/uploads/" + path, processed.width(), processed.height());
	}

	/** Every night: photos uploaded but never saved into a page, for example a closed editor. */
	@Scheduled(cron = "0 45 3 * * *", zone = "Europe/Riga")
	@Transactional
	public void deleteUnsavedUploads() {
		Set<String> used = usedUploads();
		Set<String> unused = new HashSet<>();
		for (PageUpload upload : this.uploads.findByCreatedAtBefore(this.clock.instant().minus(UNSAVED_UPLOAD_AGE))) {
			if (!used.contains(upload.getPath())) {
				unused.add(upload.getPath());
			}
		}
		deleteUploads(unused);
	}

	private Set<String> usedUploads() {
		Set<String> used = new HashSet<>();
		this.pages.findAll().forEach((page) -> used.addAll(this.documents.uploadPaths(page.getContent())));
		return used;
	}

	/** The rows now, the files once the change is committed, so a rollback keeps both. */
	private void deleteUploads(Set<String> paths) {
		if (paths.isEmpty()) {
			return;
		}
		this.uploads.deleteAll(this.uploads.findByPathIn(paths));
		afterCommit(() -> paths.forEach(this::deleteFile));
	}

	private void deleteFile(String path) {
		try {
			Files.deleteIfExists(this.uploadsRoot.resolve(path));
		}
		catch (IOException ex) {
			logger.warn("Could not delete the upload {}", path, ex);
		}
	}

	private Page find(PageKey key) {
		return this.pages.findById(key).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PAGE_NOT_FOUND"));
	}

	private PageResponse toResponse(Page page) {
		return new PageResponse(page.getKey().path(), this.documents.asMap(page.getContent()), page.getVersion(),
				page.getUpdatedAt(), editorName(page));
	}

	private String editorName(Page page) {
		return (page.getUpdatedBy() == null) ? null
				: this.admins.findById(page.getUpdatedBy()).map(AdminUser::getDisplayName).orElse(null);
	}

	private static String startingContent(PageKey key) {
		try (InputStream in = new ClassPathResource("pages/" + key.path() + ".json").getInputStream()) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("No starting content for " + key, ex);
		}
	}

	/** The audit log is read in the Latvian admin panel. */
	static String pageName(PageKey key) {
		return switch (key) {
			case HOME -> "Sākumlapa";
			case ABOUT -> "Par mums";
			case SERVICES -> "Mūsu pakalpojumi";
			case WORKS -> "Mūsu darbi";
			case CONTACT -> "Kontakti";
			case SITE_INFO -> "Kontaktinformācija";
		};
	}

	private static void afterCommit(Runnable action) {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					action.run();
				}
			});
		}
		else {
			action.run();
		}
	}

	private static void afterRollback(Runnable action) {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCompletion(int status) {
					if (status == STATUS_ROLLED_BACK) {
						action.run();
					}
				}
			});
		}
	}

}
