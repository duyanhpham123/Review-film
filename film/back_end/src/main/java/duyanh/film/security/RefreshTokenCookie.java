package duyanh.film.security;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import duyanh.film.config.AppProperties;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Đặt / xóa cookie chứa refresh token (HttpOnly). Path là /api/v1 để API đổi mật khẩu
 * cũng nhận được cookie và giữ lại phiên hiện tại khi thu hồi các phiên khác.
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenCookie {

	public static final String NAME = "refresh_token";
	private static final String PATH = "/api/v1";

	private final AppProperties props;

	/** rememberMe = false: cookie phiên, mất khi đóng trình duyệt. */
	public void set(HttpServletResponse response, String token, Duration maxAge, boolean rememberMe) {
		ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(NAME, token)
				.httpOnly(true)
				.secure(props.jwt().cookieSecure())
				.sameSite("Lax")
				.path(PATH);
		if (rememberMe) {
			builder.maxAge(maxAge);
		}
		response.addHeader(HttpHeaders.SET_COOKIE, builder.build().toString());
	}

	public void clear(HttpServletResponse response) {
		ResponseCookie cookie = ResponseCookie.from(NAME, "")
				.httpOnly(true)
				.secure(props.jwt().cookieSecure())
				.sameSite("Lax")
				.path(PATH)
				.maxAge(0)
				.build();
		response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
	}
}
