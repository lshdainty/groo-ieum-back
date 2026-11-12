package io.groo.ieum.common.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * SSO 사용자별 마지막으로 적용한 USER_UPDATED 시각 — 오래된 변경을 버린다(보안 점검 IEU-6 후속).
 *
 * <p>pub/sub 은 순서를 보장하지 않는다(SSO 는 커밋 뒤 비동기로 보낸다). 빠르게 두 번 바뀌면 옛 이름 · 이메일이 나중에
 * 도착해 새 값을 덮을 수 있었다. 이벤트의 바뀐 시각(SSO 가 트랜잭션 안에서 찍는다)이 이미 적용한 것보다 이르면 버린다.
 *
 * <pre>
 * key   ieum:sso-user-updated:{ssoUserNo}
 * value 마지막으로 적용한 바뀐 시각(epoch ms, SSO 시각 기준)
 * TTL   30일
 * </pre>
 *
 * <p>시각이 없는 이벤트(옛 SSO)는 비교할 수 없어 그대로 적용한다. Redis 를 못 쓰면 막지 않는다(가용성) — 기록이 지워지면
 * 순서 보호만 빠진다(서버 Redis ACL, INF-2).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SsoUserUpdateOrder {

    static final Duration RETENTION = Duration.ofDays(30);
    private static final String KEY_PREFIX = "ieum:sso-user-updated:";

    private final StringRedisTemplate redisTemplate;

    /** 이 변경이 마지막으로 적용한 것보다 이르면 false(버린다). 같은 시각은 적용한다. */
    public boolean isNotOlder(Long ssoUserNo, LocalDateTime changedAt) {
        if (ssoUserNo == null || changedAt == null) {
            return true;
        }
        try {
            String last = redisTemplate.opsForValue().get(KEY_PREFIX + ssoUserNo);
            return last == null || toMillis(changedAt) >= Long.parseLong(last);
        } catch (Exception e) {
            log.warn("USER_UPDATED 순서를 확인하지 못했다(적용한다): ssoUserNo={}", ssoUserNo);
            return true;
        }
    }

    /** 이 변경을 적용했다. */
    public void applied(Long ssoUserNo, LocalDateTime changedAt) {
        if (ssoUserNo == null || changedAt == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + ssoUserNo, String.valueOf(toMillis(changedAt)), RETENTION);
        } catch (Exception e) {
            log.warn("USER_UPDATED 적용 시각을 기록하지 못했다: ssoUserNo={}", ssoUserNo);
        }
    }

    private static long toMillis(LocalDateTime t) {
        // 같은 SSO 가 찍은 시각끼리만 비교한다 — 기준 시간대는 순서에 영향이 없다
        return t.toInstant(ZoneOffset.UTC).toEpochMilli();
    }
}
