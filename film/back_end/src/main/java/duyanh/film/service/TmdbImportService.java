package duyanh.film.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import duyanh.film.dto.MovieDto.MovieDetail;
import duyanh.film.dto.MovieDto.TmdbSearchResult;
import duyanh.film.exception.ApiException;
import duyanh.film.mapper.MovieMapper;
import duyanh.film.model.Country;
import duyanh.film.model.Episode;
import duyanh.film.model.Genre;
import duyanh.film.model.Movie;
import duyanh.film.model.MovieCredit;
import duyanh.film.model.Person;
import duyanh.film.repository.CountryRepository;
import duyanh.film.repository.EpisodeRepository;
import duyanh.film.repository.GenreRepository;
import duyanh.film.repository.MovieCreditRepository;
import duyanh.film.repository.MovieRepository;
import duyanh.film.repository.PersonRepository;
import duyanh.film.service.TmdbClient.TmdbCredit;
import duyanh.film.service.TmdbClient.TmdbEpisode;
import duyanh.film.service.TmdbClient.TmdbTitle;
import duyanh.film.util.SlugUtil;
import lombok.RequiredArgsConstructor;

/**
 * Import phim / phim bộ từ TMDB.
 * <ul>
 * <li>Gọi TMDB ngoài transaction (mạng chậm không giữ kết nối DB), rồi ghi tất cả trong một transaction</li>
 * <li>Phim tạo ra ở trạng thái ẩn để admin kiểm tra trước khi hiện</li>
 * <li>Người (đạo diễn, diễn viên) dùng lại theo tmdb_id nếu đã có</li>
 * <li>Ảnh lưu dạng URL đầy đủ của TMDB; muốn tự chủ thì upload lại poster trong trang sửa phim</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class TmdbImportService {

	private static final Locale VIETNAMESE = Locale.of("vi");

	private final TmdbClient tmdb;
	private final MovieRepository movieRepository;
	private final GenreRepository genreRepository;
	private final CountryRepository countryRepository;
	private final PersonRepository personRepository;
	private final MovieCreditRepository creditRepository;
	private final EpisodeRepository episodeRepository;
	private final MovieMapper movieMapper;
	private final AuditService auditService;
	private final CacheService cacheService;
	private final TransactionTemplate transactionTemplate;

	public List<TmdbSearchResult> search(String query, String type) {
		if (query == null || query.isBlank()) {
			return List.of();
		}
		return tmdb.search(query.trim(), type);
	}

	public MovieDetail importTitle(int tmdbId, String type) {
		ensureNotImported(tmdbId);
		TmdbTitle title = tmdb.fetch(tmdbId, type);
		return transactionTemplate.execute(status -> save(title));
	}

	private void ensureNotImported(int tmdbId) {
		movieRepository.findByTmdbId(tmdbId).ifPresent(m -> {
			throw ApiException.conflict("TMDB_ALREADY_IMPORTED",
					"Phim này đã có (id " + m.getId() + ": " + m.getTitle() + ")");
		});
	}

	private MovieDetail save(TmdbTitle t) {
		// Kiểm tra lại trong transaction: hai admin có thể import cùng lúc
		ensureNotImported(t.tmdbId());

		Movie movie = new Movie();
		movie.setHidden(true);
		movie.setTmdbId(t.tmdbId());
		movie.setType(t.type());
		movie.setStatus(t.status());
		movie.setTitle(truncate(t.title() != null ? t.title() : t.originalTitle(), 255));
		String original = truncate(t.originalTitle(), 255);
		movie.setOriginalTitle(original != null && !original.equals(movie.getTitle()) ? original : null);
		movie.setSlug(SlugUtil.unique(SlugUtil.toSlug(movie.getTitle()), movieRepository::existsBySlug));
		movie.setDescription(t.overview());
		movie.setReleaseDate(t.releaseDate());
		movie.setDurationMinutes(t.runtime());
		movie.setOriginalLanguage(truncate(t.originalLanguage(), 10));
		movie.setPosterUrl(t.posterUrl());
		movie.setBannerUrl(t.backdropUrl());
		movie.setTrailerUrl(t.trailerUrl());
		movie.setGenres(genres(t.genreIds()));
		movie.setCountries(countries(t.countryCodes()));
		movieRepository.save(movie);

		saveCredits(movie, t.credits());
		saveEpisodes(movie, t.episodes());

		MovieDetail result = movieMapper.toDetail(movie, creditRepository.findByMovieId(movie.getId()));
		auditService.record("MOVIE_IMPORT_TMDB", "MOVIE", movie.getId(), null,
				Map.of("tmdbId", t.tmdbId(), "type", t.type(), "title", movie.getTitle(),
						"credits", t.credits().size(), "episodes", t.episodes().size()));
		cacheService.invalidateMovieListsAfterCommit();
		return result;
	}

	private Set<Genre> genres(List<Integer> tmdbGenreIds) {
		Set<Genre> result = new HashSet<>();
		for (Integer id : tmdbGenreIds) {
			for (String slug : TmdbClient.GENRE_SLUGS.getOrDefault(id, List.of())) {
				genreRepository.findBySlug(slug).ifPresent(result::add);
			}
		}
		return result;
	}

	/** Quốc gia chưa có trong bảng thì tạo mới, tên tiếng Việt lấy từ dữ liệu locale của Java. */
	private Set<Country> countries(List<String> codes) {
		Set<Country> result = new HashSet<>();
		for (String raw : codes) {
			if (raw == null || raw.length() != 2) {
				continue;
			}
			String code = raw.toUpperCase(Locale.ROOT);
			Country country = countryRepository.findByCode(code).orElseGet(() -> {
				Country c = new Country();
				c.setCode(code);
				String name = Locale.of("", code).getDisplayCountry(VIETNAMESE);
				c.setName(name.isBlank() || countryRepository.existsByName(name) ? code : truncate(name, 100));
				return countryRepository.save(c);
			});
			result.add(country);
		}
		return result;
	}

	private void saveCredits(Movie movie, List<TmdbCredit> credits) {
		Map<Integer, Person> people = new HashMap<>();
		Set<String> seen = new HashSet<>();
		List<MovieCredit> rows = new ArrayList<>();
		for (TmdbCredit c : credits) {
			if (c.person().name() == null) {
				continue;
			}
			String character = truncate(c.character() == null ? "" : c.character(), 150);
			// TMDB đôi khi liệt kê một người hai lần cho cùng vai trò
			if (!seen.add(c.person().tmdbId() + "|" + c.role() + "|" + character)) {
				continue;
			}
			Person person = people.computeIfAbsent(c.person().tmdbId(), id -> personRepository.findByTmdbId(id)
					.orElseGet(() -> {
						Person p = new Person();
						p.setTmdbId(id);
						p.setName(truncate(c.person().name(), 150));
						p.setSlug(SlugUtil.unique(SlugUtil.toSlug(p.getName()), personRepository::existsBySlug));
						p.setAvatarUrl(c.person().profileUrl());
						return personRepository.save(p);
					}));
			MovieCredit credit = new MovieCredit();
			credit.setMovie(movie);
			credit.setPerson(person);
			credit.setRole(c.role());
			credit.setCharacterName(character);
			credit.setDisplayOrder(c.order());
			rows.add(credit);
		}
		creditRepository.saveAll(rows);
		creditRepository.flush();
	}

	private void saveEpisodes(Movie movie, List<TmdbEpisode> episodes) {
		Set<String> seen = new HashSet<>();
		List<Episode> rows = new ArrayList<>();
		for (TmdbEpisode e : episodes) {
			if (e.number() < 1 || !seen.add(e.season() + "|" + e.number())) {
				continue;
			}
			Episode ep = new Episode();
			ep.setMovieId(movie.getId());
			ep.setSeasonNumber(e.season());
			ep.setEpisodeNumber(e.number());
			ep.setTitle(truncate(e.title(), 255));
			ep.setDescription(e.overview());
			ep.setDurationMinutes(e.runtime());
			ep.setAirDate(e.airDate());
			rows.add(ep);
		}
		episodeRepository.saveAll(rows);
	}

	private static String truncate(String s, int max) {
		if (s == null) {
			return null;
		}
		String t = s.trim();
		return t.length() <= max ? t : t.substring(0, max);
	}
}
