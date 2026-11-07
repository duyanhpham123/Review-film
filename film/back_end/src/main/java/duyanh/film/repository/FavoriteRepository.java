package duyanh.film.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import duyanh.film.model.Favorite;
import duyanh.film.model.Movie;
import duyanh.film.model.UserMovieId;

public interface FavoriteRepository extends JpaRepository<Favorite, UserMovieId> {

	@Query(value = """
			select m from Favorite f join Movie m on m.id = f.movieId
			where f.userId = :userId and m.deletedAt is null and m.hidden = false
			order by f.createdAt desc
			""", countQuery = """
			select count(f) from Favorite f join Movie m on m.id = f.movieId
			where f.userId = :userId and m.deletedAt is null and m.hidden = false
			""")
	Page<Movie> findMovies(@Param("userId") Long userId, Pageable pageable);
}
