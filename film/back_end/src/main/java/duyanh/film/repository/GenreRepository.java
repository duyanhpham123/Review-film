package duyanh.film.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import duyanh.film.model.Genre;

public interface GenreRepository extends JpaRepository<Genre, Long> {

	List<Genre> findAllByOrderByNameAsc();

	Optional<Genre> findBySlug(String slug);

	boolean existsBySlug(String slug);

	boolean existsByName(String name);

	boolean existsByNameAndIdNot(String name, Long id);

	@Query(value = "SELECT COUNT(*) FROM movie_genres WHERE genre_id = :id", nativeQuery = true)
	long countMovies(@Param("id") Long id);
}
