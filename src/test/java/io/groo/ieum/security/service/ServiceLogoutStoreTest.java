package io.groo.ieum.security.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 보안 점검 CLI-6 — "이음에서 로그아웃함" 목록. 로그아웃한 SSO 로그인(sid)을 90일 기억한다. */
@DisplayName("이음에서 로그아웃함 목록 (CLI-6)")
class ServiceLogoutStoreTest {

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> ops = mock(ValueOperations.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ServiceLogoutStore store = new ServiceLogoutStore(redis);

    @Test
    @DisplayName("remember - sid 를 ieum:logged-out-sid: 접두사 키로 90일 동안 적는다")
    void rememberSetsKeyWithRetention() {
        when(redis.opsForValue()).thenReturn(ops);

        store.remember("sid-1");

        verify(ops).set("ieum:logged-out-sid:sid-1", "1", Duration.ofDays(90));
    }

    @Test
    @DisplayName("remember - sid 가 없으면(옛 토큰) 아무것도 쓰지 않는다")
    void rememberNoopWithoutSid() {
        store.remember(null);
        store.remember("");
        store.remember("  ");

        verifyNoInteractions(redis);
    }

    @Test
    @DisplayName("remember - Redis 예외는 그대로 올린다(로그아웃 쪽이 삼키고 쿠키 삭제를 계속한다)")
    void rememberPropagatesRedisFailure() {
        when(redis.opsForValue()).thenReturn(ops);
        doThrow(new RuntimeException("redis down")).when(ops).set(anyString(), anyString(), any(Duration.class));

        assertThatThrownBy(() -> store.remember("sid-1")).hasMessage("redis down");
    }

    @Test
    @DisplayName("isLoggedOut - 적힌 sid 는 true, 적히지 않은 sid 는 false")
    void loggedOutOnlyWhenRemembered() {
        when(redis.hasKey("ieum:logged-out-sid:sid-out")).thenReturn(true);
        when(redis.hasKey("ieum:logged-out-sid:sid-new")).thenReturn(false);

        assertThat(store.isLoggedOut("sid-out")).isTrue();
        assertThat(store.isLoggedOut("sid-new")).isFalse();
    }

    @Test
    @DisplayName("isLoggedOut - sid 가 없으면 Redis 를 보지 않고 false")
    void notLoggedOutWithoutSid() {
        assertThat(store.isLoggedOut(null)).isFalse();
        assertThat(store.isLoggedOut(" ")).isFalse();

        verifyNoInteractions(redis);
    }

    @Test
    @DisplayName("isLoggedOut - Redis 조회 실패는 fail-open(false) — 장애로 전원 로그인 불가가 되지 않는다")
    void failOpen() {
        when(redis.hasKey(anyString())).thenThrow(new RuntimeException("redis down"));

        assertThat(store.isLoggedOut("sid-out")).isFalse();
    }
}
