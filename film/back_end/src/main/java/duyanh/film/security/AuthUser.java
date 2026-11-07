package duyanh.film.security;

import java.util.List;

/** Thông tin người dùng lấy từ access token, dùng làm principal trong SecurityContext. */
public record AuthUser(Long id, String username, List<String> roles) {

	public boolean isAdmin() {
		return roles.contains("ROLE_ADMIN");
	}

	public boolean isModerator() {
		return roles.contains("ROLE_MODERATOR") || isAdmin();
	}
}
