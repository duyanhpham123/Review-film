package duyanh.film.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import duyanh.film.dto.MovieDto.MovieSummary;
import duyanh.film.dto.response.PageResponse;
import duyanh.film.model.MovieStatus;
import duyanh.film.model.MovieType;
import duyanh.film.service.StorageService;
import lombok.RequiredArgsConstructor;

/**
 * Truy vấn danh sách phim bằng SQL động: cần FULLTEXT (MATCH ... AGAINST) mà JPQL không hỗ trợ,
 * và chỉ lấy đúng các cột hiển thị trên thẻ phim.
 */
@Repository
@RequiredArgsConstructor
public class MovieQueryRepository {

	private static final String COLUMNS = """
			m.id, m.slug, m.title, m.original_title, m.poster_url, m.release_date, m.type, m.status,
			m.avg_rating, m.rating_count, m.review_count, m.view_count, m.is_hidden
			""";

	private final NamedParameterJdbcTemplate jdbc;
	private final StorageService storage;

	public record MovieFilter(String keyword, String genre, String country, Integer year, MovieType type,
			MovieStatus status, String sort, boolean includeHidden) {
	}

	public PageResponse<MovieSummary> search(MovieFilter f, int page, int size) {
		StringBuilder where = new StringBuilder(" WHERE m.deleted_at IS NULL");
		MapSqlParameterSource params = new MapSqlParameterSource();

		if (!f.includeHidden()) {
			where.append(" AND m.is_hidden = FALSE");
		}
		String keyword = sanitizeKeyword(f.keyword());
		if (keyword != null) {
			if (keyword.length() >= 2) {
				// ngram tách chuỗi thành từng cặp ký tự; tìm theo cụm để "bo gia" khớp "Bố già"
				where.append(" AND MATCH(m.title, m.original_title) AGAINST (:kw IN BOOLEAN MODE)");
				params.addValue("kw", "\"" + keyword + "\"");
			} else {
				where.append(" AND (m.title LIKE :kwLike OR m.original_title LIKE :kwLike)");
				params.addValue("kwLike", keyword + "%");
			}
		}
		if (notBlank(f.genre())) {
			where.append(" AND EXISTS (SELECT 1 FROM movie_genres mg JOIN genres g ON g.id = mg.genre_id"
					+ " WHERE mg.movie_id = m.id AND g.slug = :genre)");
			params.addValue("genre", f.genre());
		}
		if (notBlank(f.country())) {
			where.append(" AND EXISTS (SELECT 1 FROM movie_countries mc JOIN countries c ON c.id = mc.country_id"
					+ " WHERE mc.movie_id = m.id AND c.code = :country)");
			params.addValue("country", f.country().toUpperCase(Locale.ROOT));
		}
		if (f.year() != null) {
			// So sánh theo khoảng để dùng được index trên release_date
			where.append(" AND m.release_date >= :yearStart AND m.release_date < :yearEnd");
			params.addValue("yearStart", LocalDate.of(f.year(), 1, 1));
			params.addValue("yearEnd", LocalDate.of(f.year() + 1, 1, 1));
		}
		if (f.type() != null) {
			where.append(" AND m.type = :type");
			params.addValue("type", f.type().name());
		}
		if (f.status() != null) {
			where.append(" AND m.status = :status");
			params.addValue("status", f.status().name());
		}

		Long total = jdbc.queryForObject("SELECT COUNT(*) FROM movies m" + where, params, Long.class);
		params.addValue("limit", size);
		params.addValue("offset", (long) page * size);
		String sql = "SELECT " + COLUMNS + " FROM movies m" + where + " ORDER BY " + orderBy(f.sort())
				+ " LIMIT :limit OFFSET :offset";
		List<MovieSummary> content = jdbc.query(sql, params, (rs, i) -> map(rs));
		return PageResponse.of(content, page, size, total == null ? 0 : total);
	}

	/** Phim nhiều lượt xem nhất trong N ngày gần đây, đọc từ movie_view_daily. */
	public List<MovieSummary> trending(LocalDate from, int limit) {
		String sql = "SELECT " + COLUMNS + ", SUM(d.view_count) AS period_views"
				+ " FROM movie_view_daily d JOIN movies m ON m.id = d.movie_id"
				+ " WHERE d.view_date >= :from AND m.deleted_at IS NULL AND m.is_hidden = FALSE"
				+ " GROUP BY m.id ORDER BY period_views DESC, m.avg_rating DESC LIMIT :limit";
		return jdbc.query(sql, new MapSqlParameterSource().addValue("from", from).addValue("limit", limit),
				(rs, i) -> map(rs));
	}

	/** Thẻ phim theo danh sách id, giữ nguyên thứ tự truyền vào, bỏ phim ẩn / đã xóa. */
	public List<MovieSummary> findPublicByIds(List<Long> ids) {
		if (ids.isEmpty()) {
			return List.of();
		}
		String sql = "SELECT " + COLUMNS + " FROM movies m"
				+ " WHERE m.id IN (:ids) AND m.deleted_at IS NULL AND m.is_hidden = FALSE";
		java.util.Map<Long, MovieSummary> byId = new java.util.HashMap<>();
		jdbc.query(sql, new MapSqlParameterSource("ids", ids), rs -> {
			MovieSummary s = map(rs);
			byId.put(s.id(), s);
		});
		return ids.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
	}

	private static String orderBy(String sort) {
		String s = sort == null ? "latest" : sort.toLowerCase(Locale.ROOT);
		return switch (s) {
			case "release" -> "m.release_date IS NULL, m.release_date DESC, m.id DESC";
			case "rating" -> "m.avg_rating DESC, m.rating_count DESC, m.id DESC";
			case "views" -> "m.view_count DESC, m.id DESC";
			case "reviews" -> "m.review_count DESC, m.id DESC";
			case "title" -> "m.title ASC, m.id ASC";
			default -> "m.created_at DESC, m.id DESC";
		};
	}

	/** Bỏ các ký tự đặc biệt của BOOLEAN MODE để người dùng không chèn được toán tử. */
	static String sanitizeKeyword(String keyword) {
		if (keyword == null) {
			return null;
		}
		String cleaned = keyword.replaceAll("[+\\-<>()~*\"@%_\\\\]", " ").replaceAll("\\s+", " ").trim();
		if (cleaned.length() > 100) {
			cleaned = cleaned.substring(0, 100).trim();
		}
		return cleaned.isEmpty() ? null : cleaned;
	}

	private static boolean notBlank(String s) {
		return s != null && !s.isBlank();
	}

	private MovieSummary map(ResultSet rs) throws SQLException {
		java.sql.Date release = rs.getDate("release_date");
		return new MovieSummary(
				rs.getLong("id"),
				rs.getString("slug"),
				rs.getString("title"),
				rs.getString("original_title"),
				storage.publicUrl(rs.getString("poster_url")),
				release == null ? null : release.toLocalDate(),
				MovieType.valueOf(rs.getString("type")),
				MovieStatus.valueOf(rs.getString("status")),
				rs.getBigDecimal("avg_rating"),
				rs.getInt("rating_count"),
				rs.getInt("review_count"),
				rs.getLong("view_count"),
				rs.getBoolean("is_hidden"));
	}
}
