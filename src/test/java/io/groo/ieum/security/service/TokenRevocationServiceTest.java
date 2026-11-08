package io.groo.ieum.security.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("토큰 차단 목록 서비스")
class TokenRevocationServiceTest {

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> ops = mock(ValueOperations.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final TokenRevocationService service = new TokenRevocationService(redis);

    @Test
    @DisplayName("revoke - 남은 수명만큼 TTL 로 jti 를 넣는다")
    void revokeSetsTtl() {
        when(redis.opsForValue()).thenReturn(ops);
        service.revoke("j1", 90_000L);
        verify(ops).set("ieum:revoked-jti:j1", "1", Duration.ofMillis(90_000L));
    }

    @Test
    @DisplayName("revoke - jti 없음/이미 만료면 아무것도 쓰지 않는다")
    void revokeNoop() {
        service.revoke(null, 1000);
        service.revoke("j1", 0);
        verifyNoInteractions(redis);
    }

    @Test
    @DisplayName("isRevoked - 차단된 jti 는 true")
    void revoked() {
        when(redis.hasKey("ieum:revoked-jti:j1")).thenReturn(true);
        assertThat(service.isRevoked("j1")).isTrue();
    }

    @Test
    @DisplayName("isRevoked - Redis 장애는 fail-open(false)")
    void failOpen() {
        when(redis.hasKey(anyString())).thenThrow(new RuntimeException("redis down"));
        assertThat(service.isRevoked("j1")).isFalse();
    }
}
