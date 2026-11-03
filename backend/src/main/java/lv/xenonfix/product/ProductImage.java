package lv.xenonfix.product;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "product_image")
public class ProductImage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "product_id")
	private Product product;

	@Column(nullable = false)
	private String path;

	@Column(name = "thumbnail_path", nullable = false)
	private String thumbnailPath;

	@Column(nullable = false)
	private int width;

	@Column(nullable = false)
	private int height;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected ProductImage() {
	}

	ProductImage(Product product, String path, String thumbnailPath, int width, int height, int sortOrder,
			Instant now) {
		this.product = product;
		this.path = path;
		this.thumbnailPath = thumbnailPath;
		this.width = width;
		this.height = height;
		this.sortOrder = sortOrder;
		this.createdAt = now;
	}

	void moveTo(int sortOrder) {
		this.sortOrder = sortOrder;
	}

	public Long getId() {
		return this.id;
	}

	public String getPath() {
		return this.path;
	}

	public String getThumbnailPath() {
		return this.thumbnailPath;
	}

	public int getWidth() {
		return this.width;
	}

	public int getHeight() {
		return this.height;
	}

	public int getSortOrder() {
		return this.sortOrder;
	}

}
