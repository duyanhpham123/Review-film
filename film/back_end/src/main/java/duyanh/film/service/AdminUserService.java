package duyanh.film.service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import duyanh.film.dto.AdminDto.AdminUserResponse;
import duyanh.film.dto.response.PageResponse;
import duyanh.film.exception.ApiException;
import duyanh.film.mapper.UserMapper;
import duyanh.film.model.Role;
import duyanh.film.model.TokenType;
import duyanh.film.model.User;
import duyanh.film.model.UserStatus;
import duyanh.film.repository.AuthTokenRepository;
import duyanh.film.repository.RoleRepository;
import duyanh.film.repository.UserRepository;
import duyanh.film.security.SecurityUtils;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminUserService {

	private final UserRepository userRepository;
	private final RoleRepository roleRepository;
	private final AuthTokenRepository tokenRepository;
	private final UserMapper userMapper;
	private final AuditService auditService;

	@Transactional(readOnly = true)
	public PageResponse<AdminUserResponse> list(String keyword, UserStatus status, String role, Pageable pageable) {
		String kw = keyword == null || keyword.isBlank() ? null : keyword.trim();
		String r = role == null || role.isBlank() ? null : role.trim();
		return PageResponse.of(userRepository.search(kw, status, r, pageable), userMapper::toAdmin);
	}

	/** Khóa tài khoản thì thu hồi toàn bộ refresh token: user bị đăng xuất khi access token hết hạn. */
	@Transactional
	public AdminUserResponse setStatus(Long id, UserStatus status) {
		User user = get(id);
		if (id.equals(SecurityUtils.requireUserId())) {
			throw ApiException.badRequest("CANNOT_CHANGE_SELF", "Không thể đổi trạng thái của chính mình");
		}
		UserStatus before = user.getStatus();
		user.setStatus(status);
		if (status != UserStatus.ACTIVE) {
			tokenRepository.revokeAll(id, TokenType.REFRESH, LocalDateTime.now());
		}
		auditService.record("USER_STATUS", "USER", id, Map.of("status", before), Map.of("status", status));
		return userMapper.toAdmin(user);
	}

	@Transactional
	public AdminUserResponse setRoles(Long id, java.util.List<String> roleNames) {
		User user = get(id);
		Set<String> wanted = new LinkedHashSet<>(roleNames);
		wanted.add(Role.USER);
		if (id.equals(SecurityUtils.requireUserId()) && !wanted.contains(Role.ADMIN)) {
			throw ApiException.badRequest("CANNOT_DEMOTE_SELF", "Không thể tự hạ quyền admin của chính mình");
		}
		Set<Role> roles = new HashSet<>();
		for (String name : wanted) {
			roles.add(roleRepository.findByName(name)
					.orElseThrow(() -> ApiException.badRequest("ROLE_NOT_FOUND", "Role không tồn tại: " + name)));
		}
		var before = UserMapper.roleNames(user);
		user.setRoles(roles);
		auditService.record("USER_ROLES", "USER", id, before, UserMapper.roleNames(user));
		// Role mới có hiệu lực từ lần cấp access token tiếp theo (tối đa 15 phút)
		return userMapper.toAdmin(user);
	}

	private User get(Long id) {
		return userRepository.findByIdAndDeletedAtIsNull(id)
				.orElseThrow(() -> ApiException.notFound("Không tìm thấy người dùng"));
	}
}
