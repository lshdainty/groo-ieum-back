package io.groo.ieum.common.config.security;

import io.groo.ieum.common.config.properties.AppProperties;
import io.groo.ieum.security.filter.IpBlockFilter;
import io.groo.ieum.security.filter.JwtAuthenticationFilter;
import io.groo.ieum.security.filter.OriginCheckFilter;
import io.groo.ieum.security.handler.CustomAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security 설정 (JWT Resource Server)
 * SSO에서 발급한 JWT 토큰을 검증하여 인증을 처리합니다.
 */
@RequiredArgsConstructor
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomAuthenticationEntryPoint customAuthenticationEntryPoint;
    private final AppProperties appProperties;
    private final IpBlockFilter ipBlockFilter;
    private final OriginCheckFilter originCheckFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        // CSRF 비활성화 (Stateless JWT 사용)
        http.csrf(csrf -> csrf.disable());

        // CORS 설정
        http.cors(cors -> cors.configurationSource(corsConfigurationSource()));

        // 세션 사용 안함 (Stateless)
        http.sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        // Form 로그인 비활성화 (SSO에서 처리)
        http.formLogin(form -> form.disable());

        // HTTP Basic 비활성화
        http.httpBasic(basic -> basic.disable());

        // 경로별 인가 설정
        http.authorizeHttpRequests(auth -> auth
                // 인증 없이 접근 가능한 경로
                .requestMatchers(
                        "/",
                        "/api/v1/auth/exchange-code", // 인가코드 교환 API (OAuth2 Authorization Code + PKCE)
                        "/api/v1/auth/logout",   // 로그아웃 (쿠키 삭제)
                        "/actuator/health",      // Health check
                        "/actuator/prometheus",  // Prometheus metrics
                        "/css/**",
                        "/images/**",
                        "/js/**"
                ).permitAll()

                // 그 외 모든 요청은 인증 필요
                .anyRequest().authenticated()
        );

        // 인증 실패 처리
        http.exceptionHandling(exception -> exception
                .authenticationEntryPoint(customAuthenticationEntryPoint)
        );

        // 필터 순서 설정
        // IP 블랙리스트 필터 → JWT 인증 필터 → 기타 필터
        http.addFilterBefore(ipBlockFilter, UsernamePasswordAuthenticationFilter.class);
        // 쿠키 인증 + 상태 변경 요청은 Origin(없으면 Referer)이 허용 오리진인지 본다(S-5)
        http.addFilterBefore(originCheckFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // 허용 Origin 설정
        configuration.setAllowedOrigins(appProperties.getFrontend().allowedOrigins());

        // 허용 HTTP 메서드
        configuration.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
        ));

        // 허용 헤더 (필요한 헤더만 명시적으로 허용)
        configuration.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "Accept", "Accept-Language", "X-Requested-With"
        ));

        // Authorization 헤더 노출 (JWT 토큰 전달용)
        configuration.addExposedHeader("Authorization");

        // Credentials 허용
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}
