package io.groo.ieum.client.sso;

import io.groo.ieum.client.sso.dto.SsoInvitationStatusResponse;
import io.groo.ieum.client.sso.dto.SsoInviteRequest;
import io.groo.ieum.client.sso.dto.SsoInviteResponse;

import java.util.List;

/**
 * SSO API 클라이언트 인터페이스<br>
 * SSO 서비스와의 HTTP 통신을 담당
 */
public interface SsoApiClient {

    /**
     * SSO에 사용자 초대 요청<br>
     * 신규 사용자면 User + UserClientAccess 생성, 초대 이메일 발송<br>
     * 기존 SSO 사용자면 UserClientAccess만 생성 (자동 연결)
     *
     * @param request 초대 요청 정보 (clientCode, userId, name, email)
     * @return 초대 결과 (userNo, alreadyExists 등)
     * @throws io.groo.core.exception.ExternalServiceException SSO 서비스 연동 실패 시
     */
    SsoInviteResponse inviteUser(SsoInviteRequest request);

    /**
     * SSO에 초대 재전송 요청<br>
     * PENDING 상태의 사용자에게 초대 이메일을 재발송
     *
     * @param userId 재전송할 사용자 ID
     * @throws io.groo.core.exception.ExternalServiceException SSO 서비스 연동 실패 시
     */
    void resendInvitation(String userId);

    /**
     * SSO에서 여러 사용자의 초대 상태 조회<br>
     * 사용자 목록 조회 시 초대 상태를 표시하기 위해 사용
     *
     * @param userNos SSO 사용자 번호 목록
     * @return 각 사용자의 초대 상태 목록
     * @throws io.groo.core.exception.ExternalServiceException SSO 서비스 연동 실패 시
     */
    List<SsoInvitationStatusResponse> getInvitationStatus(List<Long> userNos);

    /**
     * 이 SSO 계정으로 합쳐진 초대 대기 계정 번호들(groo-sso-back 보안 점검 SSO-2 운영 위험).<br>
     * 초대 수락 알림(USER_LINKED)을 놓쳤을 때 토큰 교환이 이음 행을 다시 묶는 데 쓴다.
     * 실패하면 빈 목록 — 재처리 못 할 뿐 로그인 흐름의 다른 실패와 같다.
     */
    List<Long> getLinkedFrom(Long userNo);

    /**
     * OAuth2 인가코드(code)를 SSO {@code /oauth2/token} 에서 교환 → SSO access token(JWT) 반환<br>
     * PKCE code_verifier 와 함께 전달. 표준 Authorization Code + PKCE 흐름.
     *
     * @param code         인가 코드 (SSO /oauth2/authorize 발급)
     * @param codeVerifier PKCE code_verifier 원본
     * @param redirectUri  authorize 때와 동일한 redirect_uri
     * @return SSO access token (JWT)
     * @throws io.groo.core.exception.ExternalServiceException SSO 서비스 연동 실패/무효 코드 시
     */
    String exchangeOAuthCode(String code, String codeVerifier, String redirectUri);

    /**
     * client_credentials 그랜트로 SSO 서비스 토큰(ROLE_SERVICE) 발급<br>
     * grant_type=client_credentials, client_id=clientCode, client_secret=주입값 으로
     * SSO {@code /oauth2/token} 을 호출해 access token(JWT)을 반환한다.<br>
     * 비밀번호 변경/리셋 등 서비스 간 호출의 Bearer 인증에 사용.
     *
     * @return SSO 서비스 access token (JWT)
     * @throws io.groo.core.exception.ExternalServiceException SSO 서비스 연동 실패 시
     */
    String issueServiceToken();

    /**
     * 사용자 본인 비밀번호 변경<br>
     * 서비스 토큰으로 SSO {@code /api/v1/auth/password/change} 를 호출한다.<br>
     * 서비스 토큰에는 사용자 식별자가 없으므로 대상 userId 를 body 에 담아 전달.
     *
     * @param userId          대상 사용자 ID (HR User.id = SSO user_id)
     * @param currentPassword 현재 비밀번호
     * @param newPassword     새 비밀번호
     * @param confirmPassword 새 비밀번호 확인
     * @throws io.groo.core.exception.InvalidValueException    현재 비밀번호 불일치 등 검증 실패(4xx) 시
     * @throws io.groo.core.exception.ExternalServiceException SSO 서비스 연동 실패 시
     */
    void changePassword(String userId, String currentPassword, String newPassword, String confirmPassword);

    /**
     * 관리자 비밀번호 리셋<br>
     * 서비스 토큰으로 SSO {@code /api/v1/auth/password/reset-by-service} 를 호출한다.<br>
     * SSO 가 임시 비밀번호 자동 생성 + 이메일 발송 + 강제 변경 처리(평문 미반환).
     *
     * @param userId 대상 사용자 ID (HR User.id = SSO user_id)
     * @throws io.groo.core.exception.ExternalServiceException SSO 서비스 연동 실패 시
     */
    void resetPassword(String userId);
}
