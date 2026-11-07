package duyanh.film.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "movies")
@Getter
@Setter
@NoArgsConstructor
public class Movie {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String title;

	@Column(name = "original_title")
	private String originalTitle;

	@Column(nullable = false, length = 300)
	private String slug;

	@Column(columnDefinition = "TEXT")
	private String description;

	@Column(name = "poster_url", length = 500)
	private String posterUrl;

	@Column(name = "banner_url", length = 500)
	private String bannerUrl;

	@Column(name = "trailer_url", length = 500)
	private String trailerUrl;

	@Column(name = "release_date")
	private LocalDate releaseDate;

	@Column(name = "duration_minutes")
	private Integer durationMinutes;

	@Column(name = "original_language", length = 10)
	private String originalLanguage;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private MovieType type = MovieType.MOVIE;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private MovieStatus status = MovieStatus.COMPLETED;

	@Column(name = "age_rating", length = 10)
	private String ageRating;

	@Column(name = "is_hidden", nullable = false)
	private boolean hidden;

	@Column(name = "tmdb_id")
	private Integer tmdbId;

	// Các cột tổng hợp chỉ được cập nhật bằng câu UPDATE trong repository, không ghi qua entity
	@Column(name = "avg_rating", nullable = false, insertable = false, updatable = false)
	private BigDecimal avgRating;

	@Column(name = "rating_count", nullable = false, insertable = false, updatable = false)
	private int ratingCount;

	@Column(name = "review_count", nullable = false, insertable = false, updatable = false)
	private int reviewCount;

	@Column(name = "view_count", nullable = false, insertable = false, updatable = false)
	private long viewCount;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;

	@ManyToMany
	@JoinTable(name = "movie_genres", joinColumns = @JoinColumn(name = "movie_id"),
			inverseJoinColumns = @JoinColumn(name = "genre_id"))
	private Set<Genre> genres = new HashSet<>();

	@ManyToMany
	@JoinTable(name = "movie_countries", joinColumns = @JoinColumn(name = "movie_id"),
			inverseJoinColumns = @JoinColumn(name = "country_id"))
	private Set<Country> countries = new HashSet<>();

	/** Phim người dùng thường nhìn thấy được: chưa xóa và không bị ẩn. */
	public boolean isPublic() {
		return deletedAt == null && !hidden;
	}
}
