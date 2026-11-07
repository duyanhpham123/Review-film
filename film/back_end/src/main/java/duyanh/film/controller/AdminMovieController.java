package duyanh.film.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import duyanh.film.dto.MovieDto.CreditsRequest;
import duyanh.film.dto.MovieDto.EpisodeRequest;
import duyanh.film.dto.MovieDto.EpisodeResponse;
import duyanh.film.dto.MovieDto.GenreRequest;
import duyanh.film.dto.MovieDto.GenreResponse;
import duyanh.film.dto.MovieDto.MovieDetail;
import duyanh.film.dto.MovieDto.MovieRequest;
import duyanh.film.dto.MovieDto.MovieSummary;
import duyanh.film.dto.MovieDto.PersonDetail;
import duyanh.film.dto.MovieDto.PersonRequest;
import duyanh.film.dto.MovieDto.TmdbImportRequest;
import duyanh.film.dto.MovieDto.TmdbSearchResult;
import duyanh.film.dto.MovieDto.VisibilityRequest;
import duyanh.film.dto.response.PageResponse;
import duyanh.film.model.MovieStatus;
import duyanh.film.model.MovieType;
import duyanh.film.repository.MovieQueryRepository.MovieFilter;
import duyanh.film.service.AdminMovieService;
import duyanh.film.service.CatalogService;
import duyanh.film.service.PersonService;
import duyanh.film.service.TmdbImportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Quản trị nội dung: phim, ê-kíp, tập phim, người, thể loại. Chỉ ROLE_ADMIN. */
@Tag(name = "Admin Content")
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@RequiredArgsConstructor
public class AdminMovieController {

	private final AdminMovieService adminMovieService;
	private final PersonService personService;
	private final CatalogService catalogService;
	private final TmdbImportService tmdbImportService;

	// ===== Movie =====

	@GetMapping("/movies")
	public PageResponse<MovieSummary> list(
			@RequestParam(required = false) String keyword,
			@RequestParam(required = false) String genre,
			@RequestParam(required = false) String country,
			@RequestParam(required = false) Integer year,
			@RequestParam(required = false) MovieType type,
			@RequestParam(required = false) MovieStatus status,
			@RequestParam(defaultValue = "latest") String sort,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		MovieFilter filter = new MovieFilter(keyword, genre, country, year, type, status, sort, true);
		return adminMovieService.list(filter, Math.max(page, 0), UserController.clamp(size));
	}

	@GetMapping("/movies/{id:\\d+}")
	public MovieDetail get(@PathVariable Long id) {
		return adminMovieService.get(id);
	}

	// ===== TMDB =====

	/** Tìm trên TMDB để lấy tmdbId trước khi import. type: movie hoặc tv. */
	@GetMapping("/tmdb/search")
	public java.util.List<TmdbSearchResult> searchTmdb(@RequestParam String query,
			@RequestParam(defaultValue = "movie") String type) {
		return tmdbImportService.search(query, type);
	}

	/** Tạo phim (ở trạng thái ẩn) kèm thể loại, quốc gia, ê-kíp, tập phim từ TMDB. */
	@PostMapping("/movies/import-tmdb")
	@ResponseStatus(HttpStatus.CREATED)
	public MovieDetail importTmdb(@Valid @RequestBody TmdbImportRequest req) {
		return tmdbImportService.importTitle(req.tmdbId(), req.type() == null ? "movie" : req.type());
	}

	@PostMapping("/movies")
	@ResponseStatus(HttpStatus.CREATED)
	public MovieDetail create(@Valid @RequestBody MovieRequest req) {
		return adminMovieService.create(req);
	}

	@PutMapping("/movies/{id}")
	public MovieDetail update(@PathVariable Long id, @Valid @RequestBody MovieRequest req) {
		return adminMovieService.update(id, req);
	}

	@DeleteMapping("/movies/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long id) {
		adminMovieService.delete(id);
	}

	@PatchMapping("/movies/{id}/visibility")
	public MovieDetail visibility(@PathVariable Long id, @RequestBody VisibilityRequest req) {
		return adminMovieService.setVisibility(id, req.hidden());
	}

	@PutMapping(value = "/movies/{id}/poster", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public MovieDetail poster(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
		return adminMovieService.updatePoster(id, file);
	}

	@PutMapping(value = "/movies/{id}/banner", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public MovieDetail banner(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
		return adminMovieService.updateBanner(id, file);
	}

	// ===== Credit & Episode =====

	@PutMapping("/movies/{id}/credits")
	public MovieDetail credits(@PathVariable Long id, @Valid @RequestBody CreditsRequest req) {
		return adminMovieService.replaceCredits(id, req);
	}

	@GetMapping("/movies/{id}/episodes")
	public java.util.List<EpisodeResponse> episodes(@PathVariable Long id) {
		return adminMovieService.listEpisodes(id);
	}

	@PostMapping("/movies/{id}/episodes")
	@ResponseStatus(HttpStatus.CREATED)
	public EpisodeResponse createEpisode(@PathVariable Long id, @Valid @RequestBody EpisodeRequest req) {
		return adminMovieService.createEpisode(id, req);
	}

	@PutMapping("/episodes/{id}")
	public EpisodeResponse updateEpisode(@PathVariable Long id, @Valid @RequestBody EpisodeRequest req) {
		return adminMovieService.updateEpisode(id, req);
	}

	@DeleteMapping("/episodes/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteEpisode(@PathVariable Long id) {
		adminMovieService.deleteEpisode(id);
	}

	// ===== People =====

	@PostMapping("/people")
	@ResponseStatus(HttpStatus.CREATED)
	public PersonDetail createPerson(@Valid @RequestBody PersonRequest req) {
		return personService.create(req);
	}

	@PutMapping("/people/{id}")
	public PersonDetail updatePerson(@PathVariable Long id, @Valid @RequestBody PersonRequest req) {
		return personService.update(id, req);
	}

	@DeleteMapping("/people/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deletePerson(@PathVariable Long id) {
		personService.delete(id);
	}

	@PutMapping(value = "/people/{id}/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public PersonDetail personAvatar(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
		return personService.updateAvatar(id, file);
	}

	// ===== Genre =====

	@PostMapping("/genres")
	@ResponseStatus(HttpStatus.CREATED)
	public GenreResponse createGenre(@Valid @RequestBody GenreRequest req) {
		return catalogService.createGenre(req);
	}

	@PutMapping("/genres/{id}")
	public GenreResponse updateGenre(@PathVariable Long id, @Valid @RequestBody GenreRequest req) {
		return catalogService.updateGenre(id, req);
	}

	@DeleteMapping("/genres/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteGenre(@PathVariable Long id) {
		catalogService.deleteGenre(id);
	}
}
