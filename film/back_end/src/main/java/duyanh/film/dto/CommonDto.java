package duyanh.film.dto;

import java.time.LocalDate;

/** DTO tham chiếu ngắn gọn, dùng lồng trong các response khác. */
public final class CommonDto {

	private CommonDto() {
	}

	public record UserRef(Long id, String username, String fullName, String avatarUrl) {
	}

	public record MovieRef(Long id, String slug, String title, String posterUrl, LocalDate releaseDate) {
	}

	public record CountResponse(long count) {
	}
}
