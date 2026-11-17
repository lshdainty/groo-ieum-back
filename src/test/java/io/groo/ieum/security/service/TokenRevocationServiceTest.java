package io.groo.ieum.security.service;

import io.groo.ieum.common.config.properties.JwtProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("토큰 차단 목록 서비스")
class TokenRevocationServiceTest {

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> ops = mock(ValueOperations.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final JwtProperties jwtProperties = new JwtProperties();
    private final TokenRevocationService service = new TokenRevocationService(redis, jwtProperties);

    {
        jwtProperties.setHrAccessExpiration(3_600_000L);
    }

    @Test
    @DisplayName("revoke - 남은 수명만큼 TTL 로 jti 를 넣는다")
    void revokeSetsTtl() {
        when(redis.opsForValue()).thenReturn(ops);
        service.revoke("j1", 90_000L);
        verify(ops).set("revoked-jti:j1", "1", Duration.ofMillis(90_000L));
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
        when(redis.hasKey("revoked-jti:j1")).thenReturn(true);
        assertThat(service.isRevoked("j1")).isTrue();
    }

    @Test
    @DisplayName("isRevoked - Redis 장애는 fail-open(false)")
    void failOpen() {
        when(redis.hasKey(anyString())).thenThrow(new RuntimeException("redis down"));
        assertThat(service.isRevoked("j1")).isFalse();
    }

    // ==================== 사용자 단위 차단 (보안 점검 IEU-2) ====================

    @Test
    @DisplayName("revokeAllForUser - 트랜잭션 밖이면 바로 차단 시각을 HR 토큰 최대 수명 TTL 로 넣는다")
    void revokeAllForUserWritesNow() {
        when(redis.opsForValue()).thenReturn(ops);
        long before = System.currentTimeMillis();

        service.revokeAllForUser("u1");

        org.mockito.ArgumentCaptor<String> value = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(ops).set(eq("revoked-user:u1"), value.capture(), eq(Duration.ofMillis(3_600_000L)));
        assertThat(Long.parseLong(value.getValue())).isBetween(before, System.currentTimeMillis());
    }

    @Test
    @DisplayName("revokeAllForUser - 트랜잭션 안이면 커밋 뒤에 차단하고, 롤백되면 차단하지 않는다")
    void revokeAllForUserAfterCommit() {
        when(redis.opsForValue()).thenReturn(ops);
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            service.revokeAllForUser("u1");
            verifyNoInteractions(ops);

            var syncs = org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations();
            assertThat(syncs).hasSize(1);
            syncs.forEach(s -> s.afterCommit());
            verify(ops).set(eq("revoked-user:u1"), anyString(), eq(Duration.ofMillis(3_600_000L)));
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("isUserRevoked - 차단 시각 이전 · 같은 초에 발급된 토큰은 막고, 이후 발급분은 통과")
    void isUserRevokedComparesIat() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get("revoked-user:u1")).thenReturn("1700000000500");

        assertThat(service.isUserRevoked("u1", new java.util.Date(1699999999000L))).isTrue();
        assertThat(service.isUserRevoked("u1", new java.util.Date(1700000000000L))).isTrue();
        assertThat(service.isUserRevoked("u1", new java.util.Date(1700000001000L))).isFalse();
        assertThat(service.isUserRevoked("u1", null)).isTrue();
    }

    @Test
    @DisplayName("isUserRevoked - 차단 기록이 없으면 통과, Redis 장애는 fail-open")
    void isUserRevokedNoRecordOrFailure() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get("revoked-user:u2")).thenReturn(null);
        assertThat(service.isUserRevoked("u2", new java.util.Date())).isFalse();

        when(ops.get("revoked-user:u3")).thenThrow(new RuntimeException("redis down"));
        assertThat(service.isUserRevoked("u3", new java.util.Date())).isFalse();
    }

    @Test
    @DisplayName("CLI-6 revokeSsoLogin - 그 SSO 로그인 번호를 하루 동안 적어 둔다")
    void revokeSsoLoginWrites() {
        when(redis.opsForValue()).thenReturn(ops);

        service.revokeSsoLogin("sid-1");

        verify(ops).set("revoked-sso-sid:sid-1", "1", TokenRevocationService.SSO_LOGIN_RETENTION);
        assertThat(TokenRevocationService.SSO_LOGIN_RETENTION)
                .as("그 로그인의 HR 토큰 · SSO 토큰(각 1시간 안팎)이 전부 만료될 때까지는 남아 있어야 한다")
                .isGreaterThanOrEqualTo(java.time.Duration.ofMillis(jwtProperties.getHrAccessExpiration()));
    }

    @Test
    @DisplayName("CLI-6 revokeSsoLogin - 번호가 없으면 쓰지 않고, Redis 장애는 예외를 올리지 않는다")
    void revokeSsoLoginBlankOrFailure() {
        service.revokeSsoLogin(null);
        service.revokeSsoLogin(" ");
        verify(redis, org.mockito.Mockito.never()).opsForValue();

        when(redis.opsForValue()).thenThrow(new RuntimeException("redis down"));
        org.assertj.core.api.Assertions.assertThatCode(() -> service.revokeSsoLogin("sid-1")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("CLI-6 isSsoLoginRevoked - 적힌 번호는 true, 없는 번호 · 번호 없는 토큰은 false, Redis 장애는 fail-open")
    void isSsoLoginRevoked() {
        when(redis.hasKey("revoked-sso-sid:sid-1")).thenReturn(true);
        when(redis.hasKey("revoked-sso-sid:sid-2")).thenReturn(false);

        assertThat(service.isSsoLoginRevoked("sid-1")).isTrue();
        assertThat(service.isSsoLoginRevoked("sid-2")).isFalse();
        assertThat(service.isSsoLoginRevoked(null)).isFalse();
        assertThat(service.isSsoLoginRevoked(" ")).isFalse();

        when(redis.hasKey(anyString())).thenThrow(new RuntimeException("redis down"));
        assertThat(service.isSsoLoginRevoked("sid-1")).isFalse();
    }
}
