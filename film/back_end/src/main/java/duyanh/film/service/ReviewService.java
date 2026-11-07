package duyanh.film.service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import duyanh.film.dto.ReviewDto.ReviewRequest;
import duyanh.film.dto.ReviewDto.ReviewResponse;
import duyanh.film.dto.response.PageResponse;
import duyanh.film.exception.ApiException;
import duyanh.film.mapper.MovieMapper;
import duyanh.film.mapper.UserMapper;
import duyanh.film.model.Movie;
import duyanh.film.model.OutboxEvent;
import duyanh.film.model.Rating;
import duyanh.film.model.Review;
import duyanh.film.model.ReviewStatus;
import duyanh.film.model.User;
import duyanh.film.model.UserMovieId;
import duyanh.film.model.WatchStatus;
import duyanh.film.model.WatchlistItem;
import duyanh.film.repository.MovieRepository;
import duyanh.film.repository.RatingRepository;
import duyanh.film.repository.ReviewRepository;
import duyanh.film.repository.WatchlistRepository;
import duyanh.film.security.AuthUser;
import duyanh.film.security.SecurityUtils;
import duyanh.film.util.RateLimiter;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReviewService {

	private final ReviewRepository reviewRepository;
	private final RatingRepository ratingRepository;
	private final MovieRepository movieRepository;
	private final WatchlistRepository watchlistRepository;
	private final MovieService movieService;
	private final RatingService ratingService;
	private final UserService userService;
	private final OutboxService outboxService;
	private final RateLimiter rateLimiter;
	private final UserMapper userMapper;
	private final MovieMapper movieMapper;
	private final CacheService cacheService;

	@Transactional(readOnly = true)
	public PageResponse<ReviewResponse> listByMovie(Long movieId, Pageable pageable) {
		movieService.getPublicMovie(movieId);
		Page<Review> page = reviewRepository.findByMovie(movieId, ReviewStatus.VISIBLE, pageable);
		return toPage(page, movieId);
	}

	@Transactional(readOnly = true)
	public PageResponse<ReviewResponse> listByAuthor(String username, Pageable pageable) {
		User author = userService.findPublicUser(username);
		Page<Review> page = reviewRepository.findByAuthor(author.getId(), ReviewStatus.VISIBLE, pageable);
		List<Long> movieIds = page.getContent().stream().map(r -> r.getMovie().getId()).toList();
		Map<Long, Integer> scores = movieIds.isEmpty() ? Map.of()
				: ratingRepository.findByUserIdAndMovieIdIn(author.getId(), movieIds).stream()
						.collect(Collectors.toMap(Rating::getMovieId, Rating::getScore));
		Set<Long> liked = likedIds(page.getContent());
		return PageResponse.of(page, r -> toResponse(r, author, scores.get(r.getMovie().getId()), liked));
	}

	@Transactional(readOnly = true)
	public ReviewResponse get(Long id) {
		Review review = reviewRepository.findDetail(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy review"));
		if (!canView(review)) {
			throw ApiException.notFound("Không tìm thấy review");
		}
		return toResponse(review);
	}

	@Transactional
	public ReviewResponse create(Long movieId, Long userId, ReviewRequest req) {
		User user = userService.getVerifiedUser(userId);
		Movie movie = movieService.getPublicMovie(movieId);
		rateLimiter.check("review:" + userId, 5, Duration.ofMinutes(10));
		if (reviewRepository.existsByUser_IdAndMovie_Id(userId, movieId)) {
			throw ApiException.conflict("REVIEW_EXISTS", "Bạn đã viết review cho phim này, hãy sửa bài cũ");
		}
		Review review = new Review();
		review.setUser(user);
		review.setMovie(movie);
		review.setTitle(req.title().trim());
		review.setContent(req.content().trim());
		review.setHasSpoiler(req.hasSpoiler());
		reviewRepository.saveAndFlush(review);

		if (req.score() != null) {
			ratingService.upsert(userId, movieId, req.score());
		}
		movieRepository.refreshReviewCount(movieId);
		cacheService.evictMovieAfterCommit(movie.getSlug());
		markWatched(userId, movieId);
		return toResponse(review);
	}

	@Transactional
	public ReviewResponse update(Long id, Long userId, ReviewRequest req) {
		Review review = reviewRepository.findDetail(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy review"));
		if (!review.getUser().getId().equals(userId)) {
			throw ApiException.forbidden("NOT_OWNER", "Bạn chỉ sửa được review của mình");
		}
		userService.getActiveUser(userId);
		review.setTitle(req.title().trim());
		review.setContent(req.content().trim());
		review.setHasSpoiler(req.hasSpoiler());
		reviewRepository.flush();
		if (req.score() != null) {
			ratingService.upsert(userId, review.getMovie().getId(), req.score());
		}
		return toResponse(review);
	}

	/** Xóa hẳn: DB tự xóa theo bình luận, like và thông báo của bài (ON DELETE CASCADE). */
	@Transactional
	public void delete(Long id, Long userId) {
		Review review = reviewRepository.findDetail(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy review"));
		if (!review.getUser().getId().equals(userId)) {
			throw ApiException.forbidden("NOT_OWNER", "Bạn chỉ xóa được review của mình");
		}
		Long movieId = review.getMovie().getId();
		String movieSlug = review.getMovie().getSlug();
		reviewRepository.delete(review);
		reviewRepository.flush();
		movieRepository.refreshReviewCount(movieId);
		cacheService.evictMovieAfterCommit(movieSlug);
	}

	@Transactional
	public void like(Long id, Long userId) {
		userService.getActiveUser(userId);
		Review review = getVisible(id);
		if (review.getUser().getId().equals(userId)) {
			throw ApiException.badRequest("CANNOT_LIKE_OWN", "Không thể tự like review của mình");
		}
		if (reviewRepository.like(userId, id) > 0) {
			reviewRepository.refreshLikeCount(id);
			outboxService.publish(OutboxEvent.REVIEW_LIKED, "REVIEW", id, Map.of("reviewId", id, "userId", userId));
		}
	}

	@Transactional
	public void unlike(Long id, Long userId) {
		getVisible(id);
		if (reviewRepository.unlike(userId, id) > 0) {
			reviewRepository.refreshLikeCount(id);
		}
	}

	/** Review đang hiển thị của một phim đang công khai. */
	public Review getVisible(Long id) {
		Review review = reviewRepository.findDetail(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy review"));
		if (review.getStatus() != ReviewStatus.VISIBLE || !review.getMovie().isPublic()) {
			throw ApiException.notFound("Không tìm thấy review");
		}
		return review;
	}

	// ===== helpers =====

	private boolean canView(Review review) {
		if (!review.getMovie().isPublic()) {
			return SecurityUtils.currentUser().map(AuthUser::isAdmin).orElse(false);
		}
		if (review.getStatus() == ReviewStatus.VISIBLE) {
			return true;
		}
		return SecurityUtils.currentUser()
				.map(u -> u.isModerator() || u.id().equals(review.getUser().getId()))
				.orElse(false);
	}

	private void markWatched(Long userId, Long movieId) {
		WatchlistItem item = watchlistRepository.findById(new UserMovieId(userId, movieId))
				.orElseGet(() -> new WatchlistItem(userId, movieId));
		if (item.getStatus() != WatchStatus.WATCHED) {
			item.setStatus(WatchStatus.WATCHED);
			if (item.getWatchedAt() == null) {
				item.setWatchedAt(LocalDate.now());
			}
			watchlistRepository.save(item);
		}
	}

	private PageResponse<ReviewResponse> toPage(Page<Review> page, Long movieId) {
		List<Long> authorIds = page.getContent().stream().map(r -> r.getUser().getId()).distinct().toList();
		Map<Long, Integer> scores = authorIds.isEmpty() ? Map.of()
				: ratingRepository.findByMovieIdAndUserIds(movieId, authorIds).stream()
						.collect(Collectors.toMap(Rating::getUserId, Rating::getScore));
		Set<Long> liked = likedIds(page.getContent());
		return PageResponse.of(page, r -> toResponse(r, r.getUser(), scores.get(r.getUser().getId()), liked));
	}

	private Set<Long> likedIds(List<Review> reviews) {
		Long me = SecurityUtils.currentUserIdOrNull();
		if (me == null || reviews.isEmpty()) {
			return Set.of();
		}
		return new HashSet<>(reviewRepository.findLikedReviewIds(me,
				reviews.stream().map(Review::getId).toList()));
	}

	private ReviewResponse toResponse(Review r) {
		Integer score = ratingRepository.findByUserIdAndMovieId(r.getUser().getId(), r.getMovie().getId())
				.map(Rating::getScore).orElse(null);
		return toResponse(r, r.getUser(), score, likedIds(List.of(r)));
	}

	private ReviewResponse toResponse(Review r, User author, Integer score, Set<Long> likedIds) {
		Boolean likedByMe = SecurityUtils.currentUserIdOrNull() == null ? null : likedIds.contains(r.getId());
		return new ReviewResponse(r.getId(), movieMapper.toRef(r.getMovie()), userMapper.toRef(author), score,
				r.getTitle(), r.getContent(), r.isHasSpoiler(), r.getStatus(), r.getLikeCount(), r.getCommentCount(),
				likedByMe, r.getCreatedAt(), r.getUpdatedAt());
	}
}
