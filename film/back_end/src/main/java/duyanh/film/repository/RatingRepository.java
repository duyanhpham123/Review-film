package duyanh.film.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import duyanh.film.model.Rating;

public interface RatingRepository extends JpaRepository<Rating, Long> {

	Optional<Rating> findByUserIdAndMovieId(Long userId, Long movieId);

	long countByUserId(Long userId);

	List<Rating> findByUserIdAndMovieIdIn(Long userId, List<Long> movieIds);

	@Query("select avg(r.score) from Rating r where r.userId = :userId")
	Double averageScoreByUser(@Param("userId") Long userId);

	/** Mỗi phần tử: [score, count]. */
	@Query("select r.score, count(r) from Rating r where r.movieId = :movieId group by r.score")
	List<Object[]> distribution(@Param("movieId") Long movieId);

	/** Điểm của các tác giả review cho một phim, dùng khi trả danh sách review. */
	@Query("select r from Rating r where r.movieId = :movieId and r.userId in :userIds")
	List<Rating> findByMovieIdAndUserIds(@Param("movieId") Long movieId, @Param("userIds") List<Long> userIds);
}
