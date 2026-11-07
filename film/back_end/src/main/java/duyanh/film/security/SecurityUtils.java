package duyanh.film.security;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import duyanh.film.exception.ApiException;

public final class SecurityUtils {

	private SecurityUtils() {
	}

	public static Optional<AuthUser> currentUser() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth != null && auth.getPrincipal() instanceof AuthUser user) {
			return Optional.of(user);
		}
		return Optional.empty();
	}

	public static AuthUser requireUser() {
		return currentUser().orElseThrow(() -> ApiException.unauthorized("UNAUTHORIZED", "Bạn cần đăng nhập"));
	}

	public static Long requireUserId() {
		return requireUser().id();
	}

	public static Long currentUserIdOrNull() {
		return currentUser().map(AuthUser::id).orElse(null);
	}
}
