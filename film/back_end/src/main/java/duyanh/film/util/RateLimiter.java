package duyanh.film.util;

import java.time.Duration;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import duyanh.film.exception.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Giới hạn số lần gọi theo khóa trong một khoảng thời gian (fixed window) bằng Redis,
 * đúng cả khi chạy nhiều instance. Redis lỗi thì cho qua (fail-open) để không chặn người dùng.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimiter {

	/** INCR và đặt hạn trong cùng một lệnh nguyên tử, tránh khóa không bao giờ hết hạn. */
	private static final RedisScript<Long> INCR_WITH_TTL = new DefaultRedisScript<>("""
			local c = redis.call('INCR', KEYS[1])
			if c == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
			return c
			""", Long.class);

	private final StringRedisTemplate redis;

	public void check(String key, int maxRequests, Duration window) {
		Long count;
		try {
			count = redis.execute(INCR_WITH_TTL, List.of("rl:" + key), String.valueOf(window.toMillis()));
		} catch (Exception e) {
			log.warn("Rate limiter không dùng được Redis, tạm cho qua: {}", e.getMessage());
			return;
		}
		if (count != null && count > maxRequests) {
			throw ApiException.tooManyRequests();
		}
	}
}
