package duyanh.film.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import duyanh.film.model.User;
import duyanh.film.model.UserStatus;

public interface UserRepository extends JpaRepository<User, Long> {

	boolean existsByUsername(String username);

	boolean existsByEmail(String email);

	Optional<User> findByUsernameAndDeletedAtIsNull(String username);

	Optional<User> findByEmailAndDeletedAtIsNull(String email);

	Optional<User> findByIdAndDeletedAtIsNull(Long id);

	@Query("""
			select u from User u
			where u.deletedAt is null and (u.username = :login or u.email = :login)
			""")
	Optional<User> findByLogin(@Param("login") String login);

	@Query(value = """
			select distinct u from User u left join u.roles r
			where u.deletedAt is null
			  and (:keyword is null or u.username like concat('%', :keyword, '%')
			       or u.email like concat('%', :keyword, '%') or u.fullName like concat('%', :keyword, '%'))
			  and (:status is null or u.status = :status)
			  and (:role is null or r.name = :role)
			""", countQuery = """
			select count(distinct u) from User u left join u.roles r
			where u.deletedAt is null
			  and (:keyword is null or u.username like concat('%', :keyword, '%')
			       or u.email like concat('%', :keyword, '%') or u.fullName like concat('%', :keyword, '%'))
			  and (:status is null or u.status = :status)
			  and (:role is null or r.name = :role)
			""")
	Page<User> search(@Param("keyword") String keyword, @Param("status") UserStatus status,
			@Param("role") String role, Pageable pageable);
}
