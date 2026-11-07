package duyanh.film.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import duyanh.film.model.OutboxEvent;

/**
 * Exchange / queue cho event nghiệp vụ. Ứng dụng tự khai báo khi kết nối broker.
 *
 * <pre>
 * outbox ──► film.events (topic) ──[movie.released | comment.created | review.liked]──► film.notification
 *                                                                                         │ lỗi sau 3 lần thử
 *                                                     film.events.dlx (fanout) ◄─────────┘
 *                                                            └──► film.notification.dlq
 * </pre>
 */
@Configuration
public class RabbitConfig {

	public static final String EVENTS_EXCHANGE = "film.events";
	public static final String DEAD_LETTER_EXCHANGE = "film.events.dlx";
	public static final String NOTIFICATION_QUEUE = "film.notification";
	public static final String NOTIFICATION_DLQ = "film.notification.dlq";

	@Bean
	public Declarables eventTopology() {
		TopicExchange events = new TopicExchange(EVENTS_EXCHANGE, true, false);
		FanoutExchange deadLetter = new FanoutExchange(DEAD_LETTER_EXCHANGE, true, false);
		Queue notification = QueueBuilder.durable(NOTIFICATION_QUEUE)
				.deadLetterExchange(DEAD_LETTER_EXCHANGE)
				.build();
		Queue dlq = QueueBuilder.durable(NOTIFICATION_DLQ).build();

		Binding released = BindingBuilder.bind(notification).to(events).with(OutboxEvent.MOVIE_RELEASED);
		Binding commented = BindingBuilder.bind(notification).to(events).with(OutboxEvent.COMMENT_CREATED);
		Binding liked = BindingBuilder.bind(notification).to(events).with(OutboxEvent.REVIEW_LIKED);
		Binding dead = BindingBuilder.bind(dlq).to(deadLetter);

		return new Declarables(events, deadLetter, notification, dlq, released, commented, liked, dead);
	}
}
