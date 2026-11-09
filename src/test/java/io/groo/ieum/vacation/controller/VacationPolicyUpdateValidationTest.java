package io.groo.ieum.vacation.controller;

import io.groo.ieum.vacation.service.VacationPlanService;
import io.groo.ieum.vacation.service.VacationService;
import io.groo.ieum.vacation.service.VacationTimeFormatter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("휴가 정책 수정 요청 검증 테스트 (HTTP 계층)")
class VacationPolicyUpdateValidationTest {
    private VacationService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(VacationService.class);
        VacationApiController controller = new VacationApiController(
                service, mock(VacationPlanService.class), mock(MessageSource.class), mock(VacationTimeFormatter.class));
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
        given(service.updateVacationPolicy(eq(1L), any(), any())).willReturn(1L);
    }

    private String body(String name) {
        return "{\"vacation_policy_name\":\"" + name + "\",\"vacation_policy_desc\":\"설명\"}";
    }

    @Test
    @DisplayName("앞뒤 공백이 붙은 50자 이름은 컨트롤러 검증을 통과해 서비스(자른 뒤 검증)까지 간다")
    void paddedFiftyCharNamePassesToService() throws Exception {
        String padded = " " + "가".repeat(50) + " ";
        mvc.perform(put("/api/v1/vacation-policies/1").contentType(MediaType.APPLICATION_JSON).content(body(padded)))
                .andExpect(status().isOk());
        verify(service).updateVacationPolicy(1L, padded, "설명");
    }

    @Test
    @DisplayName("빈 이름 · 공백 이름은 400 이고 서비스까지 가지 못한다")
    void blankNameIs400() throws Exception {
        for (String blank : new String[]{"", "   "}) {
            mvc.perform(put("/api/v1/vacation-policies/1").contentType(MediaType.APPLICATION_JSON).content(body(blank)))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }
}
