package io.groo.ieum.common.event;

import io.groo.core.type.YNType;
import io.groo.core.type.CountryCode;
import io.groo.ieum.user.domain.User;
import io.groo.ieum.user.repository.UserRepository;
import io.groo.ieum.security.service.TokenRevocationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * groo-sso-back 보안 점검 SSO-2 — 초대 대기 계정이 기존 계정에 합쳐지면(USER_LINKED) 이음 행을 기존 계정 번호에
 * 다시 묶는다. 예전엔 SSO 가 기존 계정의 USER_UPDATED 만 보내 이음 행이 지워진 대기 계정 번호에 남았다.
 */
@DisplayName("SSO 사용자 이벤트 — USER_LINKED (SSO-2)")
class SsoUserEventSubscriberLinkedTest {

    /** 발행 쪽(groo-sso-back UserEvent)과 같은 모양 */
    record Published(String type, Long userNo, String userId, String name, String email, Long previousUserNo,
                     LocalDateTime timestamp) {}

    private final UserRepository userRepository = mock(UserRepository.class);
    private final SsoUserEventSubscriber subscriber =
            new SsoUserEventSubscriber(userRepository, mock(TokenRevocationService.class),
                    // 전환 기간 설정(서명 없음 허용) — 서명 판정은 SsoEventSignatureWiringTest 가 본다
                    new io.groo.core.security.event.SsoEventVerifier(h -> null, null, false));

    private static String linked(Long userNo, Long previous) {
        return JsonMapper.builder().build().writeValueAsString(
                new Published("USER_LINKED", userNo, "boss", "대표", "boss@example.com", previous, LocalDateTime.now()));
    }

    private static User user(String id, Long ssoUserRowId) {
        return User.createUser(ssoUserRowId, id, id, id + "@test.com", LocalDate.of(1990, 1, 1), "NONE", "9 ~ 18",
                LocalDate.now(), YNType.N, null, null, CountryCode.KR);
    }

    @Test
    @DisplayName("대기 계정 번호로 만든 행을 기존 계정 번호에 다시 묶고 아이디 · 이름 · 이메일을 맞춘다")
    void relinksPendingRow() {
        User pending = user("newboss", 101L);
        given(userRepository.findBySsoUserRowId(101L)).willReturn(Optional.of(pending));
        given(userRepository.findBySsoUserRowId(1L)).willReturn(Optional.empty());

        subscriber.handleUserEvent(linked(1L, 101L));

        assertThat(pending.getSsoUserRowId()).isEqualTo(1L);
        assertThat(pending.getId()).isEqualTo("boss");
        assertThat(pending.getEmail()).isEqualTo("boss@example.com");
    }

    @Test
    @DisplayName("기존 계정이 이미 이음 행을 가졌으면 대기 행을 지운다")
    void duplicatePendingRowDeleted() {
        User pending = user("newboss", 101L);
        User existing = user("boss", 1L);
        given(userRepository.findBySsoUserRowId(101L)).willReturn(Optional.of(pending));
        given(userRepository.findBySsoUserRowId(1L)).willReturn(Optional.of(existing));

        subscriber.handleUserEvent(linked(1L, 101L));

        assertThat(pending.getIsDeleted()).isEqualTo(YNType.Y);
        assertThat(existing.getSsoUserRowId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("이음에서 초대하지 않은 합침이면 아무 행도 새로 묶지 않는다")
    void notOurInvitation() {
        given(userRepository.findBySsoUserRowId(101L)).willReturn(Optional.empty());
        given(userRepository.findBySsoUserRowId(1L)).willReturn(Optional.empty());

        subscriber.handleUserEvent(linked(1L, 101L));
    }
}
