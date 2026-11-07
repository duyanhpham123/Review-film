package duyanh.film.service;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

import duyanh.film.dto.response.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Cache JSON trên Redis. Mỗi lần đọc chỉ rõ kiểu dữ liệu nên đọc lại được record/DTO chính xác.
 * Redis lỗi thì bỏ qua cache và đọc thẳng DB.
 * Danh sách phim dùng "phiên bản": tăng số phiên bản là mọi trang danh sách cũ hết hiệu lực cùng lúc.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheService {

	public static final String GENRES = "cache:genres";
	public static final String COUNTRIES = "cache:countries";
	private static final String MOVIE_DETAIL = "cache:movie:";
	private static final String MOVIE_LIST_VERSION = "cache:movies:ver";

	private final StringRedisTemplate redis;
	private final ObjectMapper objectMapper;

	public <T> T getOrLoad(String key, Duration ttl, JavaType type, Supplier<T> loader) {
		try {
			String cached = redis.opsForValue().get(key);
			if (cached != null) {
				return objectMapper.readValue(cached, type);
			}
		} catch (Exception e) {
			log.warn("Đọc cache {} lỗi: {}", key, e.getMessage());
		}
		T value = loader.get();
		if (value != null) {
			try {
				redis.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
			} catch (Exception e) {
				log.warn("Ghi cache {} lỗi: {}", key, e.getMessage());
			}
		}
		return value;
	}

	public JavaType type(Class<?> cls) {
		return objectMapper.getTypeFactory().constructType(cls);
	}

	public JavaType listOf(Class<?> cls) {
		return objectMapper.getTypeFactory().constructCollectionType(List.class, cls);
	}

	public JavaType pageOf(Class<?> cls) {
		return objectMapper.getTypeFactory().constructParametricType(PageResponse.class, cls);
	}

	// ===== Khóa cache phim =====

	public static String movieDetailKey(String slug) {
		return MOVIE_DETAIL + slug;
	}

	/** Khóa cho một trang danh sách phim, gắn với phiên bản hiện tại. */
	public String movieListKey(String params) {
		String version;
		try {
			version = redis.opsForValue().get(MOVIE_LIST_VERSION);
		} catch (Exception e) {
			version = null;
		}
		return "cache:movies:v" + (version == null ? "0" : version) + ":" + params;
	}

	// ===== Xóa cache =====

	/**
	 * Xóa sau khi transaction commit: nếu xóa trước, một request khác có thể kịp đọc dữ liệu cũ
	 * từ DB và ghi lại vào cache.
	 */
	public void evictAfterCommit(String... keys) {
		afterCommit(() -> redis.delete(List.of(keys)));
	}

	public void evictMovieAfterCommit(String... slugs) {
		evictAfterCommit(java.util.Arrays.stream(slugs).filter(java.util.Objects::nonNull)
				.map(CacheService::movieDetailKey).toArray(String[]::new));
	}

	/** Làm mọi trang danh sách phim đang cache hết hiệu lực (khóa cũ tự hết hạn theo TTL). */
	public void invalidateMovieListsAfterCommit() {
		afterCommit(() -> redis.opsForValue().increment(MOVIE_LIST_VERSION));
	}

	private void afterCommit(Runnable action) {
		Runnable safe = () -> {
			try {
				action.run();
			} catch (Exception e) {
				log.warn("Xóa cache lỗi: {}", e.getMessage());
			}
		};
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					safe.run();
				}
			});
		} else {
			safe.run();
		}
	}
}
