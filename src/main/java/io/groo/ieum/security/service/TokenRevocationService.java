package io.groo.ieum.security.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 로그아웃한 HR 토큰의 jti 차단 목록 (Redis, TTL = 토큰의 남은 수명이라 스스로 사라진다).
 *
 * <p>Redis 장애는 fail-open 이다 — 차단 목록을 못 읽는다고 모든 사용자의 로그인이 죽으면 안 된다.
 * 대신 경고 로그를 남긴다. 이 경우 로그아웃된 토큰이 만료(최대 1시간)까지 살아 있을 수 있다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenRevocationService {

    private static final String KEY_PREFIX = "ieum:revoked-jti:";

    private final StringRedisTemplate redisTemplate;

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
}
