package io.groo.ieum.common.config;

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

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    /**
     * SSO 이벤트 서명 검증(보안 점검 IEU-6, core 3.4.0) — Redis 에 발행할 수 있는 누구든 위조 USER_DELETED ·
     * SESSION_REVOKED_ALL 을 보낼 수 있었다. SSO 공개키(JWKS)로 서명을 확인하고 jti 를 Redis 에 짧게 기억한다.
     * {@code sso.events.require-signature} 가 꺼져 있으면(전환 기간) 서명 없는 이벤트도 경고와 함께 받는다.
     */
    @Bean
    public SsoEventVerifier ssoEventVerifier(SsoJwksKeyLocator ssoJwksKeyLocator,
                                             StringRedisTemplate stringRedisTemplate,
                                             @Value("${sso.events.require-signature:false}") boolean requireSignature) {
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
