package duyanh.film.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import duyanh.film.dto.CommonDto.MovieRef;
import duyanh.film.model.CreditRole;
import duyanh.film.model.MovieStatus;
import duyanh.film.model.MovieType;
import duyanh.film.model.WatchStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class MovieDto {

	private MovieDto() {
	}

	// ===== Response =====

	public record MovieSummary(Long id, String slug, String title, String originalTitle, String posterUrl,
			LocalDate releaseDate, MovieType type, MovieStatus status, BigDecimal avgRating, int ratingCount,
			int reviewCount, long viewCount, boolean hidden) {
	}

	public record MovieDetail(Long id, String slug, String title, String originalTitle, String description,
			String posterUrl, String bannerUrl, String trailerUrl, LocalDate releaseDate, Integer durationMinutes,
			String originalLanguage, MovieType type, MovieStatus status, String ageRating, Integer tmdbId,
			BigDecimal avgRating, int ratingCount, int reviewCount, long viewCount, boolean hidden,
			List<GenreResponse> genres, List<CountryResponse> countries, List<CreditResponse> directors,
			List<CreditResponse> writers, List<CreditResponse> cast, List<CreditResponse> producers) {
	}

	public record MovieMeResponse(Integer myRating, Long myReviewId, boolean favorite, WatchStatus watchlistStatus) {
	}

	public record GenreResponse(Long id, String name, String slug, String description) {
	}

	public record CountryResponse(Long id, String code, String name) {
	}

	public record CreditResponse(Long personId, String name, String slug, String avatarUrl, CreditRole role,
			String characterName, int displayOrder) {
	}

	public record PersonSummary(Long id, String name, String slug, String avatarUrl) {
	}

	public record PersonDetail(Long id, String name, String slug, String biography, String avatarUrl,
			LocalDate birthDate, Integer tmdbId, List<PersonCredit> credits) {
	}

	public record PersonCredit(MovieRef movie, CreditRole role, String characterName) {
	}

	public record EpisodeResponse(Long id, int seasonNumber, int episodeNumber, String title, String description,
			Integer durationMinutes, LocalDate airDate) {
	}

	public record SeasonResponse(int seasonNumber, List<EpisodeResponse> episodes) {
	}

	public record RatingSummary(BigDecimal avgRating, int ratingCount, List<ScoreCount> distribution) {
	}

	public record ScoreCount(int score, long count) {
	}

	// ===== Request =====

	public record MovieRequest(
			@NotBlank @Size(max = 255) String title,
			@Size(max = 255) String originalTitle,
			@Size(max = 300) @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "Slug chỉ gồm chữ thường, số và dấu gạch ngang") String slug,
			String description,
			@Size(max = 500) String posterUrl,
			@Size(max = 500) String bannerUrl,
			@Size(max = 500) String trailerUrl,
			LocalDate releaseDate,
			@Positive Integer durationMinutes,
			@Size(max = 10) String originalLanguage,
			@NotNull MovieType type,
			@NotNull MovieStatus status,
			@Size(max = 10) String ageRating,
			Integer tmdbId,
			Set<Long> genreIds,
			Set<Long> countryIds) {
	}

	public record VisibilityRequest(boolean hidden) {
	}

	public record CreditsRequest(@NotNull @Valid List<CreditItem> credits) {
	}

	public record CreditItem(
			@NotNull Long personId,
			@NotNull CreditRole role,
			@Size(max = 150) String characterName,
			int displayOrder) {
	}

	public record EpisodeRequest(
			@Min(1) int seasonNumber,
			@Min(1) int episodeNumber,
			@Size(max = 255) String title,
			String description,
			@Positive Integer durationMinutes,
			LocalDate airDate) {
	}

	public record PersonRequest(
			@NotBlank @Size(max = 150) String name,
			@Size(max = 180) @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "Slug chỉ gồm chữ thường, số và dấu gạch ngang") String slug,
			String biography,
			@Size(max = 500) String avatarUrl,
			LocalDate birthDate,
			Integer tmdbId) {
	}

	public record GenreRequest(
			@NotBlank @Size(max = 100) String name,
			@Size(max = 120) @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "Slug chỉ gồm chữ thường, số và dấu gạch ngang") String slug,
			@Size(max = 500) String description) {
	}

	public record RatingRequest(@NotNull @Min(1) @Max(10) Integer score) {
	}

	// ===== TMDB =====

	/** type: movie (phim lẻ, mặc định) hoặc tv (phim bộ). */
	public record TmdbImportRequest(@NotNull @Positive Integer tmdbId,
			@Pattern(regexp = "movie|tv", message = "type là movie hoặc tv") String type) {
	}

	public record TmdbSearchResult(int tmdbId, String type, String title, String originalTitle, String year,
			String posterUrl, String overview) {
	}
}
