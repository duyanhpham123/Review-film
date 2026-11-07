package duyanh.film.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import duyanh.film.model.Review;
import duyanh.film.model.ReviewStatus;

public interface ReviewRepository extends JpaRepository<Review, Long> {

	boolean existsByUser_IdAndMovie_Id(Long userId, Long movieId);

	Optional<Review> findByUser_IdAndMovie_Id(Long userId, Long movieId);

	long countByUser_IdAndStatus(Long userId, ReviewStatus status);

	@Query("select r from Review r join fetch r.user join fetch r.movie where r.id = :id")
	Optional<Review> findDetail(@Param("id") Long id);

	@Query(value = "select r from Review r join fetch r.user where r.movie.id = :movieId and r.status = :status",
			countQuery = "select count(r) from Review r where r.movie.id = :movieId and r.status = :status")
	Page<Review> findByMovie(@Param("movieId") Long movieId, @Param("status") ReviewStatus status, Pageable pageable);

	@Query(value = """
			select r from Review r join fetch r.movie m
			where r.user.id = :userId and r.status = :status and m.deletedAt is null and m.hidden = false
			""", countQuery = """
			select count(r) from Review r join r.movie m
			where r.user.id = :userId and r.status = :status and m.deletedAt is null and m.hidden = false
			""")
	Page<Review> findByAuthor(@Param("userId") Long userId, @Param("status") ReviewStatus status, Pageable pageable);

	// ----- Like -----

	/** Trả về 1 nếu vừa like, 0 nếu đã like từ trước. */
	@Modifying
	@Query(value = "INSERT IGNORE INTO review_likes (user_id, review_id) VALUES (:userId, :reviewId)", nativeQuery = true)
	int like(@Param("userId") Long userId, @Param("reviewId") Long reviewId);

	@Modifying
	@Query(value = "DELETE FROM review_likes WHERE user_id = :userId AND review_id = :reviewId", nativeQuery = true)
	int unlike(@Param("userId") Long userId, @Param("reviewId") Long reviewId);

	@Query(value = "SELECT review_id FROM review_likes WHERE user_id = :userId AND review_id IN (:reviewIds)",
			nativeQuery = true)
	List<Long> findLikedReviewIds(@Param("userId") Long userId, @Param("reviewIds") List<Long> reviewIds);

	@Modifying
	@Query(value = "UPDATE reviews SET like_count = (SELECT COUNT(*) FROM review_likes WHERE review_id = :id) WHERE id = :id",
			nativeQuery = true)
	void refreshLikeCount(@Param("id") Long id);

	@Modifying
	@Query(value = """
			UPDATE reviews SET comment_count =
			  (SELECT COUNT(*) FROM comments WHERE review_id = :id AND status = 'VISIBLE')
			WHERE id = :id
			""", nativeQuery = true)
	void refreshCommentCount(@Param("id") Long id);

	@Modifying
	@Query(value = """
			UPDATE reviews v SET
			  like_count = (SELECT COUNT(*) FROM review_likes l WHERE l.review_id = v.id),
			  comment_count = (SELECT COUNT(*) FROM comments c WHERE c.review_id = v.id AND c.status = 'VISIBLE')
			""", nativeQuery = true)
	int recalculateAllCounts();
}
