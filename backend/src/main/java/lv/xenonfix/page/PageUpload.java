package lv.xenonfix.page;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A photo uploaded for a page, kept track of so one never saved into a page can be deleted. */
@Entity
@Table(name = "page_upload")
class PageUpload {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** Under the uploads folder, for example pages/1f0c….jpg */
	@Column(nullable = false, length = 200)
	private String path;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected PageUpload() {
	}

	PageUpload(String path, Instant now) {
		this.path = path;
		this.createdAt = now;
	}

	String getPath() {
		return this.path;
	}

}
