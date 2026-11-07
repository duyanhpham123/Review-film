package duyanh.film.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import duyanh.film.model.Episode;

public interface EpisodeRepository extends JpaRepository<Episode, Long> {

	List<Episode> findByMovieIdOrderBySeasonNumberAscEpisodeNumberAsc(Long movieId);

	boolean existsByMovieIdAndSeasonNumberAndEpisodeNumber(Long movieId, int seasonNumber, int episodeNumber);

	boolean existsByMovieIdAndSeasonNumberAndEpisodeNumberAndIdNot(Long movieId, int seasonNumber,
			int episodeNumber, Long id);
}
