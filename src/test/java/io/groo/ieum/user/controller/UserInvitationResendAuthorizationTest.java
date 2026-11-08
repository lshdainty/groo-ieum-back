package io.groo.ieum.user.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("초대 재발송 권한 선언 테스트")
class UserInvitationResendAuthorizationTest {
    @Test
    @DisplayName("초대 재발송은 USER:MANAGE 가 있어야 한다")
    void resendInvitationRequiresUserManage() throws Exception {
        PreAuthorize pa = UserApiController.class.getMethod("resendInvitation", String.class)
                .getAnnotation(PreAuthorize.class);

        assertThat(pa).isNotNull();
        assertThat(pa.value()).isEqualTo("hasAuthority('USER:MANAGE')");
    }
}
