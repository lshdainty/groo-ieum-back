package io.groo.ieum.common.event;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * SSO 사용자 이벤트 DTO
 * Redis Pub/Sub을 통해 SSO 서비스로부터 수신
 */
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class UserEvent {

    private UserEventType type;
    private Long userNo;
    private String userId;
    private String name;
    private String email;
    /** USER_LINKED 에서만 — 합쳐져 사라진 초대 대기 계정 번호 */
    private Long previousUserNo;
    private LocalDateTime timestamp;
}
