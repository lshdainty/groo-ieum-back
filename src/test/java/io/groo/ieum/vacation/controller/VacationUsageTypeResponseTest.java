package io.groo.ieum.vacation.controller;

import io.groo.core.type.CountryCode;
import io.groo.core.type.YNType;
import io.groo.ieum.user.domain.User;
import io.groo.ieum.vacation.controller.dto.VacationApiDto;
import io.groo.ieum.vacation.domain.VacationUsage;
import io.groo.ieum.vacation.service.VacationPlanService;
import io.groo.ieum.vacation.service.VacationService;
import io.groo.ieum.vacation.service.VacationTimeFormatter;
import io.groo.ieum.vacation.service.dto.VacationServiceDto;
import io.groo.ieum.vacation.type.VacationTimeType;
import io.groo.ieum.vacation.type.VacationType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@DisplayName("휴가 기록 응답의 사용 줄 휴가 종류")
class VacationUsageTypeResponseTest {
    private VacationService vacationService;
    private VacationApiController controller;
    private final User me = User.createUser(
            null, "me", "나", "me@test.com", LocalDate.of(1990, 1, 1), "NONE", "9 ~ 18",
            LocalDate.now(), YNType.N, null, null, CountryCode.KR);

    @BeforeEach
    void setUp() {
        vacationService = mock(VacationService.class);
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("message/messages");
        messages.setDefaultEncoding("UTF-8");
        VacationTimeFormatter formatter = mock(VacationTimeFormatter.class);
        given(formatter.format(org.mockito.ArgumentMatchers.any())).willReturn("1일");
        controller = new VacationApiController(vacationService, mock(VacationPlanService.class), messages, formatter);
        org.springframework.context.i18n.LocaleContextHolder.setLocale(Locale.KOREAN);
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("me", "n", "VACATION:READ", "VACATION:MANAGE"));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        org.springframework.context.i18n.LocaleContextHolder.resetLocaleContext();
    }

    private VacationUsage usage(long id) {
        VacationUsage u = VacationUsage.createVacationUsage(me, "사유", VacationTimeType.DAYOFF,
                LocalDateTime.of(2030, 1, 2, 9, 0), LocalDateTime.of(2030, 1, 2, 18, 0), new BigDecimal("1.0000"));
        ReflectionTestUtils.setField(u, "rowId", id);
        return u;
    }

    @Test
    @DisplayName("본인 기록 - 사용 줄마다 vacation_type · vacation_type_name 이 실리고 차감 없는 줄은 null")
    @SuppressWarnings("unchecked")
    void userHistoryCarriesTypeAndName() {
        given(vacationService.getUserVacationHistory("me", 2030)).willReturn(VacationServiceDto.builder()
                .grants(List.of()).usages(List.of(usage(1L), usage(2L), usage(3L)))
                .usageVacationTypes(Map.of(1L, VacationType.ANNUAL, 2L, VacationType.OVERTIME)).build());

        var resp = (VacationApiDto.GetUserVacationHistoryResp)
                controller.getUserVacationHistory("me", 2030, me).getData();

        var usages = resp.getUsages();
        assertThat(usages.get(0).getVacationType()).isEqualTo(VacationType.ANNUAL);
        assertThat(usages.get(0).getVacationTypeName()).isEqualTo("연차");
        assertThat(usages.get(1).getVacationType()).isEqualTo(VacationType.OVERTIME);
        assertThat(usages.get(1).getVacationTypeName()).isEqualTo("연장");
        assertThat(usages.get(2).getVacationType()).isNull();
        assertThat(usages.get(2).getVacationTypeName()).isNull();
        // 기존 칸은 그대로
        assertThat(usages.get(0).getVacationTimeType()).isEqualTo(VacationTimeType.DAYOFF);
    }

    @Test
    @DisplayName("본인 기록 - 사용이 없으면 빈 목록")
    void userHistoryWithoutUsages() {
        given(vacationService.getUserVacationHistory("me", 2030)).willReturn(VacationServiceDto.builder()
                .grants(List.of()).usages(List.of()).usageVacationTypes(Map.of()).build());

        var resp = (VacationApiDto.GetUserVacationHistoryResp)
                controller.getUserVacationHistory("me", 2030, me).getData();

        assertThat(resp.getUsages()).isEmpty();
    }

    @Test
    @DisplayName("전체 사용자 기록 - 같은 두 칸이 실린다")
    @SuppressWarnings("unchecked")
    void allUsersHistoryCarriesTypeAndName() {
        given(vacationService.getAllUsersVacationHistory()).willReturn(List.of(VacationServiceDto.builder()
                .user(me).grants(List.of()).usages(List.of(usage(1L)))
                .usageVacationTypes(Map.of(1L, VacationType.WEDDING)).build()));

        var resp = (List<VacationApiDto.GetAllUsersVacationHistoryResp>)
                controller.getAllUsersVacationHistory().getData();

        var u = resp.get(0).getUsages().get(0);
        assertThat(u.getVacationType()).isEqualTo(VacationType.WEDDING);
        assertThat(u.getVacationTypeName()).isEqualTo("결혼");
    }
}
