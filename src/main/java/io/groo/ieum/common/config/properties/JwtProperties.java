package io.groo.ieum.common.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 설정 Properties
 * SSO에서 발급한 JWT를 검증하고, HR JWT를 발급하기 위한 설정
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /**
     * SSO 토큰(RS256) 검증용 JWKS 엔드포인트 URI
     * 예: https://sso.../.well-known/jwks.json
     */
    private String ssoJwksUri;

    /**
     * HR 자체 토큰(HMAC) 서명·검증 키
     * HMAC-SHA256 요구사항에 따라 256bit(32자) 이상이어야 한다
     */
    private String hrSecret;

    /**
     * HR Access Token 만료 시간 (밀리초)
     * 기본값: 1시간 (3600000ms)
     */
    private long hrAccessExpiration = 3600000;

    /**
     * HR 토큰 절대 수명(밀리초) — SSO 로그인(토큰 교환)부터 이 시간이 지나면 슬라이딩 갱신을 멈춘다(보안 점검 M-1).
     * 갱신은 이음 DB 만 보므로, SSO 에서 놓친 폐기 · 삭제가 끝없이 전파되지 않았다. 이 상한이 지나면 SSO 로 다시
     * 로그인해야 한다(그때 SSO 가 지금 상태를 본다). 기본 30일 — 담다 DAM-5 와 같다.
     */
    private long hrAbsoluteLifetime = 2_592_000_000L;
}
