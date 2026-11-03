package lv.xenonfix.product;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "product")
public class Product {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 200)
	private String slug;

	@Column(name = "part_number", length = 200)
	private String partNumber;

	@Column(nullable = false, precision = 10, scale = 2)
	private BigDecimal price;

	@Column(name = "sale_price", precision = 10, scale = 2)
	private BigDecimal salePrice;

	@Column(name = "stock_quantity", nullable = false)
	private int stockQuantity;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ProductStatus status;

	@ElementCollection
	@CollectionTable(name = "product_translation", joinColumns = @JoinColumn(name = "product_id"))
	@MapKeyEnumerated(EnumType.STRING)
	@MapKeyColumn(name = "language", length = 2)
	private Map<ProductLanguage, ProductTexts> translations = new HashMap<>();

	@OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("sortOrder")
	private List<ProductImage> images = new ArrayList<>();

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	private long version;

	protected Product() {
	}

	/** A new product starts as a draft, because publishing needs a photo. */
	Product(String slug, Instant now) {
		this.slug = slug;
		this.status = ProductStatus.DRAFT;
		this.createdAt = now;
		this.updatedAt = now;
	}

	void update(String slug, String partNumber, BigDecimal price, BigDecimal salePrice, int stockQuantity,
			Map<ProductLanguage, ProductTexts> translations, Instant now) {
		this.slug = slug;
		this.partNumber = partNumber;
		this.price = price;
		this.salePrice = salePrice;
		this.stockQuantity = stockQuantity;
		this.translations.keySet().retainAll(translations.keySet());
		this.translations.putAll(translations);
		this.updatedAt = now;
	}

	void changeStatus(ProductStatus status, Instant now) {
		this.status = status;
		this.updatedAt = now;
	}

	ProductImage addImage(String path, String thumbnailPath, int width, int height, Instant now) {
		int nextOrder = this.images.stream().mapToInt(ProductImage::getSortOrder).max().orElse(-1) + 1;
		ProductImage image = new ProductImage(this, path, thumbnailPath, width, height, nextOrder, now);
		this.images.add(image);
		this.updatedAt = now;
		return image;
	}

	Optional<ProductImage> findImage(long imageId) {
		return this.images.stream().filter((image) -> image.getId() == imageId).findFirst();
	}

	void removeImage(ProductImage image, Instant now) {
		this.images.remove(image);
		this.updatedAt = now;
	}

	/** Puts the photos in the given order. The ids must be exactly this product's photos. */
	void orderImages(List<Long> imageIds, Instant now) {
		for (int position = 0; position < imageIds.size(); position++) {
			findImage(imageIds.get(position)).orElseThrow().moveTo(position);
		}
		this.images.sort(Comparator.comparingInt(ProductImage::getSortOrder));
		this.updatedAt = now;
	}

	public Long getId() {
		return this.id;
	}

	public String getSlug() {
		return this.slug;
	}

	public String getPartNumber() {
		return this.partNumber;
	}

	public BigDecimal getPrice() {
		return this.price;
	}

	public BigDecimal getSalePrice() {
		return this.salePrice;
	}

	public int getStockQuantity() {
		return this.stockQuantity;
	}

	public ProductStatus getStatus() {
		return this.status;
	}

	public Map<ProductLanguage, ProductTexts> getTranslations() {
		return Map.copyOf(this.translations);
	}

	public List<ProductImage> getImages() {
		return List.copyOf(this.images);
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

	public Instant getUpdatedAt() {
		return this.updatedAt;
	}

	public long getVersion() {
		return this.version;
	}

}
