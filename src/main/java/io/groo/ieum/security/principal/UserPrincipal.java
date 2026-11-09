package io.groo.ieum.security.principal;

import io.groo.core.security.AuditorPrincipal;
import io.groo.ieum.user.domain.User;

/**
 * Form 로그인과 OAuth2 로그인의 공통 인터페이스
 * 두 가지 인증 방식 모두에서 User 객체에 접근할 수 있도록 합니다.
 * AuditorPrincipal을 상속받아 JPA Auditing에서 사용자 ID를 가져올 수 있습니다.
 */
public interface UserPrincipal extends AuditorPrincipal {
    /**
     * 인증된 사용자 정보를 반환합니다.
     * @return User 엔티티
     */
    User getUser();

    /**
     * JPA Auditing을 위한 사용자 ID 반환
     * AuditorPrincipal 인터페이스 구현
     * @return 사용자 ID (문자열)
     */
    @Override
    default String getUserId() {
        User user = getUser();
        return user != null ? user.getId() : null;
    }

    /**
     * 임시 비밀번호 상태(비밀번호 변경 필요) 여부 — 토큰 클레임에서 온 값. 클레임이 없으면 false(N).
     */
    default boolean isPasswordChangeRequired() {
        return false;
    }
}
