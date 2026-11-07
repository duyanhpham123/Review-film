package duyanh.film.repository;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import duyanh.film.model.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

	Page<Notification> findByUserId(Long userId, Pageable pageable);

	long countByUserIdAndReadAtIsNull(Long userId);

	@Modifying
	@Query("""
			update Notification n set n.readAt = :now
			where n.id = :id and n.userId = :userId and n.readAt is null
			""")
	int markRead(@Param("id") Long id, @Param("userId") Long userId, @Param("now") LocalDateTime now);

	@Modifying
	@Query("update Notification n set n.readAt = :now where n.userId = :userId and n.readAt is null")
	int markAllRead(@Param("userId") Long userId, @Param("now") LocalDateTime now);

	/** INSERT IGNORE + unique (user_id, dedupe_key): chạy lại nhiều lần không tạo trùng. */
	@Modifying
	@Query(value = """
			INSERT IGNORE INTO notifications
			  (user_id, type, actor_id, movie_id, review_id, comment_id, message, dedupe_key)
			VALUES (:userId, :type, :actorId, :movieId, :reviewId, :commentId, :message, :dedupeKey)
			""", nativeQuery = true)
	int insertIgnore(@Param("userId") Long userId, @Param("type") String type, @Param("actorId") Long actorId,
			@Param("movieId") Long movieId, @Param("reviewId") Long reviewId, @Param("commentId") Long commentId,
			@Param("message") String message, @Param("dedupeKey") String dedupeKey);

	/** Báo cho mọi người đang để phim ở trạng thái "muốn xem", trong một câu lệnh. */
	@Modifying
	@Query(value = """
			INSERT IGNORE INTO notifications (user_id, type, movie_id, message, dedupe_key)
			SELECT w.user_id, 'MOVIE_RELEASED', w.movie_id, :message, :dedupeKey
			FROM watchlist w JOIN users u ON u.id = w.user_id
			WHERE w.movie_id = :movieId AND w.status = 'WANT_TO_WATCH' AND u.deleted_at IS NULL
			""", nativeQuery = true)
	int insertMovieReleased(@Param("movieId") Long movieId, @Param("message") String message,
			@Param("dedupeKey") String dedupeKey);
}
