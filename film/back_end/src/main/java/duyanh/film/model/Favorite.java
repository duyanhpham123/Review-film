package duyanh.film.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "favorites")
@IdClass(UserMovieId.class)
@Getter
@Setter
@NoArgsConstructor
public class Favorite {

	@Id
	@Column(name = "user_id")
	private Long userId;

	@Id
	@Column(name = "movie_id")
	private Long movieId;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	public Favorite(Long userId, Long movieId) {
		this.userId = userId;
		this.movieId = movieId;
	}
}
