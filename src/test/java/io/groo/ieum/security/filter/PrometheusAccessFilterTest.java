package io.groo.ieum.security.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PrometheusAccessFilter (P-1)")
class PrometheusAccessFilterTest {

    private final PrometheusAccessFilter filter = new PrometheusAccessFilter(JsonMapper.builder().build());

    private MockHttpServletRequest req(String uri, String remote) {
        MockHttpServletRequest r = new MockHttpServletRequest("GET", uri);
        r.setRequestURI(uri);
        r.setRemoteAddr(remote);
        return r;
    }

    private MockHttpServletResponse run(MockHttpServletRequest r, MockFilterChain chain) throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(r, res, chain);
        return res;
    }

    @Test
    @DisplayName("컨테이너 네트워크 직접 접근(헤더 없음, 사설 주소)은 통과")
    void internalDirect() throws Exception {
        for (String ip : new String[] { "172.18.0.5", "10.0.0.3", "192.168.0.9", "127.0.0.1", "0:0:0:0:0:0:0:1" }) {
            MockFilterChain chain = new MockFilterChain();
            MockHttpServletResponse res = run(req("/actuator/prometheus", ip), chain);
            assertThat(chain.getRequest()).as(ip).isNotNull();
            assertThat(res.getStatus()).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("전달 헤더가 하나라도 있으면 사설 주소여도 403")
    void forwardedHeaders() throws Exception {
        for (String header : new String[] { "X-Forwarded-For", "X-Real-IP", "Forwarded" }) {
            MockHttpServletRequest r = req("/actuator/prometheus", "172.18.0.5");
            r.addHeader(header, "203.0.113.7");
            MockFilterChain chain = new MockFilterChain();
            MockHttpServletResponse res = run(r, chain);
            assertThat(chain.getRequest()).as(header).isNull();
            assertThat(res.getStatus()).isEqualTo(403);
            assertThat(res.getContentAsString()).contains("COMMON_412");
        }
    }

    @Test
    @DisplayName("공인 주소에서 직접 오면 403")
    void publicRemote() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse res = run(req("/actuator/prometheus", "203.0.113.7"), chain);
        assertThat(chain.getRequest()).isNull();
        assertThat(res.getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("다른 경로는 건드리지 않는다")
    void otherPaths() throws Exception {
        MockHttpServletRequest r = req("/actuator/health", "203.0.113.7");
        r.addHeader("X-Forwarded-For", "203.0.113.7");
        MockFilterChain chain = new MockFilterChain();
        run(r, chain);
        assertThat(chain.getRequest()).isNotNull();
    }
}
