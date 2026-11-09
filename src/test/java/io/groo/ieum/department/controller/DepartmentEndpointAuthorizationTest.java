package io.groo.ieum.department.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("부서 엔드포인트 권한 선언 테스트")
class DepartmentEndpointAuthorizationTest {
    @Test
    @DisplayName("주 부서 옮기기(PUT /departments/{id}/users/{userId}/main)는 COMPANY:MANAGE 가 있어야 한다 (옆 부서 사람 API 와 같다)")
    void changeMainDepartmentRequiresCompanyManage() throws Exception {
        PreAuthorize pa = DepartmentApiController.class
                .getMethod("changeMainDepartment", Long.class, String.class)
                .getAnnotation(PreAuthorize.class);

        assertThat(pa).as("changeMainDepartment 에 @PreAuthorize 가 있어야 한다").isNotNull();
        assertThat(pa.value()).isEqualTo("hasAuthority('COMPANY:MANAGE')");
    }
}
