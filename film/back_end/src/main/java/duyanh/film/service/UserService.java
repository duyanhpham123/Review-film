package duyanh.film.service;

import java.time.LocalDateTime;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import duyanh.film.dto.AuthDto.MeResponse;
import duyanh.film.dto.UserDto.ChangePasswordRequest;
import duyanh.film.dto.UserDto.PublicProfileResponse;
import duyanh.film.dto.UserDto.UpdateProfileRequest;
import duyanh.film.exception.ApiException;
import duyanh.film.mapper.UserMapper;
import duyanh.film.model.ReviewStatus;
import duyanh.film.model.TokenType;
import duyanh.film.model.User;
import duyanh.film.model.WatchStatus;
import duyanh.film.repository.AuthTokenRepository;
import duyanh.film.repository.RatingRepository;
import duyanh.film.repository.ReviewRepository;
import duyanh.film.repository.UserRepository;
import duyanh.film.repository.WatchlistRepository;
import duyanh.film.util.HashUtil;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

	private final UserRepository userRepository;
	private final AuthTokenRepository tokenRepository;
	private final ReviewRepository reviewRepository;
	private final RatingRepository ratingRepository;
	private final WatchlistRepository watchlistRepository;
	private final PasswordEncoder passwordEncoder;
	private final StorageService storage;
	private final UserMapper userMapper;

	/** User đang đăng nhập, phải còn tồn tại và không bị khóa. */
	public User getActiveUser(Long id) {
		User user = userRepository.findByIdAndDeletedAtIsNull(id)
				.orElseThrow(() -> ApiException.unauthorized("UNAUTHORIZED", "Bạn cần đăng nhập"));
		if (user.getStatus() != duyanh.film.model.UserStatus.ACTIVE) {
			throw ApiException.forbidden("ACCOUNT_DISABLED", "Tài khoản đã bị khóa");
		}
		return user;
	}

	/** Viết review, bình luận cần xác thực email để hạn chế spam. */
	public User getVerifiedUser(Long id) {
		User user = getActiveUser(id);
		if (!user.isEmailVerified()) {
			throw ApiException.forbidden("EMAIL_NOT_VERIFIED", "Bạn cần xác thực email trước");
		}
		return user;
	}

	@Transactional(readOnly = true)
	public MeResponse me(Long userId) {
		return userMapper.toMe(getActiveUser(userId));
	}

	@Transactional
	public MeResponse updateProfile(Long userId, UpdateProfileRequest req) {
		User user = getActiveUser(userId);
		user.setFullName(blankToNull(req.fullName()));
		user.setBio(blankToNull(req.bio()));
		return userMapper.toMe(user);
	}

	/** Đổi mật khẩu và đăng xuất mọi thiết bị khác (giữ phiên hiện tại nếu có cookie). */
	@Transactional
	public void changePassword(Long userId, ChangePasswordRequest req, String currentRefreshToken) {
		User user = getActiveUser(userId);
		if (user.getPasswordHash() == null || !passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
			throw ApiException.badRequest("WRONG_PASSWORD", "Mật khẩu hiện tại không đúng");
		}
		user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
		LocalDateTime now = LocalDateTime.now();
		if (currentRefreshToken != null && !currentRefreshToken.isBlank()) {
			tokenRepository.revokeOtherRefreshTokens(userId, HashUtil.sha256(currentRefreshToken), now);
		} else {
			tokenRepository.revokeAll(userId, TokenType.REFRESH, now);
		}
	}

	@Transactional
	public MeResponse updateAvatar(Long userId, MultipartFile file) {
		User user = getActiveUser(userId);
		user.setAvatarUrl(storage.replaceImage(file, "avatars", user.getAvatarUrl()));
		return userMapper.toMe(user);
	}

	@Transactional(readOnly = true)
	public PublicProfileResponse publicProfile(String username) {
		User user = findPublicUser(username);
		Long id = user.getId();
		return new PublicProfileResponse(user.getUsername(), user.getFullName(), storage.publicUrl(user.getAvatarUrl()),
				user.getBio(), user.getCreatedAt(),
				reviewRepository.countByUser_IdAndStatus(id, ReviewStatus.VISIBLE),
				ratingRepository.countByUserId(id),
				watchlistRepository.countByUserIdAndStatus(id, WatchStatus.WATCHED),
				round1(ratingRepository.averageScoreByUser(id)));
	}

	public User findPublicUser(String username) {
		return userRepository.findByUsernameAndDeletedAtIsNull(username)
				.orElseThrow(() -> ApiException.notFound("Không tìm thấy người dùng"));
	}

	private static Double round1(Double value) {
		return value == null ? null : Math.round(value * 10) / 10.0;
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}
}
