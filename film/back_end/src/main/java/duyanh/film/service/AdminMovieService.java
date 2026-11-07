package duyanh.film.service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import duyanh.film.dto.MovieDto.CreditItem;
import duyanh.film.dto.MovieDto.CreditsRequest;
import duyanh.film.dto.MovieDto.EpisodeRequest;
import duyanh.film.dto.MovieDto.EpisodeResponse;
import duyanh.film.dto.MovieDto.MovieDetail;
import duyanh.film.dto.MovieDto.MovieRequest;
import duyanh.film.dto.MovieDto.MovieSummary;
import duyanh.film.dto.response.PageResponse;
import duyanh.film.exception.ApiException;
import duyanh.film.mapper.MovieMapper;
import duyanh.film.model.Country;
import duyanh.film.model.Episode;
import duyanh.film.model.Genre;
import duyanh.film.model.Movie;
import duyanh.film.model.MovieCredit;
import duyanh.film.model.MovieStatus;
import duyanh.film.model.MovieType;
import duyanh.film.model.OutboxEvent;
import duyanh.film.model.Person;
import duyanh.film.repository.CountryRepository;
import duyanh.film.repository.EpisodeRepository;
import duyanh.film.repository.GenreRepository;
import duyanh.film.repository.MovieCreditRepository;
import duyanh.film.repository.MovieQueryRepository;
import duyanh.film.repository.MovieQueryRepository.MovieFilter;
import duyanh.film.repository.MovieRepository;
import duyanh.film.repository.PersonRepository;
import duyanh.film.util.SlugUtil;
import lombok.RequiredArgsConstructor;

/** Quản trị phim, ê-kíp và tập phim. Mọi thao tác ghi đều có audit log. */
@Service
@RequiredArgsConstructor
public class AdminMovieService {

	private final MovieRepository movieRepository;
	private final MovieQueryRepository movieQueryRepository;
	private final GenreRepository genreRepository;
	private final CountryRepository countryRepository;
	private final PersonRepository personRepository;
	private final MovieCreditRepository creditRepository;
	private final EpisodeRepository episodeRepository;
	private final StorageService storage;
	private final MovieMapper movieMapper;
	private final AuditService auditService;
	private final OutboxService outboxService;
	private final CacheService cacheService;

	public PageResponse<MovieSummary> list(MovieFilter filter, int page, int size) {
		return movieQueryRepository.search(filter, page, size);
	}

	@Transactional(readOnly = true)
	public MovieDetail get(Long id) {
		return detail(getMovie(id));
	}

	/** Phim mới luôn ở trạng thái ẩn để admin kiểm tra trước khi hiện. */
	@Transactional
	public MovieDetail create(MovieRequest req) {
		if (req.tmdbId() != null && movieRepository.existsByTmdbId(req.tmdbId())) {
			throw ApiException.conflict("TMDB_ID_EXISTS", "Phim với tmdbId này đã tồn tại");
		}
		Movie movie = new Movie();
		movie.setHidden(true);
		apply(movie, req);
		movieRepository.save(movie);
		MovieDetail result = detail(movie);
		auditService.record("MOVIE_CREATE", "MOVIE", movie.getId(), null, result);
		invalidate(movie);
		return result;
	}

	@Transactional
	public MovieDetail update(Long id, MovieRequest req) {
		Movie movie = getMovie(id);
		if (req.tmdbId() != null && movieRepository.existsByTmdbIdAndIdNot(req.tmdbId(), id)) {
			throw ApiException.conflict("TMDB_ID_EXISTS", "Phim với tmdbId này đã tồn tại");
		}
		String oldSlug = movie.getSlug();
		MovieDetail before = detail(movie);
		MovieStatus oldStatus = movie.getStatus();
		apply(movie, req);
		if (oldStatus == MovieStatus.UPCOMING && movie.getStatus() != MovieStatus.UPCOMING) {
			publishReleased(movie);
		}
		MovieDetail after = detail(movie);
		auditService.record("MOVIE_UPDATE", "MOVIE", id, before, after);
		invalidate(movie, oldSlug);
		return after;
	}

	@Transactional
	public void delete(Long id) {
		Movie movie = getMovie(id);
		movie.setDeletedAt(LocalDateTime.now());
		auditService.record("MOVIE_DELETE", "MOVIE", id, Map.of("title", movie.getTitle()), null);
		invalidate(movie);
	}

	@Transactional
	public MovieDetail setVisibility(Long id, boolean hidden) {
		Movie movie = getMovie(id);
		boolean before = movie.isHidden();
		movie.setHidden(hidden);
		auditService.record(hidden ? "MOVIE_HIDE" : "MOVIE_SHOW", "MOVIE", id, Map.of("hidden", before),
				Map.of("hidden", hidden));
		invalidate(movie);
		return detail(movie);
	}

	@Transactional
	public MovieDetail updatePoster(Long id, MultipartFile file) {
		Movie movie = getMovie(id);
		String old = movie.getPosterUrl();
		movie.setPosterUrl(storage.replaceImage(file, "posters", old));
		auditService.record("MOVIE_POSTER", "MOVIE", id, old, movie.getPosterUrl());
		invalidate(movie);
		return detail(movie);
	}

	@Transactional
	public MovieDetail updateBanner(Long id, MultipartFile file) {
		Movie movie = getMovie(id);
		String old = movie.getBannerUrl();
		movie.setBannerUrl(storage.replaceImage(file, "banners", old));
		auditService.record("MOVIE_BANNER", "MOVIE", id, old, movie.getBannerUrl());
		invalidate(movie);
		return detail(movie);
	}

	/** Thay toàn bộ ê-kíp của phim bằng danh sách mới. */
	@Transactional
	public MovieDetail replaceCredits(Long id, CreditsRequest req) {
		Movie movie = getMovie(id);
		MovieDetail before = detail(movie);

		Set<String> seen = new HashSet<>();
		List<MovieCredit> credits = new java.util.ArrayList<>();
		for (CreditItem item : req.credits()) {
			String character = item.characterName() == null ? "" : item.characterName().trim();
			if (!seen.add(item.personId() + "|" + item.role() + "|" + character)) {
				throw ApiException.badRequest("CREDIT_DUPLICATE", "Danh sách ê-kíp bị trùng");
			}
			Person person = personRepository.findById(item.personId())
					.orElseThrow(() -> ApiException.badRequest("PERSON_NOT_FOUND",
							"Không tìm thấy người có id " + item.personId()));
			MovieCredit credit = new MovieCredit();
			credit.setMovie(movie);
			credit.setPerson(person);
			credit.setRole(item.role());
			credit.setCharacterName(character);
			credit.setDisplayOrder(item.displayOrder());
			credits.add(credit);
		}
		creditRepository.deleteByMovieId(id);
		creditRepository.saveAll(credits);
		creditRepository.flush();

		MovieDetail after = detail(movie);
		auditService.record("MOVIE_CREDITS", "MOVIE", id, before, after);
		invalidate(movie);
		return after;
	}

	/** Danh sách tập cho trang admin, kể cả phim đang ẩn. */
	@Transactional(readOnly = true)
	public List<EpisodeResponse> listEpisodes(Long movieId) {
		getMovie(movieId);
		return episodeRepository.findByMovieIdOrderBySeasonNumberAscEpisodeNumberAsc(movieId).stream()
				.map(MovieMapper::toEpisode).toList();
	}

	@Transactional
	public EpisodeResponse createEpisode(Long movieId, EpisodeRequest req) {
		Movie movie = getMovie(movieId);
		if (movie.getType() != MovieType.SERIES) {
			throw ApiException.badRequest("NOT_SERIES", "Chỉ phim bộ mới có tập");
		}
		if (episodeRepository.existsByMovieIdAndSeasonNumberAndEpisodeNumber(movieId, req.seasonNumber(),
				req.episodeNumber())) {
			throw ApiException.conflict("EPISODE_EXISTS", "Tập này đã tồn tại trong mùa");
		}
		Episode e = new Episode();
		e.setMovieId(movieId);
		apply(e, req);
		episodeRepository.save(e);
		EpisodeResponse result = MovieMapper.toEpisode(e);
		auditService.record("EPISODE_CREATE", "EPISODE", e.getId(), null, result);
		return result;
	}

	@Transactional
	public EpisodeResponse updateEpisode(Long id, EpisodeRequest req) {
		Episode e = episodeRepository.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy tập phim"));
		if (episodeRepository.existsByMovieIdAndSeasonNumberAndEpisodeNumberAndIdNot(e.getMovieId(),
				req.seasonNumber(), req.episodeNumber(), id)) {
			throw ApiException.conflict("EPISODE_EXISTS", "Tập này đã tồn tại trong mùa");
		}
		EpisodeResponse before = MovieMapper.toEpisode(e);
		apply(e, req);
		EpisodeResponse after = MovieMapper.toEpisode(e);
		auditService.record("EPISODE_UPDATE", "EPISODE", id, before, after);
		return after;
	}

	@Transactional
	public void deleteEpisode(Long id) {
		Episode e = episodeRepository.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy tập phim"));
		auditService.record("EPISODE_DELETE", "EPISODE", id, MovieMapper.toEpisode(e), null);
		episodeRepository.delete(e);
	}

	/** Gọi từ job hằng ngày: phim UPCOMING đã tới ngày phát hành. */
	@Transactional
	public int releaseDueMovies() {
		List<Movie> due = movieRepository.findDueForRelease(java.time.LocalDate.now());
		for (Movie movie : due) {
			movie.setStatus(movie.getType() == MovieType.SERIES ? MovieStatus.ONGOING : MovieStatus.COMPLETED);
			publishReleased(movie);
			invalidate(movie);
		}
		return due.size();
	}

	// ===== helpers =====

	private Movie getMovie(Long id) {
		return movieRepository.findByIdAndDeletedAtIsNull(id)
				.orElseThrow(() -> ApiException.notFound("Không tìm thấy phim"));
	}

	private MovieDetail detail(Movie movie) {
		List<MovieCredit> credits = movie.getId() == null ? List.of() : creditRepository.findByMovieId(movie.getId());
		return movieMapper.toDetail(movie, credits);
	}

	/** Xóa cache chi tiết (cả slug cũ nếu vừa đổi) và mọi trang danh sách phim. */
	private void invalidate(Movie movie, String... oldSlugs) {
		cacheService.evictMovieAfterCommit(movie.getSlug());
		cacheService.evictMovieAfterCommit(oldSlugs);
		cacheService.invalidateMovieListsAfterCommit();
	}

	private void publishReleased(Movie movie) {
		outboxService.publish(OutboxEvent.MOVIE_RELEASED, "MOVIE", movie.getId(), Map.of("movieId", movie.getId()));
	}

	private void apply(Movie movie, MovieRequest req) {
		movie.setTitle(req.title().trim());
		movie.setOriginalTitle(blankToNull(req.originalTitle()));
		movie.setDescription(req.description());
		if (req.posterUrl() != null) {
			movie.setPosterUrl(blankToNull(req.posterUrl()));
		}
		if (req.bannerUrl() != null) {
			movie.setBannerUrl(blankToNull(req.bannerUrl()));
		}
		movie.setTrailerUrl(blankToNull(req.trailerUrl()));
		movie.setReleaseDate(req.releaseDate());
		movie.setDurationMinutes(req.durationMinutes());
		movie.setOriginalLanguage(blankToNull(req.originalLanguage()));
		movie.setType(req.type());
		movie.setStatus(req.status());
		movie.setAgeRating(blankToNull(req.ageRating()));
		movie.setTmdbId(req.tmdbId());

		String wanted = req.slug() != null && !req.slug().isBlank() ? req.slug() : SlugUtil.toSlug(req.title());
		if (!wanted.equals(movie.getSlug())) {
			if (req.slug() != null && !req.slug().isBlank() && movieRepository.existsBySlug(wanted)) {
				throw ApiException.conflict("SLUG_TAKEN", "Slug đã được sử dụng");
			}
			movie.setSlug(SlugUtil.unique(wanted, movieRepository::existsBySlug));
		}

		if (req.genreIds() != null) {
			List<Genre> genres = genreRepository.findAllById(req.genreIds());
			if (genres.size() != req.genreIds().size()) {
				throw ApiException.badRequest("GENRE_NOT_FOUND", "Có thể loại không tồn tại");
			}
			movie.setGenres(new HashSet<>(genres));
		}
		if (req.countryIds() != null) {
			List<Country> countries = countryRepository.findAllById(req.countryIds());
			if (countries.size() != req.countryIds().size()) {
				throw ApiException.badRequest("COUNTRY_NOT_FOUND", "Có quốc gia không tồn tại");
			}
			movie.setCountries(new HashSet<>(countries));
		}
	}

	private static void apply(Episode e, EpisodeRequest req) {
		e.setSeasonNumber(req.seasonNumber());
		e.setEpisodeNumber(req.episodeNumber());
		e.setTitle(blankToNull(req.title()));
		e.setDescription(req.description());
		e.setDurationMinutes(req.durationMinutes());
		e.setAirDate(req.airDate());
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}
}
