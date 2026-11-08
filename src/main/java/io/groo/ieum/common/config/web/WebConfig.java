package io.groo.ieum.common.config.web;

import io.groo.ieum.common.config.security.RequestResponseLoggingFilter;
import io.groo.ieum.security.resolver.LoginUserArgumentResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {
    private final LoginUserArgumentResolver loginUserArgumentResolver;
    private final RequestResponseLoggingFilter requestResponseLoggingFilter;
    private final LocaleChangeInterceptor localeChangeInterceptor;
    private final org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    @Value("${file.resource-handler}")
    private String resourceHandler;

    @Value("${file.resource-locations}")
    private String resourceLocations;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(resourceHandler)
                .addResourceLocations(resourceLocations);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> argumentResolvers) {
        argumentResolvers.add(loginUserArgumentResolver);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(localeChangeInterceptor);

        // 로그인(인가코드 교환): 분당 60/IP — 로그인 1회당 1요청이라 NAT 뒤 여러 명이 몰려도 여유가 있다
        registry.addInterceptor(new RateLimitInterceptor(stringRedisTemplate, "login", 60))
                .addPathPatterns("/api/v1/auth/exchange-code");
        // 메일 발송 · 비밀번호 계열: 분당 20/IP — 정상 사용은 사람이 누르는 횟수
        registry.addInterceptor(new RateLimitInterceptor(stringRedisTemplate, "sensitive", 20))
                .addPathPatterns("/api/v1/users/*/invitations/resend",
                        "/api/v1/users/me/password",
                        "/api/v1/users/*/password");
    }

    /**
     * RequestResponseLoggingFilter를 Servlet 컨테이너 레벨에서 등록
     * Spring Security 필터 체인보다 먼저 실행되어 모든 요청(OAuth, 회원가입 포함)을 캡처
     */
    @Bean
    public FilterRegistrationBean<RequestResponseLoggingFilter> loggingFilter() {
        FilterRegistrationBean<RequestResponseLoggingFilter> registrationBean = new FilterRegistrationBean<>();

        registrationBean.setFilter(requestResponseLoggingFilter);
        registrationBean.addUrlPatterns("/*"); // 모든 URL 패턴에 적용
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE); // 가장 높은 우선순위

        return registrationBean;
    }
}