package duyanh.film.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import duyanh.film.dto.AuthDto.AuthResponse;
import duyanh.film.dto.AuthDto.EmailRequest;
import duyanh.film.dto.AuthDto.LoginRequest;
import duyanh.film.dto.AuthDto.MeResponse;
import duyanh.film.dto.AuthDto.MessageResponse;
import duyanh.film.dto.AuthDto.RegisterRequest;
import duyanh.film.dto.AuthDto.ResetPasswordRequest;
import duyanh.film.dto.AuthDto.TokenRequest;
import duyanh.film.exception.ApiException;
import duyanh.film.security.RefreshTokenCookie;
import duyanh.film.service.AuthService;
import duyanh.film.service.AuthService.LoginResult;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Authentication")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;
	private final RefreshTokenCookie refreshCookie;

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	public MeResponse register(@Valid @RequestBody RegisterRequest req) {
		return authService.register(req);
	}

	@PostMapping("/login")
	public AuthResponse login(@Valid @RequestBody LoginRequest req, HttpServletResponse response) {
		return withCookie(authService.login(req), response);
	}

	@PostMapping("/refresh")
	public AuthResponse refresh(@CookieValue(name = RefreshTokenCookie.NAME, required = false) String token,
			HttpServletResponse response) {
		try {
			return withCookie(authService.refresh(token), response);
		} catch (ApiException e) {
			refreshCookie.clear(response);
			throw e;
		}
	}

	@PostMapping("/logout")
	public MessageResponse logout(@CookieValue(name = RefreshTokenCookie.NAME, required = false) String token,
			HttpServletResponse response) {
		authService.logout(token);
		refreshCookie.clear(response);
		return new MessageResponse("Đã đăng xuất");
	}

	@PostMapping("/forgot-password")
	public MessageResponse forgotPassword(@Valid @RequestBody EmailRequest req) {
		authService.forgotPassword(req.email());
		return new MessageResponse("Nếu email tồn tại, link đặt lại mật khẩu đã được gửi");
	}

	@PostMapping("/reset-password")
	public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
		authService.resetPassword(req.token(), req.newPassword());
		return new MessageResponse("Đặt lại mật khẩu thành công, vui lòng đăng nhập lại");
	}

	@PostMapping("/verify-email")
	public MessageResponse verifyEmail(@Valid @RequestBody TokenRequest req) {
		authService.verifyEmail(req.token());
		return new MessageResponse("Xác thực email thành công");
	}

	@PostMapping("/resend-verification")
	public MessageResponse resendVerification(@Valid @RequestBody EmailRequest req) {
		authService.resendVerification(req.email());
		return new MessageResponse("Nếu email chưa xác thực, link mới đã được gửi");
	}

	private AuthResponse withCookie(LoginResult result, HttpServletResponse response) {
		refreshCookie.set(response, result.refreshToken(), result.refreshTtl(), result.rememberMe());
		return result.body();
	}
}
