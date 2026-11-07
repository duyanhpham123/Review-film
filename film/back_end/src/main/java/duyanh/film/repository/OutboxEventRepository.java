package duyanh.film.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import duyanh.film.model.OutboxEvent;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

	/** Phải gọi trong transaction. Dòng đang bị instance khác khóa thì bỏ qua thay vì chờ. */
	@Query(value = """
			SELECT * FROM outbox_events
			WHERE sent_at IS NULL AND attempts < :maxAttempts
			ORDER BY id LIMIT :limit
			FOR UPDATE SKIP LOCKED
			""", nativeQuery = true)
	List<OutboxEvent> lockPending(@Param("maxAttempts") int maxAttempts, @Param("limit") int limit);
}
