package duyanh.film.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class UserDto {

	private UserDto() {
	}

	public record UpdateProfileRequest(@Size(max = 100) String fullName, @Size(max = 500) String bio) {
	}

	public record ChangePasswordRequest(
			@NotBlank String currentPassword,
			@NotBlank @Size(min = 8, max = 72, message = "Mật khẩu từ 8 đến 72 ký tự") String newPassword) {
	}

	public record PublicProfileResponse(String username, String fullName, String avatarUrl, String bio,
			LocalDateTime joinedAt, long reviewCount, long ratingCount, long watchedCount, Double averageScore) {
	}
}
