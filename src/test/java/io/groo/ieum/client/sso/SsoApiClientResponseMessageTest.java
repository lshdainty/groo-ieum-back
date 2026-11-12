package io.groo.ieum.client.sso;

import io.groo.core.exception.ExternalServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("SSO 응답 문구 전달 차단 테스트")
class SsoApiClientResponseMessageTest {
    private static final String SSO_INTERNAL = "internal-detail-xyz: user row 42 locked";
    private static final String TOKEN_OK =
            "{\"success\":true,\"code\":\"OK\",\"message\":\"OK\",\"data\":{\"access_token\":\"t\"}}";

    private MockRestServiceServer server;
    private SsoApiClientImpl client;

    @BeforeEach
    void setUp() {
        RestTemplate rest = new RestTemplate();
        rest.setUriTemplateHandler(new DefaultUriBuilderFactory("http://sso.test"));
        server = MockRestServiceServer.bindTo(rest).build();
        client = new SsoApiClientImpl(rest);
        ReflectionTestUtils.setField(client, "clientCode", "hr");
        ReflectionTestUtils.setField(client, "clientSecret", "s");
        server.expect(requestTo("http://sso.test/api/v1/oauth2/token"))
                .andRespond(withSuccess(TOKEN_OK, MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("비밀번호 초기화 - SSO 가 success=false 로 보낸 문장은 응답 예외에 담기지 않는다")
    void resetPasswordDoesNotEchoSsoBodyMessage() {
        server.expect(requestTo("http://sso.test/api/v1/auth/password/reset-by-service"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"success\":false,\"code\":\"X\",\"message\":\"" + SSO_INTERNAL + "\"}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.resetPassword("user1"))
                .isInstanceOf(ExternalServiceException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("internal-detail"));
    }

    @Test
    @DisplayName("비밀번호 초기화 - SSO 4xx 본문의 message 도 응답 예외에 담기지 않는다")
    void resetPasswordDoesNotEchoSso4xxMessage() {
        server.expect(requestTo("http://sso.test/api/v1/auth/password/reset-by-service"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"success\":false,\"message\":\"" + SSO_INTERNAL + "\"}"));

        assertThatThrownBy(() -> client.resetPassword("user1"))
                .isInstanceOf(ExternalServiceException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("internal-detail"));
    }

    @Test
    @DisplayName("보안 점검 SSO-13 - 초대 상태가 2xx 인데 본문이 비면 빈 목록이 아니라 실패다(재연결이 '모두 사라짐' 으로 읽지 않게)")
    void emptyInvitationStatusBodyFails() {
        server.expect(requestTo("http://sso.test/api/v1/users/invitation-status"))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.getInvitationStatus(java.util.List.of(7L)))
                .isInstanceOf(ExternalServiceException.class);
    }

    @Test
    @DisplayName("보안 점검 SSO-13 - data 가 없는 응답도 실패, 빈 목록([])은 정상 빈 결과")
    void nullDataFailsButEmptyListOk() {
        server.expect(requestTo("http://sso.test/api/v1/users/invitation-status"))
                .andRespond(withSuccess("{\"success\":true,\"code\":\"OK\",\"message\":\"OK\"}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.getInvitationStatus(java.util.List.of(7L)))
                .isInstanceOf(ExternalServiceException.class);

        server.reset();
        server.expect(requestTo("http://sso.test/api/v1/oauth2/token"))
                .andRespond(withSuccess(TOKEN_OK, MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://sso.test/api/v1/users/invitation-status"))
                .andRespond(withSuccess("{\"success\":true,\"code\":\"OK\",\"message\":\"OK\",\"data\":[]}",
                        MediaType.APPLICATION_JSON));
        assertThat(client.getInvitationStatus(java.util.List.of(7L))).isEmpty();
    }
}
