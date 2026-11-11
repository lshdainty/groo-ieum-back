package io.groo.ieum.calendar.controller;

import io.groo.ieum.calendar.controller.dto.CalendarApiDto;
import io.groo.core.controller.ApiResponse;
import io.groo.core.type.DisplayType;
import io.groo.ieum.schedule.domain.Schedule;
import io.groo.core.security.LoginUser;
import io.groo.ieum.user.domain.User;
import io.groo.ieum.schedule.service.ScheduleService;
import io.groo.ieum.vacation.service.VacationService;
import io.groo.ieum.vacation.service.dto.VacationServiceDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
public class CalendarApiController implements CalendarApi {
    private final ScheduleService scheduleService;
    private final VacationService vacationService;
    private final MessageSource messageSource;

    @Override
    public ApiResponse searchEventsByPeriod(LocalDateTime startDate, LocalDateTime endDate, @LoginUser User loginUser) {
        boolean canSeeAllReasons = hasAnyAuthority("VACATION:MANAGE", "VACATION:APPROVE");
        List<CalendarApiDto.searchEventsByPeriodResp> resp = new ArrayList<>();

        List<Schedule> schedules = scheduleService.searchSchedulesByPeriod(startDate, endDate);
        resp.addAll(schedules.stream()
                .map(s -> new CalendarApiDto.searchEventsByPeriodResp(
                        s.getUser().getId(),
                        s.getUser().getName(),
                        getTranslatedName(s.getType()),
                        s.getType().name(),
                        s.getDesc(),
                        s.getStartDate(),
                        s.getEndDate(),
                        "schedule",
                        null,
                        s.getRowId()
                ))
                .toList());

        List<VacationServiceDto> histories = vacationService.getVacationUsagesByPeriod(startDate, endDate);
        resp.addAll(histories.stream()
                .map(v -> new CalendarApiDto.searchEventsByPeriodResp(
                        v.getUser().getId(),
                        v.getUser().getName(),
                        getTranslatedName(v.getTimeType()),
                        v.getTimeType().name(),
                        // 남의 휴가는 종류만 보이고 사유는 본인 · 휴가 관리 · 휴가 승인 권한자에게만 내린다
                        canSeeAllReasons || v.getUser().getId().equals(loginUser.getId()) ? v.getDesc() : null,
                        v.getStartDate(),
                        v.getEndDate(),
                        "vacation",
                        v.getType(),
                        v.getId()
                ))
                .toList());

        return ApiResponse.success(resp);
    }

    private boolean hasAnyAuthority(String... authorities) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        List<String> wanted = List.of(authorities);
        return authentication.getAuthorities().stream().anyMatch(auth -> wanted.contains(auth.getAuthority()));
    }

    private String getTranslatedName(DisplayType type) {
        if (type == null) return null;
        return messageSource.getMessage(type.getMessageKey(), null, LocaleContextHolder.getLocale());
    }
}
