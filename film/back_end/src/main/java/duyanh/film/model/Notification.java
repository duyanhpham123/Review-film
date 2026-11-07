package duyanh.film.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Chỉ đọc qua JPA; thông báo được tạo bằng INSERT IGNORE trong NotificationRepository. */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
public class Notification {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private NotificationType type;

	@Column(name = "actor_id")
	private Long actorId;

	@Column(name = "movie_id")
	private Long movieId;

	@Column(name = "review_id")
	private Long reviewId;

	@Column(name = "comment_id")
	private Long commentId;

	@Column(nullable = false, length = 500)
	private String message;

	@Column(name = "dedupe_key", nullable = false, length = 100)
	private String dedupeKey;

	@Column(name = "read_at")
	private LocalDateTime readAt;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private LocalDateTime createdAt;
}
