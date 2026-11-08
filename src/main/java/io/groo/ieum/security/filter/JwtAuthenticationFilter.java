package io.groo.ieum.security.filter;

import io.groo.ieum.common.config.properties.JwtProperties;
import io.groo.ieum.security.jwt.JwtTokenProvider;
import io.groo.ieum.security.principal.JwtClaimsPrincipal;
import io.groo.ieum.security.principal.JwtUserPrincipal;
import io.groo.ieum.user.domain.User;
import io.groo.ieum.user.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JWT 인증 필터
 * Authorization 헤더의 Bearer 토큰을 검증하고 SecurityContext에 인증 정보를 설정합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserService userService;
    private final JwtProperties jwtProperties;

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ACCESS_TOKEN_COOKIE = "hr_access_token";
    private static final long RENEWAL_THRESHOLD_MS = 600_000L;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = resolveToken(request);

        if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
            try {
                Authentication authentication;

                if (jwtTokenProvider.isHrToken(token)) {
                    // HR JWT인 경우: Claims에서 직접 정보 추출 (DB 조회 없음)
                    authentication = createAuthenticationFromHrToken(token);

                    // 슬라이딩 갱신 — 잔여 수명이 임계 아래로 내려간 유효 토큰은 새 토큰을
                    // 쿠키로 실어 준다(desk-back 정합). 이게 없으면 쓰고 있는 중에도 정각
                    // 1시간에 세션이 끊긴다. SSO JWT 는 SSO 발행분이라 여기서 재발급하지 않는다.
                    renewIfExpiringSoon(token, response);
                } else {
                    // SSO JWT인 경우: HR DB 조회 필요 (하위 호환성 유지)
                    // access 토큰 + 대상(aud) + HR 접근(services) 인 것만 받는다.
                    // refresh · service · link_ticket · reauth 등 같은 키로 서명된 다른 종류는 거른다.
                    if (!jwtTokenProvider.isSsoAccessTokenForHr(token)) {
                        log.warn("SSO JWT 거부: access 토큰이 아니거나 HR 대상이 아님");
                        filterChain.doFilter(request, response);
                        return;
                    }
                    authentication = createAuthenticationFromSsoToken(token);
                }

                // SecurityContext에 인증 정보 설정
                SecurityContextHolder.getContext().setAuthentication(authentication);

            } catch (Exception e) {
                log.warn("JWT 인증 처리 중 오류: {}", e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * HR JWT에서 인증 객체 생성 (DB 조회 없음)
     * JWT Claims에서 직접 사용자 정보와 권한을 추출합니다.
     *
     * @param token HR JWT 토큰
     * @return Authentication 객체
     */
    private Authentication createAuthenticationFromHrToken(String token) {
        // JWT Claims에서 정보 추출
        String userId = jwtTokenProvider.getUserId(token);
        Long ssoUserRowId = jwtTokenProvider.getSsoUserRowIdFromHrToken(token);
        String name = jwtTokenProvider.getNameFromHrToken(token);
        String email = jwtTokenProvider.getEmailFromHrToken(token);
        List<String> roles = jwtTokenProvider.getRolesFromHrToken(token);
        List<String> permissions = jwtTokenProvider.getPermissionsFromHrToken(token);

        // 권한 목록 생성 (roles + permissions)
        List<String> allAuthorities = new ArrayList<>(roles);
        allAuthorities.addAll(permissions);
        List<SimpleGrantedAuthority> authorities = allAuthorities.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());

        // JwtClaimsPrincipal 생성 (User는 필요할 때 Lazy Loading)
        JwtClaimsPrincipal principal = new JwtClaimsPrincipal(
                userId, ssoUserRowId, name, email, roles, permissions,
                () -> userService.findUserById(userId)  // Lazy Loading Supplier
        );

        log.debug("HR JWT 인증 성공 (DB 조회 없음): userId={}, authorities={}", userId, authorities.size());

        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }

    /**
     * SSO JWT에서 인증 객체 생성 (DB 조회 필요)
     * SSO JWT에는 HR 권한 정보가 없으므로 DB에서 조회합니다.
     *
     * @param token SSO JWT 토큰
     * @return Authentication 객체
     */
    private Authentication createAuthenticationFromSsoToken(String token) {
        // JWT에서 사용자 ID 추출
        String userId = jwtTokenProvider.getUserId(token);

        // HR DB에서 사용자 및 권한 조회
        User user = userService.findUserById(userId);

        // 권한 목록 조회
        List<SimpleGrantedAuthority> authorities = user.getAllAuthorities().stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());

        // JwtUserPrincipal 생성 (기존 방식 유지)
        JwtUserPrincipal principal = new JwtUserPrincipal(user);

        log.debug("SSO JWT 인증 성공 (DB 조회): userId={}, authorities={}", userId, authorities.size());

        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }

    /**
     * 잔여 수명이 임계(10분) 미만인 HR 토큰을 같은 claims 로 재발급해 쿠키로 실어 준다.
     * 만료(잔여 0)는 살려 주지 않는다 — 죽은 토큰의 부활은 로그인 흐름의 몫이다.
     */
    private void renewIfExpiringSoon(String token, HttpServletResponse response) {
        long remainingMs = jwtTokenProvider.getRemainingExpiration(token);
        if (remainingMs <= 0 || remainingMs >= RENEWAL_THRESHOLD_MS) {
            return;
        }
        String renewed = jwtTokenProvider.createHrAccessToken(
                jwtTokenProvider.getUserId(token),
                jwtTokenProvider.getSsoUserRowIdFromHrToken(token),
                jwtTokenProvider.getNameFromHrToken(token),
                jwtTokenProvider.getEmailFromHrToken(token),
                jwtTokenProvider.getRolesFromHrToken(token),
                jwtTokenProvider.getPermissionsFromHrToken(token));
        // TokenExchangeController.setAccessTokenCookie 와 같은 속성 — 로그인이 심는 쿠키를
        // 그대로 대체해야 두 쿠키가 병존하지 않는다.
        ResponseCookie cookie = ResponseCookie.from(ACCESS_TOKEN_COOKIE, renewed)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(jwtProperties.getHrAccessExpiration() / 1000)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        log.debug("HR 토큰 슬라이딩 갱신: userId={}", jwtTokenProvider.getUserId(token));
    }

    /**
     * 쿠키 또는 Authorization 헤더에서 JWT 토큰 추출
     * HttpOnly 쿠키를 우선 확인하고, 없으면 Authorization 헤더를 확인합니다.
     *
     * @param request HTTP 요청
     * @return JWT 토큰 (없으면 null)
     */
    private String resolveToken(HttpServletRequest request) {
        // 1. HttpOnly 쿠키에서 토큰 추출
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            String cookieToken = Arrays.stream(cookies)
                    .filter(c -> ACCESS_TOKEN_COOKIE.equals(c.getName()))
                    .map(Cookie::getValue)
                    .findFirst()
                    .orElse(null);
            if (StringUtils.hasText(cookieToken)) {
                return cookieToken;
            }
        }

        // 2. Authorization 헤더에서 Bearer 토큰 추출 (fallback)
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
