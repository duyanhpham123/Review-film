package duyanh.film.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

import duyanh.film.exception.ApiException;

/** Kiểm tra ảnh upload: dung lượng, định dạng thật (không tin đuôi file hay Content-Type). */
public final class ImageFiles {

	public static final long MAX_BYTES = 5L * 1024 * 1024;
	/** Các thư mục ảnh và bảng/cột tham chiếu tới chúng (dùng cho job dọn ảnh mồ côi). */
	public static final Set<String> FOLDERS = Set.of("posters", "banners", "avatars", "people");

	public record ImageInfo(String extension, String contentType) {
	}

	private ImageFiles() {
	}

	public static ImageInfo inspect(MultipartFile file, String folder) {
		if (!FOLDERS.contains(folder)) {
			throw new IllegalArgumentException("Thư mục không hợp lệ: " + folder);
		}
		if (file == null || file.isEmpty()) {
			throw ApiException.badRequest("FILE_EMPTY", "Chưa chọn file");
		}
		if (file.getSize() > MAX_BYTES) {
			throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "Ảnh tối đa 5MB");
		}
		byte[] h = new byte[12];
		int n;
		try (InputStream in = file.getInputStream()) {
			n = in.readNBytes(h, 0, h.length);
		} catch (IOException e) {
			throw ApiException.badRequest("FILE_INVALID", "Không đọc được file");
		}
		if (n >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) {
			return new ImageInfo("jpg", "image/jpeg");
		}
		if (n >= 8 && (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G') {
			return new ImageInfo("png", "image/png");
		}
		if (n >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
				&& h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P') {
			return new ImageInfo("webp", "image/webp");
		}
		throw ApiException.badRequest("FILE_INVALID", "Chỉ chấp nhận ảnh JPG, PNG hoặc WEBP");
	}

	public static boolean isAbsoluteUrl(String value) {
		return value.startsWith("http://") || value.startsWith("https://");
	}
}
