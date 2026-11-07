package duyanh.film.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "outbox_events")
@Getter
@Setter
@NoArgsConstructor
public class OutboxEvent {

	public static final String MOVIE_RELEASED = "movie.released";
	public static final String COMMENT_CREATED = "comment.created";
	public static final String REVIEW_LIKED = "review.liked";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "event_type", nullable = false, length = 100)
	private String eventType;

	@Column(name = "aggregate_type", nullable = false, length = 50)
	private String aggregateType;

	@Column(name = "aggregate_id", nullable = false)
	private Long aggregateId;

	/** Chuỗi JSON. */
	@Column(nullable = false, columnDefinition = "JSON")
	private String payload;

	@Column(nullable = false)
	private int attempts;

	@Column(name = "last_error", length = 1000)
	private String lastError;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "sent_at")
	private LocalDateTime sentAt;
}
