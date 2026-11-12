package io.groo.ieum.common.event;

import io.groo.core.type.CountryCode;
import io.groo.core.type.YNType;
import io.groo.ieum.common.config.RedisConfig;
import io.groo.ieum.security.service.TokenRevocationService;
import io.groo.ieum.user.domain.User;
import io.groo.ieum.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 보안 점검 M-1 / SSO-5 연동 — SSO 세션 폐기 이벤트를 받으면 이음 토큰을 사용자 단위로 끊는다.
 */
@DisplayName("SSO 세션 폐기 이벤트 구독")
class SsoSessionEventSubscriberTest {

    /** 발행 쪽(groo-sso-back SessionEvent)과 같은 모양 — 발행 쪽 ObjectMapper(JsonMapper 기본값)로 직렬화한 글을 흉내 낸다 */
    record PublishedSessionEvent(String type, Long ssoUserRowId, String reason, LocalDateTime timestamp) {}

    private final UserRepository userRepository = mock(UserRepository.class);
    private final TokenRevocationService tokenRevocationService = mock(TokenRevocationService.class);
    private final SsoSessionEventSubscriber subscriber = new SsoSessionEventSubscriber(userRepository, tokenRevocationService,
            // 전환 기간 설정(서명 없음 허용) — 서명 판정은 SsoEventSignatureWiringTest 가 본다
            new io.groo.core.security.event.SsoEventVerifier(h -> null, null, false));

    private static String published(String type, Long ssoUserRowId) {
        return JsonMapper.builder().build().writeValueAsString(
                new PublishedSessionEvent(type, ssoUserRowId, "PASSWORD_CHANGED", LocalDateTime.now(ZoneOffset.UTC)));
    }

    private static User user(String id, Long ssoUserRowId) {
        return User.createUser(ssoUserRowId, id, id, id + "@test.com", LocalDate.of(1990, 1, 1), "NONE", "9 ~ 18",
                LocalDate.now(), YNType.N, null, null, CountryCode.KR);
    }

    @Test
    @DisplayName("SESSION_REVOKED_ALL - 연결 번호로 이음 사용자를 찾아 그 사용자의 토큰을 전부 끊는다")
    void revokedAllRevokesUserTokens() {
        given(userRepository.findBySsoUserRowId(42L)).willReturn(Optional.of(user("kim", 42L)));

        subscriber.handleSessionEvent(published("SESSION_REVOKED_ALL", 42L));

        verify(tokenRevocationService).revokeAllForUser("kim");
    }

    @Test
    @DisplayName("발행 쪽이 필드를 더해도 읽는다")
    void ignoresUnknownFields() {
        given(userRepository.findBySsoUserRowId(42L)).willReturn(Optional.of(user("kim", 42L)));

        subscriber.handleSessionEvent("{\"type\":\"SESSION_REVOKED_ALL\",\"ssoUserRowId\":42,\"reason\":\"ADMIN\","
                + "\"timestamp\":\"2026-11-12T01:02:03.123\",\"newField\":\"x\"}");

        verify(tokenRevocationService).revokeAllForUser("kim");
    }

    @Test
    @DisplayName("모르는 종류 · 사용자 번호 없음 · 이음에 없는 사용자 · 깨진 글은 아무것도 끊지 않고 예외도 내지 않는다")
    void ignoresOthers() {
        given(userRepository.findBySsoUserRowId(7L)).willReturn(Optional.empty());

        assertThatCode(() -> {
            subscriber.handleSessionEvent(published("SESSION_SOMETHING_NEW", 42L));
            subscriber.handleSessionEvent(published("SESSION_REVOKED_ALL", null));
            subscriber.handleSessionEvent(published("SESSION_REVOKED_ALL", 7L));
            subscriber.handleSessionEvent("not-json");
        }).doesNotThrowAnyException();

        verify(tokenRevocationService, never()).revokeAllForUser(anyString());
    }

    @Test
    @DisplayName("처리 중 예외(예: DB 장애)도 밖으로 내지 않는다")
    void swallowsHandlerFailure() {
        given(userRepository.findBySsoUserRowId(any())).willThrow(new RuntimeException("db down"));

        assertThatCode(() -> subscriber.handleSessionEvent(published("SESSION_REVOKED_ALL", 42L)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("구독 채널 이름은 발행 쪽과 같은 porest:sso:session-events 다")
    void channelName() {
        assertThat(new RedisConfig().sessionEventTopic().getTopic()).isEqualTo("porest:sso:session-events");
    }
}
