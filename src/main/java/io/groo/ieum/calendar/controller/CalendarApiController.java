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
import org.springframework.security.access.prepost.PreAuthorize;
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

    /**
     * 기간 안의 일정 · 휴가를 한 목록으로 내린다.<br>
     * 일정은 일정 조회(SCHEDULE:READ), 휴가는 휴가 조회(VACATION:READ) 권한이 있을 때만 담는다 —
     * 역할이 하나도 없는 로그인 사용자가 전 직원의 일정과 휴가를 보던 것을 막는다(보안 점검 IEU-8)
     */
    @Override
    @PreAuthorize("hasAnyAuthority('SCHEDULE:READ', 'VACATION:READ')")
    public ApiResponse searchEventsByPeriod(LocalDateTime startDate, LocalDateTime endDate, @LoginUser User loginUser) {
        // 남의 휴가 사유는 휴가 관리 권한자만 본다 — 승인 권한은 결재선과 무관하게 전 직원에게 걸려 있어
        // 캘린더 사유까지 열면 승인자가 전 직원의 사유를 읽는다(보안 점검 IEU-8). 승인자의 다른 열람 범위는 그대로다
        boolean canSeeAllReasons = hasAnyAuthority("VACATION:MANAGE");
        List<CalendarApiDto.searchEventsByPeriodResp> resp = new ArrayList<>();

        List<Schedule> schedules = hasAnyAuthority("SCHEDULE:READ")
                ? scheduleService.searchSchedulesByPeriod(startDate, endDate)
                : List.of();
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

        List<VacationServiceDto> histories = hasAnyAuthority("VACATION:READ")
                ? vacationService.getVacationUsagesByPeriod(startDate, endDate)
                : List.of();
        resp.addAll(histories.stream()
                .map(v -> new CalendarApiDto.searchEventsByPeriodResp(
                        v.getUser().getId(),
                        v.getUser().getName(),
                        getTranslatedName(v.getTimeType()),
                        v.getTimeType().name(),
                        // 남의 휴가는 종류만 보이고 사유는 본인 · 휴가 관리 권한자에게만 내린다
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
