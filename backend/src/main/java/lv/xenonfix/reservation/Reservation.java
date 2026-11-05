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

	@Column(name = "customer_name", nullable = false, length = 100)
	private String customerName;

	@Column(name = "customer_phone", nullable = false, length = 30)
	private String customerPhone;

	@Column(name = "customer_email", nullable = false, length = 254)
	private String customerEmail;

	@Column(length = 1000)
	private String comment;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 2)
	private ProductLanguage language;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ReservationStatus status;

	@Column(name = "privacy_accepted_at", nullable = false)
	private Instant privacyAcceptedAt;

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

}
