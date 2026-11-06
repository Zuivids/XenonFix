package lv.xenonfix.reservation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import lv.xenonfix.product.ProductLanguage;

@Entity
@Table(name = "reservation")
class Reservation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "product_id", nullable = false)
	private long productId;

	@Column(name = "product_name", nullable = false, length = 200)
	private String productName;

	@Column(name = "part_number", length = 200)
	private String partNumber;

	@Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
	private BigDecimal unitPrice;

	@Column(nullable = false)
	private int quantity;

	// The customer's details are empty once anonymized
	@Column(name = "customer_name", length = 100)
	private String customerName;

	@Column(name = "customer_phone", length = 30)
	private String customerPhone;

	@Column(name = "customer_email", length = 254)
	private String customerEmail;

	@Column(length = 1000)
	private String comment;

	@Column(name = "admin_note", length = 2000)
	private String adminNote;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 2)
	private ProductLanguage language;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ReservationStatus status;

	@Column(name = "privacy_accepted_at", nullable = false)
	private Instant privacyAcceptedAt;

	@Column(name = "anonymized_at")
	private Instant anonymizedAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	private long version;

	protected Reservation() {
	}

	Reservation(long productId, String productName, String partNumber, BigDecimal unitPrice, int quantity,
			String customerName, String customerPhone, String customerEmail, String comment, ProductLanguage language,
			Instant now) {
		this.productId = productId;
		this.productName = productName;
		this.partNumber = partNumber;
		this.unitPrice = unitPrice;
		this.quantity = quantity;
		this.customerName = customerName;
		this.customerPhone = customerPhone;
		this.customerEmail = customerEmail;
		this.comment = comment;
		this.language = language;
		this.status = ReservationStatus.PENDING;
		this.privacyAcceptedAt = now;
		this.createdAt = now;
		this.updatedAt = now;
	}

	Long getId() {
		return this.id;
	}

	/** What the customer and the shop call this reservation. */
	String getReference() {
		return String.format(Locale.ROOT, "XF-%06d", this.id);
	}

	void changeStatus(ReservationStatus status, Instant now) {
		this.status = status;
		this.updatedAt = now;
	}

	void changeAdminNote(String adminNote, Instant now) {
		this.adminNote = adminNote;
		this.updatedAt = now;
	}

	/** Erases everything about the customer, including notes, which may name them. */
	void anonymize(Instant now) {
		this.customerName = null;
		this.customerPhone = null;
		this.customerEmail = null;
		this.comment = null;
		this.adminNote = null;
		this.anonymizedAt = now;
		this.updatedAt = now;
	}

	boolean isAnonymized() {
		return this.anonymizedAt != null;
	}

	Instant getAnonymizedAt() {
		return this.anonymizedAt;
	}

	BigDecimal getTotal() {
		return this.unitPrice.multiply(BigDecimal.valueOf(this.quantity));
	}

	long getProductId() {
		return this.productId;
	}

	String getProductName() {
		return this.productName;
	}

	String getPartNumber() {
		return this.partNumber;
	}

	BigDecimal getUnitPrice() {
		return this.unitPrice;
	}

	int getQuantity() {
		return this.quantity;
	}

	String getCustomerName() {
		return this.customerName;
	}

	String getCustomerPhone() {
		return this.customerPhone;
	}

	String getCustomerEmail() {
		return this.customerEmail;
	}

	String getComment() {
		return this.comment;
	}

	String getAdminNote() {
		return this.adminNote;
	}

	ProductLanguage getLanguage() {
		return this.language;
	}

	ReservationStatus getStatus() {
		return this.status;
	}

	Instant getCreatedAt() {
		return this.createdAt;
	}

	Instant getUpdatedAt() {
		return this.updatedAt;
	}

	long getVersion() {
		return this.version;
	}

}
