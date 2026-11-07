package duyanh.film.config;

import java.nio.file.Path;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import lombok.RequiredArgsConstructor;

/** Khi app.storage.type=local: phục vụ ảnh đã upload tại /files/**. Với MinIO / S3 ảnh được đọc thẳng từ đó. */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

	private final AppProperties props;

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		if (!"local".equals(props.storage().type())) {
			return;
		}
		String location = Path.of(props.storage().localDir()).toAbsolutePath().toUri().toString();
		registry.addResourceHandler("/files/**").addResourceLocations(location);
	}
}
