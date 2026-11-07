package duyanh.film.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import duyanh.film.dto.AdminDto.DailyPoint;
import duyanh.film.dto.AdminDto.SummaryResponse;
import duyanh.film.dto.AdminDto.TopMovie;
import duyanh.film.exception.ApiException;
import lombok.RequiredArgsConstructor;

/** Số liệu cho trang quản trị. Khoảng ngày [from, to] tính cả hai đầu, theo UTC. */
@Service
@RequiredArgsConstructor
public class DashboardService {

	private static final int MAX_RANGE_DAYS = 366;

	private final NamedParameterJdbcTemplate jdbc;
	private final StorageService storage;

	public SummaryResponse summary() {
		LocalDate today = LocalDate.now();
		MapSqlParameterSource p = new MapSqlParameterSource().addValue("today", today)
				.addValue("tomorrow", today.plusDays(1));
		return new SummaryResponse(
				count("SELECT COUNT(*) FROM movies WHERE deleted_at IS NULL", p),
				count("SELECT COUNT(*) FROM users WHERE deleted_at IS NULL", p),
				count("SELECT COUNT(*) FROM reviews", p),
				count("SELECT COUNT(*) FROM ratings", p),
				count("SELECT COUNT(*) FROM reports WHERE status = 'PENDING'", p),
				count("SELECT COALESCE(SUM(view_count), 0) FROM movie_view_daily WHERE view_date = :today", p),
				count("SELECT COUNT(*) FROM users WHERE created_at >= :today AND created_at < :tomorrow", p),
				count("SELECT COUNT(*) FROM reviews WHERE created_at >= :today AND created_at < :tomorrow", p));
	}

	/** by: views (lượt xem trong khoảng), reviews (review mới trong khoảng), rating (điểm cao nhất hiện tại). */
	public List<TopMovie> topMovies(LocalDate from, LocalDate to, int limit, String by) {
		MapSqlParameterSource p = range(from, to).addValue("limit", Math.min(Math.max(limit, 1), 100));
		String sql = switch (by == null ? "views" : by.toLowerCase(Locale.ROOT)) {
			case "reviews" -> """
					SELECT m.id, m.slug, m.title, m.poster_url, COUNT(*) AS value
					FROM reviews r JOIN movies m ON m.id = r.movie_id
					WHERE r.created_at >= :from AND r.created_at < :toExclusive AND m.deleted_at IS NULL
					GROUP BY m.id ORDER BY value DESC LIMIT :limit
					""";
			case "rating" -> """
					SELECT m.id, m.slug, m.title, m.poster_url, m.avg_rating AS value
					FROM movies m WHERE m.deleted_at IS NULL AND m.rating_count > 0
					ORDER BY m.avg_rating DESC, m.rating_count DESC LIMIT :limit
					""";
			default -> """
					SELECT m.id, m.slug, m.title, m.poster_url, SUM(d.view_count) AS value
					FROM movie_view_daily d JOIN movies m ON m.id = d.movie_id
					WHERE d.view_date >= :from AND d.view_date <= :to AND m.deleted_at IS NULL
					GROUP BY m.id ORDER BY value DESC LIMIT :limit
					""";
		};
		return jdbc.query(sql, p, (rs, i) -> new TopMovie(rs.getLong("id"), rs.getString("slug"),
				rs.getString("title"), storage.publicUrl(rs.getString("poster_url")), rs.getDouble("value")));
	}

	public List<DailyPoint> views(LocalDate from, LocalDate to) {
		return daily("""
				SELECT view_date AS day, SUM(view_count) AS value FROM movie_view_daily
				WHERE view_date >= :from AND view_date <= :to GROUP BY view_date
				""", from, to);
	}

	public List<DailyPoint> newUsers(LocalDate from, LocalDate to) {
		return daily("""
				SELECT DATE(created_at) AS day, COUNT(*) AS value FROM users
				WHERE created_at >= :from AND created_at < :toExclusive GROUP BY DATE(created_at)
				""", from, to);
	}

	public List<DailyPoint> reviews(LocalDate from, LocalDate to) {
		return daily("""
				SELECT DATE(created_at) AS day, COUNT(*) AS value FROM reviews
				WHERE created_at >= :from AND created_at < :toExclusive GROUP BY DATE(created_at)
				""", from, to);
	}

	// ===== helpers =====

	/** Trả đủ mọi ngày trong khoảng, ngày không có dữ liệu là 0 để frontend vẽ biểu đồ liền. */
	private List<DailyPoint> daily(String sql, LocalDate from, LocalDate to) {
		MapSqlParameterSource p = range(from, to);
		Map<LocalDate, Long> values = new HashMap<>();
		jdbc.query(sql, p, rs -> {
			values.put(rs.getDate("day").toLocalDate(), rs.getLong("value"));
		});
		LocalDate start = (LocalDate) p.getValue("from");
		LocalDate end = (LocalDate) p.getValue("to");
		List<DailyPoint> result = new ArrayList<>();
		for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
			result.add(new DailyPoint(d, values.getOrDefault(d, 0L)));
		}
		return result;
	}

	private static MapSqlParameterSource range(LocalDate from, LocalDate to) {
		LocalDate end = to != null ? to : LocalDate.now();
		LocalDate start = from != null ? from : end.minusDays(29);
		if (start.isAfter(end)) {
			throw ApiException.badRequest("INVALID_RANGE", "from phải trước hoặc bằng to");
		}
		if (start.plusDays(MAX_RANGE_DAYS).isBefore(end)) {
			throw ApiException.badRequest("INVALID_RANGE", "Khoảng thời gian tối đa 366 ngày");
		}
		return new MapSqlParameterSource().addValue("from", start).addValue("to", end)
				.addValue("toExclusive", end.plusDays(1));
	}

	private long count(String sql, MapSqlParameterSource p) {
		Long value = jdbc.queryForObject(sql, p, Long.class);
		return value == null ? 0 : value;
	}
}
