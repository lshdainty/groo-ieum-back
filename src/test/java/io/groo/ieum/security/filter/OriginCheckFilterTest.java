package io.groo.ieum.security.filter;

import io.groo.ieum.common.config.properties.AppProperties;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OriginCheckFilter (S-5)")
class OriginCheckFilterTest {

    private static final String FRONT = "https://hr.example.test";

    private OriginCheckFilter filter;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties();
        props.getFrontend().setBaseUrl(FRONT);
        filter = new OriginCheckFilter(props, JsonMapper.builder().build());
    }

    private MockHttpServletRequest cookieRequest(String method) {
        MockHttpServletRequest req = new MockHttpServletRequest(method, "/api/v1/vacation-approvals/1/approve");
        req.setCookies(new Cookie("hr_access_token", "t"));
        return req;
    }

    private boolean passes(MockHttpServletRequest req, MockHttpServletResponse res) throws Exception {
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(req, res, chain);
        return chain.getRequest() != null;
    }

    @Test
    @DisplayName("허용 오리진은 통과")
    void allowedOrigin() throws Exception {
        MockHttpServletRequest req = cookieRequest("POST");
        req.addHeader("Origin", FRONT);
        MockHttpServletResponse res = new MockHttpServletResponse();
        assertThat(passes(req, res)).isTrue();
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("외부 오리진은 403 + 기존 에러 형식")
    void foreignOrigin() throws Exception {
        for (String method : new String[] { "POST", "PUT", "PATCH", "DELETE" }) {
            MockHttpServletRequest req = cookieRequest(method);
            req.addHeader("Origin", "https://evil.example.test");
            MockHttpServletResponse res = new MockHttpServletResponse();
            assertThat(passes(req, res)).as(method).isFalse();
            assertThat(res.getStatus()).isEqualTo(403);
            assertThat(res.getContentAsString()).contains("\"success\":false").contains("COMMON_412");
        }
    }

    @Test
    @DisplayName("같은 사이트의 형제 서브도메인 오리진도 403")
    void siblingSubdomain() throws Exception {
        MockHttpServletRequest req = cookieRequest("POST");
        req.addHeader("Origin", "https://damda.example.test");
        MockHttpServletResponse res = new MockHttpServletResponse();
        assertThat(passes(req, res)).isFalse();
        assertThat(res.getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("Origin 이 'null' 이면 403")
    void nullOrigin() throws Exception {
        MockHttpServletRequest req = cookieRequest("POST");
        req.addHeader("Origin", "null");
        MockHttpServletResponse res = new MockHttpServletResponse();
        assertThat(passes(req, res)).isFalse();
        assertThat(res.getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("Origin · Referer 둘 다 없으면 통과")
    void noHeaders() throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        assertThat(passes(cookieRequest("POST"), res)).isTrue();
    }

    @Test
    @DisplayName("Bearer 헤더 인증은 검사하지 않는다")
    void bearerSkipped() throws Exception {
        MockHttpServletRequest req = cookieRequest("POST");
        req.addHeader("Authorization", "Bearer abc");
        req.addHeader("Origin", "https://evil.example.test");
        assertThat(passes(req, new MockHttpServletResponse())).isTrue();
    }

    @Test
    @DisplayName("GET 은 검사하지 않는다")
    void getSkipped() throws Exception {
        MockHttpServletRequest req = cookieRequest("GET");
        req.addHeader("Origin", "https://evil.example.test");
        assertThat(passes(req, new MockHttpServletResponse())).isTrue();
    }

    @Test
    @DisplayName("Origin 이 없으면 Referer 로 판정한다")
    void refererFallback() throws Exception {
        MockHttpServletRequest ok = cookieRequest("POST");
        ok.addHeader("Referer", FRONT + "/vacation/approvals?x=1");
        assertThat(passes(ok, new MockHttpServletResponse())).isTrue();

        MockHttpServletRequest bad = cookieRequest("POST");
        bad.addHeader("Referer", "https://evil.example.test/page");
        MockHttpServletResponse res = new MockHttpServletResponse();
        assertThat(passes(bad, res)).isFalse();
        assertThat(res.getStatus()).isEqualTo(403);

        MockHttpServletRequest lookalike = cookieRequest("POST");
        lookalike.addHeader("Referer", FRONT + ".evil.test/page");
        assertThat(passes(lookalike, new MockHttpServletResponse())).isFalse();
    }

    @Test
    @DisplayName("인증 쿠키가 없는 요청은 검사 대상이 아니다")
    void noCookieSkipped() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/exchange-code");
        req.addHeader("Origin", "https://evil.example.test");
        assertThat(passes(req, new MockHttpServletResponse())).isTrue();
    }
}
