package duyanh.film.controller;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import duyanh.film.dto.MovieDto.RatingRequest;
import duyanh.film.dto.MovieDto.RatingSummary;
import duyanh.film.dto.ReviewDto.ReportRequest;
import duyanh.film.dto.ReviewDto.ReviewRequest;
import duyanh.film.dto.ReviewDto.ReviewResponse;
import duyanh.film.dto.response.PageResponse;
import duyanh.film.model.ReportTargetType;
import duyanh.film.security.SecurityUtils;
import duyanh.film.service.RatingService;
import duyanh.film.service.ReportService;
import duyanh.film.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Review / Rating")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReviewController {

	private final ReviewService reviewService;
	private final RatingService ratingService;
	private final ReportService reportService;

	// ===== Review =====

	@Operation(summary = "Review của một phim", description = "sort: latest (mới nhất), top (nhiều like nhất)")
	@GetMapping("/movies/{id:\\d+}/reviews")
	public PageResponse<ReviewResponse> listByMovie(@PathVariable Long id,
			@RequestParam(defaultValue = "latest") String sort,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
		Sort order = "top".equals(sort)
				? Sort.by(Sort.Direction.DESC, "likeCount").and(Sort.by(Sort.Direction.DESC, "createdAt"))
				: Sort.by(Sort.Direction.DESC, "createdAt");
		return reviewService.listByMovie(id, PageRequest.of(Math.max(page, 0), UserController.clamp(size), order));
	}

	@GetMapping("/reviews/{id}")
	public ReviewResponse get(@PathVariable Long id) {
		return reviewService.get(id);
	}

	@Operation(summary = "Viết review (cần xác thực email)", description = "score tùy chọn, ghi luôn vào điểm của bạn cho phim")
	@PostMapping("/movies/{id:\\d+}/reviews")
	@ResponseStatus(HttpStatus.CREATED)
	public ReviewResponse create(@PathVariable Long id, @Valid @RequestBody ReviewRequest req) {
		return reviewService.create(id, SecurityUtils.requireUserId(), req);
	}

	@PutMapping("/reviews/{id}")
	public ReviewResponse update(@PathVariable Long id, @Valid @RequestBody ReviewRequest req) {
		return reviewService.update(id, SecurityUtils.requireUserId(), req);
	}

	@DeleteMapping("/reviews/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long id) {
		reviewService.delete(id, SecurityUtils.requireUserId());
	}

	@PutMapping("/reviews/{id}/like")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void like(@PathVariable Long id) {
		reviewService.like(id, SecurityUtils.requireUserId());
	}

	@DeleteMapping("/reviews/{id}/like")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void unlike(@PathVariable Long id) {
		reviewService.unlike(id, SecurityUtils.requireUserId());
	}

	@PostMapping("/reviews/{id}/reports")
	@ResponseStatus(HttpStatus.CREATED)
	public void reportReview(@PathVariable Long id, @Valid @RequestBody ReportRequest req) {
		reportService.report(ReportTargetType.REVIEW, id, SecurityUtils.requireUserId(), req);
	}

	// ===== Rating =====

	@PutMapping("/movies/{id:\\d+}/rating")
	public RatingSummary rate(@PathVariable Long id, @Valid @RequestBody RatingRequest req) {
		return ratingService.rate(SecurityUtils.requireUserId(), id, req.score());
	}

	@DeleteMapping("/movies/{id:\\d+}/rating")
	public RatingSummary deleteRating(@PathVariable Long id) {
		return ratingService.delete(SecurityUtils.requireUserId(), id);
	}

	@Operation(summary = "Điểm trung bình, số lượt và phân bố điểm 1-10")
	@GetMapping("/movies/{id:\\d+}/rating")
	public RatingSummary rating(@PathVariable Long id) {
		return ratingService.summary(id);
	}
}
