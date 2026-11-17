package io.groo.ieum.security.filter;

import io.groo.core.security.jwt.SsoJwksKeyLocator;
import io.groo.ieum.common.config.properties.JwtProperties;
import io.groo.ieum.security.jwt.JwtTokenProvider;
import io.groo.ieum.security.principal.JwtClaimsPrincipal;
import io.groo.ieum.security.principal.UserPrincipal;
import io.groo.ieum.security.service.ServiceLogoutStore;
import io.groo.ieum.security.service.TokenRevocationService;
import io.groo.ieum.user.controller.UserApiController;
import io.groo.ieum.user.controller.dto.UserApiDto;
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

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * 보안 점검 CLI-6 — HR 토큰의 SSO 로그인 번호(sso_sid)는 같은 로그인을 잇는 재발급마다 따라가야 한다.
 * 빠지면 그 뒤의 로그아웃이 "이음에서 로그아웃함" 으로 기록되지 않아, 로그인 단추 한 번에 비밀번호 없이 다시 들어온다.
 * 진짜 HR 토큰(HMAC)으로 필터와 재발급 자리를 태운다.
 */
@DisplayName("SSO 로그인 번호 이어 싣기 (CLI-6)")
class SsoSidCarryOverTest {

    private JwtProperties props;
    private JwtTokenProvider provider;
    private JwtAuthenticationFilter filter;
    private User user;

    @BeforeEach
    void setUp() {
        props = new JwtProperties();
        props.setHrSecret("0123456789abcdef0123456789abcdef-test");
        props.setHrAccessExpiration(3_600_000L);
        props.setHrAbsoluteLifetime(2_592_000_000L);
        provider = spy(new JwtTokenProvider(props, mock(SsoJwksKeyLocator.class)));
        provider.init();
        user = mock(User.class);
        when(user.getId()).thenReturn("user1");
        when(user.getSsoUserRowId()).thenReturn(7L);
        when(user.getName()).thenReturn("사용자");
        when(user.getEmail()).thenReturn("user1@example.com");
        when(user.getRoles()).thenReturn(new ArrayList<>());
        when(user.getAllAuthorities()).thenReturn(List.of());
        when(user.isLinkedToSsoUser(any())).thenCallRealMethod();
        UserService userService = mock(UserService.class);
        when(userService.findUserById("user1")).thenReturn(user);
        filter = new JwtAuthenticationFilter(provider, userService, props,
                mock(TokenRevocationService.class), mock(ServiceLogoutStore.class), JsonMapper.builder().build());
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletResponse call(String method, String token) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest(method, "/api/v1/users/user1");
        req.setCookies(new Cookie("hr_access_token", token));
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        return res;
    }

    private UserPrincipal principal() {
        return (UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    private String tokenFromCookie(MockHttpServletResponse res) {
        String setCookie = res.getHeader("Set-Cookie");
        assertThat(setCookie).startsWith("hr_access_token=");
        return setCookie.substring(setCookie.indexOf('=') + 1, setCookie.indexOf(';'));
    }

    @Test
    @DisplayName("필터 - HR 토큰의 sso_sid 를 인증 주체에 싣는다(읽기 요청 · DB 권한으로 다시 만드는 쓰기 요청 모두)")
    void filterExposesSsoSidOnPrincipal() throws Exception {
        String token = provider.createHrAccessToken(user, List.of("ROLE_USER"), List.of(), false, "sid-1");

        call("GET", token);
        assertThat(principal().getSsoSid()).isEqualTo("sid-1");

        SecurityContextHolder.clearContext();
        call("PUT", token);
        assertThat(principal().getSsoSid()).isEqualTo("sid-1");
    }

    @Test
    @DisplayName("필터 - sso_sid 가 없는 옛 토큰은 주체에도 없다(null)")
    void legacyTokenHasNoSsoSidOnPrincipal() throws Exception {
        String legacy = provider.createHrAccessToken(user, List.of("ROLE_USER"), List.of(), false);

        call("GET", legacy);

        assertThat(principal().getSsoSid()).isNull();
    }

    @Test
    @DisplayName("슬라이딩 갱신 - 새 토큰이 옛 토큰의 sso_sid 를 그대로 싣는다. 없던 토큰은 갱신해도 없다")
    void renewalKeepsSsoSid() throws Exception {
        String withSid = provider.createHrAccessToken(user, List.of("ROLE_USER"), List.of(), false, "sid-1");
        doReturn(300_000L).when(provider).getRemainingExpiration(withSid);

        String renewed = tokenFromCookie(call("GET", withSid));

        assertThat(renewed).isNotEqualTo(withSid);
        assertThat(provider.getSsoSidFromHrToken(renewed)).isEqualTo("sid-1");

        SecurityContextHolder.clearContext();
        String legacy = provider.createHrAccessToken(user, List.of("ROLE_USER"), List.of(), false);
        doReturn(300_000L).when(provider).getRemainingExpiration(legacy);

        assertThat(provider.getSsoSidFromHrToken(tokenFromCookie(call("GET", legacy)))).isNull();
    }

    @Test
    @DisplayName("비밀번호 변경 뒤 재발급 - 표식은 풀고 sso_sid 는 그대로 싣는다")
    void passwordChangeReissueKeepsSsoSid() {
        UserApiController controller = new UserApiController(
                mock(UserService.class), mock(MessageSource.class), provider, props);
        JwtClaimsPrincipal marked = new JwtClaimsPrincipal("user1", 7L, "사용자", "user1@example.com",
                List.of(), List.of(), () -> user, true, "sid-1");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(marked, null, List.of()));
        MockHttpServletResponse res = new MockHttpServletResponse();

        controller.changePassword(user, new UserApiDto.ChangePasswordReq("a", "b", "b"), res);

        String reissued = tokenFromCookie(res);
        assertThat(provider.isPasswordChangeRequiredFromHrToken(reissued)).isFalse();
        assertThat(provider.getSsoSidFromHrToken(reissued)).isEqualTo("sid-1");
    }
}
