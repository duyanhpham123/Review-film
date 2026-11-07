package duyanh.film.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import duyanh.film.model.AuditLog;
import duyanh.film.repository.AuditLogRepository;
import duyanh.film.security.SecurityUtils;
import duyanh.film.util.RequestUtil;
import lombok.RequiredArgsConstructor;

/**
 * Ghi audit log trong cùng transaction với thao tác của admin/moderator:
 * thao tác lỗi thì rollback luôn log, thao tác thành công thì chắc chắn có log.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

	private final AuditLogRepository auditLogRepository;
	private final ObjectMapper objectMapper;

	@Transactional(propagation = Propagation.MANDATORY)
	public void record(String action, String targetType, Long targetId, Object before, Object after) {
		AuditLog log = new AuditLog();
		log.setActorId(SecurityUtils.currentUserIdOrNull());
		log.setAction(action);
		log.setTargetType(targetType);
		log.setTargetId(targetId);
		log.setBeforeData(toJson(before));
		log.setAfterData(toJson(after));
		log.setIpAddress(RequestUtil.clientIp());
		auditLogRepository.save(log);
	}

	private String toJson(Object value) {
		if (value == null) {
			return null;
		}
		try {
			return objectMapper.writeValueAsString(value);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Không ghi được audit log", e);
		}
	}
}
