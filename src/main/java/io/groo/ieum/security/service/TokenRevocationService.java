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
 *   <li>SSO 로그인 단위 — SSO 가 끊은 로그인 한 건의 번호(sid). 그 로그인에서 나온 HR 토큰 · SSO 토큰을 거절한다
 *       (보안 점검 CLI-6)</li>
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
    private static final String SSO_LOGIN_KEY_PREFIX = "ieum:revoked-sso-sid:";

    /**
     * 끊긴 SSO 로그인 기록을 두는 기간. 그 로그인 번호를 실은 토큰이 전부 만료될 때까지만 있으면 된다 — HR 토큰과
     * SSO access 토큰 둘 다 1시간 안팎이다. SSO 쪽 수명은 여기서 알 수 없어 넉넉히 하루를 둔다. 끊긴 로그인 번호는
     * 다시 쓰이지 않으므로(다시 로그인하면 새 번호) 길게 둬도 잘못 막히는 토큰이 없다.
     */
    static final Duration SSO_LOGIN_RETENTION = Duration.ofHours(24);

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
     * 이 SSO 로그인(SSO access 토큰의 sid)에서 나온 토큰을 전부 끊는다 — 그루 계정 로그아웃 · 기기 끊기 ·
     * 관리자 세션 종료로 SSO 가 그 로그인을 끝냈을 때(보안 점검 CLI-6). 사용자 단위 차단과 달리 같은 사람의 다른
     * 기기 · 다른 로그인은 그대로 둔다.
     *
     * <p>시각을 비교하지 않고 있기만 하면 막는다. 끊긴 로그인 번호로는 새 토큰이 나올 수 없어(슬라이딩 갱신은
     * 필터가 차단을 본 뒤에 돌고, SSO 는 끝난 로그인에 코드를 내주지 않는다) "차단 뒤에 발급된 토큰" 을 살릴 일이 없다.
     */
    public void revokeSsoLogin(String ssoSid) {
        if (ssoSid == null || ssoSid.isBlank()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(SSO_LOGIN_KEY_PREFIX + ssoSid, "1", SSO_LOGIN_RETENTION);
        } catch (Exception e) {
            log.error("SSO 로그인 차단 실패 — 그 로그인의 토큰이 만료까지 쓰일 수 있다: error={}", e.getMessage());
        }
    }

    /**
     * 끊긴 SSO 로그인에서 나온 토큰인지. 로그인 번호가 없는 토큰(이 클레임이 생기기 전 발급)은 대조할 값이 없어 통과한다.
     */
    public boolean isSsoLoginRevoked(String ssoSid) {
        if (ssoSid == null || ssoSid.isBlank()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(SSO_LOGIN_KEY_PREFIX + ssoSid));
        } catch (Exception e) {
            log.warn("SSO 로그인 차단 목록 조회 실패 — 허용으로 처리한다(fail-open): {}", e.getMessage());
            return false;
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
