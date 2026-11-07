package duyanh.film.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import duyanh.film.dto.MovieDto.MovieDetail;
import duyanh.film.dto.MovieDto.MovieMeResponse;
import duyanh.film.dto.MovieDto.MovieSummary;
import duyanh.film.dto.MovieDto.SeasonResponse;
import duyanh.film.dto.response.PageResponse;
import duyanh.film.model.MovieStatus;
import duyanh.film.model.MovieType;
import duyanh.film.repository.MovieQueryRepository.MovieFilter;
import duyanh.film.security.SecurityUtils;
import duyanh.film.service.MovieService;
import duyanh.film.service.ViewCounterService;
import duyanh.film.util.RequestUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Movie")
@RestController
@RequestMapping("/api/v1/movies")
@RequiredArgsConstructor
public class MovieController {

	private final MovieService movieService;
	private final ViewCounterService viewCounterService;

	@Operation(summary = "Danh sách + tìm kiếm + lọc phim", description = "sort: latest, release, rating, views, reviews, title")
	@GetMapping
	public PageResponse<MovieSummary> list(
			@RequestParam(required = false) String keyword,
			@RequestParam(required = false) String genre,
			@RequestParam(required = false) String country,
			@RequestParam(required = false) Integer year,
			@RequestParam(required = false) MovieType type,
			@RequestParam(required = false) MovieStatus status,
			@RequestParam(defaultValue = "latest") String sort,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		MovieFilter filter = new MovieFilter(keyword, genre, country, year, type, status, sort, false);
		return movieService.search(filter, Math.max(page, 0), UserController.clamp(size));
	}

	@Operation(summary = "Phim xem nhiều trong ngày / tuần / tháng")
	@GetMapping("/trending")
	public List<MovieSummary> trending(@RequestParam(defaultValue = "week") String period,
			@RequestParam(defaultValue = "10") int limit) {
		return movieService.trending(period, Math.min(Math.max(limit, 1), 50));
	}

	@GetMapping("/{slug}")
	public MovieDetail detail(@PathVariable String slug) {
		return movieService.detail(slug);
	}

	@Operation(summary = "Trạng thái của tôi với phim: điểm, review, yêu thích, watchlist")
	@GetMapping("/{id:\\d+}/me")
	public MovieMeResponse me(@PathVariable Long id) {
		return movieService.me(id, SecurityUtils.requireUserId());
	}

	@GetMapping("/{slug}/episodes")
	public List<SeasonResponse> episodes(@PathVariable String slug) {
		return movieService.episodes(slug);
	}

	@Operation(summary = "Ghi nhận lượt xem trang phim (mỗi người 1 lượt / giờ)")
	@PostMapping("/{id:\\d+}/views")
	public Map<String, Boolean> recordView(@PathVariable Long id) {
		boolean counted = viewCounterService.record(id, SecurityUtils.currentUserIdOrNull(), RequestUtil.clientIp());
		return Map.of("counted", counted);
	}
}
