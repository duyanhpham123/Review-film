package duyanh.film.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import duyanh.film.model.Comment;

public interface CommentRepository extends JpaRepository<Comment, Long> {

	@Query("select c from Comment c join fetch c.user where c.id = :id")
	Optional<Comment> findWithUser(@Param("id") Long id);

	@Query(value = """
			select c from Comment c join fetch c.user
			where c.movieId = :movieId and c.reviewId is null and c.parentId is null
			""", countQuery = """
			select count(c) from Comment c
			where c.movieId = :movieId and c.reviewId is null and c.parentId is null
			""")
	Page<Comment> findMovieRoots(@Param("movieId") Long movieId, Pageable pageable);

	@Query(value = "select c from Comment c join fetch c.user where c.reviewId = :reviewId and c.parentId is null",
			countQuery = "select count(c) from Comment c where c.reviewId = :reviewId and c.parentId is null")
	Page<Comment> findReviewRoots(@Param("reviewId") Long reviewId, Pageable pageable);

	@Query("select c from Comment c join fetch c.user where c.parentId in :parentIds order by c.createdAt, c.id")
	List<Comment> findReplies(@Param("parentIds") Collection<Long> parentIds);
}
