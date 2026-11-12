package io.groo.ieum.security.filter;

import io.groo.ieum.common.config.properties.JwtProperties;
import io.groo.ieum.security.jwt.JwtTokenProvider;
import io.groo.ieum.user.domain.User;
import io.groo.ieum.user.service.UserService;
import io.groo.core.exception.EntityNotFoundException;
import io.groo.ieum.common.exception.HrErrorCode;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;

/**
 * 슬라이딩 세션 갱신 — 액세스 토큰(1시간)이 "쓰고 있는 중"에 정각으로 끊기지 않도록,
 * 잔여 수명이 임계(10분) 아래로 내려간 유효 토큰은 응답에 새 토큰 쿠키를 실어 준다.
 * desk-back 의 JwtAuthenticationFilter 와 같은 규칙이다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JWT 인증 필터 슬라이딩 갱신 테스트")
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private UserService userService;
    @Mock
    private JwtProperties jwtProperties;
    @Mock
    private io.groo.ieum.security.service.TokenRevocationService tokenRevocationService;

    @org.mockito.Spy
    private tools.jackson.databind.ObjectMapper objectMapper = tools.jackson.databind.json.JsonMapper.builder().build();

    @InjectMocks
    private JwtAuthenticationFilter filter;

    private static final String TOKEN = "hr-jwt-token";
    private static final String RENEWED = "hr-jwt-token-renewed";

    @BeforeEach
    void setUpHrTokenAuth() {
        // 유효한 HR 토큰으로 인증되는 공통 경로 — 각 테스트는 잔여 수명만 바꾼다.
        lenient().when(jwtTokenProvider.validateToken(TOKEN)).thenReturn(true);
        lenient().when(jwtTokenProvider.isHrToken(TOKEN)).thenReturn(true);
        lenient().when(jwtTokenProvider.getUserId(TOKEN)).thenReturn("user1");
        lenient().when(jwtTokenProvider.getSsoUserRowIdFromHrToken(TOKEN)).thenReturn(7L);
        lenient().when(jwtTokenProvider.getNameFromHrToken(TOKEN)).thenReturn("사용자");
        lenient().when(jwtTokenProvider.getEmailFromHrToken(TOKEN)).thenReturn("user1@test.com");
        lenient().when(jwtTokenProvider.getRolesFromHrToken(TOKEN)).thenReturn(List.of("ROLE_USER"));
        lenient().when(jwtTokenProvider.getPermissionsFromHrToken(TOKEN)).thenReturn(List.of());
        // 갱신 분기에서만 쓰이는 스텁 — 비갱신 테스트에서는 호출되지 않는다.
        lenient().when(jwtProperties.getHrAccessExpiration()).thenReturn(3_600_000L);
        lenient().when(jwtTokenProvider.createHrAccessToken(any(User.class), anyList(), anyList(), org.mockito.ArgumentMatchers.anyBoolean()))
            .thenReturn(RENEWED);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletResponse runFilter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("hr_access_token", TOKEN));
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Nested
    @DisplayName("잔여 수명이 임계(10분) 미만이면")
    class WhenExpiringSoon {

        @Test
        @DisplayName("성공 - 새 토큰을 hr_access_token 쿠키로 실어 준다")
        void renewsCookie() throws Exception {
            given(jwtTokenProvider.getRemainingExpiration(TOKEN)).willReturn(300_000L); // 5분
            User user = org.mockito.Mockito.mock(User.class);
            linkTo(user, 7L);
            given(user.getRoles()).willReturn(new java.util.ArrayList<>());
            given(user.getAllAuthorities()).willReturn(List.of());
            given(userService.findUserById("user1")).willReturn(user);

            MockHttpServletResponse response = runFilter();

            String setCookie = response.getHeader("Set-Cookie");
            assertThat(setCookie).contains("hr_access_token=" + RENEWED);
            assertThat(setCookie).contains("HttpOnly");
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        }
    }

    @Nested
    @DisplayName("보안 점검 IEU-6 - 갱신 구간인데 행의 연결 번호가 토큰과 다르면")
    class WhenRowSwapped {

        @Test
        @DisplayName("성공 - 그 행(예: 관리자)의 권한으로 새 토큰을 만들지 않는다")
        void doesNotRenewSwappedRow() throws Exception {
            given(jwtTokenProvider.getRemainingExpiration(TOKEN)).willReturn(300_000L);
            User adminRow = org.mockito.Mockito.mock(User.class);
            linkTo(adminRow, 1L);
            given(userService.findUserById("user1")).willReturn(adminRow);

            MockHttpServletResponse response = runFilter();

            assertThat(response.getHeader("Set-Cookie")).isNull();
            org.mockito.Mockito.verify(jwtTokenProvider, org.mockito.Mockito.never())
                    .createHrAccessToken(any(User.class), anyList(), anyList(), org.mockito.ArgumentMatchers.anyBoolean());
        }
    }

    @Nested
    @DisplayName("갱신 구간인데 사용자가 삭제(퇴사)됐으면")
    class WhenUserDeleted {

        @Test
        @DisplayName("성공 - 갱신하지 않고, 남은 수명 동안의 이번 요청은 그대로 처리한다")
        void doesNotRenewDeletedUser() throws Exception {
            given(jwtTokenProvider.getRemainingExpiration(TOKEN)).willReturn(300_000L);
            given(userService.findUserById("user1")).willThrow(new EntityNotFoundException(HrErrorCode.USER_NOT_FOUND));

            MockHttpServletResponse response = runFilter();

            assertThat(response.getHeader("Set-Cookie")).isNull();
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        }
    }

    @Nested
    @DisplayName("로그아웃 차단 목록")
    class Revocation {

        @Test
        @DisplayName("성공 - 로그아웃된 jti 의 토큰은 인증되지 않는다")
        void revokedTokenRejected() throws Exception {
            given(jwtTokenProvider.getHrJti(TOKEN)).willReturn("jti-1");
            given(tokenRevocationService.isRevoked("jti-1")).willReturn(true);

            runFilter();

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        @DisplayName("보안 점검 IEU-2 - 역할이 바뀌거나 삭제돼 사용자 단위로 차단된 토큰은 인증되지 않는다")
        void userRevokedTokenRejected() throws Exception {
            java.util.Date iat = new java.util.Date(1_700_000_000_000L);
            given(jwtTokenProvider.getHrJti(TOKEN)).willReturn("jti-3");
            given(tokenRevocationService.isRevoked("jti-3")).willReturn(false);
            given(jwtTokenProvider.getHrIssuedAt(TOKEN)).willReturn(iat);
            given(tokenRevocationService.isUserRevoked("user1", iat)).willReturn(true);

            runFilter();

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        @DisplayName("성공 - jti 없는 옛 토큰은 차단 목록을 보지 않고 만료까지 허용한다")
        void tokenWithoutJtiAllowed() throws Exception {
            given(jwtTokenProvider.getHrJti(TOKEN)).willReturn(null);
            given(jwtTokenProvider.getRemainingExpiration(TOKEN)).willReturn(1_800_000L);

            runFilter();

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
            org.mockito.Mockito.verify(tokenRevocationService, org.mockito.Mockito.never()).isRevoked(anyString());
        }

        @Test
        @DisplayName("성공 - 차단되지 않은 jti 는 정상 인증 (갱신 시 옛 jti 는 차단하지 않는다)")
        void liveJtiAllowed() throws Exception {
            given(jwtTokenProvider.getHrJti(TOKEN)).willReturn("jti-2");
            given(tokenRevocationService.isRevoked("jti-2")).willReturn(false);
            given(jwtTokenProvider.getRemainingExpiration(TOKEN)).willReturn(1_800_000L);

            runFilter();

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
            org.mockito.Mockito.verify(tokenRevocationService, org.mockito.Mockito.never())
                    .revoke(anyString(), org.mockito.ArgumentMatchers.anyLong());
        }
    }

    @Nested
    @DisplayName("보안 점검 IEU-2 - 상태를 바꾸는 요청은 권한을 DB 에서 다시 읽는다")
    class WriteRequestsReloadAuthorities {

        private MockHttpServletResponse runFilter(String method) throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/v1/users/user1");
            request.setCookies(new Cookie("hr_access_token", TOKEN));
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            return response;
        }

        private List<String> currentAuthorities() {
            return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                    .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                    .toList();
        }

        @BeforeEach
        void tokenStillSaysAdmin() {
            // 토큰은 강등 전에 발급돼 아직 관리자 권한을 싣고 있다
            given(jwtTokenProvider.getRolesFromHrToken(TOKEN)).willReturn(List.of("ADMIN"));
            given(jwtTokenProvider.getPermissionsFromHrToken(TOKEN)).willReturn(List.of("ROLE:MANAGE", "USER:MANAGE"));
            lenient().when(jwtTokenProvider.getRemainingExpiration(TOKEN)).thenReturn(1_800_000L);
        }

        private void demotedInDb() {
            // DB 에서는 관리자 역할이 회수돼 일반 직원 권한만 남았다
            io.groo.ieum.permission.domain.Role userRole = org.mockito.Mockito.mock(io.groo.ieum.permission.domain.Role.class);
            given(userRole.getCode()).willReturn("USER");
            User user = org.mockito.Mockito.mock(User.class);
            linkTo(user, 7L);
            given(user.getRoles()).willReturn(List.of(userRole));
            given(user.getAllAuthorities()).willReturn(List.of("USER", "USER:EDIT"));
            given(userService.findUserById("user1")).willReturn(user);
        }

        @Test
        @DisplayName("성공 - 강등된 관리자의 PUT 은 토큰이 아니라 DB 의 지금 권한으로 인가된다")
        void demotedAdminWriteUsesDbAuthorities() throws Exception {
            demotedInDb();

            runFilter("PUT");

            assertThat(currentAuthorities()).containsExactlyInAnyOrder("USER", "USER:EDIT");
            assertThat(currentAuthorities()).doesNotContain("ADMIN", "ROLE:MANAGE", "USER:MANAGE");
        }

        @Test
        @DisplayName("성공 - POST · PATCH · DELETE 도 같다")
        void otherWriteMethodsUseDbAuthorities() throws Exception {
            demotedInDb();
            for (String method : List.of("POST", "PATCH", "DELETE", "delete")) {
                runFilter(method);
                assertThat(currentAuthorities()).as(method).doesNotContain("ROLE:MANAGE", "USER:MANAGE");
                SecurityContextHolder.clearContext();
            }
        }

        @Test
        @DisplayName("보안 점검 IEU-6 - 아이디가 같아도 행의 연결 번호가 토큰과 다르면(아이디 바꿔치기) 쓰기 요청을 인증하지 않는다")
        void swappedRowWriteNotAuthenticated() throws Exception {
            // 토큰은 연결 번호 7 의 사용자, DB 에서 같은 아이디를 가진 행은 연결 번호 1(관리자 행에 아이디를 덮어씀)
            User adminRow = org.mockito.Mockito.mock(User.class);
            linkTo(adminRow, 1L);
            lenient().when(adminRow.getAllAuthorities()).thenReturn(List.of("ADMIN", "ROLE:MANAGE"));
            given(userService.findUserById("user1")).willReturn(adminRow);

            runFilter("PUT");

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        @DisplayName("성공 - 삭제(퇴사)된 사용자의 쓰기 요청은 인증하지 않는다")
        void deletedUserWriteNotAuthenticated() throws Exception {
            given(userService.findUserById("user1")).willThrow(new EntityNotFoundException(HrErrorCode.USER_NOT_FOUND));

            runFilter("DELETE");

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        @DisplayName("성공 - 읽기(GET)는 지금처럼 토큰 권한을 쓰고 DB 를 보지 않는다")
        void readUsesTokenAuthorities() throws Exception {
            runFilter("GET");

            assertThat(currentAuthorities()).contains("ADMIN", "ROLE:MANAGE");
            org.mockito.Mockito.verify(userService, org.mockito.Mockito.never()).findUserById(anyString());
        }
    }

    @Nested
    @DisplayName("잔여 수명이 넉넉하면")
    class WhenFresh {

        @Test
        @DisplayName("성공 - 쿠키를 갱신하지 않는다")
        void doesNotRenew() throws Exception {
            given(jwtTokenProvider.getRemainingExpiration(TOKEN)).willReturn(1_800_000L); // 30분

            MockHttpServletResponse response = runFilter();

            assertThat(response.getHeader("Set-Cookie")).isNull();
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        }
    }

    @Nested
    @DisplayName("이미 만료를 지난 값이 오면")
    class WhenAlreadyExpired {

        @Test
        @DisplayName("성공 - 갱신하지 않는다 (죽은 토큰을 살려 주지 않는다)")
        void doesNotResurrect() throws Exception {
            given(jwtTokenProvider.getRemainingExpiration(TOKEN)).willReturn(0L);

            MockHttpServletResponse response = runFilter();

            assertThat(response.getHeader("Set-Cookie")).isNull();
        }
    }

    @Nested
    @DisplayName("HR 서명이 아닌 SSO 토큰이 오면")
    class WhenSsoToken {

        private static final String SSO = "sso-jwt";

        private MockHttpServletResponse run() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setCookies(new Cookie("hr_access_token", SSO));
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            return response;
        }

        @Test
        @DisplayName("성공 - access/hr 대상이면 인증된다")
        void acceptsAccess() throws Exception {
            given(jwtTokenProvider.validateToken(SSO)).willReturn(true);
            given(jwtTokenProvider.isHrToken(SSO)).willReturn(false);
            given(jwtTokenProvider.isSsoAccessTokenForHr(SSO)).willReturn(true);
            given(jwtTokenProvider.getUserId(SSO)).willReturn("user1");
            given(jwtTokenProvider.getUserNoFromSsoToken(SSO)).willReturn(7L);
            User user = linkedUser(7L);
            given(user.getAllAuthorities()).willReturn(List.of("ROLE_USER"));

            run();

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        }

        private User linkedUser(Long ssoUserRowId) {
            User user = org.mockito.Mockito.mock(User.class);
            given(user.getSsoUserRowId()).willReturn(ssoUserRowId);
            given(user.isLinkedToSsoUser(org.mockito.ArgumentMatchers.any())).willCallRealMethod();
            given(userService.findUserById("user1")).willReturn(user);
            return user;
        }

        @Test
        @DisplayName("실패 - 보안 점검 IEU-6: SSO 사용자 번호가 이음 연결 번호와 다르면 인증되지 않는다")
        void rejectsUserNoMismatch() throws Exception {
            given(jwtTokenProvider.validateToken(SSO)).willReturn(true);
            given(jwtTokenProvider.isHrToken(SSO)).willReturn(false);
            given(jwtTokenProvider.isSsoAccessTokenForHr(SSO)).willReturn(true);
            given(jwtTokenProvider.getUserId(SSO)).willReturn("user1");
            given(jwtTokenProvider.getUserNoFromSsoToken(SSO)).willReturn(42L);
            linkedUser(1L);

            run();

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        @DisplayName("실패 - refresh 등 access 가 아니면 인증되지 않는다")
        void rejectsNonAccess() throws Exception {
            given(jwtTokenProvider.validateToken(SSO)).willReturn(true);
            given(jwtTokenProvider.isHrToken(SSO)).willReturn(false);
            given(jwtTokenProvider.isSsoAccessTokenForHr(SSO)).willReturn(false);

            run();

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            org.mockito.Mockito.verifyNoInteractions(userService);
        }
    }

    /** 실제 대조 로직(User.isLinkedToSsoUser)을 쓰도록 연결 번호를 심는다 */
    private static void linkTo(User user, Long ssoUserRowId) {
        lenient().when(user.getSsoUserRowId()).thenReturn(ssoUserRowId);
        lenient().when(user.isLinkedToSsoUser(org.mockito.ArgumentMatchers.any())).thenCallRealMethod();
    }
}
