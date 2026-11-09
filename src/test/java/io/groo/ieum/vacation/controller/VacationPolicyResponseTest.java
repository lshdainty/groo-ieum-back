package io.groo.ieum.vacation.controller;

import io.groo.core.controller.ApiResponse;
import io.groo.core.type.YNType;
import io.groo.ieum.vacation.controller.dto.VacationApiDto;
import io.groo.ieum.vacation.service.VacationPlanService;
import io.groo.ieum.vacation.service.VacationService;
import io.groo.ieum.vacation.service.VacationTimeFormatter;
import io.groo.ieum.vacation.service.dto.VacationPolicyServiceDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@DisplayName("휴가 정책 응답 테스트")
class VacationPolicyResponseTest {
    private VacationPolicyServiceDto dto(Long id, YNType canDeleted) {
        return VacationPolicyServiceDto.builder()
                .id(id).name("정책" + id).grantTime(BigDecimal.ONE).canDeleted(canDeleted).build();
    }

    @Test
    @DisplayName("목록 · 단건 응답에 can_deleted 가 정책 값 그대로 실린다")
    @SuppressWarnings("unchecked")
    void policyResponsesCarryCanDeleted() {
        VacationService service = mock(VacationService.class);
        VacationTimeFormatter formatter = mock(VacationTimeFormatter.class);
        VacationApiController controller = new VacationApiController(
                service, mock(VacationPlanService.class), mock(MessageSource.class), formatter);
        given(service.getVacationPolicies()).willReturn(List.of(dto(1L, YNType.N), dto(2L, YNType.Y)));
        given(service.getVacationPolicy(1L)).willReturn(dto(1L, YNType.N));

        List<VacationApiDto.GetVacationPolicyResp> list =
                (List<VacationApiDto.GetVacationPolicyResp>) controller.getVacationPolicies().getData();
        VacationApiDto.GetVacationPolicyResp one =
                (VacationApiDto.GetVacationPolicyResp) controller.getVacationPolicy(1L).getData();

        assertThat(list).extracting(VacationApiDto.GetVacationPolicyResp::getCanDeleted)
                .containsExactly(YNType.N, YNType.Y);
        assertThat(one.getCanDeleted()).isEqualTo(YNType.N);
    }
}
