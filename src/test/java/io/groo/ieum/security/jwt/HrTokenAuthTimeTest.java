package io.groo.ieum.security.jwt;

import io.groo.core.security.jwt.SsoJwksKeyLocator;
import io.groo.ieum.common.config.properties.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** 보안 점검 M-1 — HR 토큰의 SSO 로그인 시각(auth_time). 절대 수명의 기준이며 갱신해도 바뀌지 않는다. */
@DisplayName("HR 토큰 로그인 시각 (M-1)")
class HrTokenAuthTimeTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef-test";
    private final JwtTokenProvider provider;

    HrTokenAuthTimeTest() {
        JwtProperties props = new JwtProperties();
        props.setHrSecret(SECRET);
        props.setHrAccessExpiration(3_600_000L);
        provider = new JwtTokenProvider(props, mock(SsoJwksKeyLocator.class));
        provider.init();
    }

    @Test
    @DisplayName("새로 발급하면 지금이 로그인 시각이다")
    void newTokenStampsNow() {
        long before = System.currentTimeMillis() / 1000 * 1000;
        String token = provider.createHrAccessToken("u", 7L, "n", "e@x.com", List.of(), List.of());

        assertThat(provider.getHrAuthTime(token).getTime()).isBetween(before, System.currentTimeMillis());
    }

    @Test
    @DisplayName("갱신(로그인 시각을 넘김)하면 그 시각을 그대로 싣는다")
    void renewalCarries() {
        Date loggedIn = new Date(1_800_000_000_000L);
        String renewed = provider.createHrAccessToken("u", 7L, "n", "e@x.com", List.of(), List.of(), false, loggedIn);

        assertThat(provider.getHrAuthTime(renewed)).isEqualTo(loggedIn);
    }

    @Test
    @DisplayName("로그인 시각이 없던 옛 토큰은 발급 시각으로 본다")
    void legacyFallsBackToIssuedAt() {
        Date issued = new Date(1_790_000_000_000L);
        String legacy = Jwts.builder().subject("u").issuer("porest-hr").issuedAt(issued)
                .expiration(new Date(System.currentTimeMillis() + 60_000)).claim("type", "hr_access")
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();

        assertThat(provider.getHrAuthTime(legacy)).isEqualTo(issued);
    }
}
