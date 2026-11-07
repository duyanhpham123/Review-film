package duyanh.film.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Cấu hình riêng của ứng dụng, đọc từ các khóa app.* trong application.properties. */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Fe fe, Mail mail, Storage storage, Cache cache, Tmdb tmdb) {

	/** readToken (API Read Access Token, khuyên dùng) hoặc apiKey (v3), chỉ cần một trong hai. */
	public record Tmdb(String baseUrl, String imageBaseUrl, String readToken, String apiKey, String language,
			int maxSeasons) {

		public boolean configured() {
			return (readToken != null && !readToken.isBlank()) || (apiKey != null && !apiKey.isBlank());
		}
	}

	public record Cache(Duration movieDetailTtl, Duration movieListTtl, Duration catalogTtl) {
	}

	public record Jwt(String secret, long accessExpirationMs, long refreshExpirationMs,
			long refreshExpirationRememberMs, boolean cookieSecure) {
	}

	public record Fe(String baseUrl) {
	}

	public record Mail(String from) {
	}

	/** type: s3 (MinIO / S3) hoặc local. */
	public record Storage(String type, String localDir, String publicBaseUrl, S3 s3, String cleanupCron,
			Duration orphanMinAge) {
	}

	public record S3(String endpoint, String region, String bucket, String accessKey, String secretKey,
			boolean pathStyle, String publicBaseUrl, boolean createBucket) {
	}
}
