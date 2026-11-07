package duyanh.film.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import duyanh.film.config.AppProperties;
import duyanh.film.util.ImageFiles;
import lombok.extern.slf4j.Slf4j;

/** Lưu ảnh vào thư mục local, phục vụ qua /files/** (xem WebConfig). Bật bằng app.storage.type=local. */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local")
public class LocalStorageService implements StorageService {

	private final Path root;
	private final String publicBaseUrl;

	public LocalStorageService(AppProperties props) {
		this.root = Path.of(props.storage().localDir()).toAbsolutePath().normalize();
		String base = props.storage().publicBaseUrl();
		this.publicBaseUrl = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
	}

	@Override
	public String storeImage(MultipartFile file, String folder) {
		ImageFiles.ImageInfo info = ImageFiles.inspect(file, folder);
		String key = folder + "/" + UUID.randomUUID() + "." + info.extension();
		Path target = root.resolve(key).normalize();
		try (InputStream in = file.getInputStream()) {
			Files.createDirectories(target.getParent());
			Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			throw new IllegalStateException("Không lưu được file", e);
		}
		return key;
	}

	@Override
	public void delete(String keyOrUrl) {
		if (keyOrUrl == null || keyOrUrl.isBlank() || ImageFiles.isAbsoluteUrl(keyOrUrl)) {
			return;
		}
		Path target = root.resolve(keyOrUrl).normalize();
		if (!target.startsWith(root)) {
			return;
		}
		try {
			Files.deleteIfExists(target);
		} catch (IOException e) {
			log.warn("Không xóa được file {}", keyOrUrl, e);
		}
	}

	@Override
	public String publicUrl(String keyOrUrl) {
		if (keyOrUrl == null || keyOrUrl.isBlank()) {
			return null;
		}
		return ImageFiles.isAbsoluteUrl(keyOrUrl) ? keyOrUrl : publicBaseUrl + "/" + keyOrUrl;
	}

	@Override
	public List<StoredObject> listObjects(String folder) {
		Path dir = root.resolve(folder);
		if (!Files.isDirectory(dir)) {
			return List.of();
		}
		try (Stream<Path> files = Files.list(dir)) {
			return files.filter(Files::isRegularFile).map(p -> {
				try {
					return new StoredObject(folder + "/" + p.getFileName(), Files.getLastModifiedTime(p).toInstant());
				} catch (IOException e) {
					return null;
				}
			}).filter(java.util.Objects::nonNull).toList();
		} catch (IOException e) {
			log.warn("Không đọc được thư mục {}", dir, e);
			return List.of();
		}
	}
}
