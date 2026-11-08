package io.groo.ieum.user.controller;

import io.groo.ieum.user.controller.dto.UserApiDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("사용자 API 권한 선언 테스트")
class UserApiAuthorizationTest {
    private String preAuthorize(String method, Class<?>... params) throws NoSuchMethodException {
        PreAuthorize pa = UserApiController.class.getMethod(method, params).getAnnotation(PreAuthorize.class);
        return pa == null ? null : pa.value();
    }

    @Test
    @DisplayName("사용자 초대는 USER:MANAGE 가 있어야 한다")
    void inviteUserRequiresUserManage() throws Exception {
        assertThat(preAuthorize("inviteUser", UserApiDto.InviteUserReq.class))
                .isEqualTo("hasAuthority('USER:MANAGE')");
    }
}
