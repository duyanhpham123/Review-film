package duyanh.film.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import duyanh.film.config.AppProperties;
import duyanh.film.dto.AuthDto.AuthResponse;
import duyanh.film.dto.AuthDto.LoginRequest;
import duyanh.film.dto.AuthDto.MeResponse;
import duyanh.film.dto.AuthDto.RegisterRequest;
import duyanh.film.exception.ApiException;
import duyanh.film.mapper.UserMapper;
import duyanh.film.model.AuthToken;
import duyanh.film.model.Role;
import duyanh.film.model.TokenType;
import duyanh.film.model.User;
import duyanh.film.model.UserStatus;
import duyanh.film.repository.AuthTokenRepository;
import duyanh.film.repository.RoleRepository;
import duyanh.film.repository.UserRepository;
import duyanh.film.security.JwtService;
import duyanh.film.util.HashUtil;
import duyanh.film.util.RateLimiter;
import duyanh.film.util.RequestUtil;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

	private static final Duration VERIFY_TTL = Duration.ofHours(24);
	private static final Duration RESET_TTL = Duration.ofMinutes(30);
	/** Hai request refresh gần như cùng lúc (mở nhiều tab) không bị coi là đánh cắp token. */
	private static final Duration REUSE_GRACE = Duration.ofSeconds(10);

	private final UserRepository userRepository;
	private final RoleRepository roleRepository;
	private final AuthTokenRepository tokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final MailService mailService;
	private final RateLimiter rateLimiter;
	private final UserMapper userMapper;
	private final AppProperties props;

	/** Kết quả đăng nhập / refresh: body trả về và refresh token để controller đặt cookie. */
	public record LoginResult(AuthResponse body, String refreshToken, Duration refreshTtl, boolean rememberMe) {
	}

	@Transactional
	public MeResponse register(RegisterRequest req) {
		rateLimiter.check("register:" + RequestUtil.clientIp(), 10, Duration.ofHours(1));
		String email = req.email().trim().toLowerCase(Locale.ROOT);
		if (userRepository.existsByUsername(req.username())) {
			throw ApiException.conflict("USERNAME_TAKEN", "Username đã được sử dụng");
		}
		if (userRepository.existsByEmail(email)) {
			throw ApiException.conflict("EMAIL_TAKEN", "Email đã được sử dụng");
		}
		User user = new User();
		user.setUsername(req.username());
		user.setEmail(email);
		user.setPasswordHash(passwordEncoder.encode(req.password()));
		user.setFullName(blankToNull(req.fullName()));
		user.getRoles().add(roleRepository.findByName(Role.USER)
				.orElseThrow(() -> new IllegalStateException("Chưa có ROLE_USER, hãy chạy 02_seed.sql")));
		userRepository.save(user);

		String token = createToken(user.getId(), TokenType.EMAIL_VERIFY, VERIFY_TTL);
		mailService.sendVerifyEmail(user.getEmail(), user.getUsername(), token);
		return userMapper.toMe(user);
	}

	@Transactional
	public LoginResult login(LoginRequest req) {
		String login = req.login().trim();
		rateLimiter.check("login:" + RequestUtil.clientIp(), 20, Duration.ofMinutes(5));
		rateLimiter.check("login-account:" + login.toLowerCase(Locale.ROOT), 5, Duration.ofMinutes(5));

		User user = userRepository.findByLogin(login).orElse(null);
		if (user == null || user.getPasswordHash() == null
				|| !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
			throw ApiException.unauthorized("INVALID_CREDENTIALS", "Sai tên đăng nhập hoặc mật khẩu");
		}
		ensureCanLogin(user);
		user.setLastLoginAt(LocalDateTime.now());
		return issueTokens(user, req.rememberMe());
	}

	/** Xoay vòng refresh token: token cũ bị đánh dấu đã dùng, cấp token mới. */
	@Transactional(noRollbackFor = ApiException.class)
	public LoginResult refresh(String rawToken) {
		if (rawToken == null || rawToken.isBlank()) {
			throw ApiException.unauthorized("REFRESH_TOKEN_MISSING", "Phiên đăng nhập đã hết hạn");
		}
		LocalDateTime now = LocalDateTime.now();
		AuthToken token = tokenRepository.findByTokenHashAndType(HashUtil.sha256(rawToken), TokenType.REFRESH)
				.orElseThrow(() -> ApiException.unauthorized("REFRESH_TOKEN_INVALID", "Phiên đăng nhập đã hết hạn"));

		if (token.getUsedAt() != null) {
			// Token đã dùng rồi bị gửi lại: nghi bị lộ, thu hồi mọi phiên của user
			if (token.getUsedAt().isBefore(now.minus(REUSE_GRACE))) {
				tokenRepository.revokeAll(token.getUserId(), TokenType.REFRESH, now);
			}
			throw ApiException.unauthorized("REFRESH_TOKEN_REUSED", "Phiên đăng nhập đã hết hạn");
		}
		if (!token.getExpiresAt().isAfter(now)) {
			throw ApiException.unauthorized("REFRESH_TOKEN_EXPIRED", "Phiên đăng nhập đã hết hạn");
		}
		User user = userRepository.findByIdAndDeletedAtIsNull(token.getUserId())
				.orElseThrow(() -> ApiException.unauthorized("REFRESH_TOKEN_INVALID", "Phiên đăng nhập đã hết hạn"));
		if (user.getStatus() != UserStatus.ACTIVE) {
			throw ApiException.unauthorized("ACCOUNT_DISABLED", "Tài khoản đã bị khóa");
		}
		token.setUsedAt(now);
		boolean rememberMe = Duration.between(token.getCreatedAt(), token.getExpiresAt())
				.toMillis() > props.jwt().refreshExpirationMs();
		return issueTokens(user, rememberMe);
	}

	@Transactional
	public void logout(String rawToken) {
		if (rawToken == null || rawToken.isBlank()) {
			return;
		}
		tokenRepository.findByTokenHashAndType(HashUtil.sha256(rawToken), TokenType.REFRESH)
				.filter(t -> t.getUsedAt() == null)
				.ifPresent(t -> t.setUsedAt(LocalDateTime.now()));
	}

	/** Luôn trả thành công để không lộ email nào đã đăng ký. */
	@Transactional
	public void forgotPassword(String email) {
		rateLimiter.check("forgot:" + RequestUtil.clientIp(), 5, Duration.ofMinutes(15));
		userRepository.findByEmailAndDeletedAtIsNull(email.trim().toLowerCase(Locale.ROOT))
				.filter(u -> u.getStatus() == UserStatus.ACTIVE)
				.ifPresent(user -> {
					tokenRepository.revokeAll(user.getId(), TokenType.PASSWORD_RESET, LocalDateTime.now());
					String token = createToken(user.getId(), TokenType.PASSWORD_RESET, RESET_TTL);
					mailService.sendResetPassword(user.getEmail(), user.getUsername(), token);
				});
	}

	@Transactional
	public void resetPassword(String rawToken, String newPassword) {
		LocalDateTime now = LocalDateTime.now();
		AuthToken token = consume(rawToken, TokenType.PASSWORD_RESET, now);
		User user = userRepository.findByIdAndDeletedAtIsNull(token.getUserId())
				.orElseThrow(() -> ApiException.badRequest("TOKEN_INVALID", "Link không hợp lệ hoặc đã hết hạn"));
		user.setPasswordHash(passwordEncoder.encode(newPassword));
		// Nhận được mail đặt lại mật khẩu cũng chứng minh sở hữu email
		if (user.getEmailVerifiedAt() == null) {
			user.setEmailVerifiedAt(now);
		}
		tokenRepository.revokeAll(user.getId(), TokenType.REFRESH, now);
	}

	@Transactional
	public void verifyEmail(String rawToken) {
		LocalDateTime now = LocalDateTime.now();
		AuthToken token = consume(rawToken, TokenType.EMAIL_VERIFY, now);
		userRepository.findByIdAndDeletedAtIsNull(token.getUserId()).ifPresent(user -> {
			if (user.getEmailVerifiedAt() == null) {
				user.setEmailVerifiedAt(now);
			}
		});
	}

	/** Luôn trả thành công để không lộ email nào đã đăng ký. */
	@Transactional
	public void resendVerification(String email) {
		String normalized = email.trim().toLowerCase(Locale.ROOT);
		rateLimiter.check("resend:" + RequestUtil.clientIp(), 5, Duration.ofMinutes(15));
		rateLimiter.check("resend-email:" + normalized, 3, Duration.ofMinutes(15));
		userRepository.findByEmailAndDeletedAtIsNull(normalized)
				.filter(u -> u.getEmailVerifiedAt() == null && u.getStatus() == UserStatus.ACTIVE)
				.ifPresent(user -> {
					tokenRepository.revokeAll(user.getId(), TokenType.EMAIL_VERIFY, LocalDateTime.now());
					String token = createToken(user.getId(), TokenType.EMAIL_VERIFY, VERIFY_TTL);
					mailService.sendVerifyEmail(user.getEmail(), user.getUsername(), token);
				});
	}

	// ===== helpers =====

	private void ensureCanLogin(User user) {
		if (user.getStatus() == UserStatus.BANNED) {
			throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_BANNED", "Tài khoản đã bị khóa");
		}
		if (user.getStatus() == UserStatus.INACTIVE) {
			throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_INACTIVE", "Tài khoản đang bị vô hiệu hóa");
		}
	}

	private LoginResult issueTokens(User user, boolean rememberMe) {
		Duration ttl = Duration.ofMillis(rememberMe ? props.jwt().refreshExpirationRememberMs()
				: props.jwt().refreshExpirationMs());
		String refresh = createToken(user.getId(), TokenType.REFRESH, ttl);
		String access = jwtService.createAccessToken(user.getId(), user.getUsername(), UserMapper.roleNames(user));
		AuthResponse body = new AuthResponse(access, "Bearer", jwtService.getAccessExpirationMs() / 1000,
				userMapper.toMe(user));
		return new LoginResult(body, refresh, ttl, rememberMe);
	}

	private String createToken(Long userId, TokenType type, Duration ttl) {
		String raw = HashUtil.randomToken();
		AuthToken token = new AuthToken();
		token.setUserId(userId);
		token.setType(type);
		token.setTokenHash(HashUtil.sha256(raw));
		LocalDateTime now = LocalDateTime.now();
		token.setCreatedAt(now);
		token.setExpiresAt(now.plus(ttl));
		tokenRepository.save(token);
		return raw;
	}

	private AuthToken consume(String rawToken, TokenType type, LocalDateTime now) {
		AuthToken token = tokenRepository.findByTokenHashAndType(HashUtil.sha256(rawToken), type)
				.filter(t -> t.isUsable(now))
				.orElseThrow(() -> ApiException.badRequest("TOKEN_INVALID", "Link không hợp lệ hoặc đã hết hạn"));
		token.setUsedAt(now);
		return token;
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}
}
