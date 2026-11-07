package duyanh.film;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

// Đăng nhập bằng JWT tự làm, không cần user mặc định của Spring Security
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
@EnableScheduling
@EnableAsync
public class FilmApplication {

	public static void main(String[] args) {
		// Toàn bộ thời gian trong hệ thống là UTC
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		SpringApplication.run(FilmApplication.class, args);
	}

}
