package duyanh.film.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import duyanh.film.model.UserMovieId;
import duyanh.film.model.WatchStatus;
import duyanh.film.model.WatchlistItem;

public interface WatchlistRepository extends JpaRepository<WatchlistItem, UserMovieId> {

	long countByUserIdAndStatus(Long userId, WatchStatus status);

	/** Mỗi phần tử: [WatchlistItem, Movie]. */
	@Query(value = """
			select w, m from WatchlistItem w join Movie m on m.id = w.movieId
			where w.userId = :userId and (:status is null or w.status = :status)
			  and m.deletedAt is null and m.hidden = false
			order by w.updatedAt desc
			""", countQuery = """
			select count(w) from WatchlistItem w join Movie m on m.id = w.movieId
			where w.userId = :userId and (:status is null or w.status = :status)
			  and m.deletedAt is null and m.hidden = false
			""")
	Page<Object[]> findItems(@Param("userId") Long userId, @Param("status") WatchStatus status, Pageable pageable);
}
