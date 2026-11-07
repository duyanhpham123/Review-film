package duyanh.film.dto.response;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/** Kết quả phân trang. page bắt đầu từ 0. */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

	public static <T> PageResponse<T> of(Page<T> page) {
		return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
				page.getTotalPages());
	}

	public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
		return new PageResponse<>(page.getContent().stream().map(mapper).toList(), page.getNumber(), page.getSize(),
				page.getTotalElements(), page.getTotalPages());
	}

	public static <T> PageResponse<T> of(List<T> content, int page, int size, long total) {
		int totalPages = size == 0 ? 0 : (int) Math.ceil((double) total / size);
		return new PageResponse<>(content, page, size, total, totalPages);
	}
}
