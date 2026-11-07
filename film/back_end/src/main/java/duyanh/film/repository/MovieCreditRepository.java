package duyanh.film.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import duyanh.film.model.MovieCredit;

public interface MovieCreditRepository extends JpaRepository<MovieCredit, Long> {

	@Query("""
			select c from MovieCredit c join fetch c.person
			where c.movie.id = :movieId
			order by c.role, c.displayOrder, c.id
			""")
	List<MovieCredit> findByMovieId(@Param("movieId") Long movieId);

	@Query("""
			select c from MovieCredit c join fetch c.movie m
			where c.person.id = :personId and m.deletedAt is null and m.hidden = false
			order by m.releaseDate desc, m.id desc
			""")
	List<MovieCredit> findPublicByPersonId(@Param("personId") Long personId);

	boolean existsByPerson_Id(Long personId);

	@Modifying
	@Query("delete from MovieCredit c where c.movie.id = :movieId")
	void deleteByMovieId(@Param("movieId") Long movieId);
}
