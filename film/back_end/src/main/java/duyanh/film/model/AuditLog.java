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
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
public class AuditLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "actor_id")
	private Long actorId;

	@Column(nullable = false, length = 50)
	private String action;

	@Column(name = "target_type", nullable = false, length = 50)
	private String targetType;

	@Column(name = "target_id")
	private Long targetId;

	/** Chuỗi JSON. */
	@Column(name = "before_data", columnDefinition = "JSON")
	private String beforeData;

	@Column(name = "after_data", columnDefinition = "JSON")
	private String afterData;

	@Column(name = "ip_address", length = 45)
	private String ipAddress;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;
}
