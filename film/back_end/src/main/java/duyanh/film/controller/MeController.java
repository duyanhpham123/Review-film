package duyanh.film.controller;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import duyanh.film.dto.CommonDto.CountResponse;
import duyanh.film.dto.MovieDto.MovieSummary;
import duyanh.film.dto.ReviewDto.NotificationResponse;
import duyanh.film.dto.ReviewDto.WatchlistItemResponse;
import duyanh.film.dto.ReviewDto.WatchlistRequest;
import duyanh.film.dto.response.PageResponse;
import duyanh.film.model.WatchStatus;
import duyanh.film.security.SecurityUtils;
import duyanh.film.service.LibraryService;
import duyanh.film.service.NotificationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Yêu thích, watchlist và thông báo của người đang đăng nhập. */
@Tag(name = "Favorite / Watchlist / Notification")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MeController {

	private final LibraryService libraryService;
	private final NotificationService notificationService;

	// ===== Favorite =====

	@PostMapping("/movies/{id:\\d+}/favorite")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void addFavorite(@PathVariable Long id) {
		libraryService.addFavorite(SecurityUtils.requireUserId(), id);
	}

	@DeleteMapping("/movies/{id:\\d+}/favorite")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void removeFavorite(@PathVariable Long id) {
		libraryService.removeFavorite(SecurityUtils.requireUserId(), id);
	}

	@GetMapping("/me/favorites")
	public PageResponse<MovieSummary> favorites(@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return libraryService.favorites(SecurityUtils.requireUserId(),
				PageRequest.of(Math.max(page, 0), UserController.clamp(size)));
	}

	// ===== Watchlist =====

	@PutMapping("/me/watchlist/{movieId}")
	public WatchlistItemResponse upsertWatchlist(@PathVariable Long movieId, @Valid @RequestBody WatchlistRequest req) {
		return libraryService.upsertWatchlist(SecurityUtils.requireUserId(), movieId, req);
	}

	@DeleteMapping("/me/watchlist/{movieId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void removeWatchlist(@PathVariable Long movieId) {
		libraryService.removeWatchlist(SecurityUtils.requireUserId(), movieId);
	}

	@GetMapping("/me/watchlist")
	public PageResponse<WatchlistItemResponse> watchlist(@RequestParam(required = false) WatchStatus status,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return libraryService.watchlist(SecurityUtils.requireUserId(), status,
				PageRequest.of(Math.max(page, 0), UserController.clamp(size)));
	}

	// ===== Notification =====

	@GetMapping("/me/notifications")
	public PageResponse<NotificationResponse> notifications(@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return notificationService.list(SecurityUtils.requireUserId(), PageRequest.of(Math.max(page, 0),
				UserController.clamp(size), Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))));
	}

	@GetMapping("/me/notifications/unread-count")
	public CountResponse unreadCount() {
		return new CountResponse(notificationService.unreadCount(SecurityUtils.requireUserId()));
	}

	@PatchMapping("/me/notifications/{id}/read")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void markRead(@PathVariable Long id) {
		notificationService.markRead(id, SecurityUtils.requireUserId());
	}

	@PatchMapping("/me/notifications/read-all")
	public CountResponse markAllRead() {
		return new CountResponse(notificationService.markAllRead(SecurityUtils.requireUserId()));
	}
}
