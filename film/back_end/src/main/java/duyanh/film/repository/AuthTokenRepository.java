package duyanh.film.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import duyanh.film.model.AuthToken;
import duyanh.film.model.TokenType;

public interface AuthTokenRepository extends JpaRepository<AuthToken, Long> {

	Optional<AuthToken> findByTokenHashAndType(String tokenHash, TokenType type);

	/** Thu hồi mọi token còn hiệu lực của một loại (ví dụ đăng xuất mọi thiết bị). */
	@Modifying
	@Query("""
			update AuthToken t set t.usedAt = :now
			where t.userId = :userId and t.type = :type and t.usedAt is null
			""")
	int revokeAll(@Param("userId") Long userId, @Param("type") TokenType type, @Param("now") LocalDateTime now);

	/** Thu hồi mọi refresh token trừ token của phiên hiện tại. */
	@Modifying
	@Query("""
			update AuthToken t set t.usedAt = :now
			where t.userId = :userId and t.type = duyanh.film.model.TokenType.REFRESH
			  and t.usedAt is null and t.tokenHash <> :keepHash
			""")
	int revokeOtherRefreshTokens(@Param("userId") Long userId, @Param("keepHash") String keepHash,
			@Param("now") LocalDateTime now);

	@Modifying
	@Query("delete from AuthToken t where t.expiresAt < :before")
	int deleteExpired(@Param("before") LocalDateTime before);
}
