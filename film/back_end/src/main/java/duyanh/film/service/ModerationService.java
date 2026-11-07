package duyanh.film.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import duyanh.film.dto.AdminDto.HandleReportRequest;
import duyanh.film.dto.AdminDto.ReportResponse;
import duyanh.film.dto.response.PageResponse;
import duyanh.film.exception.ApiException;
import duyanh.film.mapper.UserMapper;
import duyanh.film.model.Comment;
import duyanh.film.model.CommentStatus;
import duyanh.film.model.Report;
import duyanh.film.model.ReportStatus;
import duyanh.film.model.ReportTargetType;
import duyanh.film.model.Review;
import duyanh.film.model.ReviewStatus;
import duyanh.film.model.User;
import duyanh.film.repository.CommentRepository;
import duyanh.film.repository.MovieRepository;
import duyanh.film.repository.ReportRepository;
import duyanh.film.repository.ReviewRepository;
import duyanh.film.repository.UserRepository;
import duyanh.film.security.SecurityUtils;
import lombok.RequiredArgsConstructor;

/** Kiểm duyệt: xử lý báo cáo, ẩn/hiện review và bình luận. Dành cho moderator và admin. */
@Service
@RequiredArgsConstructor
public class ModerationService {

	private final ReportRepository reportRepository;
	private final ReviewRepository reviewRepository;
	private final CommentRepository commentRepository;
	private final MovieRepository movieRepository;
	private final UserRepository userRepository;
	private final UserMapper userMapper;
	private final AuditService auditService;
	private final CacheService cacheService;

	@Transactional(readOnly = true)
	public PageResponse<ReportResponse> reports(ReportStatus status, ReportTargetType type, Pageable pageable) {
		Page<Report> page = reportRepository.search(status, type, pageable);
		List<Long> reporterIds = page.getContent().stream().map(Report::getReporterId).distinct().toList();
		Map<Long, User> reporters = userRepository.findAllById(reporterIds).stream()
				.collect(Collectors.toMap(User::getId, Function.identity()));
		return PageResponse.of(page, r -> toResponse(r, reporters.get(r.getReporterId())));
	}

	@Transactional
	public ReportResponse handle(Long id, HandleReportRequest req) {
		if (req.status() == ReportStatus.PENDING) {
			throw ApiException.badRequest("INVALID_STATUS", "Chỉ chọn RESOLVED hoặc REJECTED");
		}
		Report report = reportRepository.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy báo cáo"));
		ReportStatus before = report.getStatus();
		report.setStatus(req.status());
		report.setHandledBy(SecurityUtils.requireUserId());
		report.setHandledAt(LocalDateTime.now());
		if (req.status() == ReportStatus.RESOLVED && req.hideTarget()) {
			if (report.getTargetType() == ReportTargetType.REVIEW) {
				applyReviewStatus(report.getTargetId(), ReviewStatus.HIDDEN);
			} else {
				applyCommentStatus(report.getTargetId(), CommentStatus.HIDDEN);
			}
		}
		auditService.record("REPORT_HANDLE", "REPORT", id, Map.of("status", before),
				Map.of("status", req.status(), "hideTarget", req.hideTarget()));
		return toResponse(report, userRepository.findById(report.getReporterId()).orElse(null));
	}

	@Transactional
	public void setReviewStatus(Long id, String status) {
		ReviewStatus target = parse(status, ReviewStatus.class);
		ReviewStatus before = applyReviewStatus(id, target);
		auditService.record(target == ReviewStatus.HIDDEN ? "REVIEW_HIDE" : "REVIEW_SHOW", "REVIEW", id,
				Map.of("status", before), Map.of("status", target));
	}

	@Transactional
	public void setCommentStatus(Long id, String status) {
		CommentStatus target = parse(status, CommentStatus.class);
		if (target == CommentStatus.DELETED) {
			throw ApiException.badRequest("INVALID_STATUS", "Chỉ chọn VISIBLE hoặc HIDDEN");
		}
		CommentStatus before = applyCommentStatus(id, target);
		auditService.record(target == CommentStatus.HIDDEN ? "COMMENT_HIDE" : "COMMENT_SHOW", "COMMENT", id,
				Map.of("status", before), Map.of("status", target));
	}

	// ===== helpers =====

	private ReviewStatus applyReviewStatus(Long id, ReviewStatus status) {
		Review review = reviewRepository.findDetail(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy review"));
		ReviewStatus before = review.getStatus();
		review.setStatus(status);
		reviewRepository.flush();
		movieRepository.refreshReviewCount(review.getMovie().getId());
		cacheService.evictMovieAfterCommit(review.getMovie().getSlug());
		return before;
	}

	private CommentStatus applyCommentStatus(Long id, CommentStatus status) {
		Comment comment = commentRepository.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy bình luận"));
		CommentStatus before = comment.getStatus();
		if (before == CommentStatus.DELETED) {
			throw ApiException.badRequest("COMMENT_DELETED", "Bình luận đã bị người viết xóa");
		}
		comment.setStatus(status);
		commentRepository.flush();
		if (comment.getReviewId() != null) {
			reviewRepository.refreshCommentCount(comment.getReviewId());
		}
		return before;
	}

	private ReportResponse toResponse(Report r, User reporter) {
		String preview = null;
		String targetStatus = null;
		if (r.getTargetType() == ReportTargetType.REVIEW) {
			Review review = reviewRepository.findById(r.getTargetId()).orElse(null);
			if (review != null) {
				preview = review.getTitle();
				targetStatus = review.getStatus().name();
			}
		} else {
			Comment comment = commentRepository.findById(r.getTargetId()).orElse(null);
			if (comment != null) {
				preview = comment.getContent().length() > 200 ? comment.getContent().substring(0, 200) + "..."
						: comment.getContent();
				targetStatus = comment.getStatus().name();
			}
		}
		return new ReportResponse(r.getId(), reporter == null ? null : userMapper.toRef(reporter), r.getTargetType(),
				r.getTargetId(), preview, targetStatus == null ? "DELETED" : targetStatus, r.getReason(), r.getNote(),
				r.getStatus(), r.getHandledBy(), r.getHandledAt(), r.getCreatedAt());
	}

	private static <E extends Enum<E>> E parse(String value, Class<E> type) {
		try {
			return Enum.valueOf(type, value.trim().toUpperCase());
		} catch (IllegalArgumentException e) {
			throw ApiException.badRequest("INVALID_STATUS", "Trạng thái không hợp lệ: " + value);
		}
	}
}
