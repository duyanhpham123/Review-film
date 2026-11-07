package duyanh.film.event;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.AmqpIOException;
import org.springframework.amqp.AmqpResourceNotAvailableException;
import org.springframework.amqp.AmqpTimeoutException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import duyanh.film.config.RabbitConfig;
import duyanh.film.model.OutboxEvent;
import duyanh.film.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Đọc outbox_events chưa gửi và đẩy lên RabbitMQ (exchange film.events, routing key = event_type).
 * <ul>
 * <li>Khóa các dòng bằng FOR UPDATE SKIP LOCKED: chạy nhiều instance cũng không gửi trùng một lúc</li>
 * <li>Chờ broker xác nhận (publisher confirm) rồi mới đánh dấu sent_at</li>
 * <li>Nếu gửi xong mà chưa kịp đánh dấu (sập giữa chừng) thì event được gửi lại;
 * consumer xử lý trùng không sao nhờ unique dedupe_key</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

	private static final int MAX_ATTEMPTS = 10;
	private static final int BATCH_SIZE = 100;
	private static final long CONFIRM_TIMEOUT_MS = 5000;

	private final OutboxEventRepository outboxRepository;
	private final RabbitTemplate rabbitTemplate;
	private final TransactionTemplate transactionTemplate;

	@Scheduled(fixedDelayString = "${app.outbox.fixed-delay-ms:5000}", initialDelay = 5000)
	public void publishPending() {
		transactionTemplate.executeWithoutResult(status -> {
			List<OutboxEvent> events = outboxRepository.lockPending(MAX_ATTEMPTS, BATCH_SIZE);
			for (OutboxEvent event : events) {
				try {
					publish(event);
					event.setSentAt(LocalDateTime.now());
				} catch (AmqpConnectException | AmqpIOException | AmqpTimeoutException
						| AmqpResourceNotAvailableException e) {
					// Broker tắt / đang khởi động / mất kết nối: lỗi của broker chứ không phải của event.
					// Dừng lượt này và không tính là lần thử thất bại, để event không bị bỏ rơi khi broker sập lâu.
					log.warn("RabbitMQ chưa sẵn sàng ({}), event chờ gửi lại sau", e.getClass().getSimpleName());
					return;
				} catch (Exception e) {
					event.setAttempts(event.getAttempts() + 1);
					String msg = String.valueOf(e.getMessage());
					event.setLastError(msg.length() > 1000 ? msg.substring(0, 1000) : msg);
					log.warn("Gửi outbox event {} ({}) thất bại lần {}: {}", event.getId(), event.getEventType(),
							event.getAttempts(), msg);
				}
			}
		});
	}

	private void publish(OutboxEvent event) {
		Message message = MessageBuilder.withBody(event.getPayload().getBytes(StandardCharsets.UTF_8))
				.setContentType(MessageProperties.CONTENT_TYPE_JSON)
				.setContentEncoding(StandardCharsets.UTF_8.name())
				.setDeliveryMode(MessageDeliveryMode.PERSISTENT)
				.setMessageId("outbox-" + event.getId())
				.setHeader("aggregateType", event.getAggregateType())
				.setHeader("aggregateId", event.getAggregateId())
				.build();
		rabbitTemplate.invoke(ops -> {
			ops.send(RabbitConfig.EVENTS_EXCHANGE, event.getEventType(), message);
			ops.waitForConfirmsOrDie(CONFIRM_TIMEOUT_MS);
			return null;
		});
	}
}
