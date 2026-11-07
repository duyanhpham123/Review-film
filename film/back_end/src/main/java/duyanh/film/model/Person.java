package duyanh.film.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Đạo diễn, diễn viên, biên kịch... (bảng people). */
@Entity
@Table(name = "people")
@Getter
@Setter
@NoArgsConstructor
public class Person {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 150)
	private String name;

	@Column(nullable = false, length = 180)
	private String slug;

	@Column(columnDefinition = "TEXT")
	private String biography;

	@Column(name = "avatar_url", length = 500)
	private String avatarUrl;

	@Column(name = "birth_date")
	private LocalDate birthDate;

	@Column(name = "tmdb_id")
	private Integer tmdbId;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;
}
