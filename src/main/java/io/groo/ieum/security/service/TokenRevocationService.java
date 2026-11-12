package io.groo.ieum.security.service;

import io.groo.ieum.common.config.properties.JwtProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.Date;

/**
 * HR 토큰 차단 목록 (Redis, TTL 로 스스로 사라진다).
 *
 * <ul>
 *   <li>토큰 단위 — 로그아웃한 토큰의 jti. TTL = 그 토큰의 남은 수명</li>
 *   <li>사용자 단위 — 역할이 바뀌거나 계정이 삭제된 사용자의 차단 시각. 그 시각 이전에 발급된 토큰(iat)을 전부
 *       거절한다. TTL = HR 토큰 최대 수명(그 뒤엔 차단 이전 토큰이 모두 만료돼 있다). 보안 점검 IEU-2,
 *       groo-sso-back 의 사용자 차단(#90)과 같은 방식</li>
 * </ul>
 *
 * <p>Redis 장애는 fail-open 이다 — 차단 목록을 못 읽는다고 모든 사용자의 로그인이 죽으면 안 된다.
 * 대신 경고 로그를 남긴다. 이 경우 차단된 토큰이 만료(최대 1시간)까지 살아 있을 수 있다.
 * 상태를 바꾸는 요청은 따로 권한을 DB 에서 다시 읽으므로(필터) 쓰기는 그때도 막힌다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenRevocationService {

    private static final String KEY_PREFIX = "ieum:revoked-jti:";
    private static final String USER_KEY_PREFIX = "ieum:revoked-user:";

    private final StringRedisTemplate redisTemplate;
    private final JwtProperties jwtProperties;

    public void revoke(String jti, long remainingMs) {
        if (jti == null || remainingMs <= 0) {
            return;
        }
        redisTemplate.opsForValue().set(KEY_PREFIX + jti, "1", Duration.ofMillis(remainingMs));
    }

    public boolean isRevoked(String jti) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + jti));
        } catch (Exception e) {
            log.warn("토큰 차단 목록 조회 실패 — 허용으로 처리한다(fail-open): {}", e.getMessage());
            return false;
        }
    }

    /**
     * 이 사용자의 지금까지 발급된 HR 토큰을 전부 끊는다.<br>
     * 트랜잭션 안에서 부르면 커밋된 뒤에 차단한다 — 커밋 전에 차단하면, 그 사이 다시 로그인한 사용자가
     * 아직 바뀌기 전 역할로 새 토큰을 받아 차단을 빠져나갈 수 있다. 롤백되면 차단하지 않는다.
     */
    public void revokeAllForUser(String userId) {
        if (userId == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    writeUserRevocation(userId);
                }
            });
            return;
        }
        writeUserRevocation(userId);
    }

    private void writeUserRevocation(String userId) {
        try {
            // 값은 차단 시각(epoch ms). 이 시각 이전에 발급된 토큰만 막는다 — isUserRevoked 참고
            redisTemplate.opsForValue().set(USER_KEY_PREFIX + userId, String.valueOf(System.currentTimeMillis()),
                    Duration.ofMillis(jwtProperties.getHrAccessExpiration()));
            log.info("사용자 토큰 전부 차단: userId={}", userId);
        } catch (Exception e) {
            log.error("사용자 토큰 차단 실패 — 기존 토큰이 만료까지 읽기에 쓰일 수 있다: userId={}, error={}",
                    userId, e.getMessage());
        }
    }

    /**
     * 사용자 차단 시각 이전에 발급된 토큰인지.<br>
     * iat 은 초 단위로 잘려 있어, 같은 초면 차단 앞인지 뒤인지 모르므로 막는다. iat 이 없는 토큰도 막는다.
     */
    public boolean isUserRevoked(String userId, Date issuedAt) {
        String value;
        try {
            value = redisTemplate.opsForValue().get(USER_KEY_PREFIX + userId);
        } catch (Exception e) {
            log.warn("사용자 차단 목록 조회 실패 — 허용으로 처리한다(fail-open): {}", e.getMessage());
            return false;
        }
        if (value == null) {
            return false;
        }
        if (issuedAt == null) {
            return true;
        }
        long revokedAtMillis;
        try {
            revokedAtMillis = Long.parseLong(value);
        } catch (NumberFormatException e) {
            return true;
        }
        return issuedAt.getTime() / 1000 <= revokedAtMillis / 1000;
    }
}
