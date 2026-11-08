package io.groo.ieum.security.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("클라이언트 IP 판정")
class ClientIpResolverTest {

    // remote | X-Real-IP | X-Forwarded-For | 기대
    @ParameterizedTest(name = "remote={0} real={1} xff={2} -> {3}")
    @DisplayName("헤더 조합")
    @CsvSource(delimiter = '|', nullValues = "-", value = {
            // groo/infra/nginx/proxy.conf 구성: nginx(사설) 가 X-Real-IP 를 덮어쓰고 XFF 를 덧붙인다
            "172.19.0.5 | 203.0.113.9 | 198.51.100.1, 203.0.113.9 | 203.0.113.9",
            // 위조 XFF 가 앞에 있어도 nginx 가 덧붙인 오른쪽 값이 실제 IP
            "172.19.0.5 | - | 1.2.3.4, 203.0.113.9 | 203.0.113.9",
            // XFF 오른쪽 끝의 사설(중간 프록시)은 걷어 낸다
            "10.0.0.2 | - | 203.0.113.9, 10.0.0.7 | 203.0.113.9",
            // 사설 remote + 헤더 없음
            "192.168.0.36 | - | - | 192.168.0.36",
            "127.0.0.1 | - | - | 127.0.0.1",
            // 공인 remote 의 위조 헤더는 무시 (차단 IP 우회 불가)
            "203.0.113.50 | 1.1.1.1 | 8.8.8.8 | 203.0.113.50",
            "203.0.113.50 | - | 8.8.8.8 | 203.0.113.50",
            // IPv6
            "::1 | 2001:db8::1 | - | 2001:db8::1",
            "fd00::5 | - | 2001:db8::2, fd00::9 | 2001:db8::2",
            "2001:db8::77 | 9.9.9.9 | 9.9.9.9 | 2001:db8::77",
            // 깨진 값은 무시하고 remote
            "172.19.0.5 | not-an-ip | garbage | 172.19.0.5",
            // XFF 전부 사설이면 remote
            "172.19.0.5 | - | 10.1.1.1 | 172.19.0.5",
    })
    void resolve(String remote, String realIp, String xff, String expected) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr(remote.trim());
        if (realIp != null) req.addHeader("X-Real-IP", realIp.trim());
        if (xff != null) req.addHeader("X-Forwarded-For", xff.trim());
        assertThat(ClientIpResolver.resolve(req)).isEqualTo(expected.trim());
    }
}
