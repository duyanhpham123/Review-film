package duyanh.film.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import duyanh.film.model.Country;

public interface CountryRepository extends JpaRepository<Country, Long> {

	List<Country> findAllByOrderByNameAsc();

	java.util.Optional<Country> findByCode(String code);

	boolean existsByName(String name);
}
