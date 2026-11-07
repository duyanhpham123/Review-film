package duyanh.film.controller;

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

import duyanh.film.dto.ReviewDto.CommentRequest;
import duyanh.film.dto.ReviewDto.CommentResponse;
import duyanh.film.dto.ReviewDto.ReportRequest;
import duyanh.film.dto.ReviewDto.UpdateCommentRequest;
import duyanh.film.dto.response.PageResponse;
import duyanh.film.model.ReportTargetType;
import duyanh.film.security.SecurityUtils;
import duyanh.film.service.CommentService;
import duyanh.film.service.ReportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Comment")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CommentController {

	private final CommentService commentService;
	private final ReportService reportService;

	@GetMapping("/movies/{id:\\d+}/comments")
	public PageResponse<CommentResponse> movieComments(@PathVariable Long id,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return commentService.listForMovie(id, page, size);
	}

	@GetMapping("/reviews/{id}/comments")
	public PageResponse<CommentResponse> reviewComments(@PathVariable Long id,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return commentService.listForReview(id, page, size);
	}

	@PostMapping("/movies/{id:\\d+}/comments")
	@ResponseStatus(HttpStatus.CREATED)
	public CommentResponse commentOnMovie(@PathVariable Long id, @Valid @RequestBody CommentRequest req) {
		return commentService.createOnMovie(id, SecurityUtils.requireUserId(), req);
	}

	@PostMapping("/reviews/{id}/comments")
	@ResponseStatus(HttpStatus.CREATED)
	public CommentResponse commentOnReview(@PathVariable Long id, @Valid @RequestBody CommentRequest req) {
		return commentService.createOnReview(id, SecurityUtils.requireUserId(), req);
	}

	@PutMapping("/comments/{id}")
	public CommentResponse update(@PathVariable Long id, @Valid @RequestBody UpdateCommentRequest req) {
		return commentService.update(id, SecurityUtils.requireUserId(), req.content());
	}

	@DeleteMapping("/comments/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long id) {
		commentService.delete(id, SecurityUtils.requireUserId());
	}

	@PostMapping("/comments/{id}/reports")
	@ResponseStatus(HttpStatus.CREATED)
	public void report(@PathVariable Long id, @Valid @RequestBody ReportRequest req) {
		reportService.report(ReportTargetType.COMMENT, id, SecurityUtils.requireUserId(), req);
	}
}
