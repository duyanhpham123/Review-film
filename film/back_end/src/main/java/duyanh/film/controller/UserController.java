package duyanh.film.controller;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import duyanh.film.dto.AuthDto.MeResponse;
import duyanh.film.dto.AuthDto.MessageResponse;
import duyanh.film.dto.ReviewDto.ReviewResponse;
import duyanh.film.dto.UserDto.ChangePasswordRequest;
import duyanh.film.dto.UserDto.PublicProfileResponse;
import duyanh.film.dto.UserDto.UpdateProfileRequest;
import duyanh.film.dto.response.PageResponse;
import duyanh.film.security.RefreshTokenCookie;
import duyanh.film.security.SecurityUtils;
import duyanh.film.service.ReviewService;
import duyanh.film.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "User / Profile")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

	private final UserService userService;
	private final ReviewService reviewService;

	@GetMapping("/me")
	public MeResponse me() {
		return userService.me(SecurityUtils.requireUserId());
	}

	@PatchMapping("/me")
	public MeResponse updateProfile(@Valid @RequestBody UpdateProfileRequest req) {
		return userService.updateProfile(SecurityUtils.requireUserId(), req);
	}

	@PutMapping("/me/password")
	public MessageResponse changePassword(@Valid @RequestBody ChangePasswordRequest req,
			@CookieValue(name = RefreshTokenCookie.NAME, required = false) String refreshToken) {
		userService.changePassword(SecurityUtils.requireUserId(), req, refreshToken);
		return new MessageResponse("Đổi mật khẩu thành công, các thiết bị khác đã bị đăng xuất");
	}

	@PutMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public MeResponse updateAvatar(@RequestPart("file") MultipartFile file) {
		return userService.updateAvatar(SecurityUtils.requireUserId(), file);
	}

	@GetMapping("/{username}")
	public PublicProfileResponse publicProfile(@PathVariable String username) {
		return userService.publicProfile(username);
	}

	@GetMapping("/{username}/reviews")
	public PageResponse<ReviewResponse> userReviews(@PathVariable String username,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
		return reviewService.listByAuthor(username,
				PageRequest.of(Math.max(page, 0), clamp(size), Sort.by(Sort.Direction.DESC, "createdAt")));
	}

	static int clamp(int size) {
		return Math.min(Math.max(size, 1), 50);
	}
}
