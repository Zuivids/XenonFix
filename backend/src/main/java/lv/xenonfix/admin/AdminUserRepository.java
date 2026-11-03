package lv.xenonfix.admin;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AdminUserRepository extends JpaRepository<AdminUser, Long> {

	Optional<AdminUser> findByEmail(String email);

	boolean existsByEmail(String email);

	List<AdminUser> findAllByOrderByEmailAsc();

	@Query("select new lv.xenonfix.admin.AdminSessionState(a.status, a.securityVersion) from AdminUser a where a.id = :id")
	Optional<AdminSessionState> findSessionState(long id);

}
