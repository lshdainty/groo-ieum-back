package io.groo.ieum.security.jwt;

import io.groo.core.security.jwt.SsoJwksKeyLocator;
import io.groo.ieum.common.config.properties.JwtProperties;
import io.groo.ieum.user.domain.User;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 보안 점검 CLI-6 — SSO 로그인 번호. SSO 토큰의 {@code sid} 를 읽어 HR 토큰의 {@code sso_sid} 로 옮겨 싣고 다시 꺼낸다.
 * 진짜 서명(SSO=RS256, HR=HMAC)으로 발급 · 파싱한다.
 */
@DisplayName("HR 토큰의 SSO 로그인 번호 (CLI-6)")
class HrTokenSsoSidTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef-test";

    private KeyPair ssoKeys;
    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        ssoKeys = g.generateKeyPair();
        SsoJwksKeyLocator locator = new SsoJwksKeyLocator("http://unused.invalid/jwks") {
            @Override
            protected Key locate(JwsHeader header) {
                return ssoKeys.getPublic();
            }
        };
        JwtProperties props = new JwtProperties();
        props.setHrSecret(SECRET);
        props.setHrAccessExpiration(3_600_000L);
        provider = new JwtTokenProvider(props, locator);
        provider.init();
    }

    /** groo-sso-back 이 내는 access 토큰 모양 — sid 는 문자열 클레임이고, 옛 토큰에는 없다 */
    private String ssoToken(Object sid) {
        var b = Jwts.builder()
                .header().keyId("k1").and()
                .subject("user1")
                .issuer("porest-sso")
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 600_000))
                .claim("type", "access")
                .claim("userNo", 7L)
                .claim("services", List.of("hr"));
        if (sid != null) {
            b.claim("sid", sid);
        }
        return b.signWith(ssoKeys.getPrivate(), Jwts.SIG.RS256).compact();
    }

    @Test
    @DisplayName("SSO 토큰 - sid 클레임을 읽는다. 없는 옛 토큰 · 빈 값은 null")
    void readsSidFromSsoToken() {
        assertThat(provider.getSidFromSsoToken(ssoToken("sid-abc"))).isEqualTo("sid-abc");
        assertThat(provider.getSidFromSsoToken(ssoToken(null))).isNull();
        assertThat(provider.getSidFromSsoToken(ssoToken(" "))).isNull();
    }

    @Test
    @DisplayName("SSO 토큰 - sid 가 문자열이 아니어도 예외 없이 글자로 읽는다")
    void readsNonStringSid() {
        assertThat(provider.getSidFromSsoToken(ssoToken(12345L))).isEqualTo("12345");
    }

    @Test
    @DisplayName("HR 토큰 - 실어 준 SSO 로그인 번호를 sso_sid 클레임으로 싣고 그대로 꺼낸다")
    void roundTrip() {
        User user = mock(User.class);
        when(user.getId()).thenReturn("user1");
        when(user.getSsoUserRowId()).thenReturn(7L);

        String token = provider.createHrAccessToken(user, List.of("ROLE_USER"), List.of(), false, "sid-abc");

        assertThat(provider.getSsoSidFromHrToken(token)).isEqualTo("sid-abc");
        // 클레임 이름은 sso_sid 다 — SSO 토큰의 sid 와 구분한다
        var claims = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .build().parseSignedClaims(token).getPayload();
        assertThat(claims.get("sso_sid", String.class)).isEqualTo("sid-abc");
        assertThat(claims.get("sid")).isNull();
        // 다른 클레임은 그대로다
        assertThat(provider.validateHrToken(token)).isTrue();
        assertThat(provider.getUserId(token)).isEqualTo("user1");
        assertThat(provider.getSsoUserRowIdFromHrToken(token)).isEqualTo(7L);
        assertThat(provider.isPasswordChangeRequiredFromHrToken(token)).isFalse();
    }

    @Test
    @DisplayName("HR 토큰 - SSO 로그인 번호가 없으면(null · 빈 값) 클레임을 싣지 않는다")
    void noClaimWithoutSid() {
        for (String sid : new String[]{null, "", "  "}) {
            String token = provider.createHrAccessToken("u", 7L, "n", "e@x.com", List.of(), List.of(), false, null, sid);

            assertThat(provider.getSsoSidFromHrToken(token)).as("sid=[" + sid + "]").isNull();
            assertThat(Jwts.parser().verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                    .build().parseSignedClaims(token).getPayload().containsKey("sso_sid")).isFalse();
        }
    }

    @Test
    @DisplayName("HR 토큰 - 이 클레임이 생기기 전의 발급 경로 · 옛 토큰은 null")
    void legacyTokensHaveNoSid() {
        // SSO 로그인 번호를 받지 않는 기존 발급 경로
        assertThat(provider.getSsoSidFromHrToken(
                provider.createHrAccessToken("u", 7L, "n", "e@x.com", List.of(), List.of()))).isNull();
        assertThat(provider.getSsoSidFromHrToken(
                provider.createHrAccessToken("u", 7L, "n", "e@x.com", List.of(), List.of(), true, new Date()))).isNull();

        // 배포 전에 발급돼 있던 토큰
        String legacy = Jwts.builder().subject("u").issuer("porest-hr").issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000)).claim("type", "hr_access")
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
        assertThat(provider.getSsoSidFromHrToken(legacy)).isNull();
    }

    @Test
    @DisplayName("갱신(옛 토큰의 값을 넘김)하면 SSO 로그인 번호 · 로그인 시각 · 비밀번호 표식을 함께 옮겨 싣는다")
    void renewalCarries() {
        Date loggedIn = new Date(1_800_000_000_000L);

        String renewed = provider.createHrAccessToken("u", 7L, "n", "e@x.com", List.of(), List.of(),
                true, loggedIn, "sid-abc");

        assertThat(provider.getSsoSidFromHrToken(renewed)).isEqualTo("sid-abc");
        assertThat(provider.getHrAuthTime(renewed)).isEqualTo(loggedIn);
        assertThat(provider.isPasswordChangeRequiredFromHrToken(renewed)).isTrue();
    }
}
