package duyanh.film.service;

import java.time.Duration;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import duyanh.film.dto.ReviewDto.ReportRequest;
import duyanh.film.exception.ApiException;
import duyanh.film.model.CommentStatus;
import duyanh.film.model.Report;
import duyanh.film.model.ReportTargetType;
import duyanh.film.repository.CommentRepository;
import duyanh.film.repository.ReportRepository;
import duyanh.film.util.RateLimiter;
import lombok.RequiredArgsConstructor;

/** Người dùng báo cáo review / bình luận vi phạm. Mỗi người báo cáo một nội dung một lần. */
@Service
@RequiredArgsConstructor
public class ReportService {

	private final ReportRepository reportRepository;
	private final CommentRepository commentRepository;
	private final ReviewService reviewService;
	private final UserService userService;
	private final RateLimiter rateLimiter;

	@Transactional
	public void report(ReportTargetType type, Long targetId, Long reporterId, ReportRequest req) {
		userService.getActiveUser(reporterId);
		rateLimiter.check("report:" + reporterId, 10, Duration.ofMinutes(10));
		if (type == ReportTargetType.REVIEW) {
			reviewService.getVisible(targetId);
		} else {
			commentRepository.findById(targetId).filter(c -> c.getStatus() == CommentStatus.VISIBLE)
					.orElseThrow(() -> ApiException.notFound("Không tìm thấy bình luận"));
		}
		if (reportRepository.existsByReporterIdAndTargetTypeAndTargetId(reporterId, type, targetId)) {
			throw ApiException.conflict("ALREADY_REPORTED", "Bạn đã báo cáo nội dung này");
		}
		Report report = new Report();
		report.setReporterId(reporterId);
		report.setTargetType(type);
		report.setTargetId(targetId);
		report.setReason(req.reason());
		report.setNote(req.note());
		reportRepository.save(report);
	}
}
