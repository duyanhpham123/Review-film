package duyanh.film.model;

import java.time.LocalDate;
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
@Table(name = "episodes")
@Getter
@Setter
@NoArgsConstructor
public class Episode {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "movie_id", nullable = false)
	private Long movieId;

	@Column(name = "season_number", nullable = false)
	private int seasonNumber = 1;

	@Column(name = "episode_number", nullable = false)
	private int episodeNumber;

	private String title;

	@Column(columnDefinition = "TEXT")
	private String description;

	@Column(name = "duration_minutes")
	private Integer durationMinutes;

	@Column(name = "air_date")
	private LocalDate airDate;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;
}
