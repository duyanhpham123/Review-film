package duyanh.film.service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import duyanh.film.exception.ApiException;
import duyanh.film.repository.MovieRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Đếm lượt xem trang phim bằng Redis:
 * <ul>
 * <li>SET NX theo (phim + user/IP), hạn 1 giờ: mỗi người tối đa 1 lượt / phim / giờ</li>
 * <li>HINCRBY views:pending:{ngày}: cộng dồn chờ ghi DB</li>
 * <li>ZINCRBY trending:{ngày}: bảng xếp hạng theo ngày, giữ 35 ngày</li>
 * </ul>
 * Job định kỳ gom lượt xem ghi xuống movies.view_count và movie_view_daily, nên view_count
 * trên trang phim trễ tối đa một chu kỳ job (mặc định 5 phút).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ViewCounterService {

	private static final Duration DEDUPE_WINDOW = Duration.ofHours(1);
	private static final Duration TRENDING_KEEP = Duration.ofDays(35);
	private static final Duration AGGREGATE_TTL = Duration.ofMinutes(1);
	private static final String PENDING_DATES = "views:pending:dates";

	private final MovieRepository movieRepository;
	private final StringRedisTemplate redis;
	private final TransactionTemplate transactionTemplate;

	/** Trả về true nếu lượt xem được tính. */
	public boolean record(Long movieId, Long userId, String ip) {
		movieRepository.findByIdAndDeletedAtIsNull(movieId)
				.filter(m -> !m.isHidden())
				.orElseThrow(() -> ApiException.notFound("Không tìm thấy phim"));
		String viewer = userId != null ? "u" + userId : "ip" + ip;
		String today = LocalDate.now().toString();
		try {
			Boolean first = redis.opsForValue().setIfAbsent("views:seen:" + movieId + ":" + viewer, "1", DEDUPE_WINDOW);
			if (!Boolean.TRUE.equals(first)) {
				return false;
			}
			redis.opsForHash().increment(pendingKey(today), movieId.toString(), 1);
			redis.opsForSet().add(PENDING_DATES, today);
			String trendingKey = trendingKey(today);
			redis.opsForZSet().incrementScore(trendingKey, movieId.toString(), 1);
			redis.expire(trendingKey, TRENDING_KEEP);
			return true;
		} catch (Exception e) {
			log.warn("Không ghi được lượt xem vào Redis: {}", e.getMessage());
			return false;
		}
	}

	/**
	 * Id phim nhiều lượt xem nhất trong N ngày gần nhất (gồm hôm nay), theo thứ tự giảm dần.
	 * Trả về null nếu Redis lỗi để nơi gọi đọc từ DB.
	 */
	public List<Long> trendingIds(int days, int limit) {
		try {
			LocalDate today = LocalDate.now();
			String key;
			if (days <= 1) {
				key = trendingKey(today.toString());
			} else {
				key = "trending:agg:" + days + ":" + today;
				if (!Boolean.TRUE.equals(redis.hasKey(key))) {
					List<String> others = new ArrayList<>();
					for (int i = 1; i < days; i++) {
						others.add(trendingKey(today.minusDays(i).toString()));
					}
					redis.opsForZSet().unionAndStore(trendingKey(today.toString()), others, key);
					redis.expire(key, AGGREGATE_TTL);
				}
			}
			Set<TypedTuple<String>> top = redis.opsForZSet().reverseRangeWithScores(key, 0, limit - 1L);
			List<Long> ids = new ArrayList<>();
			if (top != null) {
				top.forEach(t -> ids.add(Long.valueOf(t.getValue())));
			}
			return ids;
		} catch (Exception e) {
			log.warn("Không đọc được trending từ Redis: {}", e.getMessage());
			return null;
		}
	}

	/** Gom lượt xem từ Redis ghi xuống DB. */
	@Scheduled(fixedDelayString = "${app.views.flush-delay-ms:300000}", initialDelay = 10_000)
	public void flushToDatabase() {
		Set<String> dates;
		try {
			dates = redis.opsForSet().members(PENDING_DATES);
		} catch (Exception e) {
			log.warn("Bỏ qua lượt gom view vì Redis lỗi: {}", e.getMessage());
			return;
		}
		if (dates == null) {
			return;
		}
		String today = LocalDate.now().toString();
		for (String date : dates) {
			flushDate(date);
			if (!date.equals(today) && !Boolean.TRUE.equals(redis.hasKey(pendingKey(date)))) {
				redis.opsForSet().remove(PENDING_DATES, date);
			}
		}
	}

	private void flushDate(String date) {
		String key = pendingKey(date);
		if (!Boolean.TRUE.equals(redis.hasKey(key))) {
			return;
		}
		// Đổi tên khóa (nguyên tử): lượt xem mới sẽ vào khóa mới, còn ta xử lý bản đã tách ra
		String working = "views:flushing:" + date + ":" + UUID.randomUUID();
		try {
			redis.rename(key, working);
		} catch (Exception e) {
			return;
		}
		Map<Object, Object> counts = redis.opsForHash().entries(working);
		try {
			LocalDate day = LocalDate.parse(date);
			transactionTemplate.executeWithoutResult(status -> counts.forEach((movieId, count) -> {
				long id = Long.parseLong(movieId.toString());
				long n = Long.parseLong(count.toString());
				movieRepository.incrementViews(id, n);
				movieRepository.addDailyViews(id, day, n);
			}));
			redis.delete(working);
		} catch (Exception e) {
			log.warn("Ghi lượt xem ngày {} xuống DB lỗi, trả lại Redis để lần sau thử lại: {}", date, e.getMessage());
			counts.forEach((movieId, count) -> redis.opsForHash().increment(key, movieId, Long.parseLong(count.toString())));
			redis.delete(working);
		}
	}

	private static String pendingKey(String date) {
		return "views:pending:" + date;
	}

	private static String trendingKey(String date) {
		return "trending:" + date;
	}
}
