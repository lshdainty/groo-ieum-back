package io.groo.ieum.security.jwt;

import io.groo.ieum.common.config.properties.JwtProperties;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S-2 — SSO 서명 토큰이라도 HR API Bearer 로는 access + 대상 aud + services 에 hr 인 것만 받는다.
 * 클레임 값은 groo-sso-back JwtTokenProvider 가 발급하는 형태를 그대로 쓴다
 * (issuer porest-sso, aud porest-services, type access|refresh|service|link_ticket|reauth, services ["hr", ...]).
 */
@DisplayName("SSO 토큰 HR 수용 검사")
class SsoTokenAcceptanceTest {

    private KeyPair keyPair;
    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        keyPair = g.generateKeyPair();
        SsoJwksKeyLocator locator = new SsoJwksKeyLocator(Mockito.mock(JwtProperties.class)) {
            @Override
            protected Key locate(JwsHeader header) {
                return keyPair.getPublic();
            }
        };
        provider = new JwtTokenProvider(Mockito.mock(JwtProperties.class), locator);
    }

    private String sso(String type, String aud, List<String> services) {
        var b = Jwts.builder()
                .header().keyId("k1").and()
                .subject("user1")
                .issuer("porest-sso")
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 600_000))
                .claim("type", type);
        if (aud != null) b.audience().add(aud);
        if (services != null) b.claim("services", services);
        return b.signWith(keyPair.getPrivate(), Jwts.SIG.RS256).compact();
    }

    @Test
    @DisplayName("access + porest-services + services 에 hr -> 통과")
    void acceptsRealAccessShape() {
        assertThat(provider.isSsoAccessTokenForHr(sso("access", "porest-services", List.of("hr", "desk")))).isTrue();
    }

    @Test
    @DisplayName("refresh 토큰은 거절")
    void rejectsRefresh() {
        assertThat(provider.isSsoAccessTokenForHr(sso("refresh", "porest-services", List.of("hr")))).isFalse();
    }

    @Test
    @DisplayName("service / link_ticket / reauth 토큰은 거절")
    void rejectsOtherTypes() {
        for (String t : List.of("service", "link_ticket", "reauth")) {
            assertThat(provider.isSsoAccessTokenForHr(sso(t, "porest-services", List.of("hr")))).as(t).isFalse();
        }
    }

    @Test
    @DisplayName("services 에 hr 이 없으면(담다용) 거절")
    void rejectsNoHrService() {
        assertThat(provider.isSsoAccessTokenForHr(sso("access", "porest-services", List.of("desk")))).isFalse();
        assertThat(provider.isSsoAccessTokenForHr(sso("access", "porest-services", null))).isFalse();
    }

    @Test
    @DisplayName("aud 가 다르거나 없으면 거절")
    void rejectsWrongAudience() {
        assertThat(provider.isSsoAccessTokenForHr(sso("access", "other", List.of("hr")))).isFalse();
        assertThat(provider.isSsoAccessTokenForHr(sso("access", null, List.of("hr")))).isFalse();
    }

    @Test
    @DisplayName("서명이 다른 토큰은 거절")
    void rejectsBadSignature() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        String forged = Jwts.builder().subject("u").claim("type", "access")
                .audience().add("porest-services").and().claim("services", List.of("hr"))
                .signWith(g.generateKeyPair().getPrivate(), Jwts.SIG.RS256).compact();
        assertThat(provider.isSsoAccessTokenForHr(forged)).isFalse();
    }
}
