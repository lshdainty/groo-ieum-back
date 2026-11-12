package io.groo.ieum.user.controller;

import io.groo.ieum.user.controller.dto.UserApiDto;
import io.groo.ieum.work.controller.WorkApiController;
import io.groo.ieum.work.controller.dto.WorkApiDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

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

    @Test
    @DisplayName("아이디 중복 확인은 USER:MANAGE 가 있어야 한다 (초대 화면에서만 쓴다)")
    void checkDuplicateRequiresUserManage() throws Exception {
        assertUserManage("checkUserIdDuplicate", String.class);
    }

    @Test
    @DisplayName("주부서 존재 확인은 USER:MANAGE 가 있어야 한다 (부서 이동 관리 화면에서만 쓴다)")
    void mainDepartmentExistenceRequiresUserManage() throws Exception {
        assertUserManage("checkUserMainDepartmentExistence", String.class);
    }

    @Test
    @DisplayName("프로필 사진 업로드(POST /users/profiles)는 USER:EDIT 가 있어야 한다")
    void uploadProfileRequiresUserEdit() throws Exception {
        PreAuthorize pa = UserApiController.class.getMethod("uploadProfile", UserApiDto.UploadProfileReq.class)
                .getAnnotation(PreAuthorize.class);
        assertThat(pa).isNotNull();
        assertThat(pa.value()).isEqualTo("hasAuthority('USER:EDIT')");
    }

    @Test
    @DisplayName("시스템 데일리 체크 토글은 WORK:WRITE, 상태 조회는 WORK:READ 가 있어야 한다")
    void systemLogRequiresWorkAuthority() throws Exception {
        PreAuthorize toggle = WorkApiController.class
                .getMethod("toggleSystemCheck", WorkApiDto.ToggleSystemCheckReq.class)
                .getAnnotation(PreAuthorize.class);
        PreAuthorize status = WorkApiController.class
                .getMethod("checkSystemStatus", List.class)
                .getAnnotation(PreAuthorize.class);
        assertThat(toggle).isNotNull();
        assertThat(toggle.value()).isEqualTo("hasAuthority('WORK:WRITE')");
        assertThat(status).isNotNull();
        assertThat(status.value()).isEqualTo("hasAuthority('WORK:READ')");
    }
}
