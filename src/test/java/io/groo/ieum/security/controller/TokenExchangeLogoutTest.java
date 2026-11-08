package io.groo.ieum.security.controller;

import io.groo.ieum.common.config.properties.JwtProperties;
import io.groo.ieum.security.jwt.JwtTokenProvider;
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
    private final TokenExchangeController controller = new TokenExchangeController(
            mock(TokenExchangeService.class), mock(JwtProperties.class), provider, revocation);

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
}
