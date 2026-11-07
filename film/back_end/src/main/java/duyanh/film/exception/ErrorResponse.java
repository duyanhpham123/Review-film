package duyanh.film.exception;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Định dạng lỗi chung cho mọi API. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(
		int status,
		String code,
		String message,
		String path,
		Instant timestamp,
		List<FieldError> errors) {

	public record FieldError(String field, String message) {
	}
}
