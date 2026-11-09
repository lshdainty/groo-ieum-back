package io.groo.ieum.security.service;

import io.groo.ieum.client.sso.SsoApiClient;
import io.groo.ieum.security.controller.dto.TokenExchangeDto;
import io.groo.ieum.security.jwt.JwtTokenProvider;
import io.groo.ieum.user.domain.User;
import io.groo.ieum.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 토큰 교환 서비스 단위 테스트 — OAuth2 인가코드(+PKCE) 교환 경로.
 */
@ExtendWith(MockitoExtension.class)
class TokenExchangeServiceTest {

    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private UserService userService;
    @Mock private SsoApiClient ssoApiClient;

    @InjectMocks private TokenExchangeService sut;

    @Test
    @DisplayName("exchangeCode — SSO /oauth2/token 으로 code 교환 후 HR JWT 를 발급한다")
    void exchangeCodeIssuesHrToken() {
        // SSO 인가코드 → SSO access token
        given(ssoApiClient.exchangeOAuthCode("code", "verifier", "https://hr/cb")).willReturn("ssoToken");
        // 기존 exchange 내부 흐름
        given(jwtTokenProvider.validateSsoToken("ssoToken")).willReturn(true);
        given(jwtTokenProvider.getServicesFromSsoToken("ssoToken")).willReturn(List.of("hr"));
        given(jwtTokenProvider.getUserIdFromSsoToken("ssoToken")).willReturn("user1");

        User user = mock(User.class);
        given(user.getRoles()).willReturn(Collections.emptyList());
        given(user.getAllAuthorities()).willReturn(Collections.emptyList());
        given(user.getSsoUserRowId()).willReturn(1L);
        given(user.getId()).willReturn("user1");
        given(user.getName()).willReturn("홍길동");
        given(user.getEmail()).willReturn("hong@porest.com");
        given(userService.findUserById("user1")).willReturn(user);

        given(jwtTokenProvider.createHrAccessToken(eq(user), anyList(), anyList(), eq(false))).willReturn("hrToken");
        given(jwtTokenProvider.getHrAccessExpirationSeconds()).willReturn(3600L);

        TokenExchangeDto.ExchangeResult result = sut.exchangeCode("code", "verifier", "https://hr/cb");

        assertThat(result.getAccessToken()).isEqualTo("hrToken");
        assertThat(result.getExpiresIn()).isEqualTo(3600L);
        verify(ssoApiClient).exchangeOAuthCode("code", "verifier", "https://hr/cb");
    }
}
