package io.groo.ieum.security.filter;

import io.groo.core.controller.ApiResponse;
import io.groo.ieum.common.config.properties.JwtProperties;
import io.groo.ieum.common.exception.HrErrorCode;
import io.groo.ieum.security.principal.UserPrincipal;
import io.groo.ieum.security.jwt.JwtTokenProvider;
import io.groo.ieum.security.principal.JwtClaimsPrincipal;
import io.groo.ieum.security.principal.JwtUserPrincipal;
import io.groo.ieum.permission.domain.Role;
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
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
    private final io.groo.ieum.security.service.TokenRevocationService tokenRevocationService;
    private final ObjectMapper objectMapper;

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ACCESS_TOKEN_COOKIE = "hr_access_token";
    private static final long RENEWAL_THRESHOLD_MS = 600_000L;

    /**
     * 비밀번호 변경이 필요한(pwdChange=Y) 토큰으로 부를 수 있는 요청 — (메서드, 경로).
     * 강제 변경 화면이 쓰는 로그인 확인 · 비밀번호 변경 · 로그아웃(+재로그인용 코드 교환)뿐이다. 나머지는 403.
     */
    private static final Set<String> PASSWORD_CHANGE_ALLOWED = Set.of(
            "GET /api/v1/login/check",
            "PATCH /api/v1/users/me/password",
            "POST /api/v1/auth/logout",
            "POST /api/v1/auth/exchange-code"
    );

    /**
     * 상태를 바꾸는 요청의 메서드 — 이 요청들은 토큰에 실린 권한이 아니라 DB 의 지금 권한으로 인가한다.
     * 토큰(최대 1시간)이 발급된 뒤 역할이 회수 · 삭제된 관리자가 남은 수명 동안 자기 권한을 되돌리거나
     * 다른 관리 작업을 하지 못하게 한다(보안 점검 IEU-2)
     */
    private static final Set<String> STATE_CHANGING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = resolveToken(request);

        if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
            try {
                Authentication authentication;

                if (jwtTokenProvider.isHrToken(token)) {
                    // 로그아웃된 토큰(차단 목록의 jti)은 거절한다. jti 없는 옛 토큰은 만료까지 허용.
                    String jti = jwtTokenProvider.getHrJti(token);
                    if (jti != null && tokenRevocationService.isRevoked(jti)) {
                        filterChain.doFilter(request, response);
                        return;
                    }
                    // HR JWT인 경우: Claims에서 직접 정보 추출 (DB 조회 없음)
                    authentication = createAuthenticationFromHrToken(token);
                    // 상태를 바꾸는 요청은 권한을 DB 에서 다시 읽는다 — 삭제(퇴사)된 사용자면 인증하지 않는다
                    if (isStateChanging(request)) {
                        authentication = reloadAuthoritiesFromDb(token, authentication);
                        if (authentication == null) {
                            filterChain.doFilter(request, response);
                            return;
                        }
                    }
                    if (rejectIfPasswordChangeRequired(authentication, request, response)) {
                        return;
                    }

                    // 슬라이딩 갱신 — 잔여 수명이 임계 아래로 내려간 유효 토큰은 새 토큰을
                    // 쿠키로 실어 준다(desk-back 정합). 이게 없으면 쓰고 있는 중에도 정각
                    // 1시간에 세션이 끊긴다. SSO JWT 는 SSO 발행분이라 여기서 재발급하지 않는다.
                    renewIfExpiringSoon(token, response,
                            ((UserPrincipal) authentication.getPrincipal()).isPasswordChangeRequired());
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
                    if (rejectIfPasswordChangeRequired(authentication, request, response)) {
                        return;
                    }
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
                () -> userService.findUserById(userId),  // Lazy Loading Supplier
                jwtTokenProvider.isPasswordChangeRequiredFromHrToken(token)
        );

        log.debug("HR JWT 인증 성공 (DB 조회 없음): userId={}, authorities={}", userId, authorities.size());

        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }

    private boolean isStateChanging(HttpServletRequest request) {
        String method = request.getMethod();
        return method != null && STATE_CHANGING_METHODS.contains(method.toUpperCase(Locale.ROOT));
    }

    /**
     * HR JWT 인증의 역할 · 권한을 DB 의 지금 값으로 바꿔 끼운다.
     * 사용자 식별 · 비밀번호 변경 표식은 토큰 것을 그대로 쓴다.
     *
     * @return DB 권한으로 만든 인증 객체. 사용자가 없거나 삭제됐으면 null
     */
    private Authentication reloadAuthoritiesFromDb(String token, Authentication fromToken) {
        UserPrincipal tokenPrincipal = (UserPrincipal) fromToken.getPrincipal();
        String userId = jwtTokenProvider.getUserId(token);
        User user;
        try {
            user = userService.findUserById(userId);
        } catch (Exception e) {
            log.warn("상태 변경 요청 인증 거절 — 사용자 없음/삭제: userId={}", userId);
            return null;
        }
        List<String> roles = user.getRoles().stream().map(Role::getCode).collect(Collectors.toList());
        List<String> permissions = user.getAllAuthorities();

        List<String> allAuthorities = new ArrayList<>(roles);
        allAuthorities.addAll(permissions);
        List<SimpleGrantedAuthority> authorities = allAuthorities.stream()
                .distinct()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());

        JwtClaimsPrincipal principal = new JwtClaimsPrincipal(
                userId,
                jwtTokenProvider.getSsoUserRowIdFromHrToken(token),
                jwtTokenProvider.getNameFromHrToken(token),
                jwtTokenProvider.getEmailFromHrToken(token),
                roles, permissions,
                // 컨트롤러가 쓰는 User 는 지금처럼 필요할 때 읽는다 — 필터에서 읽은 엔티티는 요청의
                // 영속성 컨텍스트 밖이라 지연 로딩이 깨질 수 있어 넘기지 않는다
                () -> userService.findUserById(userId),
                tokenPrincipal.isPasswordChangeRequired()
        );
        log.debug("상태 변경 요청 — DB 권한으로 인증: userId={}, authorities={}", userId, authorities.size());
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
        JwtUserPrincipal principal = new JwtUserPrincipal(user, jwtTokenProvider.isPasswordChangeRequiredFromSsoToken(token));

        log.debug("SSO JWT 인증 성공 (DB 조회): userId={}, authorities={}", userId, authorities.size());

        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }

    /**
     * 비밀번호 변경이 필요한 인증(HR 토큰 · SSO 토큰 직통 모두)이 허용 목록 밖 요청을 하면 403 으로 끊는다.
     * 응답은 기존 에러 형식({@code code}=AUTH_003, PASSWORD_CHANGE_REQUIRED)이다.
     *
     * @return 막았으면 true (호출자는 필터 체인을 이어 가지 않는다)
     */
    private boolean rejectIfPasswordChangeRequired(Authentication authentication,
                                                   HttpServletRequest request,
                                                   HttpServletResponse response) throws IOException {
        if (!(authentication.getPrincipal() instanceof UserPrincipal principal)
                || !principal.isPasswordChangeRequired()) {
            return false;
        }
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        String method = request.getMethod().toUpperCase(Locale.ROOT);
        // 사전 요청(OPTIONS)은 CORS 필터가 앞에서 끝내지만, 남아 오는 것도 막을 이유가 없다
        if ("OPTIONS".equals(method) || PASSWORD_CHANGE_ALLOWED.contains(method + " " + path)) {
            return false;
        }

        log.warn("비밀번호 변경 필요 — 허용 목록 밖 요청 차단: userId={}, {} {}", principal.getUserId(), method, path);
        response.setStatus(HrErrorCode.PASSWORD_CHANGE_REQUIRED.getHttpStatus().value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                ApiResponse.error(HrErrorCode.PASSWORD_CHANGE_REQUIRED.getCode(), "임시 비밀번호를 먼저 변경해야 해요.")));
        return true;
    }

    /**
     * 잔여 수명이 임계(10분) 미만인 HR 토큰을 같은 claims 로 재발급해 쿠키로 실어 준다.
     * 만료(잔여 0)는 살려 주지 않는다 — 죽은 토큰의 부활은 로그인 흐름의 몫이다.
     */
    private void renewIfExpiringSoon(String token, HttpServletResponse response, boolean passwordChangeRequired) {
        long remainingMs = jwtTokenProvider.getRemainingExpiration(token);
        if (remainingMs <= 0 || remainingMs >= RENEWAL_THRESHOLD_MS) {
            return;
        }
        // 갱신 때 사용자 상태 · 권한을 DB 에서 다시 읽는다 — 삭제(퇴사)된 사용자는 더 살려 주지 않고
        // (남은 수명 10분 안에 만료), 바뀐 역할 · 권한은 새 토큰에 반영한다.
        // 조회 실패가 이번 요청의 인증까지 깨지 않도록 예외는 여기서 삼킨다.
        String userId = jwtTokenProvider.getUserId(token);
        User user;
        try {
            user = userService.findUserById(userId);
        } catch (Exception e) {
            log.warn("HR 토큰 갱신 거절 — 사용자 없음/삭제: userId={}", userId);
            return;
        }
        List<String> roles = user.getRoles().stream().map(Role::getCode).collect(Collectors.toList());
        String renewed = jwtTokenProvider.createHrAccessToken(
                user, roles, user.getAllAuthorities(), passwordChangeRequired);  // 표식은 옛 토큰에서 옮겨 싣는다
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
