package io.groo.ieum.client.sso;

import io.groo.core.controller.ApiResponse;
import io.groo.core.exception.ExternalServiceException;
import io.groo.core.exception.InvalidValueException;
import io.groo.ieum.client.sso.dto.SsoInvitationStatusRequest;
import io.groo.ieum.client.sso.dto.SsoInvitationStatusResponse;
import io.groo.ieum.client.sso.dto.SsoInviteRequest;
import io.groo.ieum.client.sso.dto.SsoInviteResponse;
import io.groo.ieum.client.sso.dto.SsoTokenRequest;
import io.groo.ieum.client.sso.dto.SsoTokenResponse;
import io.groo.ieum.common.exception.HrErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * SSO API 클라이언트 구현체<br>
 * RestTemplate을 사용하여 SSO 서비스와 HTTP 통신
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SsoApiClientImpl implements SsoApiClient {

    @Qualifier("ssoRestTemplate")
    private final RestTemplate ssoRestTemplate;

    @Value("${sso.client-code:hr}")
    private String clientCode;

    @Value("${sso.client-secret:}")
    private String clientSecret;

    private static final String INVITE_USER_PATH = "/api/v1/users/invite";
    private static final String RESEND_INVITATION_PATH = "/api/v1/users/resend";
    private static final String INVITATION_STATUS_PATH = "/api/v1/users/invitation-status";
    private static final String OAUTH_TOKEN_PATH = "/api/v1/oauth2/token";
    private static final String SSO_CHANGE_PASSWORD_PATH = "/api/v1/auth/password/change";
    private static final String SSO_RESET_BY_SERVICE_PATH = "/api/v1/auth/password/reset-by-service";

    @Override
    public SsoInviteResponse inviteUser(SsoInviteRequest request) {
        log.debug("Calling SSO inviteUser API: userId={}, email={}", request.getUserId(), request.getEmail());

        try {
            // 서버 간 호출 전용 API — SSO 가 ROLE_SERVICE 를 요구한다
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(issueServiceToken());

            HttpEntity<SsoInviteRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<ApiResponse<SsoInviteResponse>> response = ssoRestTemplate.exchange(
                    INVITE_USER_PATH,
                    HttpMethod.POST,
                    entity,
                    new ParameterizedTypeReference<ApiResponse<SsoInviteResponse>>() {}
            );

            ApiResponse<SsoInviteResponse> body = response.getBody();
            if (body == null || body.getData() == null) {
                throw new ExternalServiceException(HrErrorCode.SSO_SERVICE_ERROR, "SSO 응답이 비어있습니다");
            }

            log.info("SSO inviteUser API success: userNo={}, alreadyExists={}",
                    body.getData().getUserNo(), body.getData().isAlreadyExists());

            return body.getData();

        } catch (RestClientException e) {
            log.error("SSO inviteUser API failed: {}", e.getMessage(), e);
            throw new ExternalServiceException(HrErrorCode.SSO_SERVICE_ERROR, "SSO 초대 API 호출 실패", e);
        }
    }

    @Override
    public void resendInvitation(String userId) {
        log.debug("Calling SSO resendInvitation API: userId={}", userId);

        try {
            // 서버 간 호출 전용 API — SSO 가 ROLE_SERVICE 를 요구한다
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(issueServiceToken());

            Map<String, String> requestBody = Map.of("userId", userId);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, headers);

            ssoRestTemplate.exchange(
                    RESEND_INVITATION_PATH,
                    HttpMethod.POST,
                    entity,
                    new ParameterizedTypeReference<ApiResponse<Void>>() {}
            );

            log.info("SSO resendInvitation API success: userId={}", userId);

        } catch (RestClientException e) {
            log.error("SSO resendInvitation API failed: {}", e.getMessage(), e);
            throw new ExternalServiceException(HrErrorCode.SSO_SERVICE_ERROR, "SSO 초대 재전송 API 호출 실패", e);
        }
    }

    @Override
    public List<SsoInvitationStatusResponse> getInvitationStatus(List<Long> userNos) {
        if (userNos == null || userNos.isEmpty()) {
            return Collections.emptyList();
        }

        log.debug("Calling SSO getInvitationStatus API: userNos count={}", userNos.size());

        try {
            // 서버 간 호출 전용 API — SSO 가 ROLE_SERVICE 를 요구한다
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(issueServiceToken());

            SsoInvitationStatusRequest request = SsoInvitationStatusRequest.builder()
                    .userNos(userNos)
                    .build();

            HttpEntity<SsoInvitationStatusRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<ApiResponse<List<SsoInvitationStatusResponse>>> response = ssoRestTemplate.exchange(
                    INVITATION_STATUS_PATH,
                    HttpMethod.POST,
                    entity,
                    new ParameterizedTypeReference<ApiResponse<List<SsoInvitationStatusResponse>>>() {}
            );

            ApiResponse<List<SsoInvitationStatusResponse>> body = response.getBody();
            if (body == null || body.getData() == null) {
                log.warn("SSO getInvitationStatus API returned empty response");
                return Collections.emptyList();
            }

            log.info("SSO getInvitationStatus API success: count={}", body.getData().size());

            return body.getData();

        } catch (RestClientException e) {
            log.error("SSO getInvitationStatus API failed: {}", e.getMessage(), e);
            throw new ExternalServiceException(HrErrorCode.SSO_SERVICE_ERROR, "SSO 초대 상태 조회 API 호출 실패", e);
        }
    }

    @Override
    public List<Long> getLinkedFrom(Long userNo) {
        if (userNo == null) {
            return Collections.emptyList();
        }
        try {
            // 서버 간 호출 전용 API — SSO 가 ROLE_SERVICE + 이 계정의 이음 접근을 요구한다
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(issueServiceToken());
            ResponseEntity<ApiResponse<List<Long>>> response = ssoRestTemplate.exchange(
                    "/api/v1/users/" + userNo + "/linked-from",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<ApiResponse<List<Long>>>() {}
            );
            ApiResponse<List<Long>> body = response.getBody();
            return body == null || body.getData() == null ? Collections.emptyList() : body.getData();
        } catch (Exception e) {
            log.warn("SSO linked-from 조회 실패(재연결 없이 진행): userNo={}, error={}", userNo, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public String exchangeOAuthCode(String code, String codeVerifier, String redirectUri) {
        log.debug("Calling SSO /oauth2/token: clientId={}", clientCode);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            SsoTokenRequest requestBody = SsoTokenRequest.builder()
                    .grantType("authorization_code")
                    .code(code)
                    .codeVerifier(codeVerifier)
                    .clientId(clientCode)
                    .redirectUri(redirectUri)
                    .build();

            HttpEntity<SsoTokenRequest> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<ApiResponse<SsoTokenResponse>> response = ssoRestTemplate.exchange(
                    OAUTH_TOKEN_PATH,
                    HttpMethod.POST,
                    entity,
                    new ParameterizedTypeReference<ApiResponse<SsoTokenResponse>>() {}
            );

            ApiResponse<SsoTokenResponse> body = response.getBody();
            if (body == null || body.getData() == null || body.getData().getAccessToken() == null) {
                throw new ExternalServiceException(HrErrorCode.SSO_SERVICE_ERROR, "SSO 토큰 응답이 비어있습니다");
            }

            log.info("SSO /oauth2/token success");
            return body.getData().getAccessToken();

        } catch (RestClientException e) {
            log.error("SSO /oauth2/token API failed: {}", e.getMessage(), e);
            throw new ExternalServiceException(HrErrorCode.SSO_SERVICE_ERROR, "SSO 토큰 교환 실패", e);
        }
    }

    @Override
    public String issueServiceToken() {
        log.debug("Calling SSO /oauth2/token (client_credentials): clientId={}", clientCode);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            SsoTokenRequest requestBody = SsoTokenRequest.builder()
                    .grantType("client_credentials")
                    .clientId(clientCode)
                    .clientSecret(clientSecret)
                    .build();

            HttpEntity<SsoTokenRequest> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<ApiResponse<SsoTokenResponse>> response = ssoRestTemplate.exchange(
                    OAUTH_TOKEN_PATH,
                    HttpMethod.POST,
                    entity,
                    new ParameterizedTypeReference<ApiResponse<SsoTokenResponse>>() {}
            );

            ApiResponse<SsoTokenResponse> body = response.getBody();
            if (body == null || body.getData() == null || body.getData().getAccessToken() == null) {
                throw new ExternalServiceException(HrErrorCode.SSO_SERVICE_ERROR, "SSO 서비스 토큰 응답이 비어있습니다");
            }

            log.info("SSO client_credentials token success");
            return body.getData().getAccessToken();

        } catch (RestClientException e) {
            log.error("SSO client_credentials token API failed: {}", e.getMessage(), e);
            throw new ExternalServiceException(HrErrorCode.SSO_SERVICE_ERROR, "SSO 서비스 토큰 발급 실패", e);
        }
    }

    @Override
    public void changePassword(String userId, String currentPassword, String newPassword, String confirmPassword) {
        log.debug("Calling SSO changePassword API: userId={}", userId);

        String serviceToken = issueServiceToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(serviceToken);

        Map<String, String> requestBody = Map.of(
                "userId", userId,
                "currentPassword", currentPassword,
                "newPassword", newPassword,
                "confirmPassword", confirmPassword
        );
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<ApiResponse<Void>> response = ssoRestTemplate.exchange(
                    SSO_CHANGE_PASSWORD_PATH,
                    HttpMethod.POST,
                    entity,
                    new ParameterizedTypeReference<ApiResponse<Void>>() {}
            );

            ApiResponse<Void> body = response.getBody();
            if (body != null && !body.isSuccess()) {
                throw new InvalidValueException(HrErrorCode.USER_INVALID_PASSWORD, body.getMessage());
            }

            log.info("SSO changePassword API success: userId={}", userId);

        } catch (HttpClientErrorException e) {
            log.warn("SSO changePassword client error for user {}: {}", userId, e.getMessage());
            throw new InvalidValueException(HrErrorCode.USER_INVALID_PASSWORD, extractSsoErrorMessage(e));
        } catch (RestClientException e) {
            log.error("SSO changePassword API failed for user {}: {}", userId, e.getMessage(), e);
            throw new ExternalServiceException(HrErrorCode.SSO_SERVICE_ERROR, "SSO 비밀번호 변경 API 호출 실패", e);
        }
    }

    @Override
    public void resetPassword(String userId) {
        log.debug("Calling SSO resetPassword API: userId={}", userId);

        String serviceToken = issueServiceToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(serviceToken);

        Map<String, String> requestBody = Map.of("userId", userId);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<ApiResponse<Void>> response = ssoRestTemplate.exchange(
                    SSO_RESET_BY_SERVICE_PATH,
                    HttpMethod.POST,
                    entity,
                    new ParameterizedTypeReference<ApiResponse<Void>>() {}
            );

            ApiResponse<Void> body = response.getBody();
            if (body != null && !body.isSuccess()) {
                // SSO 가 돌려준 문장은 응답에 싣지 않고 로그로만 남긴다 (내부 단서 노출 방지)
                log.warn("SSO resetPassword rejected for user {}: {}", userId, body.getMessage());
                throw new ExternalServiceException(HrErrorCode.SSO_SERVICE_ERROR);
            }

            log.info("SSO resetPassword API success: userId={}", userId);

        } catch (HttpClientErrorException e) {
            log.warn("SSO resetPassword client error for user {}: {}", userId, e.getMessage());
            throw new ExternalServiceException(HrErrorCode.SSO_SERVICE_ERROR, e);
        } catch (RestClientException e) {
            log.error("SSO resetPassword API failed for user {}: {}", userId, e.getMessage(), e);
            throw new ExternalServiceException(HrErrorCode.SSO_SERVICE_ERROR, "SSO 비밀번호 리셋 API 호출 실패", e);
        }
    }

    /**
     * SSO 4xx 응답 본문에서 message 필드를 추출한다(간단 파싱).<br>
     * 비밀번호 불일치 등 SSO 의 사용자용 메시지를 그대로 전달하기 위함.
     */
    private String extractSsoErrorMessage(HttpClientErrorException e) {
        try {
            String responseBody = e.getResponseBodyAsString();
            if (responseBody.contains("\"message\"")) {
                int start = responseBody.indexOf("\"message\"") + 11;
                int end = responseBody.indexOf("\"", start);
                if (end > start) {
                    return responseBody.substring(start, end);
                }
            }
        } catch (Exception ex) {
            log.debug("Failed to extract SSO error message", ex);
        }
        return "비밀번호 처리에 실패했습니다";
    }
}
