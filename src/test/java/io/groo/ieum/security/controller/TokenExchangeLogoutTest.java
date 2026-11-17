package io.groo.ieum.security.controller;

import io.groo.ieum.common.config.properties.JwtProperties;
import io.groo.ieum.security.jwt.JwtTokenProvider;
import io.groo.ieum.security.service.ServiceLogoutStore;
import io.groo.ieum.security.service.TokenExchangeService;
import io.groo.ieum.security.service.TokenRevocationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("로그아웃 토큰 무효화")
class TokenExchangeLogoutTest {

    private final JwtTokenProvider provider = mock(JwtTokenProvider.class);
    private final TokenRevocationService revocation = mock(TokenRevocationService.class);
    private final ServiceLogoutStore logoutStore = mock(ServiceLogoutStore.class);
    private final TokenExchangeController controller = new TokenExchangeController(
            mock(TokenExchangeService.class), mock(JwtProperties.class), provider, revocation, logoutStore);

    @Test
    @DisplayName("HR 토큰이면 jti 를 남은 수명만큼 차단하고 쿠키를 지운다")
    void revokes() {
        when(provider.validateHrToken("t")).thenReturn(true);
        when(provider.getHrJti("t")).thenReturn("j1");
        when(provider.getRemainingExpiration("t")).thenReturn(120_000L);
        MockHttpServletResponse res = new MockHttpServletResponse();

        controller.logout("t", res);

        verify(revocation).revoke("j1", 120_000L);
        assertThat(res.getHeader("Set-Cookie")).contains("Max-Age=0");
    }

    @Test
    @DisplayName("Redis 가 실패해도 쿠키는 지운다")
    void cookieClearedEvenIfRedisFails() {
        when(provider.validateHrToken("t")).thenReturn(true);
        when(provider.getHrJti("t")).thenReturn("j1");
        doThrow(new RuntimeException("down")).when(revocation).revoke(any(), anyLong());
        MockHttpServletResponse res = new MockHttpServletResponse();

        controller.logout("t", res);

        assertThat(res.getHeader("Set-Cookie")).contains("Max-Age=0");
    }

    // ==================== 서비스만 로그아웃 (보안 점검 CLI-6) ====================

    @Test
    @DisplayName("보안 점검 CLI-6 - HR 토큰이 실고 있는 SSO 로그인 번호(sso_sid)를 '이음에서 로그아웃함' 목록에 넣는다")
    void remembersSsoLoginOnLogout() {
        when(provider.validateHrToken("t")).thenReturn(true);
        when(provider.getHrJti("t")).thenReturn("j1");
        when(provider.getRemainingExpiration("t")).thenReturn(120_000L);
        when(provider.getSsoSidFromHrToken("t")).thenReturn("sid-1");
        MockHttpServletResponse res = new MockHttpServletResponse();

        controller.logout("t", res);

        verify(logoutStore).remember("sid-1");
        verify(revocation).revoke("j1", 120_000L);
        assertThat(res.getHeader("Set-Cookie")).contains("Max-Age=0");
    }

    @Test
    @DisplayName("보안 점검 CLI-6 - 기록(Redis)이 실패해도 jti 는 차단돼 있고 쿠키는 지운다")
    void cookieClearedEvenIfRememberFails() {
        when(provider.validateHrToken("t")).thenReturn(true);
        when(provider.getHrJti("t")).thenReturn("j1");
        when(provider.getRemainingExpiration("t")).thenReturn(120_000L);
        when(provider.getSsoSidFromHrToken("t")).thenReturn("sid-1");
        doThrow(new RuntimeException("down")).when(logoutStore).remember(any());
        MockHttpServletResponse res = new MockHttpServletResponse();

        controller.logout("t", res);

        verify(revocation).revoke("j1", 120_000L);
        assertThat(res.getHeader("Set-Cookie")).contains("Max-Age=0");
    }

    @Test
    @DisplayName("보안 점검 CLI-6 - jti 차단이 실패해도 SSO 로그인 기록은 따로 시도한다")
    void remembersEvenIfJtiRevocationFails() {
        when(provider.validateHrToken("t")).thenReturn(true);
        when(provider.getHrJti("t")).thenReturn("j1");
        when(provider.getSsoSidFromHrToken("t")).thenReturn("sid-1");
        doThrow(new RuntimeException("down")).when(revocation).revoke(any(), anyLong());
        MockHttpServletResponse res = new MockHttpServletResponse();

        controller.logout("t", res);

        verify(logoutStore).remember("sid-1");
        assertThat(res.getHeader("Set-Cookie")).contains("Max-Age=0");
    }

    @Test
    @DisplayName("보안 점검 CLI-6 - 쿠키가 없거나 HR 토큰이 아니면(만료 · 위조) 아무것도 기록하지 않고 쿠키만 지운다")
    void remembersNothingWithoutValidHrToken() {
        when(provider.validateHrToken("bad")).thenReturn(false);

        for (String token : new String[]{null, " ", "bad"}) {
            MockHttpServletResponse res = new MockHttpServletResponse();
            controller.logout(token, res);
            assertThat(res.getHeader("Set-Cookie")).as("token=" + token).contains("Max-Age=0");
        }

        verifyNoInteractions(logoutStore, revocation);
        verify(provider, never()).getSsoSidFromHrToken(any());
    }
}
