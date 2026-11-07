package duyanh.film.event;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import duyanh.film.config.AppProperties;
import duyanh.film.service.StorageService;
import duyanh.film.service.StorageService.StoredObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Dọn ảnh mồ côi: file trên MinIO / S3 mà không bảng nào trỏ tới, do upload xong nhưng transaction lỗi,
 * xóa ảnh cũ thất bại, v.v. Chỉ xóa file cũ hơn orphan-min-age để không đụng ảnh vừa upload
 * mà transaction chưa kịp commit.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StorageCleanupJob {

	/** Thư mục ảnh và câu SQL lấy các key đang được dùng. Phim đã xóa mềm vẫn giữ ảnh. */
	private static final Map<String, String> REFERENCES = Map.of(
			"posters", "SELECT poster_url FROM movies WHERE poster_url LIKE 'posters/%'",
			"banners", "SELECT banner_url FROM movies WHERE banner_url LIKE 'banners/%'",
			"avatars", "SELECT avatar_url FROM users WHERE avatar_url LIKE 'avatars/%'",
			"people", "SELECT avatar_url FROM people WHERE avatar_url LIKE 'people/%'");

	private final StorageService storage;
	private final JdbcTemplate jdbc;
	private final AppProperties props;

	@Scheduled(cron = "${app.storage.cleanup-cron:0 0 4 * * *}", zone = "UTC")
	public void deleteOrphans() {
		Instant cutoff = Instant.now().minus(props.storage().orphanMinAge());
		int deleted = 0;
		for (Map.Entry<String, String> entry : REFERENCES.entrySet()) {
			try {
				List<StoredObject> objects = storage.listObjects(entry.getKey());
				if (objects.isEmpty()) {
					continue;
				}
				Set<String> used = new HashSet<>(jdbc.queryForList(entry.getValue(), String.class));
				for (StoredObject o : objects) {
					if (o.lastModified().isBefore(cutoff) && !used.contains(o.key())) {
						storage.delete(o.key());
						deleted++;
					}
				}
			} catch (Exception e) {
				log.warn("Dọn ảnh thư mục {} lỗi: {}", entry.getKey(), e.getMessage());
			}
		}
		if (deleted > 0) {
			log.info("Đã xóa {} ảnh mồ côi", deleted);
		}
	}
}
