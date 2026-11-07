package duyanh.film.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import duyanh.film.dto.ReviewDto.CommentRequest;
import duyanh.film.dto.ReviewDto.CommentResponse;
import duyanh.film.dto.response.PageResponse;
import duyanh.film.exception.ApiException;
import duyanh.film.mapper.UserMapper;
import duyanh.film.model.Comment;
import duyanh.film.model.CommentStatus;
import duyanh.film.model.OutboxEvent;
import duyanh.film.model.Review;
import duyanh.film.model.User;
import duyanh.film.repository.CommentRepository;
import duyanh.film.repository.ReviewRepository;
import duyanh.film.util.RateLimiter;
import lombok.RequiredArgsConstructor;

/**
 * Bình luận 2 cấp: bình luận gốc và các trả lời. Trả lời một trả lời sẽ được gắn vào bình luận gốc.
 * Nội dung hiển thị dạng text thuần, frontend không render HTML.
 */
@Service
@RequiredArgsConstructor
public class CommentService {

	private final CommentRepository commentRepository;
	private final ReviewRepository reviewRepository;
	private final MovieService movieService;
	private final ReviewService reviewService;
	private final UserService userService;
	private final OutboxService outboxService;
	private final RateLimiter rateLimiter;
	private final UserMapper userMapper;

	@Transactional(readOnly = true)
	public PageResponse<CommentResponse> listForMovie(Long movieId, int page, int size) {
		movieService.getPublicMovie(movieId);
		return withReplies(commentRepository.findMovieRoots(movieId, newestFirst(page, size)));
	}

	@Transactional(readOnly = true)
	public PageResponse<CommentResponse> listForReview(Long reviewId, int page, int size) {
		reviewService.getVisible(reviewId);
		return withReplies(commentRepository.findReviewRoots(reviewId, newestFirst(page, size)));
	}

	@Transactional
	public CommentResponse createOnMovie(Long movieId, Long userId, CommentRequest req) {
		User user = userService.getVerifiedUser(userId);
		movieService.getPublicMovie(movieId);
		return create(user, movieId, null, req);
	}

	@Transactional
	public CommentResponse createOnReview(Long reviewId, Long userId, CommentRequest req) {
		User user = userService.getVerifiedUser(userId);
		Review review = reviewService.getVisible(reviewId);
		return create(user, review.getMovie().getId(), reviewId, req);
	}

	@Transactional
	public CommentResponse update(Long id, Long userId, String content) {
		Comment comment = getOwned(id, userId);
		if (comment.getStatus() != CommentStatus.VISIBLE) {
			throw ApiException.badRequest("COMMENT_NOT_EDITABLE", "Bình luận này không thể sửa");
		}
		userService.getActiveUser(userId);
		comment.setContent(content.trim());
		commentRepository.flush();
		return toResponse(comment, List.of());
	}

	/** Xóa mềm để giữ cây trả lời; hiển thị "Bình luận đã bị xóa". */
	@Transactional
	public void delete(Long id, Long userId) {
		Comment comment = getOwned(id, userId);
		if (comment.getStatus() == CommentStatus.DELETED) {
			return;
		}
		comment.setStatus(CommentStatus.DELETED);
		commentRepository.flush();
		if (comment.getReviewId() != null) {
			reviewRepository.refreshCommentCount(comment.getReviewId());
		}
	}

	// ===== helpers =====

	private CommentResponse create(User user, Long movieId, Long reviewId, CommentRequest req) {
		rateLimiter.check("comment:" + user.getId(), 10, Duration.ofMinutes(1));
		Long parentId = null;
		if (req.parentId() != null) {
			Comment parent = commentRepository.findById(req.parentId())
					.orElseThrow(() -> ApiException.badRequest("PARENT_NOT_FOUND", "Bình luận được trả lời không tồn tại"));
			boolean sameThread = parent.getMovieId().equals(movieId) && java.util.Objects.equals(parent.getReviewId(), reviewId);
			if (!sameThread || parent.getStatus() != CommentStatus.VISIBLE) {
				throw ApiException.badRequest("PARENT_INVALID", "Không thể trả lời bình luận này");
			}
			parentId = parent.getParentId() != null ? parent.getParentId() : parent.getId();
		}
		Comment comment = new Comment();
		comment.setUser(user);
		comment.setMovieId(movieId);
		comment.setReviewId(reviewId);
		comment.setParentId(parentId);
		comment.setContent(req.content().trim());
		commentRepository.saveAndFlush(comment);

		if (reviewId != null) {
			reviewRepository.refreshCommentCount(reviewId);
		}
		outboxService.publish(OutboxEvent.COMMENT_CREATED, "COMMENT", comment.getId(),
				Map.of("commentId", comment.getId()));
		return toResponse(comment, List.of());
	}

	private Comment getOwned(Long id, Long userId) {
		Comment comment = commentRepository.findWithUser(id)
				.orElseThrow(() -> ApiException.notFound("Không tìm thấy bình luận"));
		if (!comment.getUser().getId().equals(userId)) {
			throw ApiException.forbidden("NOT_OWNER", "Bạn chỉ thao tác được trên bình luận của mình");
		}
		return comment;
	}

	private static Pageable newestFirst(int page, int size) {
		return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50),
				Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
	}

	private PageResponse<CommentResponse> withReplies(Page<Comment> roots) {
		Map<Long, List<Comment>> replies = new LinkedHashMap<>();
		if (!roots.isEmpty()) {
			List<Long> ids = roots.getContent().stream().map(Comment::getId).toList();
			for (Comment reply : commentRepository.findReplies(ids)) {
				replies.computeIfAbsent(reply.getParentId(), k -> new ArrayList<>()).add(reply);
			}
		}
		return PageResponse.of(roots, root -> toResponse(root,
				replies.getOrDefault(root.getId(), List.of()).stream().map(r -> toResponse(r, List.of())).toList()));
	}

	private CommentResponse toResponse(Comment c, List<CommentResponse> replies) {
		String content = switch (c.getStatus()) {
			case VISIBLE -> c.getContent();
			case DELETED -> "Bình luận đã bị xóa";
			case HIDDEN -> "Bình luận đã bị ẩn bởi quản trị viên";
		};
		boolean visible = c.getStatus() == CommentStatus.VISIBLE;
		boolean edited = visible && c.getUpdatedAt() != null && c.getCreatedAt() != null
				&& c.getUpdatedAt().isAfter(c.getCreatedAt().plusSeconds(5));
		return new CommentResponse(c.getId(), visible ? userMapper.toRef(c.getUser()) : null, content, c.getStatus(),
				c.getParentId(), c.getReviewId(), edited, c.getCreatedAt(), replies);
	}
}
