package io.groo.ieum.security.jwt;

import io.groo.core.security.jwt.SsoJwksKeyLocator;

import io.groo.ieum.common.config.properties.JwtProperties;
import io.groo.ieum.user.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * JWT 토큰 Provider
 * SSO에서 발급한 JWT 토큰을 검증하고, HR JWT를 발급합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;
    private final SsoJwksKeyLocator ssoJwksKeyLocator;
    private SecretKey hrKey;

    private static final String HR_ISSUER = "porest-hr";
    private static final String TOKEN_TYPE_CLAIM = "type";
    private static final String TOKEN_TYPE_HR_ACCESS = "hr_access";
    private static final String SSO_TOKEN_TYPE_ACCESS = "access";
    private static final String SSO_AUDIENCE = "porest-services";
    private static final String HR_SERVICE_CODE = "hr";
    /** 비밀번호 변경 필요 표식 클레임 — SSO access token 과 HR 토큰이 같은 이름·값('Y')을 쓴다. 없으면 N. */
    private static final String PWD_CHANGE_CLAIM = "pwdChange";
    private static final String PWD_CHANGE_REQUIRED = "Y";

    @PostConstruct
    public void init() {
        // HR 자체 토큰(HMAC) 서명/검증 키.
        // SSO 토큰은 HMAC 이 아니라 SSO JWKS(RS256 public key) 로 검증한다(SsoJwksKeyLocator).
        String hrSecret = jwtProperties.getHrSecret();
        if (hrSecret == null || hrSecret.isBlank()) {
            throw new IllegalStateException("jwt.hr-secret(JWT_HR_SECRET) 이 설정되지 않았습니다");
        }
        this.hrKey = Keys.hmacShaKeyFor(hrSecret.getBytes(StandardCharsets.UTF_8));
    }

    // ==================== SSO JWT 검증 ====================

    /**
     * SSO JWT 토큰 유효성 검증
     *
     * @param token JWT 토큰
     * @return 유효 여부
     */
    public boolean validateSsoToken(String token) {
        try {
            Jwts.parser()
                    .keyLocator(ssoJwksKeyLocator)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("SSO JWT token expired: {}", e.getMessage());
        } catch (JwtException e) {
            log.warn("Invalid SSO JWT token: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("SSO JWT token is empty: {}", e.getMessage());
        }
        return false;
    }

    /**
     * HR API 의 Bearer 로 쓸 수 있는 SSO 토큰인지 검사한다 (서명 검증 포함).
     * SSO 는 같은 키로 access · refresh · service · link_ticket · reauth 를 모두 서명하므로
     * 서명만으로는 부족하다. type=access, aud 에 SSO 대상(porest-services), services 에 HR(hr) 가
     * 모두 있어야 통과한다. 값은 groo-sso-back JwtTokenProvider 가 access 토큰에 넣는 값이다.
     */
    public boolean isSsoAccessTokenForHr(String token) {
        try {
            return isSsoAccessClaimsForHr(getSsoClaims(token));
        } catch (Exception e) {
            return false;
        }
    }

    static boolean isSsoAccessClaimsForHr(Claims claims) {
        if (!SSO_TOKEN_TYPE_ACCESS.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
            return false;
        }
        java.util.Set<String> aud = claims.getAudience();
        if (aud == null || !aud.contains(SSO_AUDIENCE)) {
            return false;
        }
        Object services = claims.get("services");
        return services instanceof java.util.Collection<?> c && c.contains(HR_SERVICE_CODE);
    }

    /**
     * SSO JWT 토큰에서 사용자 번호 추출
     *
     * @param token JWT 토큰
     * @return 사용자 번호
     */
    public Long getUserNoFromSsoToken(String token) {
        Claims claims = getSsoClaims(token);
        return claims.get("userNo", Long.class);
    }

    /**
     * SSO JWT 토큰에서 사용자 ID 추출
     *
     * @param token JWT 토큰
     * @return 사용자 ID
     */
    public String getUserIdFromSsoToken(String token) {
        Claims claims = getSsoClaims(token);
        return claims.getSubject();
    }

    /**
     * SSO JWT 토큰에서 서비스 목록 추출
     *
     * @param token JWT 토큰
     * @return 서비스 코드 목록
     */
    @SuppressWarnings("unchecked")
    public List<String> getServicesFromSsoToken(String token) {
        Claims claims = getSsoClaims(token);
        List<String> services = claims.get("services", List.class);
        return services != null ? services : Collections.emptyList();
    }

    /**
     * SSO JWT 토큰의 비밀번호 변경 필요 표식. 클레임이 없는 옛 토큰은 false(N).
     */
    public boolean isPasswordChangeRequiredFromSsoToken(String token) {
        return PWD_CHANGE_REQUIRED.equals(getSsoClaims(token).get(PWD_CHANGE_CLAIM, String.class));
    }

    /**
     * SSO JWT 토큰에서 Claims 추출
     *
     * @param token JWT 토큰
     * @return Claims
     */
    private Claims getSsoClaims(String token) {
        return Jwts.parser()
                .keyLocator(ssoJwksKeyLocator)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // ==================== HR JWT 생성 ====================

    /**
     * HR Access Token 생성
     *
     * @param user HR 사용자
     * @param roles 역할 목록
     * @param permissions 권한 목록
     * @return HR Access Token
     */
    public String createHrAccessToken(User user, List<String> roles, List<String> permissions) {
        return createHrAccessToken(user, roles, permissions, false);
    }

    /**
     * HR Access Token 생성 (비밀번호 변경 필요 표식 포함)
     *
     * @param passwordChangeRequired true 면 pwdChange=Y 클레임을 싣는다 (강제 변경이 끝날 때까지 다른 API 가 막힌다)
     */
    public String createHrAccessToken(User user, List<String> roles, List<String> permissions,
                                      boolean passwordChangeRequired) {
        return createHrAccessToken(user.getId(), user.getSsoUserRowId(),
                user.getName(), user.getEmail(), roles, permissions, passwordChangeRequired);
    }

    /**
     * HR Access Token 생성 (claims 직접 전달)
     *
     * <p>슬라이딩 갱신처럼 이미 검증한 토큰의 claims 만 손에 있는 자리에서 쓴다 —
     * User 엔티티를 다시 조회하면 갱신 창(만료 전 10분) 동안 요청마다 DB 를 때린다.
     */
    public String createHrAccessToken(String userId, Long ssoUserRowId,
                                      String name, String email,
                                      List<String> roles, List<String> permissions) {
        return createHrAccessToken(userId, ssoUserRowId, name, email, roles, permissions, false);
    }

    public String createHrAccessToken(String userId, Long ssoUserRowId,
                                      String name, String email,
                                      List<String> roles, List<String> permissions,
                                      boolean passwordChangeRequired) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + jwtProperties.getHrAccessExpiration());

        JwtBuilder builder = Jwts.builder()
                .id(java.util.UUID.randomUUID().toString())  // jti — 로그아웃 차단 목록의 키
                .subject(userId)
                .issuer(HR_ISSUER)
                .issuedAt(now)
                .expiration(expiration)
                .claim(TOKEN_TYPE_CLAIM, TOKEN_TYPE_HR_ACCESS)
                .claim("ssoUserRowId", ssoUserRowId)
                .claim("name", name)
                .claim("email", email)
                .claim("roles", roles)
                .claim("permissions", permissions);
        if (passwordChangeRequired) {
            builder.claim(PWD_CHANGE_CLAIM, PWD_CHANGE_REQUIRED);
        }
        return builder
                .signWith(hrKey)
                .compact();
    }

    /**
     * HR 토큰의 jti. 로그아웃 차단 목록 도입 전에 발급된 옛 토큰에는 없어 null 이다.
     */
    public String getHrJti(String token) {
        return getHrClaims(token).getId();
    }

    /**
     * 토큰의 남은 수명 (밀리초). 만료·파싱 실패는 0 — 갱신 판단에서 자연히 제외된다.
     */
    public long getRemainingExpiration(String token) {
        try {
            Claims claims = isHrToken(token) ? getHrClaims(token) : getSsoClaims(token);
            return Math.max(0, claims.getExpiration().getTime() - System.currentTimeMillis());
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * HR Access Token 만료 시간 반환 (초 단위)
     *
     * @return 만료 시간 (초)
     */
    public long getHrAccessExpirationSeconds() {
        return jwtProperties.getHrAccessExpiration() / 1000;
    }

    // ==================== HR JWT 검증 ====================

    /**
     * HR JWT 토큰 유효성 검증
     *
     * @param token JWT 토큰
     * @return 유효 여부
     */
    public boolean validateHrToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(hrKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            // HR JWT인지 확인 (issuer 체크)
            String issuer = claims.getIssuer();
            return HR_ISSUER.equals(issuer);
        } catch (ExpiredJwtException e) {
            log.warn("HR JWT token expired: {}", e.getMessage());
        } catch (JwtException e) {
            log.warn("Invalid HR JWT token: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("HR JWT token is empty: {}", e.getMessage());
        }
        return false;
    }

    /**
     * JWT 토큰 유효성 검증 (HR 또는 SSO)
     * HR JWT를 우선 검증하고, 실패하면 SSO JWT 검증
     *
     * @param token JWT 토큰
     * @return 유효 여부
     */
    public boolean validateToken(String token) {
        // HR JWT 먼저 검증
        if (validateHrToken(token)) {
            return true;
        }
        // SSO JWT 검증 (기존 호환성)
        return validateSsoToken(token);
    }

    /**
     * JWT 토큰에서 사용자 ID 추출 (HR 또는 SSO)
     *
     * @param token JWT 토큰
     * @return 사용자 ID
     */
    public String getUserId(String token) {
        // HR JWT인지 확인
        if (isHrToken(token)) {
            return getHrClaims(token).getSubject();
        }
        // SSO JWT
        return getSsoClaims(token).getSubject();
    }

    /**
     * JWT 토큰에서 사용자 이름 추출
     *
     * @param token JWT 토큰
     * @return 사용자 이름
     */
    public String getUserName(String token) {
        Claims claims = isHrToken(token) ? getHrClaims(token) : getSsoClaims(token);
        return claims.get("name", String.class);
    }

    /**
     * JWT 토큰에서 이메일 추출
     *
     * @param token JWT 토큰
     * @return 이메일
     */
    public String getEmail(String token) {
        Claims claims = isHrToken(token) ? getHrClaims(token) : getSsoClaims(token);
        return claims.get("email", String.class);
    }

    /**
     * HR JWT인지 확인
     *
     * @param token JWT 토큰
     * @return HR JWT 여부
     */
    public boolean isHrToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(hrKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return HR_ISSUER.equals(claims.getIssuer());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * HR JWT 토큰에서 Claims 추출
     *
     * @param token JWT 토큰
     * @return Claims
     */
    private Claims getHrClaims(String token) {
        return Jwts.parser()
                .verifyWith(hrKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // ==================== HR JWT Claims 추출 메서드 ====================

    /**
     * HR JWT 토큰에서 SSO 사용자 행 아이디 추출
     *
     * @param token JWT 토큰
     * @return SSO 사용자 행 아이디
     */
    public Long getSsoUserRowIdFromHrToken(String token) {
        Claims claims = getHrClaims(token);
        return claims.get("ssoUserRowId", Long.class);
    }

    /**
     * HR JWT 토큰에서 사용자 이름 추출
     *
     * @param token JWT 토큰
     * @return 사용자 이름
     */
    public String getNameFromHrToken(String token) {
        Claims claims = getHrClaims(token);
        return claims.get("name", String.class);
    }

    /**
     * HR JWT 토큰에서 이메일 추출
     *
     * @param token JWT 토큰
     * @return 이메일
     */
    public String getEmailFromHrToken(String token) {
        Claims claims = getHrClaims(token);
        return claims.get("email", String.class);
    }

    /**
     * HR JWT 토큰에서 역할 목록 추출
     *
     * @param token JWT 토큰
     * @return 역할 코드 목록
     */
    @SuppressWarnings("unchecked")
    public List<String> getRolesFromHrToken(String token) {
        Claims claims = getHrClaims(token);
        List<String> roles = claims.get("roles", List.class);
        return roles != null ? roles : Collections.emptyList();
    }

    /**
     * HR JWT 토큰에서 권한 목록 추출
     *
     * @param token JWT 토큰
     * @return 권한 코드 목록
     */
    @SuppressWarnings("unchecked")
    public List<String> getPermissionsFromHrToken(String token) {
        Claims claims = getHrClaims(token);
        List<String> permissions = claims.get("permissions", List.class);
        return permissions != null ? permissions : Collections.emptyList();
    }

    /**
     * HR JWT 토큰의 비밀번호 변경 필요 표식. 클레임이 없는 옛 토큰은 false(N).
     */
    public boolean isPasswordChangeRequiredFromHrToken(String token) {
        return PWD_CHANGE_REQUIRED.equals(getHrClaims(token).get(PWD_CHANGE_CLAIM, String.class));
    }
}
