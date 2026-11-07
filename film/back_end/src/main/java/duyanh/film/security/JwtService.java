package duyanh.film.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import duyanh.film.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/** Tạo và kiểm tra access token (JWT ký HMAC-SHA256). */
@Service
public class JwtService {

	private final SecretKey key;
	private final long accessExpirationMs;

	public JwtService(AppProperties props) {
		String secret = props.jwt().secret();
		if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
			throw new IllegalStateException(
					"Thiếu app.jwt.secret (tối thiểu 32 ký tự). Đặt biến môi trường JWT_SECRET hoặc ghi vào application-local.properties");
		}
		this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
		this.accessExpirationMs = props.jwt().accessExpirationMs();
	}

	public String createAccessToken(Long userId, String username, List<String> roles) {
		long now = System.currentTimeMillis();
		return Jwts.builder()
				.subject(String.valueOf(userId))
				.claim("username", username)
				.claim("roles", roles)
				.issuedAt(new Date(now))
				.expiration(new Date(now + accessExpirationMs))
				.signWith(key)
				.compact();
	}

	public long getAccessExpirationMs() {
		return accessExpirationMs;
	}

	/** Trả về null nếu token sai chữ ký, hết hạn hoặc sai định dạng. */
	public AuthUser parse(String token) {
		try {
			Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
			List<?> rawRoles = claims.get("roles", List.class);
			List<String> roles = rawRoles == null ? List.of() : rawRoles.stream().map(String::valueOf).toList();
			return new AuthUser(Long.valueOf(claims.getSubject()), claims.get("username", String.class), roles);
		} catch (JwtException | IllegalArgumentException e) {
			return null;
		}
	}
}
