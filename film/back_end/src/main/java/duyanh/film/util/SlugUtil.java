package duyanh.film.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;

/** Sinh slug không dấu cho đường dẫn, ví dụ "Bố Già" thành "bo-gia". */
public final class SlugUtil {

	private SlugUtil() {
	}

	public static String toSlug(String input) {
		if (input == null) {
			return "";
		}
		String s = input.trim().replace('đ', 'd').replace('Đ', 'D');
		s = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
		s = s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
		return s.length() > 250 ? s.substring(0, 250).replaceAll("-+$", "") : s;
	}

	/** Thêm hậu tố -2, -3... cho tới khi slug chưa bị dùng. */
	public static String unique(String base, Predicate<String> exists) {
		String root = base == null || base.isBlank() ? "item" : base;
		String candidate = root;
		int i = 2;
		while (exists.test(candidate)) {
			candidate = root + "-" + i++;
		}
		return candidate;
	}
}
