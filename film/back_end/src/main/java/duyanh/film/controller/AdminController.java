package duyanh.film.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import duyanh.film.dto.AdminDto.AdminUserResponse;
import duyanh.film.dto.AdminDto.AuditLogResponse;
import duyanh.film.dto.AdminDto.ContentStatusRequest;
import duyanh.film.dto.AdminDto.DailyPoint;
import duyanh.film.dto.AdminDto.HandleReportRequest;
import duyanh.film.dto.AdminDto.ReportResponse;
import duyanh.film.dto.AdminDto.SummaryResponse;
import duyanh.film.dto.AdminDto.TopMovie;
import duyanh.film.dto.AdminDto.UserRolesRequest;
import duyanh.film.dto.AdminDto.UserStatusRequest;
import duyanh.film.dto.response.PageResponse;
import duyanh.film.model.ReportStatus;
import duyanh.film.model.ReportTargetType;
import duyanh.film.model.UserStatus;
import duyanh.film.service.AdminUserService;
import duyanh.film.service.AuditQueryService;
import duyanh.film.service.DashboardService;
import duyanh.film.service.ModerationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Quản trị người dùng, kiểm duyệt, dashboard, audit log.
 * Kiểm duyệt mở cho ROLE_MODERATOR; các phần còn lại chỉ ROLE_ADMIN.
 */
@Tag(name = "Admin")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

	private static final String ADMIN = "hasAuthority('ROLE_ADMIN')";
	private static final String MOD = "hasAnyAuthority('ROLE_ADMIN', 'ROLE_MODERATOR')";

	private final AdminUserService adminUserService;
	private final ModerationService moderationService;
	private final DashboardService dashboardService;
	private final AuditQueryService auditQueryService;

	// ===== User =====

	@PreAuthorize(ADMIN)
	@GetMapping("/users")
	public PageResponse<AdminUserResponse> users(@RequestParam(required = false) String keyword,
			@RequestParam(required = false) UserStatus status, @RequestParam(required = false) String role,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return adminUserService.list(keyword, status, role,
				PageRequest.of(Math.max(page, 0), UserController.clamp(size), Sort.by(Sort.Direction.DESC, "createdAt")));
	}

	@PreAuthorize(ADMIN)
	@PatchMapping("/users/{id}/status")
	public AdminUserResponse userStatus(@PathVariable Long id, @Valid @RequestBody UserStatusRequest req) {
		return adminUserService.setStatus(id, req.status());
	}

	@PreAuthorize(ADMIN)
	@PatchMapping("/users/{id}/role")
	public AdminUserResponse userRoles(@PathVariable Long id, @Valid @RequestBody UserRolesRequest req) {
		return adminUserService.setRoles(id, req.roles());
	}

	// ===== Moderation =====

	@PreAuthorize(MOD)
	@GetMapping("/reports")
	public PageResponse<ReportResponse> reports(@RequestParam(required = false) ReportStatus status,
			@RequestParam(required = false) ReportTargetType targetType,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return moderationService.reports(status, targetType,
				PageRequest.of(Math.max(page, 0), UserController.clamp(size), Sort.by(Sort.Direction.DESC, "createdAt")));
	}

	@PreAuthorize(MOD)
	@PatchMapping("/reports/{id}")
	public ReportResponse handleReport(@PathVariable Long id, @Valid @RequestBody HandleReportRequest req) {
		return moderationService.handle(id, req);
	}

	@PreAuthorize(MOD)
	@PatchMapping("/reviews/{id}/status")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void reviewStatus(@PathVariable Long id, @Valid @RequestBody ContentStatusRequest req) {
		moderationService.setReviewStatus(id, req.status());
	}

	@PreAuthorize(MOD)
	@PatchMapping("/comments/{id}/status")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void commentStatus(@PathVariable Long id, @Valid @RequestBody ContentStatusRequest req) {
		moderationService.setCommentStatus(id, req.status());
	}

	// ===== Dashboard =====

	@PreAuthorize(ADMIN)
	@GetMapping("/dashboard/summary")
	public SummaryResponse summary() {
		return dashboardService.summary();
	}

	@PreAuthorize(ADMIN)
	@GetMapping("/dashboard/top-movies")
	public List<TopMovie> topMovies(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(defaultValue = "10") int limit, @RequestParam(defaultValue = "views") String by) {
		return dashboardService.topMovies(from, to, limit, by);
	}

	@PreAuthorize(ADMIN)
	@GetMapping("/dashboard/views")
	public List<DailyPoint> views(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return dashboardService.views(from, to);
	}

	@PreAuthorize(ADMIN)
	@GetMapping("/dashboard/new-users")
	public List<DailyPoint> newUsers(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return dashboardService.newUsers(from, to);
	}

	@PreAuthorize(ADMIN)
	@GetMapping("/dashboard/reviews")
	public List<DailyPoint> reviews(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return dashboardService.reviews(from, to);
	}

	// ===== Audit Log =====

	@PreAuthorize(ADMIN)
	@GetMapping("/audit-logs")
	public PageResponse<AuditLogResponse> auditLogs(@RequestParam(required = false) String actor,
			@RequestParam(required = false) String action,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return auditQueryService.list(actor, action, from, to,
				PageRequest.of(Math.max(page, 0), UserController.clamp(size), Sort.by(Sort.Direction.DESC, "createdAt")));
	}

	@PreAuthorize(ADMIN)
	@GetMapping("/audit-logs/{id}")
	public AuditLogResponse auditLog(@PathVariable Long id) {
		return auditQueryService.get(id);
	}
}
