package duyanh.film.service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriBuilder;

import com.fasterxml.jackson.databind.JsonNode;

import duyanh.film.config.AppProperties;
import duyanh.film.dto.MovieDto.TmdbSearchResult;
import duyanh.film.exception.ApiException;
import duyanh.film.model.CreditRole;
import duyanh.film.model.MovieStatus;
import duyanh.film.model.MovieType;
import lombok.extern.slf4j.Slf4j;

/**
 * Gọi API TMDB v3 và chuyển kết quả về mô hình của mình. Chỉ đọc, không ghi DB.
 * Tài liệu: https://developer.themoviedb.org/reference
 */
@Slf4j
@Component
public class TmdbClient {

	private static final int MAX_CAST = 15;
	private static final int MAX_WRITERS = 3;

	public record TmdbPerson(int tmdbId, String name, String profileUrl) {
	}

	public record TmdbCredit(TmdbPerson person, CreditRole role, String character, int order) {
	}

	public record TmdbEpisode(int season, int number, String title, String overview, Integer runtime, LocalDate airDate) {
	}

	public record TmdbTitle(int tmdbId, MovieType type, String title, String originalTitle, String overview,
			LocalDate releaseDate, Integer runtime, String originalLanguage, MovieStatus status, String posterUrl,
			String backdropUrl, String trailerUrl, List<Integer> genreIds, List<String> countryCodes,
			List<TmdbCredit> credits, List<TmdbEpisode> episodes) {
	}

	private final AppProperties.Tmdb cfg;
	private final RestClient http;

	public TmdbClient(AppProperties props) {
		this.cfg = props.tmdb();
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofSeconds(5));
		factory.setReadTimeout(Duration.ofSeconds(15));
		RestClient.Builder builder = RestClient.builder().baseUrl(cfg.baseUrl()).requestFactory(factory);
		if (cfg.readToken() != null && !cfg.readToken().isBlank()) {
			builder.defaultHeader("Authorization", "Bearer " + cfg.readToken().trim());
		}
		this.http = builder.build();
	}

	/** type: movie hoặc tv. */
	public List<TmdbSearchResult> search(String query, String type) {
		String kind = "tv".equals(type) ? "tv" : "movie";
		JsonNode body = get("/search/" + kind, b -> b.queryParam("query", query).queryParam("include_adult", false)
				.queryParam("language", cfg.language()));
		List<TmdbSearchResult> results = new ArrayList<>();
		for (JsonNode r : body.path("results")) {
			String date = text(r, "movie".equals(kind) ? "release_date" : "first_air_date");
			results.add(new TmdbSearchResult(r.path("id").asInt(), kind,
					text(r, "movie".equals(kind) ? "title" : "name"),
					text(r, "movie".equals(kind) ? "original_title" : "original_name"),
					date != null && date.length() >= 4 ? date.substring(0, 4) : null,
					image("w185", text(r, "poster_path")), text(r, "overview")));
		}
		return results;
	}

	public TmdbTitle fetch(int tmdbId, String type) {
		return "tv".equals(type) ? fetchTv(tmdbId) : fetchMovie(tmdbId);
	}

	// ===== Phim lẻ =====

	private TmdbTitle fetchMovie(int id) {
		JsonNode m = get("/movie/" + id, b -> b.queryParam("language", cfg.language())
				.queryParam("append_to_response", "credits,videos")
				.queryParam("include_video_language", languageCode() + ",en"));
		LocalDate release = date(text(m, "release_date"));
		MovieStatus status = release != null && release.isAfter(LocalDate.now()) ? MovieStatus.UPCOMING
				: MovieStatus.COMPLETED;

		List<TmdbCredit> credits = new ArrayList<>();
		JsonNode crew = m.path("credits").path("crew");
		addCrew(credits, crew, "Director", CreditRole.DIRECTOR, Integer.MAX_VALUE);
		addCrew(credits, crew, Set.of("Screenplay", "Writer", "Story"), CreditRole.WRITER, MAX_WRITERS);
		int order = 0;
		for (JsonNode c : m.path("credits").path("cast")) {
			if (order >= MAX_CAST) {
				break;
			}
			credits.add(new TmdbCredit(person(c), CreditRole.ACTOR, text(c, "character"), order++));
		}

		List<String> countries = new ArrayList<>();
		m.path("production_countries").forEach(c -> countries.add(text(c, "iso_3166_1")));

		return new TmdbTitle(id, MovieType.MOVIE, text(m, "title"), text(m, "original_title"),
				overview(m, "/movie/" + id), release, positive(m.path("runtime")), text(m, "original_language"), status,
				image("w500", text(m, "poster_path")), image("w1280", text(m, "backdrop_path")), trailer(m),
				ids(m.path("genres")), countries, credits, List.of());
	}

	// ===== Phim bộ =====

	private TmdbTitle fetchTv(int id) {
		JsonNode t = get("/tv/" + id, b -> b.queryParam("language", cfg.language())
				.queryParam("append_to_response", "aggregate_credits,videos")
				.queryParam("include_video_language", languageCode() + ",en"));
		MovieStatus status = switch (String.valueOf(text(t, "status"))) {
			case "Returning Series", "In Production" -> MovieStatus.ONGOING;
			case "Planned", "Pilot" -> MovieStatus.UPCOMING;
			default -> MovieStatus.COMPLETED;
		};

		List<TmdbCredit> credits = new ArrayList<>();
		// Phim bộ không có một đạo diễn chung: người sáng tạo (created_by) được ghi là biên kịch
		int w = 0;
		for (JsonNode c : t.path("created_by")) {
			credits.add(new TmdbCredit(person(c), CreditRole.WRITER, null, w++));
		}
		int order = 0;
		for (JsonNode c : t.path("aggregate_credits").path("cast")) {
			if (order >= MAX_CAST) {
				break;
			}
			JsonNode role = c.path("roles").path(0);
			credits.add(new TmdbCredit(person(c), CreditRole.ACTOR, text(role, "character"), order++));
		}

		List<String> countries = new ArrayList<>();
		t.path("origin_country").forEach(c -> countries.add(c.asText()));

		List<TmdbEpisode> episodes = new ArrayList<>();
		int fetched = 0;
		for (JsonNode s : t.path("seasons")) {
			int season = s.path("season_number").asInt();
			if (season < 1 || fetched >= cfg.maxSeasons()) {
				continue; // mùa 0 là "Specials"
			}
			fetched++;
			JsonNode detail = get("/tv/" + id + "/season/" + season, b -> b.queryParam("language", cfg.language()));
			for (JsonNode e : detail.path("episodes")) {
				episodes.add(new TmdbEpisode(season, e.path("episode_number").asInt(), text(e, "name"),
						text(e, "overview"), positive(e.path("runtime")), date(text(e, "air_date"))));
			}
		}

		Integer runtime = t.path("episode_run_time").isArray() && !t.path("episode_run_time").isEmpty()
				? positive(t.path("episode_run_time").path(0)) : null;
		return new TmdbTitle(id, MovieType.SERIES, text(t, "name"), text(t, "original_name"), overview(t, "/tv/" + id),
				date(text(t, "first_air_date")), runtime, text(t, "original_language"), status,
				image("w500", text(t, "poster_path")), image("w1280", text(t, "backdrop_path")), trailer(t),
				ids(t.path("genres")), countries, credits, episodes);
	}

	// ===== helpers =====

	private JsonNode get(String path, Function<UriBuilder, UriBuilder> params) {
		if (!cfg.configured()) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "TMDB_NOT_CONFIGURED",
					"Chưa cấu hình TMDB (TMDB_READ_TOKEN hoặc TMDB_API_KEY)");
		}
		try {
			return http.get().uri(b -> {
				UriBuilder ub = params.apply(b.path(path));
				if (cfg.apiKey() != null && !cfg.apiKey().isBlank() && (cfg.readToken() == null || cfg.readToken().isBlank())) {
					ub.queryParam("api_key", cfg.apiKey().trim());
				}
				return ub.build();
			}).retrieve().body(JsonNode.class);
		} catch (RestClientResponseException e) {
			if (e.getStatusCode().value() == 404) {
				throw new ApiException(HttpStatus.NOT_FOUND, "TMDB_NOT_FOUND", "Không tìm thấy trên TMDB");
			}
			if (e.getStatusCode().value() == 401) {
				throw new ApiException(HttpStatus.BAD_GATEWAY, "TMDB_AUTH_FAILED", "Key TMDB không hợp lệ");
			}
			log.warn("TMDB {} trả về {}", path, e.getStatusCode());
			throw new ApiException(HttpStatus.BAD_GATEWAY, "TMDB_UNAVAILABLE", "TMDB đang lỗi, vui lòng thử lại sau");
		} catch (RestClientException e) {
			log.warn("Không gọi được TMDB {}: {}", path, e.getMessage());
			throw new ApiException(HttpStatus.BAD_GATEWAY, "TMDB_UNAVAILABLE", "Không kết nối được TMDB");
		}
	}

	/** TMDB trả mô tả rỗng khi chưa có bản dịch tiếng Việt: lấy bản tiếng Anh. */
	private String overview(JsonNode node, String path) {
		String text = text(node, "overview");
		if (text != null || cfg.language().startsWith("en")) {
			return text;
		}
		return text(get(path, b -> b.queryParam("language", "en-US")), "overview");
	}

	private void addCrew(List<TmdbCredit> out, JsonNode crew, String job, CreditRole role, int max) {
		addCrew(out, crew, Set.of(job), role, max);
	}

	private void addCrew(List<TmdbCredit> out, JsonNode crew, Set<String> jobs, CreditRole role, int max) {
		Set<Integer> seen = new LinkedHashSet<>();
		for (JsonNode c : crew) {
			if (seen.size() >= max) {
				break;
			}
			int personId = c.path("id").asInt();
			if (jobs.contains(text(c, "job")) && seen.add(personId)) {
				out.add(new TmdbCredit(person(c), role, null, seen.size() - 1));
			}
		}
	}

	private TmdbPerson person(JsonNode c) {
		return new TmdbPerson(c.path("id").asInt(), text(c, "name"), image("w185", text(c, "profile_path")));
	}

	/** Trailer YouTube: ưu tiên "Trailer" chính thức. */
	private static String trailer(JsonNode node) {
		JsonNode best = null;
		for (JsonNode v : node.path("videos").path("results")) {
			if (!"YouTube".equals(text(v, "site")) || text(v, "key") == null) {
				continue;
			}
			boolean trailer = "Trailer".equals(text(v, "type"));
			if (best == null || (trailer && !"Trailer".equals(text(best, "type")))
					|| (trailer && v.path("official").asBoolean() && !best.path("official").asBoolean())) {
				best = v;
			}
		}
		return best == null ? null : "https://www.youtube.com/watch?v=" + text(best, "key");
	}

	private String image(String size, String path) {
		return path == null ? null : cfg.imageBaseUrl() + "/" + size + path;
	}

	private String languageCode() {
		return cfg.language().split("-")[0];
	}

	private static List<Integer> ids(JsonNode array) {
		List<Integer> ids = new ArrayList<>();
		array.forEach(n -> ids.add(n.path("id").asInt()));
		return ids;
	}

	private static String text(JsonNode node, String field) {
		JsonNode v = node.path(field);
		if (v.isMissingNode() || v.isNull()) {
			return null;
		}
		String s = v.asText().trim();
		return s.isEmpty() ? null : s;
	}

	private static Integer positive(JsonNode node) {
		int v = node.asInt(0);
		return v > 0 ? v : null;
	}

	private static LocalDate date(String value) {
		try {
			return value == null ? null : LocalDate.parse(value);
		} catch (Exception e) {
			return null;
		}
	}

	/** Thể loại TMDB (phim lẻ và phim bộ) -> slug thể loại trong 02_seed.sql. */
	public static final Map<Integer, List<String>> GENRE_SLUGS = Map.ofEntries(
			Map.entry(28, List.of("hanh-dong")), Map.entry(12, List.of("phieu-luu")),
			Map.entry(16, List.of("hoat-hinh")), Map.entry(35, List.of("hai")), Map.entry(80, List.of("hinh-su")),
			Map.entry(99, List.of("tai-lieu")), Map.entry(18, List.of("chinh-kich")),
			Map.entry(10751, List.of("gia-dinh")), Map.entry(14, List.of("gia-tuong")),
			Map.entry(36, List.of("lich-su")), Map.entry(27, List.of("kinh-di")), Map.entry(10402, List.of("am-nhac")),
			Map.entry(9648, List.of("bi-an")), Map.entry(10749, List.of("lang-man")),
			Map.entry(878, List.of("khoa-hoc-vien-tuong")), Map.entry(53, List.of("giat-gan")),
			Map.entry(10752, List.of("chien-tranh")),
			// Chỉ có ở phim bộ
			Map.entry(10759, List.of("hanh-dong", "phieu-luu")),
			Map.entry(10765, List.of("khoa-hoc-vien-tuong", "gia-tuong")),
			Map.entry(10768, List.of("chien-tranh")), Map.entry(10762, List.of("gia-dinh")));
}
