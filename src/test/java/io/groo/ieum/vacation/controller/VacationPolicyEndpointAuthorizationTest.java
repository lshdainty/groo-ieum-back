package io.groo.ieum.vacation.controller;

import io.groo.ieum.vacation.controller.dto.VacationApiDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("휴가 정책 엔드포인트 권한 선언 테스트")
class VacationPolicyEndpointAuthorizationTest {
    @Test
    @DisplayName("정책 이름 · 설명 수정(PUT /vacation-policies/{id})은 VACATION:MANAGE 가 있어야 한다 (만들기 · 지우기와 같다)")
    void updatePolicyRequiresVacationManage() throws Exception {
        PreAuthorize pa = VacationApiController.class
                .getMethod("updateVacationPolicy", Long.class, VacationApiDto.UpdateVacationPolicyReq.class)
                .getAnnotation(PreAuthorize.class);

        assertThat(pa).as("updateVacationPolicy 에 @PreAuthorize 가 있어야 한다").isNotNull();
        assertThat(pa.value()).isEqualTo("hasAuthority('VACATION:MANAGE')");
    }
}
