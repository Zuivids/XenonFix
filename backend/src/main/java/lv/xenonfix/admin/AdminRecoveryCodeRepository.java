package lv.xenonfix.admin;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AdminRecoveryCodeRepository extends JpaRepository<AdminRecoveryCode, Long> {

	Optional<AdminRecoveryCode> findByAdminIdAndCodeHashAndUsedAtIsNull(long adminId, String codeHash);

	long countByAdminIdAndUsedAtIsNull(long adminId);

	@Modifying
	@Query("delete from AdminRecoveryCode c where c.adminId = :adminId")
	void deleteAllOf(long adminId);

}
