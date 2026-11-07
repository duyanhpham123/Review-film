package duyanh.film.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import duyanh.film.dto.AdminDto.AdminUserResponse;
import duyanh.film.dto.AuthDto.MeResponse;
import duyanh.film.dto.CommonDto.UserRef;
import duyanh.film.model.Role;
import duyanh.film.model.User;
import duyanh.film.service.StorageService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class UserMapper {

	private final StorageService storage;

	public static List<String> roleNames(User user) {
		return user.getRoles().stream().map(Role::getName).sorted().toList();
	}

	public MeResponse toMe(User u) {
		return new MeResponse(u.getId(), u.getUsername(), u.getEmail(), u.getFullName(),
				storage.publicUrl(u.getAvatarUrl()), u.getBio(), u.isEmailVerified(), roleNames(u), u.getCreatedAt());
	}

	/** Người dùng đã xóa tài khoản hiển thị ẩn danh. */
	public UserRef toRef(User u) {
		if (u.getDeletedAt() != null) {
			return new UserRef(null, "deleted", "Người dùng đã xóa", null);
		}
		return new UserRef(u.getId(), u.getUsername(), u.getFullName(), storage.publicUrl(u.getAvatarUrl()));
	}

	public AdminUserResponse toAdmin(User u) {
		return new AdminUserResponse(u.getId(), u.getUsername(), u.getEmail(), u.getFullName(), u.getStatus(),
				roleNames(u), u.isEmailVerified(), u.getCreatedAt(), u.getLastLoginAt());
	}
}
