package duyanh.film.service;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import duyanh.film.model.OutboxEvent;
import duyanh.film.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;

/** Ghi event vào outbox_events trong cùng transaction với dữ liệu nghiệp vụ. */
@Service
@RequiredArgsConstructor
public class OutboxService {

	private final OutboxEventRepository outboxEventRepository;
	private final ObjectMapper objectMapper;

	@Transactional(propagation = Propagation.MANDATORY)
	public void publish(String eventType, String aggregateType, Long aggregateId, Map<String, Object> payload) {
		OutboxEvent event = new OutboxEvent();
		event.setEventType(eventType);
		event.setAggregateType(aggregateType);
		event.setAggregateId(aggregateId);
		try {
			event.setPayload(objectMapper.writeValueAsString(payload));
		} catch (JsonProcessingException e) {
			throw new IllegalStateException(e);
		}
		outboxEventRepository.save(event);
	}
}
