package io.groo.ieum.common.config;

import io.groo.core.redis.NamespacedStringRedisTemplate;
import io.groo.core.security.event.SsoEventSignature;
import io.groo.core.security.event.SsoEventVerifier;
import io.groo.core.security.jwt.SsoJwksKeyLocator;
import org.springframework.beans.factory.annotation.Value;
import io.groo.ieum.common.event.SsoSessionEventSubscriber;
import io.groo.ieum.common.event.SsoUserEventSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

/**
 * Redis 설정
 * SSO 서비스로부터 사용자 이벤트 · 세션 폐기 이벤트 수신용.<br>
 * 채널 이름은 발행 쪽(groo-sso-back {@code SsoUserEventPublisher} · {@code SsoSessionEventPublisher})과 문자열이
 * 같아야 한다 — 공유 상수가 없어 어긋나면 조용히 아무 이벤트도 안 온다.
 */
@Configuration
public class RedisConfig {

    private static final String USER_EVENT_CHANNEL = SsoEventSignature.USER_EVENTS_CHANNEL;
    static final String SESSION_EVENT_CHANNEL = SsoEventSignature.SESSION_EVENTS_CHANNEL;

    /** Redis 계정 권한({@code ~ieum:*})과 같은 이름이어야 한다 — 다르면 모든 키가 권한 밖이 된다. */
    public static final String KEY_NAMESPACE = "ieum";

    /**
     * 이음의 Redis 키를 전부 {@code ieum:} 아래에 둔다(보안 점검 INF-2 · SSO-13, core 3.5.0).
     *
     * <p>Redis 한 대를 SSO · 이음 · 담다가 같이 쓴다. 예전에는 세 서비스가 계정 하나로 붙어서, 이음이 뚫리면 SSO 의
     * 차단 기록이나 로그인 잠금을 지울 수 있었다. 이제 이음은 전용 계정({@code REDIS_USERNAME=ieum})으로 붙고, 그
     * 계정은 {@code ieum:*} 키만 읽고 쓸 수 있다. Redis 의 계정 권한은 키 이름으로만 나눌 수 있으므로 이음이 쓰는 키는
     * 하나도 빠짐없이 {@code ieum:} 로 시작해야 한다.
     *
     * <p>그래서 접두사를 키마다 손으로 붙이지 않고 <b>템플릿이 한 번에 붙인다.</b> 코드의 키 이름에는 서비스 접두사를
     * 쓰지 않는다 — {@code revoked-jti:abc} 라고 쓰면 Redis 에는 {@code ieum:revoked-jti:abc} 로 들어간다.
     * core 의 요청 한도({@code RedisRateLimitInterceptor})와 이벤트 번호 기억({@code SsoEventVerifier})도 이 템플릿을
     * 받아 같은 접두사 아래에 놓인다.
     *
     * <p><b>새 Redis 키를 만들 때</b>: {@code StringRedisTemplate} 을 주입받아 쓰면 된다. 접두사를 직접 붙이면
     * {@code ieum:ieum:...} 가 되고, 템플릿을 거치지 않고 연결에 바로 명령을 보내면 접두사가 빠져 권한 오류
     * (NOPERM)가 난다. 채널 이름(pub/sub)에는 접두사가 붙지 않는다.
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new NamespacedStringRedisTemplate(connectionFactory, KEY_NAMESPACE);
    }

    /**
     * SSO 이벤트 서명 검증(보안 점검 IEU-6, core 3.4.0) — Redis 에 발행할 수 있는 누구든 위조 USER_DELETED ·
     * SESSION_REVOKED_ALL 을 보낼 수 있었다. SSO 공개키(JWKS)로 서명을 확인하고 jti 를 Redis 에 짧게 기억한다.
     * 서명 없는 이벤트는 처리하지 않는다(③, 기본 켜짐). {@code sso.events.require-signature=false} 로 끄면
     * 전환 기간처럼 경고와 함께 받는다 — 되돌릴 때만 쓴다.
     */
    @Bean
    public SsoEventVerifier ssoEventVerifier(SsoJwksKeyLocator ssoJwksKeyLocator,
                                             StringRedisTemplate stringRedisTemplate,
                                             @Value("${sso.events.require-signature:true}") boolean requireSignature) {
        return new SsoEventVerifier(ssoJwksKeyLocator, stringRedisTemplate, requireSignature);
    }

    @Bean
    public ChannelTopic userEventTopic() {
        return new ChannelTopic(USER_EVENT_CHANNEL);
    }

    @Bean
    public MessageListenerAdapter userEventListenerAdapter(SsoUserEventSubscriber subscriber) {
        return new MessageListenerAdapter(subscriber, "handleUserEvent");
    }

    @Bean
    public ChannelTopic sessionEventTopic() {
        return new ChannelTopic(SESSION_EVENT_CHANNEL);
    }

    @Bean
    public MessageListenerAdapter sessionEventListenerAdapter(SsoSessionEventSubscriber subscriber) {
        return new MessageListenerAdapter(subscriber, "handleSessionEvent");
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            MessageListenerAdapter userEventListenerAdapter,
            ChannelTopic userEventTopic,
            MessageListenerAdapter sessionEventListenerAdapter,
            ChannelTopic sessionEventTopic) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(userEventListenerAdapter, userEventTopic);
        // SSO 에서 세션을 끊으면(비밀번호 변경 · 강제 종료 등) 이음 토큰도 끊는다(보안 점검 M-1 / SSO-5)
        container.addMessageListener(sessionEventListenerAdapter, sessionEventTopic);
        return container;
    }
}
