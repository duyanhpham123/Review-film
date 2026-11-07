package duyanh.film.model;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Khóa chính (user_id, movie_id) dùng cho favorites và watchlist. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserMovieId implements Serializable {

	private Long userId;
	private Long movieId;
}
