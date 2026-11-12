package io.groo.ieum.common.event;

/**
 * SSO 사용자 이벤트 타입
 */
public enum UserEventType {
    USER_CREATED,
    USER_UPDATED,
    USER_DELETED,
    /**
     * 초대 대기 계정이 기존 SSO 계정에 합쳐졌다(groo-sso-back 보안 점검 SSO-2) — {@code previousUserNo}
     * (대기 계정, SSO 에서 지워짐)로 만든 이음 행을 {@code userNo}(기존 계정)에 다시 묶는다.
     */
    USER_LINKED
}
