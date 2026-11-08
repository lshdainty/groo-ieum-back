package io.groo.ieum.security.filter;

import io.groo.core.controller.ApiResponse;
import io.groo.core.exception.ErrorCode;
import io.groo.ieum.security.util.ClientIpResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;

/**
 * {@code /actuator/prometheus} 는 컨테이너 네트워크에서 Prometheus 가 직접 긁는 경로다.
 *
 * <p>전달 헤더(X-Forwarded-For · X-Real-IP · Forwarded)가 하나라도 있으면 프록시를 거친 외부 요청으로 보고
 * 403 으로 거절한다. 헤더가 없어도 직접 연결한 상대(remoteAddr)가 사설 · 루프백이 아니면 거절한다.
 * 엣지 설정과 무관하게 코드만으로 지켜진다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PrometheusAccessFilter extends OncePerRequestFilter {

    private static final String PATH = "/actuator/prometheus";
    private static final List<String> FORWARD_HEADERS = List.of("X-Forwarded-For", "X-Real-IP", "Forwarded");

    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !(PATH.equals(uri) || (PATH + "/").equals(uri));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (isInternalDirect(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        log.warn("프록시를 거친 /actuator/prometheus 요청 차단 - remoteAddr: {}", request.getRemoteAddr());
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                ApiResponse.error(ErrorCode.FORBIDDEN.getCode(), "접근 권한이 없습니다.")));
    }

    private boolean isInternalDirect(HttpServletRequest request) {
        for (String header : FORWARD_HEADERS) {
            if (request.getHeader(header) != null) {
                return false;
            }
        }
        return ClientIpResolver.isTrustedProxy(request.getRemoteAddr());
    }
}
