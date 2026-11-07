package duyanh.film.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import duyanh.film.dto.CommonDto.UserRef;
import duyanh.film.model.ReportReason;
import duyanh.film.model.ReportStatus;
import duyanh.film.model.ReportTargetType;
import duyanh.film.model.UserStatus;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public final class AdminDto {

	private AdminDto() {
	}

	// ===== User =====

	public record AdminUserResponse(Long id, String username, String email, String fullName, UserStatus status,
			List<String> roles, boolean emailVerified, LocalDateTime createdAt, LocalDateTime lastLoginAt) {
	}

	public record UserStatusRequest(@NotNull UserStatus status) {
	}

	/** Danh sách role mới, ví dụ ["ROLE_USER", "ROLE_MODERATOR"]. ROLE_USER luôn được giữ. */
	public record UserRolesRequest(@NotEmpty List<String> roles) {
	}

	// ===== Moderation =====

	public record ReportResponse(Long id, UserRef reporter, ReportTargetType targetType, Long targetId,
			String targetPreview, String targetStatus, ReportReason reason, String note, ReportStatus status,
			Long handledBy, LocalDateTime handledAt, LocalDateTime createdAt) {
	}

	public record HandleReportRequest(@NotNull ReportStatus status, boolean hideTarget) {
	}

	/** VISIBLE hoặc HIDDEN. */
	public record ContentStatusRequest(@NotNull String status) {
	}

	// ===== Audit =====

	public record AuditLogResponse(Long id, Long actorId, String actorUsername, String action, String targetType,
			Long targetId, String beforeData, String afterData, String ipAddress, LocalDateTime createdAt) {
	}

	// ===== Dashboard =====

	public record SummaryResponse(long totalMovies, long totalUsers, long totalReviews, long totalRatings,
			long pendingReports, long viewsToday, long newUsersToday, long newReviewsToday) {
	}

	public record DailyPoint(LocalDate date, long value) {
	}

	public record TopMovie(Long id, String slug, String title, String posterUrl, double value) {
	}
}
