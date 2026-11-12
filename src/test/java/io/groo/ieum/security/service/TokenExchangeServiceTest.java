package io.groo.ieum.security.service;

import io.groo.core.exception.UnauthorizedException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
        given(jwtTokenProvider.getUserNoFromSsoToken("ssoToken")).willReturn(1L);

        User user = mock(User.class);
        given(user.getRoles()).willReturn(Collections.emptyList());
        given(user.getAllAuthorities()).willReturn(Collections.emptyList());
        given(user.getSsoUserRowId()).willReturn(1L);
        given(user.isLinkedToSsoUser(1L)).willCallRealMethod();
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

    private User linkedUser(Long ssoUserRowId) {
        User user = mock(User.class);
        org.mockito.Mockito.lenient().when(user.getSsoUserRowId()).thenReturn(ssoUserRowId);
        given(user.isLinkedToSsoUser(any())).willCallRealMethod();
        given(userService.findUserById("user1")).willReturn(user);
        return user;
    }

    private void validSsoToken(Long userNo) {
        given(jwtTokenProvider.validateSsoToken("ssoToken")).willReturn(true);
        given(jwtTokenProvider.getServicesFromSsoToken("ssoToken")).willReturn(List.of("hr"));
        given(jwtTokenProvider.getUserIdFromSsoToken("ssoToken")).willReturn("user1");
        given(jwtTokenProvider.getUserNoFromSsoToken("ssoToken")).willReturn(userNo);
    }

    @Test
    @DisplayName("보안 점검 IEU-6 - 로그인 아이디가 같아도 SSO 사용자 번호가 이음 연결 번호와 다르면 HR 토큰을 내주지 않는다")
    void exchangeRejectsUserNoMismatch() {
        // 위조 이벤트로 관리자 행(연결 번호 1)의 로그인 아이디가 공격자 아이디로 덮였다 — 공격자의 SSO 번호는 42
        validSsoToken(42L);
        linkedUser(1L);

        assertThatThrownBy(() -> sut.exchange("ssoToken"))
                .isInstanceOf(UnauthorizedException.class);
        verify(jwtTokenProvider, never()).createHrAccessToken(any(User.class), anyList(), anyList(), anyBoolean());
    }

    @Test
    @DisplayName("보안 점검 IEU-6 - SSO 토큰에 사용자 번호가 없거나 이음 행이 SSO 와 연결되지 않았으면 거절한다")
    void exchangeRejectsMissingUserNo() {
        validSsoToken(null);
        linkedUser(1L);
        assertThatThrownBy(() -> sut.exchange("ssoToken")).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("보안 점검 IEU-6 - 이음 행에 연결 번호가 없으면 거절한다")
    void exchangeRejectsUnlinkedRow() {
        validSsoToken(1L);
        linkedUser(null);
        assertThatThrownBy(() -> sut.exchange("ssoToken")).isInstanceOf(UnauthorizedException.class);
    }

    // ── SSO-2 운영 위험 — 초대 수락 알림(USER_LINKED)을 놓쳤을 때 토큰 교환이 다시 묶는다 ──

    private void givenSsoToken(String userId, Long userNo) {
        given(jwtTokenProvider.validateSsoToken("ssoToken")).willReturn(true);
        given(jwtTokenProvider.getServicesFromSsoToken("ssoToken")).willReturn(List.of("hr"));
        given(jwtTokenProvider.getUserIdFromSsoToken("ssoToken")).willReturn(userId);
        given(jwtTokenProvider.getUserNoFromSsoToken("ssoToken")).willReturn(userNo);
        org.mockito.Mockito.lenient().when(jwtTokenProvider.getNameFromSsoToken("ssoToken")).thenReturn("대표");
        org.mockito.Mockito.lenient().when(jwtTokenProvider.getEmailFromSsoToken("ssoToken")).thenReturn("boss@example.com");
    }

    @Test
    @DisplayName("행을 못 찾으면 SSO 가 알려 준 합쳐진 대기 번호의 행을 다시 묶고 로그인시킨다")
    void relinksWhenLinkedEventWasLost() {
        givenSsoToken("boss", 1L);
        User relinked = mock(User.class);
        org.mockito.Mockito.lenient().when(relinked.getSsoUserRowId()).thenReturn(1L);
        given(relinked.isLinkedToSsoUser(any())).willCallRealMethod();
        given(relinked.getRoles()).willReturn(Collections.emptyList());
        given(relinked.getAllAuthorities()).willReturn(Collections.emptyList());
        given(userService.findUserById("boss"))
                .willThrow(new io.groo.core.exception.EntityNotFoundException(io.groo.ieum.common.exception.HrErrorCode.USER_NOT_FOUND))
                .willReturn(relinked);
        given(ssoApiClient.getLinkedFrom(1L)).willReturn(List.of(101L));
        given(userService.relinkMergedSsoUser(1L, List.of(101L), "boss", "대표", "boss@example.com")).willReturn(true);
        given(jwtTokenProvider.createHrAccessToken(eq(relinked), anyList(), anyList(), eq(false))).willReturn("hrToken");

        assertThat(sut.exchange("ssoToken").getAccessToken()).isEqualTo("hrToken");
    }

    @Test
    @DisplayName("합쳐진 번호가 없으면 다시 묶지 않고 예전처럼 실패한다")
    void noLinkRecordFailsAsBefore() {
        givenSsoToken("boss", 1L);
        given(userService.findUserById("boss"))
                .willThrow(new io.groo.core.exception.EntityNotFoundException(io.groo.ieum.common.exception.HrErrorCode.USER_NOT_FOUND));
        given(ssoApiClient.getLinkedFrom(1L)).willReturn(List.of());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> sut.exchange("ssoToken"))
                .isInstanceOf(io.groo.core.exception.EntityNotFoundException.class);
        verify(userService, org.mockito.Mockito.never()).relinkMergedSsoUser(any(), anyList(), any(), any(), any());
    }

    @Test
    @DisplayName("행이 이미 맞게 묶여 있으면 SSO 에 묻지 않는다")
    void linkedUserSkipsLookup() {
        givenSsoToken("user1", 1L);
        User user = linkedUser(1L);
        given(user.getRoles()).willReturn(Collections.emptyList());
        given(user.getAllAuthorities()).willReturn(Collections.emptyList());
        given(jwtTokenProvider.createHrAccessToken(eq(user), anyList(), anyList(), eq(false))).willReturn("hrToken");

        sut.exchange("ssoToken");

        verify(ssoApiClient, org.mockito.Mockito.never()).getLinkedFrom(any());
    }
}
