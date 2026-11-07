package duyanh.film.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import duyanh.film.dto.AdminDto.AuditLogResponse;
import duyanh.film.dto.response.PageResponse;
import duyanh.film.exception.ApiException;
import duyanh.film.model.AuditLog;
import duyanh.film.model.User;
import duyanh.film.repository.AuditLogRepository;
import duyanh.film.repository.UserRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuditQueryService {

	private final AuditLogRepository auditLogRepository;
	private final UserRepository userRepository;

	/** actor: username của người thực hiện. */
	@Transactional(readOnly = true)
	public PageResponse<AuditLogResponse> list(String actor, String action, LocalDate from, LocalDate to,
			Pageable pageable) {
		Long actorId = null;
		if (actor != null && !actor.isBlank()) {
			actorId = userRepository.findByUsernameAndDeletedAtIsNull(actor.trim()).map(User::getId).orElse(-1L);
		}
		Page<AuditLog> page = auditLogRepository.search(actorId, blankToNull(action),
				from == null ? null : from.atStartOfDay(), to == null ? null : to.plusDays(1).atStartOfDay(), pageable);
		List<Long> ids = page.getContent().stream().map(AuditLog::getActorId).filter(java.util.Objects::nonNull)
				.distinct().toList();
		Map<Long, User> users = userRepository.findAllById(ids).stream()
				.collect(Collectors.toMap(User::getId, Function.identity()));
		return PageResponse.of(page, a -> toResponse(a, users.get(a.getActorId())));
	}

	@Transactional(readOnly = true)
	public AuditLogResponse get(Long id) {
		AuditLog log = auditLogRepository.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy log"));
		User actor = log.getActorId() == null ? null : userRepository.findById(log.getActorId()).orElse(null);
		return toResponse(log, actor);
	}

	private static AuditLogResponse toResponse(AuditLog a, User actor) {
		return new AuditLogResponse(a.getId(), a.getActorId(), actor == null ? null : actor.getUsername(), a.getAction(),
				a.getTargetType(), a.getTargetId(), a.getBeforeData(), a.getAfterData(), a.getIpAddress(),
				a.getCreatedAt());
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}
}
