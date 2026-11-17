package io.groo.ieum.security.wiring;

import io.groo.core.security.jwt.SsoJwksKeyLocator;
import io.groo.core.security.web.JsonAuthenticationEntryPoint;
import io.groo.core.security.web.SecurityErrorResponder;
import io.groo.ieum.common.config.properties.JwtProperties;
import io.groo.ieum.security.filter.JwtAuthenticationFilter;
import io.groo.ieum.security.filter.ServiceLogoutEntryPoint;
import io.groo.ieum.security.jwt.JwtTokenProvider;
import io.groo.ieum.security.service.ServiceLogoutStore;
import io.groo.ieum.security.service.TokenRevocationService;
import io.groo.ieum.user.domain.User;
import io.groo.ieum.user.service.UserService;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;
import tools.jackson.databind.json.JsonMapper;

import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 보안 점검 CLI-6 — SSO 토큰 직통 인증의 거절이 응답까지 어떻게 나가는지 Spring Security 필터 체인으로 본다.
 *
 * <p>진짜 {@link JwtAuthenticationFilter}(진짜 RS256 SSO 토큰) → 인가(anyRequest authenticated) → 인증 실패 처리
 * ({@link ServiceLogoutEntryPoint} — {@code SecurityConfig} 와 같은 식으로 만든다) → 응답 본문. 운영 메시지 파일을 쓴다.
 */
@SpringJUnitWebConfig(SsoTokenServiceLogoutWiringTest.Config.class)
@DisplayName("SSO 토큰 직통 인증 거절 응답 — 필터 → 인가 → 인증 실패 응답 (CLI-6)")
class SsoTokenServiceLogoutWiringTest {

    private static final KeyPair SSO_KEYS = rsaKeys();

    private static KeyPair rsaKeys() {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            return g.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @RestController
    static class ProbeController {
        /** 보호된 경로 */
        @GetMapping("/api/v1/probe")
        String probe() {
            return "pong";
        }

        /** 운영에서 인증 없이 열려 있는 경로(로그아웃) 자리 */
        @PostMapping("/api/v1/auth/logout")
        String open() {
            return "open";
        }
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    static class Config {
        @Bean
        JwtProperties jwtProperties() {
            JwtProperties props = new JwtProperties();
            props.setHrSecret("0123456789abcdef0123456789abcdef-wiring-test");
            props.setHrAccessExpiration(3_600_000L);
            return props;
        }

        @Bean
        JwtTokenProvider jwtTokenProvider(JwtProperties props) {
            JwtTokenProvider provider = new JwtTokenProvider(props, new SsoJwksKeyLocator("http://unused.invalid/jwks") {
                @Override
                protected Key locate(JwsHeader header) {
                    return SSO_KEYS.getPublic();
                }
            });
            provider.init();
            return provider;
        }

        @Bean
        UserService userService() {
            return mock(UserService.class);
        }

        @Bean
        ServiceLogoutStore serviceLogoutStore() {
            return mock(ServiceLogoutStore.class);
        }

        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter(JwtTokenProvider provider, UserService userService,
                                                        JwtProperties props, ServiceLogoutStore logoutStore) {
            return new JwtAuthenticationFilter(provider, userService, props, mock(TokenRevocationService.class),
                    logoutStore, JsonMapper.builder().build());
        }

        @Bean
        ProbeController probeController() {
            return new ProbeController();
        }

        @Bean
        SecurityErrorResponder securityErrorResponder() {
            ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
            messages.setBasenames("message/core-messages", "message/messages");
            messages.setDefaultEncoding("UTF-8");
            messages.setFallbackToSystemLocale(false);
            return new SecurityErrorResponder(JsonMapper.builder().build(), messages, new AcceptHeaderLocaleResolver());
        }

        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter,
                                                SecurityErrorResponder responder) throws Exception {
            http.csrf(csrf -> csrf.disable())
                    .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/api/v1/auth/logout").permitAll()
                            .anyRequest().authenticated())
                    // SecurityConfig 와 같은 식 — 로그아웃한 SSO 로그인만 AUTH_010, 나머지는 core 의 기존 응답
                    .exceptionHandling(e -> e.authenticationEntryPoint(
                            new ServiceLogoutEntryPoint(responder, new JsonAuthenticationEntryPoint(responder))))
                    .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
            return http.build();
        }
    }

    @Autowired private WebApplicationContext context;
    @Autowired private UserService userService;
    @Autowired private ServiceLogoutStore serviceLogoutStore;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        reset(userService, serviceLogoutStore);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        User user = mock(User.class);
        when(user.getId()).thenReturn("user1");
        when(user.getSsoUserRowId()).thenReturn(7L);
        when(user.getRoles()).thenReturn(new ArrayList<>());
        when(user.getAllAuthorities()).thenReturn(List.of("ROLE_USER"));
        when(user.isLinkedToSsoUser(any())).thenCallRealMethod();
        when(userService.findUserById("user1")).thenReturn(user);

        // 이음에서 S1 로그인으로 로그아웃해 둔 상태
        when(serviceLogoutStore.isLoggedOut("S1")).thenReturn(true);
    }

    private String ssoBearer(String sid) {
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
        return "Bearer " + b.signWith(SSO_KEYS.getPrivate(), Jwts.SIG.RS256).compact();
    }

    @Test
    @DisplayName("이음에서 로그아웃한 sid 의 SSO 토큰 - 보호된 경로는 401 이고 본문 code 는 AUTH_010 이다")
    void loggedOutSsoLoginGets401WithAuth010() throws Exception {
        mvc.perform(get("/api/v1/probe").header("Authorization", ssoBearer("S1")).header("Accept-Language", "ko"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("""
                        {"success":false,"code":"AUTH_010","message":"로그아웃한 상태예요. 비밀번호를 입력해 다시 로그인해 주세요.","data":null}
                        """, JsonCompareMode.STRICT));

        mvc.perform(get("/api/v1/probe").header("Authorization", ssoBearer("S1")).header("Accept-Language", "en"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("""
                        {"code":"AUTH_010","message":"You signed out of this service. Please sign in again with your password."}
                        """));
    }

    @Test
    @DisplayName("다른 sid · sid 없는 SSO 토큰 - 지금처럼 통과한다")
    void otherSsoLoginsPass() throws Exception {
        mvc.perform(get("/api/v1/probe").header("Authorization", ssoBearer("S2")))
                .andExpect(status().isOk())
                .andExpect(content().string("pong"));
        mvc.perform(get("/api/v1/probe").header("Authorization", ssoBearer(null)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("토큰이 없거나 무효면 - 기존 인증 실패 응답 그대로다(AUTH_010 이 아니다)")
    void otherAuthenticationFailuresKeepTheirResponse() throws Exception {
        String unauthorized = """
                {"success":false,"code":"COMMON_411","message":"인증이 필요합니다.","data":null}
                """;
        mvc.perform(get("/api/v1/probe").header("Accept-Language", "ko"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json(unauthorized, JsonCompareMode.STRICT));
        mvc.perform(get("/api/v1/probe").header("Authorization", "Bearer not-a-token").header("Accept-Language", "ko"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json(unauthorized, JsonCompareMode.STRICT));
    }

    @Test
    @DisplayName("인증 없이 열린 경로 - 로그아웃한 sid 의 SSO 토큰이 실려 와도 무효 토큰처럼 그대로 진행한다")
    void openPathStillProceeds() throws Exception {
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", ssoBearer("S1")))
                .andExpect(status().isOk())
                .andExpect(content().string("open"));
    }
}
