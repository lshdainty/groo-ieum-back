package io.groo.ieum.security.resolver;

import io.groo.core.security.web.AbstractLoginUserArgumentResolver;
import io.groo.ieum.security.principal.UserPrincipal;
import io.groo.ieum.user.domain.User;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * {@code @LoginUser User} 를 채운다 — 공통 판단(인증 없음·익명이면 401)은 core 뼈대, 이음은 주체에서 {@link User} 를
 * 꺼내는 법만 정한다(core 3.2.0).
 */
@Component
public class LoginUserArgumentResolver extends AbstractLoginUserArgumentResolver<User> {

    public LoginUserArgumentResolver() {
        super(User.class);
    }

    @Override
    protected User toLoginUser(Authentication authentication) {
        return authentication.getPrincipal() instanceof UserPrincipal principal ? principal.getUser() : null;
    }
}
