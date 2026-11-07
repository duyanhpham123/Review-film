package duyanh.film.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import duyanh.film.model.Movie;

public interface MovieRepository extends JpaRepository<Movie, Long> {

	Optional<Movie> findBySlugAndDeletedAtIsNull(String slug);

	Optional<Movie> findByIdAndDeletedAtIsNull(Long id);

	boolean existsBySlug(String slug);

	boolean existsByTmdbId(Integer tmdbId);

	Optional<Movie> findByTmdbId(Integer tmdbId);

	boolean existsByTmdbIdAndIdNot(Integer tmdbId, Long id);

	@Query("""
			select m from Movie m
			where m.deletedAt is null and m.status = duyanh.film.model.MovieStatus.UPCOMING
			  and m.releaseDate is not null and m.releaseDate <= :today
			""")
	List<Movie> findDueForRelease(@Param("today") LocalDate today);

	/** Tính lại điểm trung bình từ bảng gốc, đúng cả khi nhiều người chấm cùng lúc. */
	@Modifying
	@Query(value = """
			UPDATE movies SET
			  avg_rating = COALESCE((SELECT ROUND(AVG(score), 2) FROM ratings WHERE movie_id = :id), 0),
			  rating_count = (SELECT COUNT(*) FROM ratings WHERE movie_id = :id)
			WHERE id = :id
			""", nativeQuery = true)
	void refreshRatingStats(@Param("id") Long id);

	@Modifying
	@Query(value = """
			UPDATE movies SET
			  review_count = (SELECT COUNT(*) FROM reviews WHERE movie_id = :id AND status = 'VISIBLE')
			WHERE id = :id
			""", nativeQuery = true)
	void refreshReviewCount(@Param("id") Long id);

	@Modifying
	@Query(value = "UPDATE movies SET view_count = view_count + :n WHERE id = :id", nativeQuery = true)
	void incrementViews(@Param("id") Long id, @Param("n") long n);

	@Modifying
	@Query(value = """
			INSERT INTO movie_view_daily (movie_id, view_date, view_count) VALUES (:id, :date, :n)
			ON DUPLICATE KEY UPDATE view_count = view_count + :n
			""", nativeQuery = true)
	void addDailyViews(@Param("id") Long id, @Param("date") LocalDate date, @Param("n") long n);

	/** Job hằng đêm: sửa lệch mọi cột tổng hợp của phim. */
	@Modifying
	@Query(value = """
			UPDATE movies m SET
			  avg_rating = COALESCE((SELECT ROUND(AVG(score), 2) FROM ratings r WHERE r.movie_id = m.id), 0),
			  rating_count = (SELECT COUNT(*) FROM ratings r WHERE r.movie_id = m.id),
			  review_count = (SELECT COUNT(*) FROM reviews v WHERE v.movie_id = m.id AND v.status = 'VISIBLE')
			""", nativeQuery = true)
	int recalculateAllStats();
}
