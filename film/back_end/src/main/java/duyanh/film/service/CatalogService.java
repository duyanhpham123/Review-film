package duyanh.film.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import duyanh.film.config.AppProperties;
import duyanh.film.dto.MovieDto.CountryResponse;
import duyanh.film.dto.MovieDto.GenreRequest;
import duyanh.film.dto.MovieDto.GenreResponse;
import duyanh.film.exception.ApiException;
import duyanh.film.mapper.MovieMapper;
import duyanh.film.model.Genre;
import duyanh.film.repository.CountryRepository;
import duyanh.film.repository.GenreRepository;
import duyanh.film.util.SlugUtil;
import lombok.RequiredArgsConstructor;

/** Thể loại và quốc gia. */
@Service
@RequiredArgsConstructor
public class CatalogService {

	private final GenreRepository genreRepository;
	private final CountryRepository countryRepository;
	private final AuditService auditService;
	private final CacheService cacheService;
	private final AppProperties props;

	public List<GenreResponse> genres() {
		return cacheService.getOrLoad(CacheService.GENRES, props.cache().catalogTtl(),
				cacheService.listOf(GenreResponse.class),
				() -> genreRepository.findAllByOrderByNameAsc().stream().map(MovieMapper::toGenre).toList());
	}

	public GenreResponse genre(String slug) {
		return genreRepository.findBySlug(slug).map(MovieMapper::toGenre)
				.orElseThrow(() -> ApiException.notFound("Không tìm thấy thể loại"));
	}

	public List<CountryResponse> countries() {
		return cacheService.getOrLoad(CacheService.COUNTRIES, props.cache().catalogTtl(),
				cacheService.listOf(CountryResponse.class),
				() -> countryRepository.findAllByOrderByNameAsc().stream().map(MovieMapper::toCountry).toList());
	}

	/** Đổi thể loại: xóa cache danh sách thể loại và các trang danh sách phim lọc theo thể loại. */
	private void invalidateGenres() {
		cacheService.evictAfterCommit(CacheService.GENRES);
		cacheService.invalidateMovieListsAfterCommit();
	}

	@Transactional
	public GenreResponse createGenre(GenreRequest req) {
		if (genreRepository.existsByName(req.name().trim())) {
			throw ApiException.conflict("GENRE_EXISTS", "Thể loại đã tồn tại");
		}
		Genre genre = new Genre();
		apply(genre, req);
		genreRepository.save(genre);
		GenreResponse result = MovieMapper.toGenre(genre);
		auditService.record("GENRE_CREATE", "GENRE", genre.getId(), null, result);
		invalidateGenres();
		return result;
	}

	@Transactional
	public GenreResponse updateGenre(Long id, GenreRequest req) {
		Genre genre = genreRepository.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy thể loại"));
		if (genreRepository.existsByNameAndIdNot(req.name().trim(), id)) {
			throw ApiException.conflict("GENRE_EXISTS", "Thể loại đã tồn tại");
		}
		GenreResponse before = MovieMapper.toGenre(genre);
		apply(genre, req);
		GenreResponse after = MovieMapper.toGenre(genre);
		auditService.record("GENRE_UPDATE", "GENRE", id, before, after);
		invalidateGenres();
		return after;
	}

	@Transactional
	public void deleteGenre(Long id) {
		Genre genre = genreRepository.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy thể loại"));
		if (genreRepository.countMovies(id) > 0) {
			throw ApiException.conflict("GENRE_IN_USE", "Còn phim thuộc thể loại này, không thể xóa");
		}
		auditService.record("GENRE_DELETE", "GENRE", id, MovieMapper.toGenre(genre), null);
		genreRepository.delete(genre);
		invalidateGenres();
	}

	private void apply(Genre genre, GenreRequest req) {
		genre.setName(req.name().trim());
		genre.setDescription(req.description());
		String wanted = req.slug() != null && !req.slug().isBlank() ? req.slug() : SlugUtil.toSlug(req.name());
		if (!wanted.equals(genre.getSlug())) {
			if (req.slug() != null && !req.slug().isBlank() && genreRepository.existsBySlug(wanted)) {
				throw ApiException.conflict("SLUG_TAKEN", "Slug đã được sử dụng");
			}
			genre.setSlug(SlugUtil.unique(wanted, genreRepository::existsBySlug));
		}
	}
}
