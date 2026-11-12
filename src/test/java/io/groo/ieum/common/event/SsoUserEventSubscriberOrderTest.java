package io.groo.ieum.common.event;

import io.groo.core.type.CountryCode;
import io.groo.core.type.YNType;
import io.groo.ieum.security.service.TokenRevocationService;
import io.groo.ieum.user.domain.User;
import io.groo.ieum.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 보안 점검 IEU-6 후속 — 늦게 도착한 옛 USER_UPDATED 가 새 값을 덮지 않는다. */
@DisplayName("SSO 사용자 이벤트 — USER_UPDATED 순서")
class SsoUserEventSubscriberOrderTest {

    record Published(String type, Long userNo, String userId, String name, String email, LocalDateTime timestamp) {}

    private static final LocalDateTime T1 = LocalDateTime.of(2026, 11, 12, 1, 0, 0);
    private static final LocalDateTime T2 = T1.plusSeconds(1);

    private final Map<String, String> redis = new HashMap<>();
    private final UserRepository userRepository = mock(UserRepository.class);
    private SsoUserEventSubscriber subscriber;
    private User hong;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenAnswer(inv -> redis.get(inv.<String>getArgument(0)));
        doAnswer(inv -> redis.put(inv.getArgument(0), inv.getArgument(1)))
                .when(ops).set(anyString(), anyString(), any(Duration.class));
        subscriber = new SsoUserEventSubscriber(userRepository, mock(TokenRevocationService.class),
                new io.groo.core.security.event.SsoEventVerifier(h -> null, null, false),
                new SsoUserUpdateOrder(template));
        hong = User.createUser(7L, "hong", "홍길동", "hong@test.com", LocalDate.of(1990, 1, 1), "NONE", "9 ~ 18",
                LocalDate.now(), YNType.N, null, null, CountryCode.KR);
        given(userRepository.findBySsoUserRowId(7L)).willReturn(Optional.of(hong));
    }

    private void updated(String email, LocalDateTime at) {
        subscriber.handleUserEvent(JsonMapper.builder().build().writeValueAsString(
                new Published("USER_UPDATED", 7L, "hong", "홍길동", email, at)));
    }

    @Test
    @DisplayName("새 변경 뒤에 도착한 옛 변경은 버린다")
    void olderAfterNewerDropped() {
        updated("new@test.com", T2);
        updated("old@test.com", T1);

        assertThat(hong.getEmail()).isEqualTo("new@test.com");
    }

    @Test
    @DisplayName("순서대로 오면 차례로 적용한다 · 같은 시각은 적용한다")
    void inOrderApplied() {
        updated("a@test.com", T1);
        updated("b@test.com", T2);
        updated("c@test.com", T2);

        assertThat(hong.getEmail()).isEqualTo("c@test.com");
    }

    @Test
    @DisplayName("시각이 없는 이벤트(옛 SSO)는 비교할 수 없어 그대로 적용한다")
    void noTimestampApplied() {
        updated("new@test.com", T2);
        updated("legacy@test.com", null);

        assertThat(hong.getEmail()).isEqualTo("legacy@test.com");
    }
}
