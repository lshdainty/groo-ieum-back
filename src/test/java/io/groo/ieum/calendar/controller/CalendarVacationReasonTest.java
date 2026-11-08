package io.groo.ieum.calendar.controller;

import io.groo.core.type.CountryCode;
import io.groo.core.type.YNType;
import io.groo.ieum.calendar.controller.dto.CalendarApiDto;
import io.groo.ieum.schedule.service.ScheduleService;
import io.groo.ieum.user.domain.User;
import io.groo.ieum.vacation.service.VacationService;
import io.groo.ieum.vacation.service.dto.VacationServiceDto;
import io.groo.ieum.vacation.type.VacationTimeType;
import io.groo.ieum.vacation.type.VacationType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@DisplayName("캘린더 휴가 사유 공개 범위 테스트")
class CalendarVacationReasonTest {
    private CalendarApiController controller;
    private final User me = user("me");

    private static User user(String id) {
        return User.createUser(null, id, id, id + "@test.com", LocalDate.of(1990, 1, 1), "NONE", "9 ~ 18",
                LocalDate.now(), YNType.N, null, null, CountryCode.KR);
    }

    @BeforeEach
    void setUp() {
        ScheduleService scheduleService = mock(ScheduleService.class);
        VacationService vacationService = mock(VacationService.class);
        MessageSource messageSource = mock(MessageSource.class);
        given(scheduleService.searchSchedulesByPeriod(any(), any())).willReturn(List.of());
        given(vacationService.getVacationUsagesByPeriod(any(), any())).willReturn(List.of(
                vacation(me, "내 사유"), vacation(user("other"), "남의 사유")));
        controller = new CalendarApiController(scheduleService, vacationService, messageSource);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private VacationServiceDto vacation(User u, String desc) {
        return VacationServiceDto.builder().user(u).id(1L).desc(desc).timeType(VacationTimeType.DAYOFF)
                .type(VacationType.ANNUAL)
                .startDate(LocalDateTime.of(2030, 1, 2, 9, 0)).endDate(LocalDateTime.of(2030, 1, 2, 18, 0)).build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> descByUser(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("me", "n", authorities));
        List<CalendarApiDto.searchEventsByPeriodResp> resp = (List<CalendarApiDto.searchEventsByPeriodResp>)
                controller.searchEventsByPeriod(LocalDateTime.of(2030, 1, 1, 0, 0), LocalDateTime.of(2030, 1, 31, 0, 0), me).getData();
        java.util.HashMap<String, String> m = new java.util.HashMap<>();
        resp.forEach(r -> m.put(r.getUserId(), r.getCalendarDesc()));
        return m;
    }

    @Test
    @DisplayName("가림 - 일반 직원에게 남의 휴가 사유는 비어 있고 종류와 본인 사유는 보인다")
    void ordinaryUserDoesNotSeeOthersReason() {
        Map<String, String> m = descByUser("SCHEDULE:READ");
        assertThat(m.get("me")).isEqualTo("내 사유");
        assertThat(m.get("other")).isNull();
    }

    @Test
    @DisplayName("공개 - 휴가 관리 · 휴가 승인 권한자는 남의 사유도 본다")
    void managerAndApproverSeeReason() {
        assertThat(descByUser("VACATION:MANAGE").get("other")).isEqualTo("남의 사유");
        assertThat(descByUser("VACATION:APPROVE").get("other")).isEqualTo("남의 사유");
    }
}
