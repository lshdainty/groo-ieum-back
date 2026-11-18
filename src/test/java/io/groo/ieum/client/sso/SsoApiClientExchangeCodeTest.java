package io.groo.ieum.client.sso;

import io.groo.core.exception.BusinessException;
import io.groo.core.exception.ExternalServiceException;
import io.groo.core.exception.ForbiddenException;
import io.groo.core.exception.UnauthorizedException;
import io.groo.ieum.common.exception.HrErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 인가 코드 교환에서 SSO 의 거절과 SSO 장애를 나눠 답하는지 본다(보안 점검 IEU-9).
 *
 * <p>{@code POST /api/v1/auth/exchange-code} 는 로그인하지 않은 누구나 부를 수 있다. SSO 가 틀린 코드를 400 으로
 * 거절한 것까지 502 와 ERROR(스택 포함)로 남기면, 요청하는 쪽의 실수가 서버 장애처럼 기록되고 오류 로그와 장애 경보를
 * 일부러 늘릴 수 있다. 거절은 4xx 와 WARN 한 줄, 장애만 502 와 ERROR 다.
 */
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("인가 코드 교환 — SSO 의 거절과 장애를 나눠 답한다 (IEU-9)")
class SsoApiClientExchangeCodeTest {
    private static final String TOKEN_URL = "http://sso.test/api/v1/oauth2/token";
    private static final String SSO_INTERNAL = "internal-detail-xyz: code row 42 already used";
    private static final String ERROR_LOG = "SSO /oauth2/token API failed";

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
    }

    private void ssoAnswers(HttpStatus status) {
        server.expect(requestTo(TOKEN_URL)).andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(status).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"success\":false,\"code\":\"X\",\"message\":\"" + SSO_INTERNAL + "\"}"));
    }

    private int statusOf(Throwable e) {
        return ((BusinessException) e).getErrorCode().getHttpStatusCode();
    }

    @Test
    @DisplayName("SSO 가 400 으로 거절하면(틀렸거나 이미 쓴 코드) 401 로 답하고 ERROR 를 남기지 않는다")
    void rejectedCodeIsUnauthorizedNotBadGateway(CapturedOutput output) {
        ssoAnswers(HttpStatus.BAD_REQUEST);

        assertThatThrownBy(() -> client.exchangeOAuthCode("wrong-code", "verifier", "https://ieum.test/auth/callback"))
                .isInstanceOf(UnauthorizedException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(401))
                .satisfies(e -> assertThat(e.getMessage()).as("SSO 의 응답 문구를 싣지 않는다").doesNotContain("internal-detail"));

        assertThat(output.getAll()).as("장애 로그(ERROR · 스택)를 남기지 않는다").doesNotContain(ERROR_LOG);
        assertThat(output.getAll()).as("거절은 WARN 한 줄").contains("인가 코드 교환을 거절했다: status=400");
        assertThat(output.getAll()).as("인가 코드 · SSO 문구가 로그에 실리지 않는다")
                .doesNotContain("wrong-code").doesNotContain("internal-detail");
    }

    @Test
    @DisplayName("SSO 가 401 로 거절해도 401 이다")
    void unauthorizedFromSsoIsUnauthorized() {
        ssoAnswers(HttpStatus.UNAUTHORIZED);

        assertThatThrownBy(() -> client.exchangeOAuthCode("c", "v", "https://ieum.test/auth/callback"))
                .isInstanceOf(UnauthorizedException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(401));
    }

    @Test
    @DisplayName("SSO 가 403 으로 거절하면(비활성 계정 · 이음 접근 없음) 403 으로 답하고 ERROR 를 남기지 않는다")
    void forbiddenAccountIsForbidden(CapturedOutput output) {
        ssoAnswers(HttpStatus.FORBIDDEN);

        assertThatThrownBy(() -> client.exchangeOAuthCode("c", "v", "https://ieum.test/auth/callback"))
                .isInstanceOf(ForbiddenException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(403));

        assertThat(output.getAll()).doesNotContain(ERROR_LOG);
    }

    @Test
    @DisplayName("SSO 가 404 로 답하면(클라이언트 미등록 — 이음 설정 문제) 여전히 502 와 ERROR 다")
    void unknownClientStaysBadGateway(CapturedOutput output) {
        ssoAnswers(HttpStatus.NOT_FOUND);

        assertThatThrownBy(() -> client.exchangeOAuthCode("c", "v", "https://ieum.test/auth/callback"))
                .isInstanceOf(ExternalServiceException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(HrErrorCode.SSO_SERVICE_ERROR))
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(502));

        assertThat(output.getAll()).contains(ERROR_LOG);
    }

    @Test
    @DisplayName("SSO 가 5xx 로 답하면 502 와 ERROR 다")
    void ssoServerErrorIsBadGateway(CapturedOutput output) {
        ssoAnswers(HttpStatus.INTERNAL_SERVER_ERROR);

        assertThatThrownBy(() -> client.exchangeOAuthCode("c", "v", "https://ieum.test/auth/callback"))
                .isInstanceOf(ExternalServiceException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(502));

        assertThat(output.getAll()).contains(ERROR_LOG);
    }

    @Test
    @DisplayName("SSO 에 닿지 못하면(연결 실패 · 시간 초과) 502 와 ERROR 다")
    void unreachableSsoIsBadGateway(CapturedOutput output) {
        server.expect(requestTo(TOKEN_URL)).andRespond(withException(new IOException("connect timed out")));

        assertThatThrownBy(() -> client.exchangeOAuthCode("c", "v", "https://ieum.test/auth/callback"))
                .isInstanceOf(ExternalServiceException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(502));

        assertThat(output.getAll()).contains(ERROR_LOG);
    }

    @Test
    @DisplayName("SSO 가 토큰을 주면 그 토큰을 돌려준다")
    void successReturnsAccessToken() {
        server.expect(requestTo(TOKEN_URL)).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"success\":true,\"code\":\"OK\",\"message\":\"OK\",\"data\":{\"access_token\":\"sso-token\"}}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.exchangeOAuthCode("c", "v", "https://ieum.test/auth/callback")).isEqualTo("sso-token");
    }
}
