package io.groo.ieum.common.config;

import io.groo.ieum.common.event.SsoUserEventSubscriber;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

/**
 * Redis 설정
 * SSO 서비스로부터 사용자 이벤트 수신용
 */
@Configuration
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    @Bean
    public ChannelTopic userEventTopic(
            // 기본값은 옛 이름이고, 새 스택은 SSO_USER_EVENTS_CHANNEL 로 바꾼다(발행 쪽 sso 와 같아야 한다)
            @Value("${app.redis.channels.user-events:porest:sso:user-events}") String channel) {
        return new ChannelTopic(channel);
    }

    @Bean
    public MessageListenerAdapter userEventListenerAdapter(SsoUserEventSubscriber subscriber) {
        return new MessageListenerAdapter(subscriber, "handleUserEvent");
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            MessageListenerAdapter userEventListenerAdapter,
            ChannelTopic userEventTopic) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(userEventListenerAdapter, userEventTopic);
        return container;
    }
}
