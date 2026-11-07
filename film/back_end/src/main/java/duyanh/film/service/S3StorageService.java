package duyanh.film.service;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import duyanh.film.config.AppProperties;
import duyanh.film.exception.ApiException;
import duyanh.film.util.ImageFiles;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * Lưu ảnh lên MinIO hoặc S3 (cùng API S3). Ảnh được đọc thẳng từ MinIO / CDN, không đi qua Spring Boot.
 * <ul>
 * <li>Khi dev (app.storage.s3.create-bucket=true): tự tạo bucket và cho phép đọc công khai</li>
 * <li>Key là UUID nên không bao giờ đổi nội dung: đặt Cache-Control 1 năm cho trình duyệt / CDN</li>
 * <li>MinIO chưa chạy thì app vẫn khởi động; upload lúc đó báo 503</li>
 * </ul>
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3", matchIfMissing = true)
public class S3StorageService implements StorageService {

	private static final String CACHE_CONTROL = "public, max-age=31536000, immutable";

	private final S3Client s3;
	private final String bucket;
	private final String publicBaseUrl;
	private final boolean createBucket;
	private volatile boolean bucketReady;

	public S3StorageService(AppProperties props) {
		AppProperties.S3 cfg = props.storage().s3();
		this.bucket = cfg.bucket();
		this.createBucket = cfg.createBucket();
		String base = cfg.publicBaseUrl();
		this.publicBaseUrl = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
		var builder = S3Client.builder()
				.region(Region.of(cfg.region()))
				.credentialsProvider(StaticCredentialsProvider.create(
						AwsBasicCredentials.create(cfg.accessKey(), cfg.secretKey())))
				.serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(cfg.pathStyle()).build())
				// Chỉ tính checksum khi API bắt buộc: tương thích cả MinIO bản cũ
				.requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
				.responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED);
		if (cfg.endpoint() != null && !cfg.endpoint().isBlank()) {
			builder.endpointOverride(URI.create(cfg.endpoint()));
		}
		this.s3 = builder.build();
	}

	@EventListener(ApplicationReadyEvent.class)
	public void onReady() {
		try {
			ensureBucket();
		} catch (Exception e) {
			log.warn("Chưa kết nối được MinIO/S3 (bucket {}): {}. Sẽ thử lại khi có upload.", bucket, e.getMessage());
		}
	}

	@Override
	public String storeImage(MultipartFile file, String folder) {
		ImageFiles.ImageInfo info = ImageFiles.inspect(file, folder);
		String key = folder + "/" + UUID.randomUUID() + "." + info.extension();
		try {
			// Ảnh tối đa 5MB nên đọc hết vào bộ nhớ: SDK gửi lại được khi lỗi mạng thoáng qua
			// (với InputStream thì lần thử lại hỏng vì stream đã bị đọc)
			byte[] bytes = file.getBytes();
			ensureBucket();
			s3.putObject(b -> b.bucket(bucket).key(key).contentType(info.contentType()).cacheControl(CACHE_CONTROL),
					RequestBody.fromBytes(bytes));
		} catch (IOException | RuntimeException e) {
			log.error("Upload {} lên MinIO/S3 thất bại: {}", key, e.getMessage());
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_UNAVAILABLE",
					"Không lưu được ảnh, vui lòng thử lại sau");
		}
		return key;
	}

	@Override
	public void delete(String keyOrUrl) {
		if (keyOrUrl == null || keyOrUrl.isBlank() || ImageFiles.isAbsoluteUrl(keyOrUrl)) {
			return;
		}
		try {
			s3.deleteObject(b -> b.bucket(bucket).key(keyOrUrl));
		} catch (RuntimeException e) {
			// Không xóa được thì job dọn ảnh mồ côi sẽ xóa sau
			log.warn("Không xóa được {} trên MinIO/S3: {}", keyOrUrl, e.getMessage());
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
		List<StoredObject> result = new ArrayList<>();
		for (S3Object o : s3.listObjectsV2Paginator(b -> b.bucket(bucket).prefix(folder + "/")).contents()) {
			result.add(new StoredObject(o.key(), o.lastModified()));
		}
		return result;
	}

	private void ensureBucket() {
		if (bucketReady) {
			return;
		}
		try {
			s3.headBucket(b -> b.bucket(bucket));
		} catch (NoSuchBucketException e) {
			if (!createBucket) {
				throw new IllegalStateException("Bucket " + bucket + " chưa tồn tại");
			}
			s3.createBucket(b -> b.bucket(bucket));
			s3.putBucketPolicy(b -> b.bucket(bucket).policy(publicReadPolicy()));
			log.info("Đã tạo bucket {} và cho phép đọc công khai", bucket);
		}
		bucketReady = true;
	}

	/** Ai cũng được đọc (GetObject) ảnh trong bucket; ghi / xóa vẫn cần access key. */
	private String publicReadPolicy() {
		return """
				{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"AWS":["*"]},\
				"Action":["s3:GetObject"],"Resource":["arn:aws:s3:::%s/*"]}]}""".formatted(bucket);
	}

	@PreDestroy
	void close() {
		s3.close();
	}
}
