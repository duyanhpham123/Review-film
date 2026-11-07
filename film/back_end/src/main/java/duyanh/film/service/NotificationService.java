package duyanh.film.service;

import java.time.LocalDateTime;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import duyanh.film.dto.ReviewDto.NotificationResponse;
import duyanh.film.dto.response.PageResponse;
import duyanh.film.exception.ApiException;
import duyanh.film.model.Comment;
import duyanh.film.model.CommentStatus;
import duyanh.film.model.Movie;
import duyanh.film.model.Notification;
import duyanh.film.model.NotificationType;
import duyanh.film.model.Review;
import duyanh.film.model.User;
import duyanh.film.repository.CommentRepository;
import duyanh.film.repository.MovieRepository;
import duyanh.film.repository.NotificationRepository;
import duyanh.film.repository.ReviewRepository;
import duyanh.film.repository.UserRepository;
import lombok.RequiredArgsConstructor;

/**
 * Đọc thông báo của người dùng, và tạo thông báo từ event trong outbox.
 * Tạo bằng INSERT IGNORE + dedupe_key nên xử lý lại một event không sinh thông báo trùng.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

	private final NotificationRepository notificationRepository;
	private final CommentRepository commentRepository;
	private final ReviewRepository reviewRepository;
	private final MovieRepository movieRepository;
	private final UserRepository userRepository;

	// ===== API của người dùng =====

	@Transactional(readOnly = true)
	public PageResponse<NotificationResponse> list(Long userId, Pageable pageable) {
		var page = notificationRepository.findByUserId(userId, pageable);
		// Kèm slug phim để frontend tạo link trang phim
		var movieIds = page.getContent().stream().map(Notification::getMovieId).filter(java.util.Objects::nonNull)
				.distinct().toList();
		java.util.Map<Long, String> slugs = new java.util.HashMap<>();
		movieRepository.findAllById(movieIds).forEach(m -> slugs.put(m.getId(), m.getSlug()));
		return PageResponse.of(page, n -> toResponse(n, slugs.get(n.getMovieId())));
	}

	public long unreadCount(Long userId) {
		return notificationRepository.countByUserIdAndReadAtIsNull(userId);
	}

	@Transactional
	public void markRead(Long id, Long userId) {
		Notification n = notificationRepository.findById(id).filter(x -> x.getUserId().equals(userId))
				.orElseThrow(() -> ApiException.notFound("Không tìm thấy thông báo"));
		if (n.getReadAt() == null) {
			notificationRepository.markRead(id, userId, LocalDateTime.now());
		}
	}

	@Transactional
	public int markAllRead(Long userId) {
		return notificationRepository.markAllRead(userId, LocalDateTime.now());
	}

	// ===== Xử lý event (gọi từ OutboxProcessor) =====

	@Transactional
	public void onMovieReleased(Long movieId) {
		Movie movie = movieRepository.findById(movieId).orElse(null);
		if (movie == null || !movie.isPublic()) {
			return;
		}
		notificationRepository.insertMovieReleased(movieId,
				truncate("Phim \"" + movie.getTitle() + "\" trong danh sách muốn xem của bạn đã ra mắt"),
				"MOVIE_RELEASED:" + movieId);
	}

	@Transactional
	public void onCommentCreated(Long commentId) {
		Comment comment = commentRepository.findWithUser(commentId).orElse(null);
		if (comment == null || comment.getStatus() != CommentStatus.VISIBLE) {
			return;
		}
		User actor = comment.getUser();
		if (comment.getParentId() != null) {
			commentRepository.findWithUser(comment.getParentId()).ifPresent(parent -> {
				Long recipient = parent.getUser().getId();
				if (!recipient.equals(actor.getId())) {
					notificationRepository.insertIgnore(recipient, NotificationType.COMMENT_REPLY.name(), actor.getId(),
							comment.getMovieId(), comment.getReviewId(), comment.getId(),
							truncate(actor.getUsername() + " đã trả lời bình luận của bạn"),
							"COMMENT_REPLY:" + comment.getId());
				}
			});
		} else if (comment.getReviewId() != null) {
			reviewRepository.findDetail(comment.getReviewId()).ifPresent(review -> {
				Long recipient = review.getUser().getId();
				if (!recipient.equals(actor.getId())) {
					notificationRepository.insertIgnore(recipient, NotificationType.REVIEW_COMMENT.name(), actor.getId(),
							comment.getMovieId(), review.getId(), comment.getId(),
							truncate(actor.getUsername() + " đã bình luận về review \"" + review.getTitle() + "\""),
							"REVIEW_COMMENT:" + comment.getId());
				}
			});
		}
	}

	@Transactional
	public void onReviewLiked(Long reviewId, Long likerId) {
		Review review = reviewRepository.findDetail(reviewId).orElse(null);
		User liker = userRepository.findById(likerId).orElse(null);
		if (review == null || liker == null || review.getUser().getId().equals(likerId)) {
			return;
		}
		notificationRepository.insertIgnore(review.getUser().getId(), NotificationType.REVIEW_LIKE.name(), likerId,
				review.getMovie().getId(), reviewId, null,
				truncate(liker.getUsername() + " đã thích review \"" + review.getTitle() + "\""),
				"REVIEW_LIKE:" + reviewId + ":" + likerId);
	}

	private static String truncate(String message) {
		return message.length() <= 500 ? message : message.substring(0, 497) + "...";
	}

	private static NotificationResponse toResponse(Notification n, String movieSlug) {
		return new NotificationResponse(n.getId(), n.getType(), n.getActorId(), n.getMovieId(), movieSlug,
				n.getReviewId(), n.getCommentId(), n.getMessage(), n.getReadAt() != null, n.getCreatedAt());
	}
}
