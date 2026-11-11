package io.groo.ieum.common.config.security;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * {@link RequestResponseLoggingFilter} 왕복 테스트.
 *
 * <p><b>이 테스트가 존재하는 이유</b> — 예전에 "마스킹한다고 request 에서부터 마스킹해서
 * 그 뒤에 싹 다 동작 안 한" 사고가 있었다. HTTP 요청 본문 스트림은 한 번 읽으면 끝이라
 * 필터가 스트림을 직접 읽으면 컨트롤러가 빈 본문을 받고 모든 POST/PUT 이 <b>조용히</b> 깨진다.
 * 응답도 마찬가지로 {@code copyBodyToResponse()} 를 빠뜨리면 빈 본문이 나간다.
 *
 * <p>그래서 "로그에 {@code ***} 가 있다" 만 보는 테스트로는 부족하다. 여기서는 실제 요청을
 * 태워 <b>컨트롤러가 받은 본문</b>과 <b>클라이언트가 받은 본문</b>을 직접 확인한다.
 * HR 은 인증(인가코드 교환·비밀번호 변경)을 다루므로 여기가 깨지면 로그인이 통째로 안 된다.
 */
class RequestResponseLoggingFilterTest {

    /** 실제 JWT 모양(eyJ + 점 3파트). 값 자체는 테스트용 더미다. */
    private static final String JWT =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"
                    + ".eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ"
                    + ".SflKxwRJSMeKKF2QTVsHTfNiYSHwGXBUUWxUmYCa8gY";

    /** OAuth2 인가코드 길이(43자 base64url). core 는 이 모양일 때만 code 를 가린다. */
    private static final String AUTH_CODE = "Ab3dEf6hIj9lMn2pQr5tUv8xYz1cDe4gHi7kLm0oPq3";

    private Logger filterLogger;
    private ListAppender<ILoggingEvent> appender;
    private Level originalLevel;

    @BeforeEach
    void setUp() {
        filterLogger = (Logger) LoggerFactory.getLogger(RequestResponseLoggingFilter.class);
        originalLevel = filterLogger.getLevel();
        appender = new ListAppender<>();
        appender.start();
        filterLogger.addAppender(appender);
        filterLogger.setLevel(Level.DEBUG);
    }

    @AfterEach
    void tearDown() {
        filterLogger.detachAppender(appender);
        filterLogger.setLevel(originalLevel);
        appender.stop();
    }

    private MockMvc mockMvc(jakarta.servlet.Filter filter) {
        return MockMvcBuilders.standaloneSetup(new EchoController())
                .addFilters(filter)
                .build();
    }

    private MockMvc mockMvc() {
        return mockMvc(new RequestResponseLoggingFilter());
    }

    private String logText() {
        return appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (a, b) -> a + "\n" + b);
    }

    // ------------------------------------------------------------------
    // 1. 기능이 깨지지 않는다 — 요청 본문 왕복
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("요청 본문이 컨트롤러에 온전히 도착한다")
    class RequestBodyRoundTrip {

        @Test
        @DisplayName("민감 필드가 든 JSON POST 도 컨트롤러는 원문 그대로 받는다")
        void controllerReceivesUnmaskedBody() throws Exception {
            String body = "{\"userId\":\"honggd\",\"password\":\"P@ssw0rd!\",\"memo\":\"hello\"}";

            MvcResult result = mockMvc().perform(post("/echo")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andReturn();

            // 컨트롤러가 받은 본문 = 원문. 마스킹이 요청을 건드리면 여기서 깨진다.
            assertThat(EchoController.lastBody).isEqualTo(body);
            assertThat(EchoController.lastBody).contains("P@ssw0rd!");
            // 클라이언트가 받은 응답 = 원문(마스킹은 로그에만).
            assertThat(result.getResponse().getContentAsString()).isEqualTo(body);
        }

        @Test
        @DisplayName("인가코드 교환 요청(code + code_verifier)도 온전히 도착한다")
        void authorizationCodeExchangeBodySurvives() throws Exception {
            String body = "{\"code\":\"" + AUTH_CODE + "\",\"codeVerifier\":\"" + AUTH_CODE
                    + "\",\"redirectUri\":\"https://hr.porest.com/callback\"}";

            mockMvc().perform(post("/echo").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andReturn();

            assertThat(EchoController.lastBody).isEqualTo(body);
        }

        @Test
        @DisplayName("본문 캐시 상한(10KB)을 넘는 큰 본문도 잘리지 않고 도착한다")
        void hugeBodySurvives() throws Exception {
            String filler = "x".repeat(50_000);
            String body = "{\"password\":\"secret\",\"blob\":\"" + filler + "\"}";

            mockMvc().perform(post("/echo").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andReturn();

            // ContentCachingRequestWrapper 의 캐시 상한은 "로그용 사본"만 자른다.
            // 컨트롤러로 가는 스트림은 온전해야 한다.
            assertThat(EchoController.lastBody).hasSize(body.length());
            assertThat(EchoController.lastBody).isEqualTo(body);
        }

        @Test
        @DisplayName("비-JSON 본문(text/plain)도 그대로 도착한다")
        void nonJsonBodySurvives() throws Exception {
            String body = "이건 JSON 이 아니다 password=지나가는말";

            mockMvc().perform(post("/echo")
                            .contentType(MediaType.TEXT_PLAIN)
                            .characterEncoding(StandardCharsets.UTF_8)
                            .content(body.getBytes(StandardCharsets.UTF_8)))
                    .andReturn();

            assertThat(EchoController.lastBody).isEqualTo(body);
        }

        @Test
        @DisplayName("본문이 비어도 예외 없이 통과한다")
        void emptyBodySurvives() throws Exception {
            MvcResult result = mockMvc().perform(get("/ping")).andReturn();

            assertThat(result.getResponse().getStatus()).isEqualTo(200);
            assertThat(result.getResponse().getContentAsString()).isEqualTo("pong");
        }

        @Test
        @DisplayName("멀티파트 업로드도 파일 내용이 온전히 도착한다")
        void multipartSurvives() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "a.txt", MediaType.TEXT_PLAIN_VALUE, "파일내용".getBytes(StandardCharsets.UTF_8));

            mockMvc().perform(multipart("/upload").file(file)).andReturn();

            assertThat(EchoController.lastUpload).isEqualTo("파일내용");
        }

        @Test
        @DisplayName("쿼리 파라미터는 마스킹돼도 컨트롤러에는 원값이 온다")
        void queryParamReachesControllerUnmasked() throws Exception {
            mockMvc().perform(get("/search?token=" + JWT + "&keyword=vacation")).andReturn();

            assertThat(EchoController.lastQueryToken).isEqualTo(JWT);
            assertThat(EchoController.lastQueryKeyword).isEqualTo("vacation");
        }
    }

    // ------------------------------------------------------------------
    // 2. 기능이 깨지지 않는다 — 응답 본문 왕복
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("응답 본문이 클라이언트에 온전히 나간다")
    class ResponseBodyRoundTrip {

        @Test
        @DisplayName("copyBodyToResponse 가 돌아 응답이 비지 않는다")
        void responseIsNotEmptied() throws Exception {
            String body = "{\"code\":\"COMMON_200\",\"data\":{\"access_token\":\"" + JWT + "\"}}";

            MvcResult result = mockMvc().perform(post("/echo")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andReturn();

            assertThat(result.getResponse().getContentAsString()).isEqualTo(body);
            assertThat(result.getResponse().getContentAsString()).contains(JWT);
        }

        @Test
        @DisplayName("로깅이 Error 로 터져도 응답 본문은 그대로 나간다")
        void responseSurvivesLoggingError() throws Exception {
            // StackOverflowError 는 Error 라 catch(Exception) 을 뚫고 나간다. 예전 구조는
            // logRequestResponse() 와 copyBodyToResponse() 가 같은 finally 안에 나란히 있어서
            // 로깅이 Error 로 터지면 copyBodyToResponse() 가 통째로 안 돌았다 — 빈 응답이 나간다.
            // MockMvc 는 Error 를 그대로 올려 보내 응답을 못 보므로 필터를 직접 돌린다.
            RequestResponseLoggingFilter exploding = new RequestResponseLoggingFilter() {
                @Override
                protected void logRequestResponse(ContentCachingRequestWrapper request,
                                                  ContentCachingResponseWrapper response,
                                                  String traceId,
                                                  long executionTime) {
                    throw new StackOverflowError("boom");
                }
            };

            String body = "{\"hello\":\"world\"}";
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/echo");
            request.setContentType(MediaType.APPLICATION_JSON_VALUE);
            request.setContent(body.getBytes(StandardCharsets.UTF_8));
            MockHttpServletResponse response = new MockHttpServletResponse();

            // core 3.1.0 공통 뼈대는 로깅 실패(Error 포함)를 삼키고 요청을 정상 통과시킨다 — 로깅은 부가 기능이라
            // 실패해도 응답이 사고가 되면 안 된다. 예전 이음 판은 Error 를 밖으로 던졌다(응답은 지켰지만 500 으로 바뀌었다).
            assertThatCode(() -> exploding.doFilter(request, response,
                    (req, res) -> res.getWriter().write(body)))
                    .doesNotThrowAnyException();

            // 응답 본문은 그대로 나가야 한다(copyBodyToResponse 가 돌았다).
            assertThat(response.getContentAsString()).isEqualTo(body);
        }

        @Test
        @DisplayName("컨트롤러가 예외를 던져도 필터가 요청을 삼키지 않는다")
        void controllerExceptionPropagates() {
            try {
                mockMvc().perform(get("/boom")).andReturn();
            } catch (Exception e) {
                // MockMvc 는 예외를 그대로 올려 준다. 필터가 삼키지 않았다는 뜻.
                assertThat(e).hasRootCauseMessage("controller failed");
                return;
            }
            // DispatcherServlet 이 500 으로 바꿔 준 경우도 정상 — 필터가 막지만 않으면 된다.
        }
    }

    // ------------------------------------------------------------------
    // 3. 그 다음에야 마스킹
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("로그에서 민감값이 가려진다")
    class Masking {

        @Test
        @DisplayName("요청 본문의 비밀번호가 가려진다")
        void requestPasswordMasked() throws Exception {
            mockMvc().perform(post("/echo").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"password\":\"P@ssw0rd!\"}")).andReturn();

            assertThat(logText()).doesNotContain("P@ssw0rd!").contains("***");
        }

        @Test
        @DisplayName("요청 본문의 이메일 · 생년월일이 가려진다 — 사용자 수정 · 초대 본문")
        void requestEmailAndBirthMasked() throws Exception {
            mockMvc().perform(post("/echo").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"user_name\":\"홍길동\",\"user_email\":\"hong@example.com\",\"email\":\"a@example.com\","
                            + "\"user_birth\":\"1990-01-02\"}")).andReturn();

            String logs = logText();
            assertThat(logs).doesNotContain("hong@example.com").doesNotContain("a@example.com").doesNotContain("1990-01-02");
            // 진단에 필요한 비민감 칸은 그대로 남는다
            assertThat(logs).contains("홍길동");
        }

        @Test
        @DisplayName("쿼리스트링의 이메일이 가려진다")
        void queryEmailMasked() throws Exception {
            mockMvc().perform(get("/search?email=hong@example.com&keyword=vacation")).andReturn();

            String logs = logText();
            assertThat(logs).doesNotContain("hong@example.com");
            assertThat(logs).contains("keyword=vacation");
        }

        @Test
        @DisplayName("응답 본문의 토큰이 가려진다 — sso 가 빠뜨렸던 경로")
        void responseTokenMasked() throws Exception {
            mockMvc().perform(post("/echo").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"access_token\":\"" + JWT + "\"}")).andReturn();

            String logs = logText();
            assertThat(logs).contains("Res:");
            assertThat(logs).doesNotContain(JWT);
            assertThat(logs).doesNotContain("eyJ");
        }

        @Test
        @DisplayName("쿼리스트링의 토큰이 가려진다 — hr 에 없던 경로")
        void queryStringMasked() throws Exception {
            mockMvc().perform(get("/search?token=" + JWT + "&keyword=vacation")).andReturn();

            String logs = logText();
            assertThat(logs).doesNotContain(JWT);
            assertThat(logs).contains("token=***");
            // 진단에 필요한 비민감 파라미터는 그대로 남는다
            assertThat(logs).contains("keyword=vacation");
        }

        @Test
        @DisplayName("잘린 본문의 DEBUG 전문 로그도 마스킹을 거친다")
        void debugFullBodyIsMasked() throws Exception {
            // 500자를 넘겨 truncate → logFullBody(DEBUG) 경로를 태운다.
            String body = "{\"filler\":\"" + "a".repeat(600) + "\",\"refresh_token\":\"" + JWT + "\"}";

            mockMvc().perform(post("/echo").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andReturn();

            String logs = logText();
            assertThat(logs).contains("Full Request Body");
            assertThat(logs).contains("Full Response Body");
            assertThat(logs).doesNotContain(JWT);
            assertThat(logs).doesNotContain("eyJ");
        }

        @Test
        @DisplayName("HR 고유 키(invitation_token)가 가려진다")
        void hrSpecificKeyMasked() throws Exception {
            mockMvc().perform(post("/echo").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"invitationToken\":\"inv-9f2c-abcdef\"}")).andReturn();

            assertThat(logText()).doesNotContain("inv-9f2c-abcdef");
        }

        @Test
        @DisplayName("인가코드는 가리고 ApiResponse 봉투의 code 는 남긴다")
        void authCodeMaskedButEnvelopeCodeKept() throws Exception {
            String body = "{\"code\":\"" + AUTH_CODE + "\"}";
            mockMvc().perform(post("/echo").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andReturn();
            assertThat(logText()).doesNotContain(AUTH_CODE);

            appender.list.clear();

            mockMvc().perform(post("/echo").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"code\":\"COMMON_200\",\"message\":\"성공\"}")).andReturn();
            // 봉투 상태코드까지 가리면 로그가 못 쓰게 된다.
            assertThat(logText()).contains("COMMON_200");
        }

        @Test
        @DisplayName("Authorization·Cookie 헤더는 애초에 로그에 나가지 않는다")
        void headersAreNotLogged() throws Exception {
            mockMvc().perform(get("/ping")
                            .header("Authorization", "Bearer " + JWT)
                            .header("Cookie", "hr_access_token=" + JWT))
                    .andReturn();

            String logs = logText();
            assertThat(logs).doesNotContain(JWT);
            assertThat(logs).doesNotContain("hr_access_token");
        }
    }

    // ------------------------------------------------------------------
    // 4. 기존 동작 유지
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("기존 동작이 유지된다")
    class ExistingBehaviour {

        @Test
        @DisplayName("제외 경로는 로그를 남기지 않고 통과한다")
        void excludedPathIsNotLogged() throws Exception {
            MvcResult result = mockMvc().perform(get("/actuator/health")).andReturn();

            assertThat(result.getResponse().getContentAsString()).isEqualTo("UP");
            assertThat(appender.list).isEmpty();
        }

        @Test
        @DisplayName("4xx 는 WARN, 5xx 는 ERROR 로 나간다")
        void logLevelFollowsStatus() throws Exception {
            mockMvc().perform(get("/status/404")).andReturn();
            assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.WARN);

            appender.list.clear();
            mockMvc().perform(get("/status/500")).andReturn();
            assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.ERROR);
        }

        @Test
        @DisplayName("한 줄 로그의 본문은 500자에서 잘린다")
        void bodyIsTruncatedInOneLiner() throws Exception {
            String body = "{\"filler\":\"" + "a".repeat(2_000) + "\"}";
            mockMvc().perform(post("/echo").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andReturn();

            String oneLiner = appender.list.get(0).getFormattedMessage();
            assertThat(oneLiner).contains("...");
            assertThat(oneLiner.length()).isLessThan(2_000);
        }
    }

    // ------------------------------------------------------------------

    @RestController
    static class EchoController {
        static String lastBody;
        static String lastQueryToken;
        static String lastQueryKeyword;
        static String lastUpload;

        @PostMapping("/echo")
        String echo(@RequestBody(required = false) String body) {
            lastBody = body;
            return body;
        }

        @GetMapping("/ping")
        String ping() {
            return "pong";
        }

        @GetMapping("/search")
        String search(@RequestParam String token, @RequestParam String keyword) {
            lastQueryToken = token;
            lastQueryKeyword = keyword;
            return "{\"code\":\"COMMON_200\"}";
        }

        @PostMapping("/upload")
        String upload(@RequestPart("file") MultipartFile file) throws IOException {
            lastUpload = new String(file.getBytes(), StandardCharsets.UTF_8);
            return "ok";
        }

        @GetMapping("/actuator/health")
        String health() {
            return "UP";
        }

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("controller failed");
        }

        @GetMapping("/status/404")
        org.springframework.http.ResponseEntity<String> notFound() {
            return org.springframework.http.ResponseEntity.status(404).body("{\"code\":\"COMMON_404\"}");
        }

        @GetMapping("/status/500")
        org.springframework.http.ResponseEntity<String> serverError() {
            return org.springframework.http.ResponseEntity.status(500).body("{\"code\":\"COMMON_500\"}");
        }
    }
}
