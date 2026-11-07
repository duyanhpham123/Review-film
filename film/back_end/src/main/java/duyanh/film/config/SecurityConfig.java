package duyanh.film.config;

import java.time.Instant;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.fasterxml.jackson.databind.ObjectMapper;

import duyanh.film.exception.ErrorResponse;
import duyanh.film.security.JwtAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

	private final JwtAuthFilter jwtAuthFilter;
	private final ObjectMapper objectMapper;
	private final AppProperties props;

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
				.csrf(AbstractHttpConfigurer::disable)
				.cors(c -> c.configurationSource(corsSource()))
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.formLogin(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/files/**", "/error",
								"/actuator/health", "/actuator/health/**")
						.permitAll()
						.requestMatchers("/api/v1/auth/**").permitAll()
						.requestMatchers("/api/v1/admin/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_MODERATOR")
						.requestMatchers("/api/v1/me/**", "/api/v1/users/me", "/api/v1/users/me/**").authenticated()
						.requestMatchers(HttpMethod.GET, "/api/v1/movies/*/me").authenticated()
						.requestMatchers(HttpMethod.POST, "/api/v1/movies/*/views").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/v1/**").permitAll()
						.anyRequest().authenticated())
				.exceptionHandling(e -> e
						.authenticationEntryPoint((req, res, ex) -> writeError(req, res, HttpStatus.UNAUTHORIZED,
								"UNAUTHORIZED", "Bạn cần đăng nhập"))
						.accessDeniedHandler((req, res, ex) -> writeError(req, res, HttpStatus.FORBIDDEN,
								"FORBIDDEN", "Bạn không có quyền thực hiện thao tác này")))
				.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	private CorsConfigurationSource corsSource() {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(List.of(props.fe().baseUrl()));
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of("*"));
		config.setAllowCredentials(true);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", config);
		return source;
	}

	private void writeError(HttpServletRequest req, HttpServletResponse res, HttpStatus status, String code,
			String message) throws java.io.IOException {
		res.setStatus(status.value());
		res.setContentType(MediaType.APPLICATION_JSON_VALUE);
		res.setCharacterEncoding("UTF-8");
		objectMapper.writeValue(res.getOutputStream(),
				new ErrorResponse(status.value(), code, message, req.getRequestURI(), Instant.now(), null));
	}
}
