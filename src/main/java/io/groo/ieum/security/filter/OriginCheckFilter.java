package io.groo.ieum.security.filter;

import io.groo.core.controller.ApiResponse;
import io.groo.core.exception.ErrorCode;
import io.groo.ieum.common.config.properties.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * 쿠키 인증 요청의 CSRF 방어 — 상태 변경 요청(POST/PUT/PATCH/DELETE)의 Origin(없으면 Referer)이
 * 허용 오리진(CORS 설정과 같은 목록)인지 본다.
 *
 * <p>SameSite=Lax 는 같은 사이트의 형제 서브도메인에서 온 폼 POST 를 막지 못한다.
 * 이 필터가 그 틈을 닫는다.
 * <ul>
 *   <li>검사 대상: 상태 변경 메서드 + 인증 쿠키(hr_access_token)가 있고 Bearer 헤더가 없는 요청</li>
 *   <li>Bearer 헤더 인증은 브라우저가 자동으로 붙이지 않으므로 검사하지 않는다</li>
 *   <li>Origin · Referer 둘 다 없으면 통과(서버 간 호출 · 도구)</li>
 *   <li>허용 밖이면 403 (기존 에러 형식 COMMON_412)</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OriginCheckFilter extends OncePerRequestFilter {

    private static final String ACCESS_TOKEN_COOKIE = "hr_access_token";
    private static final Set<String> UNSAFE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!needsCheck(request) || isAllowed(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        log.warn("허용되지 않은 오리진의 상태 변경 요청 차단 - Method: {}, URI: {}, Origin: {}, Referer: {}",
                request.getMethod(), request.getRequestURI(),
                request.getHeader("Origin"), request.getHeader("Referer"));

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                ApiResponse.error(ErrorCode.FORBIDDEN.getCode(), "접근 권한이 없습니다.")));
    }

    private boolean needsCheck(HttpServletRequest request) {
        if (!UNSAFE_METHODS.contains(request.getMethod().toUpperCase(Locale.ROOT))) {
            return false;
        }
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return false;
        }
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return false;
        }
        for (Cookie cookie : cookies) {
            if (ACCESS_TOKEN_COOKIE.equals(cookie.getName())) {
                return true;
            }
        }
        return false;
    }

    private boolean isAllowed(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin != null && !origin.isBlank()) {
            return matches(origin);
        }
        String referer = request.getHeader("Referer");
        if (referer != null && !referer.isBlank()) {
            return matches(referer);
        }
        return true; // 둘 다 없음 — 서버 간 · 도구
    }

    /** 값의 scheme://host[:port] 부분이 허용 오리진 중 하나와 같은지 본다. */
    private boolean matches(String value) {
        String normalized = originOf(value);
        if (normalized == null) {
            return false;
        }
        for (String allowed : appProperties.getFrontend().allowedOrigins()) {
            if (normalized.equals(originOf(allowed))) {
                return true;
            }
        }
        return false;
    }

    private static String originOf(String value) {
        try {
            URI uri = URI.create(value.trim());
            if (uri.getScheme() == null || uri.getHost() == null) {
                return null;
            }
            String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
            int port = uri.getPort();
            if (port == -1 || (port == 80 && "http".equals(scheme)) || (port == 443 && "https".equals(scheme))) {
                port = -1;
            }
            return scheme + "://" + uri.getHost().toLowerCase(Locale.ROOT) + (port == -1 ? "" : ":" + port);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
