package io.groo.ieum.common.event;

import io.groo.ieum.security.service.TokenRevocationService;
import io.groo.ieum.user.domain.User;
import io.groo.ieum.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * SSO 세션 폐기 이벤트 구독자 — {@code porest:sso:session-events}.
 *
 * <p>SSO 에서 비밀번호를 바꾸거나 관리자가 강제 종료해도 이음의 HR 토큰은 따로 발급된 것이라 그대로 살아
 * 있었고, 슬라이딩 갱신으로 계속 연장됐다. 이 구독자가 SSO 의 폐기를 받아 그 사용자의 이음 토큰을
 * 사용자 단위 차단(차단 시각 이전 발급분 거절)으로 전부 끊는다(보안 점검 M-1 / SSO-5 연동).
 *
 * <p><b>무엇을 하든 예외를 밖으로 내지 않는다.</b> Redis pub/sub 은 저장하지 않아 재시도할 방법이 없고,
 * 리스너에서 예외가 올라가면 컨테이너 로그만 시끄러워진다. 형태는 groo-damda-back 의 같은 이름 구독자를 따른다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SsoSessionEventSubscriber {

    static final String SESSION_REVOKED_ALL = "SESSION_REVOKED_ALL";

    private final UserRepository userRepository;
    private final TokenRevocationService tokenRevocationService;

    /** 발행 쪽이 필드를 더해도 계속 읽히게 모르는 필드는 무시한다 */
    private final ObjectMapper objectMapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    /** Redis {@code MessageListenerAdapter} 가 부른다. */
    public void handleSessionEvent(String message) {
        SessionEvent event;
        try {
            event = objectMapper.readValue(message, SessionEvent.class);
        } catch (JacksonException e) {
            log.error("세션 이벤트를 읽지 못했다: {}", e.getMessage());
            return;
        }

        if (!SESSION_REVOKED_ALL.equals(event.type())) {
            // 발행 쪽이 새 종류를 냈는데 이쪽이 아직 모른다 — 정상적인 시차다
            log.debug("모르는 세션 이벤트 종류 — 무시한다: type={}", event.type());
            return;
        }

        try {
            handleRevokedAll(event);
        } catch (Exception e) {
            log.error("세션 이벤트 처리 실패: ssoUserRowId={}, error={}", event.ssoUserRowId(), e.getMessage());
        }
    }

    private void handleRevokedAll(SessionEvent event) {
        if (event.ssoUserRowId() == null) {
            log.warn("세션 폐기 이벤트에 사용자가 없다 — 무시한다. reason={}", event.reason());
            return;
        }
        User user = userRepository.findBySsoUserRowId(event.ssoUserRowId()).orElse(null);
        if (user == null) {
            // 이음을 쓰지 않는 SSO 사용자다. 끊을 토큰도 없다
            log.debug("이음에 없는 사용자의 세션 폐기 — 넘어간다. ssoUserRowId={}", event.ssoUserRowId());
            return;
        }
        tokenRevocationService.revokeAllForUser(user.getId());
        log.info("SSO 폐기 전파 — 이음 토큰을 끊었다. ssoUserRowId={}, userId={}, reason={}",
                event.ssoUserRowId(), user.getId(), event.reason());
    }
}
