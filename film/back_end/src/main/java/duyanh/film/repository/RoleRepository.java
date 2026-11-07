package duyanh.film.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import duyanh.film.model.Role;

public interface RoleRepository extends JpaRepository<Role, Long> {

	Optional<Role> findByName(String name);
}
