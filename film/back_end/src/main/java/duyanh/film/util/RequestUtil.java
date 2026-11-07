package duyanh.film.util;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

public final class RequestUtil {

	private RequestUtil() {
	}

	/** IP của client hiện tại, null nếu không nằm trong request (ví dụ job nền). */
	public static String clientIp() {
		if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
			return null;
		}
		HttpServletRequest req = attrs.getRequest();
		// Chỉ tin X-Forwarded-For khi chạy sau reverse proxy do mình quản lý
		String forwarded = req.getHeader("X-Forwarded-For");
		if (forwarded != null && !forwarded.isBlank()) {
			return forwarded.split(",")[0].trim();
		}
		return req.getRemoteAddr();
	}
}
