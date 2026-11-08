package io.groo.ieum.common.config.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("레이트리밋 인터셉터")
class RateLimitInterceptorTest {

    private final Map<String, Long> counters = new HashMap<>();
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> ops = mock(ValueOperations.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final RateLimitInterceptor interceptor = new RateLimitInterceptor(redis, "login", 3);

    RateLimitInterceptorTest() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.increment(anyString())).thenAnswer(i -> counters.merge(i.getArgument(0), 1L, Long::sum));
    }

    private int call(String ip) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr(ip);
        MockHttpServletResponse res = new MockHttpServletResponse();
        return interceptor.preHandle(req, res, new Object()) ? 200 : res.getStatus();
    }

    @Test
    @DisplayName("한도 이하는 통과, 초과는 429")
    void limit() throws Exception {
        assertThat(call("203.0.113.1")).isEqualTo(200);
        assertThat(call("203.0.113.1")).isEqualTo(200);
        assertThat(call("203.0.113.1")).isEqualTo(200);
        assertThat(call("203.0.113.1")).isEqualTo(429);
    }

    @Test
    @DisplayName("다른 IP 는 독립")
    void independentIps() throws Exception {
        for (int i = 0; i < 4; i++) call("203.0.113.1");
        assertThat(call("203.0.113.2")).isEqualTo(200);
    }

    @Test
    @DisplayName("창이 지나(키 만료) 초기화되면 다시 통과")
    void reset() throws Exception {
        for (int i = 0; i < 4; i++) call("203.0.113.1");
        assertThat(call("203.0.113.1")).isEqualTo(429);
        counters.clear(); // Redis TTL 만료
        assertThat(call("203.0.113.1")).isEqualTo(200);
        verify(redis, atLeastOnce()).expire(anyString(), eq(60L), eq(java.util.concurrent.TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("Redis 장애는 fail-open")
    void failOpen() throws Exception {
        when(ops.increment(anyString())).thenThrow(new RuntimeException("down"));
        assertThat(call("203.0.113.1")).isEqualTo(200);
    }

    @Test
    @DisplayName("위조 X-Forwarded-For 로 한도를 피할 수 없다")
    void spoofedHeaderDoesNotEvade() throws Exception {
        for (int i = 0; i < 4; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest();
            req.setRemoteAddr("203.0.113.1");
            req.addHeader("X-Forwarded-For", "9.9.9." + i);
            interceptor.preHandle(req, new MockHttpServletResponse(), new Object());
        }
        assertThat(call("203.0.113.1")).isEqualTo(429);
    }
}
