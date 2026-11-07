package duyanh.film.event;

import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import duyanh.film.repository.AuthTokenRepository;
import duyanh.film.repository.MovieRepository;
import duyanh.film.repository.ReviewRepository;
import duyanh.film.service.AdminMovieService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledJobs {

	private final AdminMovieService adminMovieService;
	private final MovieRepository movieRepository;
	private final ReviewRepository reviewRepository;
	private final AuthTokenRepository tokenRepository;

	/** Phim UPCOMING tới ngày phát hành: đổi trạng thái và báo cho người có phim trong watchlist. */
	@Scheduled(cron = "${app.release.cron:0 5 0 * * *}", zone = "UTC")
	public void releaseDueMovies() {
		int count = adminMovieService.releaseDueMovies();
		if (count > 0) {
			log.info("Đã chuyển {} phim sang trạng thái phát hành", count);
		}
	}

	/** Sửa lệch các cột tổng hợp và dọn token hết hạn, chạy 03:00 UTC mỗi ngày. */
	@Scheduled(cron = "0 0 3 * * *", zone = "UTC")
	@Transactional
	public void nightlyMaintenance() {
		movieRepository.recalculateAllStats();
		reviewRepository.recalculateAllCounts();
		int deleted = tokenRepository.deleteExpired(LocalDateTime.now().minusDays(7));
		log.info("Bảo trì hằng đêm xong, đã xóa {} token hết hạn", deleted);
	}
}
