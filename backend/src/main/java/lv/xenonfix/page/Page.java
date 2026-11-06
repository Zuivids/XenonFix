package lv.xenonfix.page;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** One editable page: its whole content as JSON, in all three languages. */
@Entity
@Table(name = "page")
class Page {

	@Id
	@Enumerated(EnumType.STRING)
	@Column(name = "page_key", length = 20)
	private PageKey key;

	@Lob
	@Column(nullable = false, columnDefinition = "longtext")
	private String content;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "updated_by")
	private Long updatedBy;

	@Version
	private long version;

	protected Page() {
	}

	/** The starting content, which nobody edited yet. */
	Page(PageKey key, String content, Instant now) {
		this.key = key;
		this.content = content;
		this.updatedAt = now;
	}

	void change(String content, long adminId, Instant now) {
		this.content = content;
		this.updatedBy = adminId;
		this.updatedAt = now;
	}

	PageKey getKey() {
		return this.key;
	}

	String getContent() {
		return this.content;
	}

	Instant getUpdatedAt() {
		return this.updatedAt;
	}

	Long getUpdatedBy() {
		return this.updatedBy;
	}

	long getVersion() {
		return this.version;
	}

}
