package duyanh.film.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import duyanh.film.model.Report;
import duyanh.film.model.ReportStatus;
import duyanh.film.model.ReportTargetType;

public interface ReportRepository extends JpaRepository<Report, Long> {

	boolean existsByReporterIdAndTargetTypeAndTargetId(Long reporterId, ReportTargetType targetType, Long targetId);

	long countByStatus(ReportStatus status);

	@Query("""
			select r from Report r
			where (:status is null or r.status = :status)
			  and (:targetType is null or r.targetType = :targetType)
			""")
	Page<Report> search(@Param("status") ReportStatus status, @Param("targetType") ReportTargetType targetType,
			Pageable pageable);
}
