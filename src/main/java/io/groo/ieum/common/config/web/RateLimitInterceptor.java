package io.groo.ieum.common.config.web;

import io.groo.ieum.security.util.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.TimeUnit;

/**
 * IP 기준 분당 요청 제한 (고정 창, Redis). 그룹마다 키를 따로 둬서 서로의 한도를 깎지 않는다.
 * groo-sso-back 의 OAuth2RedirectUriRateLimitInterceptor 와 같은 방식이다.
 *
 * <p>Redis 장애는 fail-open — 제한이 꺼질 뿐 로그인 · 업무가 죽지는 않는다(경고 로그).
 */
@Slf4j
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final long WINDOW_SECONDS = 60L;

    private final StringRedisTemplate redis;
    private final String group;
    private final int maxPerMinute;

    public RateLimitInterceptor(StringRedisTemplate redis, String group, int maxPerMinute) {
        this.redis = redis;
        this.group = group;
        this.maxPerMinute = maxPerMinute;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String ip = ClientIpResolver.resolve(request);
        String key = "ieum:rate:" + group + ":" + ip;

        Long count;
        try {
            count = redis.opsForValue().increment(key);
            if (count != null && count == 1) {
                redis.expire(key, WINDOW_SECONDS, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            log.warn("레이트리밋 Redis 실패 — 제한 없이 통과(fail-open): {}", e.getMessage());
            return true;
        }

        if (count != null && count > maxPerMinute) {
            log.warn("rate limit exceeded - group: {}, ip: {}", group, ip);
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"code\":\"RATE_LIMIT\",\"message\":\"요청 횟수가 초과되었습니다. 잠시 후 다시 시도해주세요.\"}");
            return false;
        }
        return true;
    }
}
