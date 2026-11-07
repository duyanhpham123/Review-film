package duyanh.film.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import duyanh.film.dto.MovieDto.RatingSummary;
import duyanh.film.dto.MovieDto.ScoreCount;
import duyanh.film.model.Rating;
import duyanh.film.repository.MovieRepository;
import duyanh.film.repository.RatingRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RatingService {

	private final RatingRepository ratingRepository;
	private final MovieRepository movieRepository;
	private final MovieService movieService;
	private final UserService userService;
	private final CacheService cacheService;

	@Transactional
	public RatingSummary rate(Long userId, Long movieId, int score) {
		userService.getActiveUser(userId);
		movieService.getPublicMovie(movieId);
		upsert(userId, movieId, score);
		return summaryOf(movieId);
	}

	@Transactional
	public RatingSummary delete(Long userId, Long movieId) {
		movieService.getPublicMovie(movieId);
		ratingRepository.findByUserIdAndMovieId(userId, movieId).ifPresent(r -> {
			ratingRepository.delete(r);
			ratingRepository.flush();
			movieRepository.refreshRatingStats(movieId);
			evictMovie(movieId);
		});
		return summaryOf(movieId);
	}

	@Transactional(readOnly = true)
	public RatingSummary summary(Long movieId) {
		movieService.getPublicMovie(movieId);
		return summaryOf(movieId);
	}

	/** Dùng chung khi viết review kèm điểm. Cập nhật luôn điểm trung bình của phim. */
	@Transactional
	public void upsert(Long userId, Long movieId, int score) {
		Rating rating = ratingRepository.findByUserIdAndMovieId(userId, movieId).orElseGet(() -> {
			Rating r = new Rating();
			r.setUserId(userId);
			r.setMovieId(movieId);
			return r;
		});
		rating.setScore(score);
		ratingRepository.saveAndFlush(rating);
		movieRepository.refreshRatingStats(movieId);
		evictMovie(movieId);
	}

	/** Điểm trung bình nằm trong cache chi tiết phim. */
	void evictMovie(Long movieId) {
		movieRepository.findById(movieId).ifPresent(m -> cacheService.evictMovieAfterCommit(m.getSlug()));
	}

	private RatingSummary summaryOf(Long movieId) {
		Map<Integer, Long> counts = new HashMap<>();
		for (Object[] row : ratingRepository.distribution(movieId)) {
			counts.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
		}
		List<ScoreCount> distribution = IntStream.rangeClosed(1, 10)
				.mapToObj(s -> new ScoreCount(s, counts.getOrDefault(s, 0L))).toList();
		long total = counts.values().stream().mapToLong(Long::longValue).sum();
		double sum = counts.entrySet().stream().mapToDouble(e -> e.getKey() * (double) e.getValue()).sum();
		java.math.BigDecimal avg = total == 0 ? java.math.BigDecimal.ZERO
				: java.math.BigDecimal.valueOf(sum / total).setScale(2, java.math.RoundingMode.HALF_UP);
		return new RatingSummary(avg, (int) total, distribution);
	}
}
