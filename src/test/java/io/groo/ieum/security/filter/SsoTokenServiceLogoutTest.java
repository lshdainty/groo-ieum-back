package io.groo.ieum.security.filter;

import io.groo.core.security.jwt.SsoJwksKeyLocator;
import io.groo.ieum.common.config.properties.JwtProperties;
import io.groo.ieum.security.jwt.JwtTokenProvider;
import io.groo.ieum.security.service.ServiceLogoutStore;
import io.groo.ieum.security.service.TokenRevocationService;
import io.groo.ieum.user.domain.User;
import io.groo.ieum.user.service.UserService;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 보안 점검 CLI-6 — SSO 액세스 토큰을 그대로 들고 오는 직통 인증(하위 호환 경로)도 "이음에서 로그아웃함" 목록을 본다.
 * 인가코드 교환만 막으면, 로그아웃 뒤에도 살아 있는 SSO 로그인으로 받은 SSO 토큰으로 이음 API 를 부를 수 있다.
 *
 * <p>진짜 SSO 토큰(RS256)과 진짜 {@link ServiceLogoutStore} 로 필터를 태운다. Redis 는 키만 기억하는 가짜다.
 */
@DisplayName("SSO 토큰 직통 인증 — 이음에서 로그아웃한 SSO 로그인은 받지 않는다 (CLI-6)")
class SsoTokenServiceLogoutTest {

    private KeyPair ssoKeys;
    private JwtTokenProvider provider;
    private UserService userService;
    private StringRedisTemplate redis;
    private JwtAuthenticationFilter filter;
    private User user;

    private final Set<String> redisKeys = new HashSet<>();
    private boolean redisDown;

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
        props.setHrSecret("0123456789abcdef0123456789abcdef-test");
        props.setHrAccessExpiration(3_600_000L);
        provider = new JwtTokenProvider(props, locator);
        provider.init();

        redis = mock(StringRedisTemplate.class);
        when(redis.hasKey(anyString())).thenAnswer(inv -> {
            if (redisDown) {
                throw new IllegalStateException("redis down");
            }
            return redisKeys.contains(inv.<String>getArgument(0));
        });

        user = mock(User.class);
        when(user.getId()).thenReturn("user1");
        when(user.getSsoUserRowId()).thenReturn(7L);
        when(user.getName()).thenReturn("사용자");
        when(user.getEmail()).thenReturn("user1@example.com");
        when(user.getRoles()).thenReturn(new ArrayList<>());
        when(user.getAllAuthorities()).thenReturn(List.of("ROLE_USER"));
        when(user.isLinkedToSsoUser(any())).thenCallRealMethod();
        userService = mock(UserService.class);
        when(userService.findUserById("user1")).thenReturn(user);

        filter = new JwtAuthenticationFilter(provider, userService, props,
                mock(TokenRevocationService.class), new ServiceLogoutStore(redis), JsonMapper.builder().build());
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    /** groo-sso-back 이 내는 토큰 모양. type 은 access · service · refresh 등, sid 가 null 이면 클레임 없음 */
    private String ssoToken(String type, String sid) {
        var b = Jwts.builder()
                .header().keyId("k1").and()
                .subject("user1")
                .issuer("porest-sso")
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 600_000))
                .claim("type", type)
                .claim("userNo", 7L)
                .claim("services", List.of("hr"));
        b.audience().add("porest-services");
        if (sid != null) {
            b.claim("sid", sid);
        }
        return b.signWith(ssoKeys.getPrivate(), Jwts.SIG.RS256).compact();
    }

    private void loggedOut(String sid) {
        redisKeys.add("ieum:logged-out-sid:" + sid);
    }

    /** Authorization: Bearer 로 보낸 요청 — 필터가 인증을 세웠는지와 체인을 이어 갔는지를 본다 */
    private MockHttpServletRequest bearer(String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");
        request.addHeader("Authorization", "Bearer " + token);
        return run(request);
    }

    private MockHttpServletRequest run(MockHttpServletRequest request) throws Exception {
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        // 무효 토큰과 같은 방식 — 필터가 응답을 끊지 않고 넘긴다(401 은 뒤의 인증 실패 처리 몫)
        assertThat(chain.getRequest()).as("체인을 이어 간다").isNotNull();
        return request;
    }

    private boolean authenticated() {
        return SecurityContextHolder.getContext().getAuthentication() != null;
    }

    private Object loggedOutMarker(MockHttpServletRequest request) {
        return request.getAttribute(JwtAuthenticationFilter.SSO_LOGIN_LOGGED_OUT_ATTRIBUTE);
    }

    @Test
    @DisplayName("이음에서 로그아웃한 sid 의 SSO 토큰 - 인증을 세우지 않고, AUTH_010 표식을 남기며, 사용자 조회도 하지 않는다")
    void loggedOutSidIsNotAuthenticated() throws Exception {
        loggedOut("S1");

        MockHttpServletRequest request = bearer(ssoToken("access", "S1"));

        assertThat(authenticated()).isFalse();
        assertThat(loggedOutMarker(request)).isEqualTo(Boolean.TRUE);
        verify(userService, never()).findUserById(any());
    }

    @Test
    @DisplayName("쿠키(hr_access_token)에 실어 보낸 SSO 토큰도 같은 분기라 똑같이 막힌다")
    void loggedOutSidInCookieIsNotAuthenticated() throws Exception {
        loggedOut("S1");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");
        request.setCookies(new Cookie("hr_access_token", ssoToken("access", "S1")));

        run(request);

        assertThat(authenticated()).isFalse();
        assertThat(loggedOutMarker(request)).isEqualTo(Boolean.TRUE);
    }

    @Test
    @DisplayName("다른 sid(비밀번호로 다시 로그인한 새 로그인)의 SSO 토큰 - 지금처럼 인증된다")
    void otherSidIsAuthenticated() throws Exception {
        loggedOut("S1");

        MockHttpServletRequest request = bearer(ssoToken("access", "S2"));

        assertThat(authenticated()).isTrue();
        assertThat(loggedOutMarker(request)).isNull();
    }

    @Test
    @DisplayName("sid 가 없는 옛 SSO 토큰 - Redis 를 보지 않고 지금처럼 인증된다")
    void tokenWithoutSidIsAuthenticatedWithoutRedis() throws Exception {
        loggedOut("S1");

        MockHttpServletRequest request = bearer(ssoToken("access", null));

        assertThat(authenticated()).isTrue();
        assertThat(loggedOutMarker(request)).isNull();
        verify(redis, never()).hasKey(anyString());
    }

    @Test
    @DisplayName("Redis 조회 실패 - 로그아웃한 sid 라도 인증을 막지 않는다(fail-open)")
    void redisFailureDoesNotBlock() throws Exception {
        loggedOut("S1");
        redisDown = true;

        MockHttpServletRequest request = bearer(ssoToken("access", "S1"));

        assertThat(authenticated()).isTrue();
        assertThat(loggedOutMarker(request)).isNull();
        verify(redis).hasKey("ieum:logged-out-sid:S1");
    }

    @Test
    @DisplayName("서비스 토큰 등 access 가 아닌 SSO 토큰 - 전처럼 이 경로에서 받지 않고, 목록을 보지도 표식을 남기지도 않는다")
    void nonAccessTokensNeverReachTheLogoutList() throws Exception {
        loggedOut("S1");

        // 서버 간 호출용 서비스 토큰에는 사용자 sid 가 없다. 있더라도(S1) access 가 아니면 대조 앞에서 걸러진다
        for (String[] typeAndSid : new String[][]{
                {"service", null}, {"service", "S1"}, {"refresh", "S1"}, {"link_ticket", null}, {"reauth", "S1"}}) {
            MockHttpServletRequest request = bearer(ssoToken(typeAndSid[0], typeAndSid[1]));

            assertThat(authenticated()).as(typeAndSid[0]).isFalse();
            assertThat(loggedOutMarker(request)).as(typeAndSid[0]).isNull();
        }
        verify(redis, never()).hasKey(anyString());
        verify(userService, never()).findUserById(any());
    }

    @Test
    @DisplayName("HR 토큰(프런트의 쿠키 인증) - 이미 들어와 있는 세션이라 이 목록을 보지 않는다(요청마다 Redis 를 더 읽지 않는다)")
    void hrTokenPathDoesNotReadLogoutList() throws Exception {
        loggedOut("S1");
        String hrToken = provider.createHrAccessToken(user, List.of("ROLE_USER"), List.of(), false, "S1");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");
        request.setCookies(new Cookie("hr_access_token", hrToken));

        run(request);

        assertThat(authenticated()).isTrue();
        assertThat(loggedOutMarker(request)).isNull();
        verify(redis, never()).hasKey(anyString());
    }
}
