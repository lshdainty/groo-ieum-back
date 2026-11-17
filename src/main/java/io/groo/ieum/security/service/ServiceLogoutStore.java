package io.groo.ieum.security.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * "이음에서 로그아웃함" 목록 (Redis, TTL 로 스스로 사라진다) — 보안 점검 CLI-6.
 *
 * <p>로그아웃 방식은 "서비스에서 로그아웃하면 그 서비스만 끝낸다. 그루 계정(SSO) 로그인과 다른 서비스는 그대로 둔다" 다.
 * 그런데 SSO 로그인이 살아 있으니, 이음에서 로그아웃해도 로그인 단추 한 번에 SSO 가 비밀번호 없이 인가코드를 다시
 * 내줘 곧바로 들어와졌다. 그래서 이음이 자기 로그아웃을 기억한다 — 로그아웃한 HR 토큰이 실고 있던 SSO 로그인 번호
 * (sid)를 여기에 적어 두고, 같은 sid 의 SSO 토큰으로 돌아오면 받지 않는다 — 인가코드 교환은 HR 토큰을 내주지 않고
 * ({@link TokenExchangeService}), SSO 토큰을 그대로 들고 오는 직통 인증은 인증을 세우지 않는다
 * ({@code JwtAuthenticationFilter}). 비밀번호로 다시 로그인하면 SSO 가 새 sid 를 내므로 그때는 통과한다.
 *
 * <p>키 접두사 · 예외 처리는 {@link TokenRevocationService} 를 따른다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceLogoutStore {

    // 서비스 접두사(ieum:)는 여기 쓰지 않는다 — 템플릿(RedisConfig)이 모든 키에 붙인다
    private static final String KEY_PREFIX = "logged-out-sid:";

    /**
     * 기록을 남겨 두는 기간. SSO 로그인은 쓰는 동안 계속 연장돼, 로그아웃한 그 로그인이 언제 끝나는지 이쪽에서는
     * 알 수 없다. 넉넉히 두고 스스로 사라지게 한다 — 기록이 먼저 사라지면 그 로그인으로 다시 조용히 들어올 수 있다.
     */
    static final Duration RETENTION = Duration.ofDays(90);

    private final StringRedisTemplate redisTemplate;

    /**
     * 이 SSO 로그인(sid)을 "이음에서 로그아웃함" 으로 적는다. sid 가 없으면(이 클레임이 생기기 전 토큰) 적을 게 없다.
     * Redis 예외는 그대로 올린다 — 부르는 쪽(로그아웃)이 삼키고 쿠키 삭제를 계속한다.
     */
    public void remember(String ssoSid) {
        if (ssoSid == null || ssoSid.isBlank()) {
            return;
        }
        redisTemplate.opsForValue().set(KEY_PREFIX + ssoSid, "1", RETENTION);
    }

    /**
     * 이음에서 로그아웃한 SSO 로그인인지.
     *
     * <p>Redis 조회가 실패하면 로그인을 막지 않는다(fail-open, 경고 로그만). 목록을 못 읽는다고 거절하면 Redis 장애
     * 한 번에 전원이 로그인하지 못한다. 반대로 통과시켰을 때 잃는 것은 "로그아웃한 뒤 비밀번호를 다시 묻는다" 뿐이다 —
     * 이 검사를 지나도 SSO 로그인이 살아 있는 브라우저만 들어올 수 있고, SSO 토큰 검증 · 접근 권한 · 사용자 대조는
     * 그대로 탄다.
     */
    public boolean isLoggedOut(String ssoSid) {
        if (ssoSid == null || ssoSid.isBlank()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + ssoSid));
        } catch (Exception e) {
            log.warn("로그아웃한 SSO 로그인 목록 조회 실패 — 허용으로 처리한다(fail-open): {}", e.getMessage());
            return false;
        }
    }
}
