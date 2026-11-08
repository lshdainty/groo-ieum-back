package io.groo.ieum.user.controller;

import io.groo.ieum.user.controller.dto.UserApiDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("사용자 엔드포인트 권한 선언 테스트")
class UserEndpointAuthorizationTest {
    private void assertUserManage(String method, Class<?>... params) throws Exception {
        PreAuthorize pa = UserApiController.class.getMethod(method, params).getAnnotation(PreAuthorize.class);

        assertThat(pa).as(method + " 에 @PreAuthorize 가 있어야 한다").isNotNull();
        assertThat(pa.value()).isEqualTo("hasAuthority('USER:MANAGE')");
    }

    @Test
    @DisplayName("사용자 직접 생성(POST /users)은 USER:MANAGE 가 있어야 한다")
    void joinUserRequiresUserManage() throws Exception {
        assertUserManage("joinUser", UserApiDto.JoinUserReq.class);
    }
}
