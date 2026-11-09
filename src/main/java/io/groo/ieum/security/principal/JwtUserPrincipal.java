package io.groo.ieum.security.principal;

import io.groo.ieum.user.domain.User;
import lombok.Getter;

/**
 * JWT 인증 시 사용되는 UserPrincipal 구현체
 * JWT 토큰에서 추출한 사용자 ID로 HR DB에서 조회한 User 정보를 보관합니다.
 */
@Getter
public class JwtUserPrincipal implements UserPrincipal {

    private final User user;
    private final boolean passwordChangeRequired;

    public JwtUserPrincipal(User user) {
        this(user, false);
    }

    /**
     * @param passwordChangeRequired SSO access token 의 pwdChange 클레임 값
     */
    public JwtUserPrincipal(User user, boolean passwordChangeRequired) {
        this.user = user;
        this.passwordChangeRequired = passwordChangeRequired;
    }

    @Override
    public User getUser() {
        return user;
    }
}
