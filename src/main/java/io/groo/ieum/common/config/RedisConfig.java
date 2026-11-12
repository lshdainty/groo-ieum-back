package io.groo.ieum.common.config;

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

    private static final String USER_EVENT_CHANNEL = "porest:sso:user-events";
    static final String SESSION_EVENT_CHANNEL = "porest:sso:session-events";

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
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
