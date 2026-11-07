package duyanh.film.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "watchlist")
@IdClass(UserMovieId.class)
@Getter
@Setter
@NoArgsConstructor
public class WatchlistItem {

	@Id
	@Column(name = "user_id")
	private Long userId;

	@Id
	@Column(name = "movie_id")
	private Long movieId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private WatchStatus status = WatchStatus.WANT_TO_WATCH;

	@Column(name = "watched_at")
	private LocalDate watchedAt;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	public WatchlistItem(Long userId, Long movieId) {
		this.userId = userId;
		this.movieId = movieId;
	}
}
