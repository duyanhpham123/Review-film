package duyanh.film.repository;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import duyanh.film.model.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

	@Query("""
			select a from AuditLog a
			where (:actorId is null or a.actorId = :actorId)
			  and (:action is null or a.action = :action)
			  and (:from is null or a.createdAt >= :from)
			  and (:to is null or a.createdAt < :to)
			""")
	Page<AuditLog> search(@Param("actorId") Long actorId, @Param("action") String action,
			@Param("from") LocalDateTime from, @Param("to") LocalDateTime to, Pageable pageable);
}
