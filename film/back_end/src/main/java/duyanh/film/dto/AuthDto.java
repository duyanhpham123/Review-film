package duyanh.film.dto;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDto {

	private AuthDto() {
	}

	public record RegisterRequest(
			@NotBlank @Pattern(regexp = "^[a-zA-Z0-9_]{3,50}$", message = "Username 3-50 ký tự, chỉ gồm chữ, số và dấu gạch dưới") String username,
			@NotBlank @Email @Size(max = 254) String email,
			@NotBlank @Size(min = 8, max = 72, message = "Mật khẩu từ 8 đến 72 ký tự") String password,
			@Size(max = 100) String fullName) {
	}

	public record LoginRequest(
			@NotBlank String login,
			@NotBlank String password,
			boolean rememberMe) {
	}

	public record EmailRequest(@NotBlank @Email String email) {
	}

	public record TokenRequest(@NotBlank String token) {
	}

	public record ResetPasswordRequest(
			@NotBlank String token,
			@NotBlank @Size(min = 8, max = 72, message = "Mật khẩu từ 8 đến 72 ký tự") String newPassword) {
	}

	public record AuthResponse(String accessToken, String tokenType, long expiresIn, MeResponse user) {
	}

	public record MeResponse(Long id, String username, String email, String fullName, String avatarUrl, String bio,
			boolean emailVerified, List<String> roles, LocalDateTime createdAt) {
	}

	public record MessageResponse(String message) {
	}
}
