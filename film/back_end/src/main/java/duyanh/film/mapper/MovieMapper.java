package duyanh.film.mapper;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import duyanh.film.dto.CommonDto.MovieRef;
import duyanh.film.dto.MovieDto.CountryResponse;
import duyanh.film.dto.MovieDto.CreditResponse;
import duyanh.film.dto.MovieDto.EpisodeResponse;
import duyanh.film.dto.MovieDto.GenreResponse;
import duyanh.film.dto.MovieDto.MovieDetail;
import duyanh.film.dto.MovieDto.MovieSummary;
import duyanh.film.dto.MovieDto.PersonSummary;
import duyanh.film.model.Country;
import duyanh.film.model.CreditRole;
import duyanh.film.model.Episode;
import duyanh.film.model.Genre;
import duyanh.film.model.Movie;
import duyanh.film.model.MovieCredit;
import duyanh.film.model.Person;
import duyanh.film.service.StorageService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MovieMapper {

	private final StorageService storage;

	public String url(String keyOrUrl) {
		return storage.publicUrl(keyOrUrl);
	}

	public MovieSummary toSummary(Movie m) {
		return new MovieSummary(m.getId(), m.getSlug(), m.getTitle(), m.getOriginalTitle(), url(m.getPosterUrl()),
				m.getReleaseDate(), m.getType(), m.getStatus(), avg(m.getAvgRating()), m.getRatingCount(),
				m.getReviewCount(), m.getViewCount(), m.isHidden());
	}

	public MovieRef toRef(Movie m) {
		return new MovieRef(m.getId(), m.getSlug(), m.getTitle(), url(m.getPosterUrl()), m.getReleaseDate());
	}

	public MovieDetail toDetail(Movie m, List<MovieCredit> credits) {
		List<GenreResponse> genres = m.getGenres().stream().sorted(Comparator.comparing(Genre::getName))
				.map(MovieMapper::toGenre).toList();
		List<CountryResponse> countries = m.getCountries().stream().sorted(Comparator.comparing(Country::getName))
				.map(MovieMapper::toCountry).toList();
		return new MovieDetail(m.getId(), m.getSlug(), m.getTitle(), m.getOriginalTitle(), m.getDescription(),
				url(m.getPosterUrl()), url(m.getBannerUrl()), m.getTrailerUrl(), m.getReleaseDate(),
				m.getDurationMinutes(), m.getOriginalLanguage(), m.getType(), m.getStatus(), m.getAgeRating(),
				m.getTmdbId(), avg(m.getAvgRating()), m.getRatingCount(), m.getReviewCount(), m.getViewCount(),
				m.isHidden(), genres, countries,
				credits(credits, CreditRole.DIRECTOR), credits(credits, CreditRole.WRITER),
				credits(credits, CreditRole.ACTOR), credits(credits, CreditRole.PRODUCER));
	}

	private List<CreditResponse> credits(List<MovieCredit> credits, CreditRole role) {
		return credits.stream().filter(c -> c.getRole() == role).map(this::toCredit).toList();
	}

	public CreditResponse toCredit(MovieCredit c) {
		Person p = c.getPerson();
		String character = c.getCharacterName() == null || c.getCharacterName().isEmpty() ? null : c.getCharacterName();
		return new CreditResponse(p.getId(), p.getName(), p.getSlug(), url(p.getAvatarUrl()), c.getRole(), character,
				c.getDisplayOrder());
	}

	public PersonSummary toPersonSummary(Person p) {
		return new PersonSummary(p.getId(), p.getName(), p.getSlug(), url(p.getAvatarUrl()));
	}

	public static GenreResponse toGenre(Genre g) {
		return new GenreResponse(g.getId(), g.getName(), g.getSlug(), g.getDescription());
	}

	public static CountryResponse toCountry(Country c) {
		return new CountryResponse(c.getId(), c.getCode(), c.getName());
	}

	public static EpisodeResponse toEpisode(Episode e) {
		return new EpisodeResponse(e.getId(), e.getSeasonNumber(), e.getEpisodeNumber(), e.getTitle(),
				e.getDescription(), e.getDurationMinutes(), e.getAirDate());
	}

	private static BigDecimal avg(BigDecimal value) {
		return value == null ? BigDecimal.ZERO : value;
	}
}
