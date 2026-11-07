package duyanh.film.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import duyanh.film.model.Person;

public interface PersonRepository extends JpaRepository<Person, Long> {

	Optional<Person> findBySlug(String slug);

	boolean existsBySlug(String slug);

	boolean existsByTmdbId(Integer tmdbId);

	Optional<Person> findByTmdbId(Integer tmdbId);

	boolean existsByTmdbIdAndIdNot(Integer tmdbId, Long id);

	Page<Person> findByNameContaining(String keyword, Pageable pageable);
}
