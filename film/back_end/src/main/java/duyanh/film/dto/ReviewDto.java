package duyanh.film.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import duyanh.film.dto.CommonDto.MovieRef;
import duyanh.film.dto.CommonDto.UserRef;
import duyanh.film.dto.MovieDto.MovieSummary;
import duyanh.film.model.CommentStatus;
import duyanh.film.model.NotificationType;
import duyanh.film.model.ReportReason;
import duyanh.film.model.ReviewStatus;
import duyanh.film.model.WatchStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Review, bình luận, báo cáo, watchlist, thông báo. */
public final class ReviewDto {

	private ReviewDto() {
	}

	// ===== Review =====

	public record ReviewRequest(
			@NotBlank @Size(max = 255) String title,
			@NotBlank @Size(min = 20, max = 50000, message = "Nội dung review từ 20 đến 50000 ký tự") String content,
			boolean hasSpoiler,
			@Min(1) @Max(10) Integer score) {
	}

	public record ReviewResponse(Long id, MovieRef movie, UserRef author, Integer score, String title, String content,
			boolean hasSpoiler, ReviewStatus status, int likeCount, int commentCount, Boolean likedByMe,
			LocalDateTime createdAt, LocalDateTime updatedAt) {
	}

	// ===== Comment =====

	public record CommentRequest(
			@NotBlank @Size(max = 2000, message = "Bình luận tối đa 2000 ký tự") String content,
			Long parentId) {
	}

	public record UpdateCommentRequest(
			@NotBlank @Size(max = 2000, message = "Bình luận tối đa 2000 ký tự") String content) {
	}

	public record CommentResponse(Long id, UserRef author, String content, CommentStatus status, Long parentId,
			Long reviewId, boolean edited, LocalDateTime createdAt, List<CommentResponse> replies) {
	}

	// ===== Report =====

	public record ReportRequest(@NotNull ReportReason reason, @Size(max = 500) String note) {
	}

	// ===== Watchlist =====

	public record WatchlistRequest(@NotNull WatchStatus status, LocalDate watchedAt) {
	}

	public record WatchlistItemResponse(MovieSummary movie, WatchStatus status, LocalDate watchedAt,
			LocalDateTime updatedAt) {
	}

	// ===== Notification =====

	public record NotificationResponse(Long id, NotificationType type, Long actorId, Long movieId, String movieSlug,
			Long reviewId, Long commentId, String message, boolean read, LocalDateTime createdAt) {
	}
}
