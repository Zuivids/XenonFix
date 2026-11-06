package lv.xenonfix.admin;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A one-time backup code for logging in without the phone, stored as a hash only. */
@Entity
@Table(name = "admin_recovery_code")
public class AdminRecoveryCode {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "admin_id", nullable = false)
	private long adminId;

	@Column(name = "code_hash", nullable = false, length = 64)
	private String codeHash;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "used_at")
	private Instant usedAt;

	protected AdminRecoveryCode() {
	}

	public AdminRecoveryCode(long adminId, String codeHash, Instant now) {
		this.adminId = adminId;
		this.codeHash = codeHash;
		this.createdAt = now;
	}

	public void use(Instant now) {
		this.usedAt = now;
	}

}
