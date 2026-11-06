package lv.xenonfix.page;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface PageUploadRepository extends JpaRepository<PageUpload, Long> {

	List<PageUpload> findByPathIn(Collection<String> paths);

	List<PageUpload> findByCreatedAtBefore(Instant before);

}
