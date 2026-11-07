package duyanh.film.event;

import java.io.IOException;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import duyanh.film.config.RabbitConfig;
import duyanh.film.model.OutboxEvent;
import duyanh.film.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Consumer của module notification: nhận event từ RabbitMQ và tạo thông báo theo lô.
 * Xử lý lỗi thì Spring thử lại 3 lần (cấu hình spring.rabbitmq.listener.simple.retry),
 * vẫn lỗi thì message vào film.notification.dlq để xem lại bằng tay.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

	private final NotificationService notificationService;
	private final ObjectMapper objectMapper;

	@RabbitListener(queues = RabbitConfig.NOTIFICATION_QUEUE)
	public void onEvent(Message message) {
		String type = message.getMessageProperties().getReceivedRoutingKey();
		JsonNode payload;
		try {
			payload = objectMapper.readTree(message.getBody());
		} catch (IOException e) {
			// Payload hỏng thì thử lại cũng vô ích: đưa thẳng vào DLQ
			throw new AmqpRejectAndDontRequeueException("Payload không hợp lệ: " + message.getMessageProperties().getMessageId(), e);
		}
		switch (type) {
			case OutboxEvent.MOVIE_RELEASED -> notificationService.onMovieReleased(payload.get("movieId").asLong());
			case OutboxEvent.COMMENT_CREATED -> notificationService.onCommentCreated(payload.get("commentId").asLong());
			case OutboxEvent.REVIEW_LIKED -> notificationService.onReviewLiked(payload.get("reviewId").asLong(),
					payload.get("userId").asLong());
			default -> log.warn("Bỏ qua event không xác định: {}", type);
		}
	}
}
