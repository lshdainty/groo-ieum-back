package io.groo.ieum.security.controller;

import io.groo.core.controller.GlobalExceptionHandler;
import io.groo.core.security.jwt.SsoJwksKeyLocator;
import io.groo.core.util.MessageResolver;
import io.groo.ieum.client.sso.SsoApiClient;
import io.groo.ieum.common.config.properties.JwtProperties;
import io.groo.ieum.security.jwt.JwtTokenProvider;
import io.groo.ieum.security.service.ServiceLogoutStore;
import io.groo.ieum.security.service.TokenExchangeService;
import io.groo.ieum.security.service.TokenRevocationService;
import io.groo.ieum.user.domain.User;
import io.groo.ieum.user.service.UserService;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 보안 점검 CLI-6 — "서비스만 로그아웃" 을 HTTP 로 끝까지 태운다.
 *
 * <p>진짜 SSO 토큰(RS256 서명 · sid 클레임) → 진짜 {@link TokenExchangeService} → 진짜 HR 토큰(sso_sid) →
 * 진짜 {@link TokenExchangeController} 로그아웃 → 진짜 {@link ServiceLogoutStore} → 진짜 전역 예외 처리기(응답 본문).
 * 목은 SSO 호출 · 사용자 조회 · Redis(키만 기억하는 가짜)뿐이다.
 */
@DisplayName("서비스만 로그아웃 — 로그아웃한 SSO 로그인으로 돌아오면 거절 (CLI-6)")
class ServiceLogoutFlowTest {

    private static final String CODE_BODY =
            "{\"code\":\"c\",\"codeVerifier\":\"v\",\"redirectUri\":\"https://ieum.test/cb\"}";

    private KeyPair ssoKeys;
    private JwtTokenProvider provider;
    private SsoApiClient ssoApiClient;
    private MockMvc mvc;

    /** 가짜 Redis — 키와 수명만 기억한다 */
    private final Map<String, Duration> redisKeys = new HashMap<>();
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
        props.setHrSecret("0123456789abcdef0123456789abcdef-flow-test");
        props.setHrAccessExpiration(3_600_000L);
        provider = new JwtTokenProvider(props, locator);
        provider.init();

        StringRedisTemplate redis = fakeRedis();

        User user = mock(User.class);
        when(user.getId()).thenReturn("user1");
        when(user.getSsoUserRowId()).thenReturn(7L);
        when(user.getName()).thenReturn("사용자");
        when(user.getEmail()).thenReturn("user1@example.com");
        when(user.getRoles()).thenReturn(new ArrayList<>());
        when(user.getAllAuthorities()).thenReturn(List.of());
        when(user.isLinkedToSsoUser(any())).thenCallRealMethod();
        UserService userService = mock(UserService.class);
        when(userService.findUserById("user1")).thenReturn(user);

        ssoApiClient = mock(SsoApiClient.class);
        ServiceLogoutStore store = new ServiceLogoutStore(redis);
        TokenExchangeService service = new TokenExchangeService(provider, userService, ssoApiClient, store);
        TokenExchangeController controller = new TokenExchangeController(
                service, props, provider, new TokenRevocationService(redis, props), store);

        // 운영과 같은 메시지 파일(message/messages*.properties)로 응답 메시지까지 본다
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasenames("message/messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(new MessageResolver(messages)))
                .build();
    }

    @SuppressWarnings("unchecked")
    private StringRedisTemplate fakeRedis() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        doAnswer(inv -> {
            failIfDown();
            redisKeys.put(inv.getArgument(0), inv.getArgument(2));
            return null;
        }).when(ops).set(anyString(), anyString(), any(Duration.class));
        when(redis.hasKey(anyString())).thenAnswer(inv -> {
            failIfDown();
            return redisKeys.containsKey(inv.<String>getArgument(0));
        });
        return redis;
    }

    private void failIfDown() {
        if (redisDown) {
            throw new IllegalStateException("redis down");
        }
    }

    /** groo-sso-back 이 내는 access 토큰 모양. sid 가 null 이면 클레임 없는 옛 토큰 */
    private String ssoToken(String sid) {
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
        b.audience().add("porest-services");
        if (sid != null) {
            b.claim("sid", sid);
        }
        return b.signWith(ssoKeys.getPrivate(), Jwts.SIG.RS256).compact();
    }

    /** 인가코드 교환 — SSO 가 이 sid 의 토큰을 내준 상황 */
    private MvcResult exchange(String sid, String language) throws Exception {
        when(ssoApiClient.exchangeOAuthCode("c", "v", "https://ieum.test/cb")).thenReturn(ssoToken(sid));
        return mvc.perform(post("/api/v1/auth/exchange-code")
                        .contentType(MediaType.APPLICATION_JSON).content(CODE_BODY)
                        .header("Accept-Language", language))
                .andReturn();
    }

    /** 로그인에 성공해 쿠키로 받은 HR 토큰 */
    private String login(String sid) throws Exception {
        MvcResult result = exchange(sid, "ko");
        assertThat(result.getResponse().getStatus()).as("sid=" + sid + " 로그인").isEqualTo(200);
        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).startsWith("hr_access_token=");
        return setCookie.substring(setCookie.indexOf('=') + 1, setCookie.indexOf(';'));
    }

    private void logout(String hrToken) throws Exception {
        mvc.perform(post("/api/v1/auth/logout").cookie(new Cookie("hr_access_token", hrToken)))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
    }

    @Test
    @DisplayName("로그인 → 로그아웃 → 같은 SSO 로그인(sid)으로 돌아오면 401 AUTH_010, 비밀번호로 다시 로그인한 새 sid 는 통과")
    void loggedOutSsoLoginIsRejectedUntilNewLogin() throws Exception {
        // 1) 로그인 — HR 토큰이 SSO 로그인 번호를 싣는다
        String hrToken = login("S1");
        assertThat(provider.getSsoSidFromHrToken(hrToken)).isEqualTo("S1");
        assertThat(redisKeys).isEmpty();

        // 2) 이음에서 로그아웃 — jti 차단 + 그 SSO 로그인을 90일 기억
        logout(hrToken);
        assertThat(redisKeys).containsEntry("logged-out-sid:S1", Duration.ofDays(90));
        assertThat(redisKeys).containsKey("revoked-jti:" + provider.getHrJti(hrToken));

        // 3) 로그인 단추만 다시 누름 — SSO 가 비밀번호 없이 같은 로그인(S1)의 토큰을 내준다 → 거절, 쿠키 없음
        MvcResult rejected = exchange("S1", "ko");
        assertThat(rejected.getResponse().getStatus()).isEqualTo(401);
        assertThat(rejected.getResponse().getHeader("Set-Cookie")).isNull();
        // 본문은 이 네 필드가 전부다 — 프런트는 code 로 구분한다
        content().json("""
                {"success":false,"code":"AUTH_010","message":"로그아웃한 상태예요. 비밀번호를 입력해 다시 로그인해 주세요.","data":null}
                """, JsonCompareMode.STRICT).match(rejected);

        // 4) 비밀번호를 다시 입력 — SSO 가 새 로그인(S2)을 낸다 → 통과
        String again = login("S2");
        assertThat(provider.getSsoSidFromHrToken(again)).isEqualTo("S2");

        // 5) 로그아웃한 S1 은 그 뒤에도 계속 거절된다
        assertThat(exchange("S1", "ko").getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("거절 응답 - 영어 로케일이면 영어 메시지, 코드는 같다")
    void rejectionMessageInEnglish() throws Exception {
        logout(login("S1"));

        MvcResult rejected = exchange("S1", "en");

        assertThat(rejected.getResponse().getStatus()).isEqualTo(401);
        content().json("""
                {"success":false,"code":"AUTH_010",
                 "message":"You signed out of this service. Please sign in again with your password."}
                """).match(rejected);
    }

    @Test
    @DisplayName("sid 가 없는 옛 SSO 토큰 - HR 토큰에 싣지 않고, 로그아웃해도 기록하지 않으며, 다시 들어올 때도 막지 않는다")
    void legacySsoTokenWithoutSidIsSkipped() throws Exception {
        String hrToken = login(null);
        assertThat(provider.getSsoSidFromHrToken(hrToken)).isNull();

        logout(hrToken);
        assertThat(redisKeys.keySet()).noneMatch(key -> key.startsWith("logged-out-sid:"));

        login(null);
    }

    @Test
    @DisplayName("Redis 조회 실패 - 로그아웃한 sid 라도 로그인을 막지 않는다(fail-open)")
    void redisFailureDoesNotBlockLogin() throws Exception {
        logout(login("S1"));
        assertThat(exchange("S1", "ko").getResponse().getStatus()).isEqualTo(401);

        redisDown = true;

        String hrToken = login("S1");
        assertThat(provider.getSsoSidFromHrToken(hrToken)).isEqualTo("S1");
    }

    @Test
    @DisplayName("Redis 기록 실패 - 로그아웃은 200 이고 쿠키를 지운다(기록만 남지 않는다)")
    void redisFailureDoesNotBlockLogout() throws Exception {
        String hrToken = login("S1");
        redisDown = true;

        logout(hrToken);

        assertThat(redisKeys).isEmpty();
    }
}
