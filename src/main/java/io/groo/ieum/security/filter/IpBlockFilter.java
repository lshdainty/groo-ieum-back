package io.groo.ieum.security.filter;

import tools.jackson.databind.ObjectMapper;
import io.groo.ieum.security.service.IpBlacklistService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * IP 블랙리스트 차단 필터
 * - Spring Security 필터 체인 앞단에서 실행
 * - 블랙리스트 IP의 모든 요청을 차단
 * - 403 Forbidden 응답 반환
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IpBlockFilter extends OncePerRequestFilter {

    private final IpBlacklistService ipBlacklistService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String clientIp = getClientIp(request);

        // IP 블랙리스트 확인
        if (ipBlacklistService.isBlocked(clientIp)) {
            handleBlockedRequest(request, response, clientIp);
            return; // 필터 체인 중단
        }

        // 정상 요청은 다음 필터로 전달
        filterChain.doFilter(request, response);
    }

    /**
     * 차단된 요청 처리
     */
    private void handleBlockedRequest(HttpServletRequest request,
                                      HttpServletResponse response,
                                      String clientIp) throws IOException {

        log.warn("🚫 IP BLOCKED - IP: {}, URI: {}, Method: {}, User-Agent: {}",
                clientIp,
                request.getRequestURI(),
                request.getMethod(),
                request.getHeader("User-Agent"));

        // 403 Forbidden 응답
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String jsonResponse = objectMapper.writeValueAsString(
                new BlockedIpResponse(
                        403,
                        "Forbidden",
                        "Access denied. Your IP address has been blocked."
                )
        );

        response.getWriter().write(jsonResponse);
    }

    private String getClientIp(HttpServletRequest request) {
        return io.groo.ieum.security.util.ClientIpResolver.resolve(request);
    }

    /**
     * 차단 응답 DTO
     */
    private record BlockedIpResponse(
            int status,
            String error,
            String message
    ) {}
}
