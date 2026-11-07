package duyanh.film.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import duyanh.film.config.AppProperties;
import duyanh.film.dto.MovieDto.EpisodeResponse;
import duyanh.film.dto.MovieDto.MovieDetail;
import duyanh.film.dto.MovieDto.MovieMeResponse;
import duyanh.film.dto.MovieDto.MovieSummary;
import duyanh.film.dto.MovieDto.SeasonResponse;
import duyanh.film.dto.response.PageResponse;
import duyanh.film.exception.ApiException;
import duyanh.film.mapper.MovieMapper;
import duyanh.film.model.Movie;
import duyanh.film.model.Rating;
import duyanh.film.model.Review;
import duyanh.film.model.UserMovieId;
import duyanh.film.model.WatchlistItem;
import duyanh.film.repository.EpisodeRepository;
import duyanh.film.repository.FavoriteRepository;
import duyanh.film.repository.MovieCreditRepository;
import duyanh.film.repository.MovieQueryRepository;
import duyanh.film.repository.MovieQueryRepository.MovieFilter;
import duyanh.film.repository.MovieRepository;
import duyanh.film.repository.RatingRepository;
import duyanh.film.repository.ReviewRepository;
import duyanh.film.repository.WatchlistRepository;
import lombok.RequiredArgsConstructor;

/** Phần xem phim công khai. */
@Service
@RequiredArgsConstructor
public class MovieService {

	private final MovieRepository movieRepository;
	private final MovieQueryRepository movieQueryRepository;
	private final MovieCreditRepository creditRepository;
	private final EpisodeRepository episodeRepository;
	private final RatingRepository ratingRepository;
	private final ReviewRepository reviewRepository;
	private final FavoriteRepository favoriteRepository;
	private final WatchlistRepository watchlistRepository;
	private final MovieMapper movieMapper;
	private final CacheService cacheService;
	private final ViewCounterService viewCounterService;
	private final TransactionTemplate transactionTemplate;
	private final AppProperties props;

	/** Phim đang hiển thị công khai, không thì báo 404 (không lộ phim đang ẩn). */
	public Movie getPublicMovie(Long id) {
		return movieRepository.findByIdAndDeletedAtIsNull(id).filter(m -> !m.isHidden())
				.orElseThrow(() -> ApiException.notFound("Không tìm thấy phim"));
	}

	public Movie getPublicMovieBySlug(String slug) {
		return movieRepository.findBySlugAndDeletedAtIsNull(slug).filter(m -> !m.isHidden())
				.orElseThrow(() -> ApiException.notFound("Không tìm thấy phim"));
	}

	/** Danh sách không có từ khóa (trang chủ, lọc thể loại...) được cache; tìm kiếm thì không. */
	public PageResponse<MovieSummary> search(MovieFilter filter, int page, int size) {
		if (filter.keyword() != null && !filter.keyword().isBlank()) {
			return movieQueryRepository.search(filter, page, size);
		}
		String params = String.join("|", String.valueOf(filter.genre()), String.valueOf(filter.country()),
				String.valueOf(filter.year()), String.valueOf(filter.type()), String.valueOf(filter.status()),
				String.valueOf(filter.sort()), String.valueOf(page), String.valueOf(size));
		return cacheService.getOrLoad(cacheService.movieListKey(params), props.cache().movieListTtl(),
				cacheService.pageOf(MovieSummary.class), () -> movieQueryRepository.search(filter, page, size));
	}

	/** Cache theo slug, không chứa dữ liệu riêng của người dùng. Điểm số có thể trễ vài phút. */
	public MovieDetail detail(String slug) {
		return cacheService.getOrLoad(CacheService.movieDetailKey(slug), props.cache().movieDetailTtl(),
				cacheService.type(MovieDetail.class), () -> transactionTemplate.execute(status -> {
					Movie movie = getPublicMovieBySlug(slug);
					return movieMapper.toDetail(movie, creditRepository.findByMovieId(movie.getId()));
				}));
	}

	/** Xếp hạng lấy từ Redis; Redis lỗi hoặc chưa có dữ liệu thì đọc bảng movie_view_daily. */
	public List<MovieSummary> trending(String period, int limit) {
		int days = switch (period == null ? "week" : period) {
			case "day" -> 1;
			case "month" -> 30;
			default -> 7;
		};
		// Lấy dư để bù các phim đã bị ẩn / xóa
		List<Long> ids = viewCounterService.trendingIds(days, limit * 2);
		if (ids != null && !ids.isEmpty()) {
			List<MovieSummary> movies = movieQueryRepository.findPublicByIds(ids);
			return movies.size() > limit ? movies.subList(0, limit) : movies;
		}
		return movieQueryRepository.trending(LocalDate.now().minusDays(days - 1L), limit);
	}

	@Transactional(readOnly = true)
	public MovieMeResponse me(Long movieId, Long userId) {
		getPublicMovie(movieId);
		Integer myRating = ratingRepository.findByUserIdAndMovieId(userId, movieId).map(Rating::getScore).orElse(null);
		Long myReviewId = reviewRepository.findByUser_IdAndMovie_Id(userId, movieId).map(Review::getId).orElse(null);
		boolean favorite = favoriteRepository.existsById(new UserMovieId(userId, movieId));
		var status = watchlistRepository.findById(new UserMovieId(userId, movieId)).map(WatchlistItem::getStatus)
				.orElse(null);
		return new MovieMeResponse(myRating, myReviewId, favorite, status);
	}

	@Transactional(readOnly = true)
	public List<SeasonResponse> episodes(String slug) {
		Movie movie = getPublicMovieBySlug(slug);
		Map<Integer, List<EpisodeResponse>> bySeason = new TreeMap<>();
		episodeRepository.findByMovieIdOrderBySeasonNumberAscEpisodeNumberAsc(movie.getId())
				.forEach(e -> bySeason.computeIfAbsent(e.getSeasonNumber(), k -> new java.util.ArrayList<>())
						.add(MovieMapper.toEpisode(e)));
		return bySeason.entrySet().stream().map(e -> new SeasonResponse(e.getKey(), e.getValue())).toList();
	}
}
