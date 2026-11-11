package io.groo.ieum.security.filter;

import io.groo.ieum.common.config.properties.JwtProperties;
import io.groo.ieum.security.controller.TokenExchangeController;
import io.groo.ieum.security.jwt.JwtTokenProvider;
import io.groo.core.security.jwt.SsoJwksKeyLocator;
import io.groo.ieum.security.principal.JwtClaimsPrincipal;
import io.groo.ieum.security.principal.UserPrincipal;
import io.groo.ieum.security.service.TokenExchangeService;
import io.groo.ieum.security.service.TokenRevocationService;
import io.groo.ieum.user.controller.UserApiController;
import io.groo.ieum.user.domain.User;
import io.groo.ieum.user.service.UserService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * 비밀번호 강제 변경 — 실제 HR 토큰(HMAC)으로 필터를 태운다.
 * 표식(pwdChange=Y) 토큰은 허용 목록 밖 요청이 403(AUTH_003)이고, 슬라이딩 갱신과 비밀번호 변경 뒤 재발급이
 * 표식을 바르게 유지·해제하는지, 표식이 없는 옛 토큰은 N 으로 취급되는지 본다.
 */
@DisplayName("비밀번호 강제 변경")
class PasswordChangeRequiredTest {

    private JwtProperties props;
    private JwtTokenProvider provider;
    private UserService userService;
    private JwtAuthenticationFilter filter;
    private User user;

    @BeforeEach
    void setUp() {
        props = new JwtProperties();
        props.setHrSecret("0123456789abcdef0123456789abcdef-test");
        props.setHrAccessExpiration(3_600_000L);
        provider = spy(new JwtTokenProvider(props, mock(SsoJwksKeyLocator.class)));
        provider.init();
        userService = mock(UserService.class);
        user = mock(User.class);
        when(user.getId()).thenReturn("user1");
        when(user.getSsoUserRowId()).thenReturn(7L);
        when(user.getName()).thenReturn("사용자");
        when(user.getEmail()).thenReturn("user1@example.com");
        when(user.getRoles()).thenReturn(new java.util.ArrayList<>());
        when(user.getAllAuthorities()).thenReturn(List.of());
        when(userService.findUserById("user1")).thenReturn(user);
        filter = new JwtAuthenticationFilter(provider, userService, props,
                mock(TokenRevocationService.class), JsonMapper.builder().build());
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private String hrToken(boolean pwRequired) {
        return provider.createHrAccessToken(user, List.of("ROLE_USER"), List.of(), pwRequired);
    }

    private MockHttpServletResponse call(String method, String uri, String token) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest(method, uri);
        req.setRequestURI(uri);
        req.setCookies(new Cookie("hr_access_token", token));
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(req, res, chain);
        return res;
    }

    private boolean reachedController(MockHttpServletResponse res) throws Exception {
        return res.getStatus() != 403 && res.getContentAsString().isEmpty();
    }

    @Test
    @DisplayName("표식 Y 토큰 - 허용 목록 밖 요청은 403 AUTH_003 으로 막힌다")
    void blocksOtherApis() throws Exception {
        String t = hrToken(true);
        for (String[] r : new String[][]{
                {"GET", "/api/v1/users"}, {"GET", "/api/v1/dashboard"}, {"PATCH", "/api/v1/users/me/profile"},
                {"POST", "/api/v1/vacations"}, {"GET", "/api/v1/users/me/password"}}) {
            MockHttpServletResponse res = call(r[0], r[1], t);
            assertThat(res.getStatus()).as(r[0] + " " + r[1]).isEqualTo(403);
            assertThat(res.getContentAsString()).contains("AUTH_003");
        }
    }

    @Test
    @DisplayName("표식 Y 토큰 - 허용 목록(로그인 확인 · 비밀번호 변경 · 로그아웃 · 코드 교환)은 막히지 않는다")
    void allowsAllowList() throws Exception {
        String t = hrToken(true);
        for (String[] r : new String[][]{
                {"GET", "/api/v1/login/check"}, {"PATCH", "/api/v1/users/me/password"},
                {"POST", "/api/v1/auth/logout"}, {"POST", "/api/v1/auth/exchange-code"}}) {
            MockHttpServletResponse res = call(r[0], r[1], t);
            assertThat(reachedController(res)).as(r[0] + " " + r[1]).isTrue();
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("표식 없는 토큰(N) 과 표식이 없던 옛 토큰 - 모든 API 가 그대로 통과한다")
    void noMarkerIsN() throws Exception {
        String t = hrToken(false);
        assertThat(provider.isPasswordChangeRequiredFromHrToken(t)).isFalse();
        assertThat(reachedController(call("GET", "/api/v1/users", t))).isTrue();

        // 클레임 자체가 없던 옛 3-인자 발급 토큰
        String legacy = provider.createHrAccessToken("user1", 7L, "사용자", "user1@example.com", List.of(), List.of());
        assertThat(provider.isPasswordChangeRequiredFromHrToken(legacy)).isFalse();
        assertThat(reachedController(call("GET", "/api/v1/users", legacy))).isTrue();
    }

    @Test
    @DisplayName("슬라이딩 갱신 - 표식 Y 는 새 토큰에도 남고, N 은 그대로 N")
    void renewalKeepsMarker() throws Exception {
        for (boolean marked : new boolean[]{true, false}) {
            String t = hrToken(marked);
            doReturn(300_000L).when(provider).getRemainingExpiration(t);

            MockHttpServletResponse res = call("GET", "/api/v1/login/check", t);

            String setCookie = res.getHeader("Set-Cookie");
            assertThat(setCookie).as("marked=" + marked).contains("hr_access_token=");
            String renewed = setCookie.substring(setCookie.indexOf('=') + 1, setCookie.indexOf(';'));
            assertThat(provider.isPasswordChangeRequiredFromHrToken(renewed)).isEqualTo(marked);
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("SSO 토큰을 헤더로 직접 보내는 길 - 같은 판정으로 막힌다")
    void ssoHeaderPathIsBlocked() throws Exception {
        doReturn(true).when(provider).validateToken("sso");
        doReturn(false).when(provider).isHrToken("sso");
        doReturn(true).when(provider).isSsoAccessTokenForHr("sso");
        doReturn("user1").when(provider).getUserId("sso");

        doReturn(true).when(provider).isPasswordChangeRequiredFromSsoToken("sso");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/users");
        req.setRequestURI("/api/v1/users");
        req.addHeader("Authorization", "Bearer sso");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(res.getContentAsString()).contains("AUTH_003");

        // 허용 목록은 통과
        SecurityContextHolder.clearContext();
        MockHttpServletRequest ok = new MockHttpServletRequest("GET", "/api/v1/login/check");
        ok.setRequestURI("/api/v1/login/check");
        ok.addHeader("Authorization", "Bearer sso");
        MockHttpServletResponse okRes = new MockHttpServletResponse();
        filter.doFilter(ok, okRes, new MockFilterChain());
        assertThat(okRes.getStatus()).isEqualTo(200);
        assertThat(((UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isPasswordChangeRequired()).isTrue();

        // 클레임 없는 SSO 토큰(N)은 그대로 통과
        SecurityContextHolder.clearContext();
        doReturn(false).when(provider).isPasswordChangeRequiredFromSsoToken("sso");
        MockHttpServletRequest n = new MockHttpServletRequest("GET", "/api/v1/users");
        n.setRequestURI("/api/v1/users");
        n.addHeader("Authorization", "Bearer sso");
        MockHttpServletResponse nRes = new MockHttpServletResponse();
        filter.doFilter(n, nRes, new MockFilterChain());
        assertThat(nRes.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("비밀번호 변경 성공 - 표식 Y 였으면 표식 없는 새 쿠키를 내리고, N 이었으면 쿠키를 건드리지 않는다")
    void changePasswordReissuesCookie() throws Exception {
        UserService svc = mock(UserService.class);
        UserApiController controller = new UserApiController(svc, mock(MessageSource.class), provider, props);
        var req = new io.groo.ieum.user.controller.dto.UserApiDto.ChangePasswordReq("a", "b", "b");

        // 표식 Y 인 인증 상태
        setPrincipal(true);
        MockHttpServletResponse res = new MockHttpServletResponse();
        controller.changePassword(user, req, res);
        String setCookie = res.getHeader("Set-Cookie");
        assertThat(setCookie).contains("hr_access_token=").contains("HttpOnly");
        String issued = setCookie.substring(setCookie.indexOf('=') + 1, setCookie.indexOf(';'));
        assertThat(provider.isPasswordChangeRequiredFromHrToken(issued)).isFalse();
        // 새 쿠키로는 모든 API 통과
        SecurityContextHolder.clearContext();
        assertThat(reachedController(call("GET", "/api/v1/users", issued))).isTrue();

        // 표식 N 이면 쿠키를 내리지 않는다
        setPrincipal(false);
        MockHttpServletResponse res2 = new MockHttpServletResponse();
        controller.changePassword(user, req, res2);
        assertThat(res2.getHeader("Set-Cookie")).isNull();
    }

    private void setPrincipal(boolean pwRequired) {
        JwtClaimsPrincipal p = new JwtClaimsPrincipal("user1", 7L, "사용자", "user1@example.com",
                List.of(), List.of(), () -> user, pwRequired);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p, null, List.of()));
    }

    @Test
    @DisplayName("코드 교환 - SSO 토큰의 표식을 HR 토큰에 옮겨 싣는다")
    void exchangeCarriesMarker() {
        JwtTokenProvider p = mock(JwtTokenProvider.class);
        io.groo.ieum.client.sso.SsoApiClient sso = mock(io.groo.ieum.client.sso.SsoApiClient.class);
        TokenExchangeService svc = new TokenExchangeService(p, userService, sso);
        when(p.validateSsoToken("s")).thenReturn(true);
        when(p.getServicesFromSsoToken("s")).thenReturn(List.of("hr"));
        when(p.getUserIdFromSsoToken("s")).thenReturn("user1");
        when(p.isPasswordChangeRequiredFromSsoToken("s")).thenReturn(true);
        when(p.createHrAccessToken(any(User.class), any(), any(), org.mockito.ArgumentMatchers.eq(true))).thenReturn("hr-y");

        assertThat(svc.exchange("s").getAccessToken()).isEqualTo("hr-y");
    }

    @Test
    @DisplayName("로그인 확인 응답 - 인증 객체의 표식을 password_change_required 로 내려준다")
    void loginCheckReflectsMarker() {
        io.groo.ieum.security.controller.AuthController ctl = new io.groo.ieum.security.controller.AuthController(userService);
        for (boolean marked : new boolean[]{true, false}) {
            setPrincipal(marked);
            var info = ctl.getUserInfo().getData();
            assertThat(info.getPasswordChangeRequired()).isEqualTo(marked ? io.groo.core.type.YNType.Y : io.groo.core.type.YNType.N);
        }
    }
}
