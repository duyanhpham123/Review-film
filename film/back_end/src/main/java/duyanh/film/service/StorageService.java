package duyanh.film.service;

import java.time.Instant;
import java.util.List;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * Lưu ảnh. DB chỉ giữ object key (ví dụ "posters/abc.jpg"), URL được ghép khi trả về
 * để đổi nơi lưu hoặc CDN mà không phải sửa dữ liệu.
 * Chọn bản cài đặt bằng app.storage.type: s3 (MinIO / S3, mặc định) hoặc local.
 */
public interface StorageService {

	record StoredObject(String key, Instant lastModified) {
	}

	/** Kiểm tra định dạng thật của ảnh, lưu với tên tự sinh, trả về object key. */
	String storeImage(MultipartFile file, String folder);

	/** Xóa file nếu là object key do hệ thống lưu; bỏ qua URL ngoài như ảnh TMDB. */
	void delete(String keyOrUrl);

	/** Ghép URL công khai từ object key; URL đầy đủ thì trả nguyên. */
	String publicUrl(String keyOrUrl);

	/** Mọi file trong một thư mục, dùng cho job dọn ảnh mồ côi. */
	List<StoredObject> listObjects(String folder);

	/** Xóa ảnh cũ chỉ khi transaction commit: rollback thì DB vẫn trỏ tới ảnh cũ nên phải giữ lại. */
	default void deleteAfterCommit(String keyOrUrl) {
		if (keyOrUrl == null) {
			return;
		}
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			delete(keyOrUrl);
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				delete(keyOrUrl);
			}
		});
	}

	/** Ảnh vừa upload mà transaction rollback thì xóa đi để không thành ảnh mồ côi. */
	default void deleteOnRollback(String key) {
		if (key == null || !TransactionSynchronizationManager.isSynchronizationActive()) {
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCompletion(int status) {
				if (status == STATUS_ROLLED_BACK) {
					delete(key);
				}
			}
		});
	}

	/** Upload ảnh mới thay ảnh cũ, an toàn với rollback. Trả về key mới. */
	default String replaceImage(MultipartFile file, String folder, String oldKeyOrUrl) {
		String key = storeImage(file, folder);
		deleteOnRollback(key);
		deleteAfterCommit(oldKeyOrUrl);
		return key;
	}
}
