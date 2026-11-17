package io.groo.ieum.security.service;

import io.groo.core.exception.ForbiddenException;
import io.groo.core.exception.UnauthorizedException;
import io.groo.ieum.client.sso.SsoApiClient;
import io.groo.ieum.common.exception.HrErrorCode;
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
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
    @Mock private ServiceLogoutStore serviceLogoutStore;

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

        given(jwtTokenProvider.createHrAccessToken(eq(user), anyList(), anyList(), eq(false), any())).willReturn("hrToken");
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
        verify(jwtTokenProvider, never()).createHrAccessToken(any(User.class), anyList(), anyList(), anyBoolean(), any());
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
        given(jwtTokenProvider.createHrAccessToken(eq(relinked), anyList(), anyList(), eq(false), any())).willReturn("hrToken");

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
        given(jwtTokenProvider.createHrAccessToken(eq(user), anyList(), anyList(), eq(false), any())).willReturn("hrToken");

        sut.exchange("ssoToken");

        verify(ssoApiClient, org.mockito.Mockito.never()).getLinkedFrom(any());
    }

    @Test
    @DisplayName("보안 점검 SSO-13 - 합침 기록에 있어도 SSO 에 살아 있는 계정(관리자 등)의 행은 다시 묶지 않는다")
    void doesNotRelinkLiveAccountRow() {
        givenSsoToken("attacker", 1L);
        given(userService.findUserById("attacker"))
                .willThrow(new io.groo.core.exception.EntityNotFoundException(io.groo.ieum.common.exception.HrErrorCode.USER_NOT_FOUND));
        given(ssoApiClient.getLinkedFrom(1L)).willReturn(List.of(7L));
        io.groo.ieum.client.sso.dto.SsoInvitationStatusResponse admin =
                mock(io.groo.ieum.client.sso.dto.SsoInvitationStatusResponse.class);
        given(admin.getUserNo()).willReturn(7L);
        given(ssoApiClient.getInvitationStatus(List.of(7L))).willReturn(List.of(admin));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> sut.exchange("ssoToken"))
                .isInstanceOf(io.groo.core.exception.EntityNotFoundException.class);
        verify(userService, org.mockito.Mockito.never()).relinkMergedSsoUser(any(), anyList(), any(), any(), any());
    }

    @Test
    @DisplayName("보안 점검 SSO-13 - 대기 계정 여부를 확인하지 못하면(SSO 오류) 다시 묶지 않는다")
    void doesNotRelinkWhenStatusUnknown() {
        givenSsoToken("boss", 1L);
        given(userService.findUserById("boss"))
                .willThrow(new io.groo.core.exception.EntityNotFoundException(io.groo.ieum.common.exception.HrErrorCode.USER_NOT_FOUND));
        given(ssoApiClient.getLinkedFrom(1L)).willReturn(List.of(101L));
        given(ssoApiClient.getInvitationStatus(List.of(101L))).willThrow(new RuntimeException("SSO down"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> sut.exchange("ssoToken"))
                .isInstanceOf(io.groo.core.exception.EntityNotFoundException.class);
        verify(userService, org.mockito.Mockito.never()).relinkMergedSsoUser(any(), anyList(), any(), any(), any());
    }

    // ── 보안 점검 CLI-6 — 이음에서 로그아웃한 SSO 로그인(sid)으로 돌아오면 거절한다 ──

    /** 로그아웃 목록 대조까지만 가는 SSO 토큰 — 거절되면 사용자 번호는 읽지 않는다 */
    private void ssoTokenWithSid(String sid) {
        given(jwtTokenProvider.validateSsoToken("ssoToken")).willReturn(true);
        given(jwtTokenProvider.getServicesFromSsoToken("ssoToken")).willReturn(List.of("hr"));
        given(jwtTokenProvider.getUserIdFromSsoToken("ssoToken")).willReturn("user1");
        given(jwtTokenProvider.getSidFromSsoToken("ssoToken")).willReturn(sid);
    }

    private User loginableUser() {
        User user = linkedUser(1L);
        given(user.getRoles()).willReturn(Collections.emptyList());
        given(user.getAllAuthorities()).willReturn(Collections.emptyList());
        return user;
    }

    @Test
    @DisplayName("보안 점검 CLI-6 - 이음에서 로그아웃한 sid 의 SSO 토큰이면 AUTH_010 으로 거절하고 HR 토큰을 내주지 않는다")
    void exchangeRejectsLoggedOutSsoLogin() {
        ssoTokenWithSid("sid-out");
        given(serviceLogoutStore.isLoggedOut("sid-out")).willReturn(true);

        assertThatThrownBy(() -> sut.exchange("ssoToken"))
                .isInstanceOfSatisfying(UnauthorizedException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(HrErrorCode.SSO_LOGIN_LOGGED_OUT);
                    assertThat(e.getErrorCode().getCode()).isEqualTo("AUTH_010");
                    assertThat(e.getErrorCode().getHttpStatus().value()).isEqualTo(401);
                });

        verify(jwtTokenProvider, never()).createHrAccessToken(any(User.class), anyList(), anyList(), anyBoolean(), any());
        // 거절한 로그인으로는 사용자 조회 · 재연결(SSO 호출)도 하지 않는다
        verify(userService, never()).findUserById(any());
        verify(ssoApiClient, never()).getLinkedFrom(any());
    }

    @Test
    @DisplayName("보안 점검 CLI-6 - 인가코드 교환도 같은 검사를 탄다")
    void exchangeCodeRejectsLoggedOutSsoLogin() {
        given(ssoApiClient.exchangeOAuthCode("code", "verifier", "https://hr/cb")).willReturn("ssoToken");
        ssoTokenWithSid("sid-out");
        given(serviceLogoutStore.isLoggedOut("sid-out")).willReturn(true);

        assertThatThrownBy(() -> sut.exchangeCode("code", "verifier", "https://hr/cb"))
                .isInstanceOfSatisfying(UnauthorizedException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(HrErrorCode.SSO_LOGIN_LOGGED_OUT));
    }

    @Test
    @DisplayName("보안 점검 CLI-6 - 로그아웃 목록에 없는 새 sid 는 통과하고, 그 sid 를 HR 토큰에 싣는다")
    void exchangePassesNewSidAndCarriesIt() {
        validSsoToken(1L);
        User user = loginableUser();
        given(jwtTokenProvider.getSidFromSsoToken("ssoToken")).willReturn("sid-new");
        given(serviceLogoutStore.isLoggedOut("sid-new")).willReturn(false);
        given(jwtTokenProvider.createHrAccessToken(eq(user), anyList(), anyList(), eq(false), eq("sid-new")))
                .willReturn("hrToken");

        assertThat(sut.exchange("ssoToken").getAccessToken()).isEqualTo("hrToken");
    }

    @Test
    @DisplayName("보안 점검 CLI-6 - sid 가 없는 옛 SSO 토큰은 목록을 보지 않고 통과하며, HR 토큰에도 싣지 않는다")
    void exchangeSkipsCheckWithoutSid() {
        validSsoToken(1L);
        User user = loginableUser();
        given(jwtTokenProvider.getSidFromSsoToken("ssoToken")).willReturn(null);
        given(jwtTokenProvider.createHrAccessToken(eq(user), anyList(), anyList(), eq(false), isNull()))
                .willReturn("hrToken");

        assertThat(sut.exchange("ssoToken").getAccessToken()).isEqualTo("hrToken");
        verify(serviceLogoutStore, never()).isLoggedOut(any());
    }

    @Test
    @DisplayName("보안 점검 CLI-6 - 이음 접근 권한이 없으면 로그아웃 여부보다 먼저 403 이다(비밀번호를 다시 묻지 않는다)")
    void exchangeChecksServiceAccessBeforeLogoutList() {
        given(jwtTokenProvider.validateSsoToken("ssoToken")).willReturn(true);
        given(jwtTokenProvider.getServicesFromSsoToken("ssoToken")).willReturn(List.of("desk"));

        assertThatThrownBy(() -> sut.exchange("ssoToken"))
                .isInstanceOf(ForbiddenException.class);
        verify(serviceLogoutStore, never()).isLoggedOut(any());
    }

    @Test
    @DisplayName("보안 점검 CLI-6 - Redis 조회가 실패하면 로그인을 막지 않는다(fail-open)")
    void exchangePassesWhenRedisFails() {
        // 목이 아닌 진짜 저장소 — Redis 가 죽었을 때 교환까지 이어서 통과하는지 본다
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        given(redis.hasKey(anyString())).willThrow(new RuntimeException("redis down"));
        TokenExchangeService withRealStore = new TokenExchangeService(
                jwtTokenProvider, userService, ssoApiClient, new ServiceLogoutStore(redis));

        validSsoToken(1L);
        User user = loginableUser();
        given(jwtTokenProvider.getSidFromSsoToken("ssoToken")).willReturn("sid-1");
        given(jwtTokenProvider.createHrAccessToken(eq(user), anyList(), anyList(), eq(false), eq("sid-1")))
                .willReturn("hrToken");

        assertThat(withRealStore.exchange("ssoToken").getAccessToken()).isEqualTo("hrToken");
        verify(redis).hasKey("ieum:logged-out-sid:sid-1");
    }
}
