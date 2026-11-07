package duyanh.film.service;

import java.time.LocalDate;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import duyanh.film.dto.MovieDto.MovieSummary;
import duyanh.film.dto.ReviewDto.WatchlistItemResponse;
import duyanh.film.dto.ReviewDto.WatchlistRequest;
import duyanh.film.dto.response.PageResponse;
import duyanh.film.mapper.MovieMapper;
import duyanh.film.model.Favorite;
import duyanh.film.model.Movie;
import duyanh.film.model.UserMovieId;
import duyanh.film.model.WatchStatus;
import duyanh.film.model.WatchlistItem;
import duyanh.film.repository.FavoriteRepository;
import duyanh.film.repository.WatchlistRepository;
import lombok.RequiredArgsConstructor;

/** Danh sách cá nhân: yêu thích và watchlist. Các thao tác đều idempotent. */
@Service
@RequiredArgsConstructor
public class LibraryService {

	private final FavoriteRepository favoriteRepository;
	private final WatchlistRepository watchlistRepository;
	private final MovieService movieService;
	private final UserService userService;
	private final MovieMapper movieMapper;

	@Transactional
	public void addFavorite(Long userId, Long movieId) {
		userService.getActiveUser(userId);
		movieService.getPublicMovie(movieId);
		if (!favoriteRepository.existsById(new UserMovieId(userId, movieId))) {
			favoriteRepository.save(new Favorite(userId, movieId));
		}
	}

	@Transactional
	public void removeFavorite(Long userId, Long movieId) {
		favoriteRepository.findById(new UserMovieId(userId, movieId)).ifPresent(favoriteRepository::delete);
	}

	@Transactional(readOnly = true)
	public PageResponse<MovieSummary> favorites(Long userId, Pageable pageable) {
		return PageResponse.of(favoriteRepository.findMovies(userId, pageable), movieMapper::toSummary);
	}

	@Transactional
	public WatchlistItemResponse upsertWatchlist(Long userId, Long movieId, WatchlistRequest req) {
		userService.getActiveUser(userId);
		Movie movie = movieService.getPublicMovie(movieId);
		WatchlistItem item = watchlistRepository.findById(new UserMovieId(userId, movieId))
				.orElseGet(() -> new WatchlistItem(userId, movieId));
		item.setStatus(req.status());
		if (req.status() == WatchStatus.WATCHED) {
			item.setWatchedAt(req.watchedAt() != null ? req.watchedAt()
					: item.getWatchedAt() != null ? item.getWatchedAt() : LocalDate.now());
		} else {
			item.setWatchedAt(null);
		}
		watchlistRepository.saveAndFlush(item);
		return new WatchlistItemResponse(movieMapper.toSummary(movie), item.getStatus(), item.getWatchedAt(),
				item.getUpdatedAt());
	}

	@Transactional
	public void removeWatchlist(Long userId, Long movieId) {
		watchlistRepository.findById(new UserMovieId(userId, movieId)).ifPresent(watchlistRepository::delete);
	}

	@Transactional(readOnly = true)
	public PageResponse<WatchlistItemResponse> watchlist(Long userId, WatchStatus status, Pageable pageable) {
		return PageResponse.of(watchlistRepository.findItems(userId, status, pageable), row -> {
			WatchlistItem item = (WatchlistItem) row[0];
			Movie movie = (Movie) row[1];
			return new WatchlistItemResponse(movieMapper.toSummary(movie), item.getStatus(), item.getWatchedAt(),
					item.getUpdatedAt());
		});
	}
}
