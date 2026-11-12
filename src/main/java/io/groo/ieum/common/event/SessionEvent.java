package io.groo.ieum.common.event;

import java.time.LocalDateTime;

/**
 * SSO 가 내리는 세션 폐기 이벤트 — {@code porest:sso:session-events} 채널.
 *
 * <p><b>발행 쪽(groo-sso-back {@code io.groo.sso.common.event.SessionEvent})과 형태를 맞춰야 한다.</b>
 * 두 레포 사이에 공유 타입이 없어 컴파일러가 어긋남을 잡아 주지 않는다 — 필드를 바꾸면 조용히
 * 역직렬화만 실패하고 로그아웃이 전파되지 않는다. 담다(groo-damda-back)의 같은 이름 record 와도 같은 모양이다.
 *
 * <p>{@code type} 은 문자열로 받는다 — 발행 쪽이 새 종류를 더해도 역직렬화가 깨지지 않고 모르는 값으로 무시된다.
 *
 * @param type         이벤트 종류. 지금은 {@value SsoSessionEventSubscriber#SESSION_REVOKED_ALL} 하나
 * @param ssoUserRowId SSO 의 {@code users.row_id}. 이음은 {@code users.sso_user_row_id} 로 대조한다
 * @param reason       왜 끊었는지 — 로그에만 쓴다
 * @param timestamp    [UTC] 발행 시각
 */
public record SessionEvent(
        String type,
        Long ssoUserRowId,
        String reason,
        LocalDateTime timestamp
) {}
