package io.groo.ieum.schedule.controller;

import io.groo.core.controller.ApiResponse;
import io.groo.ieum.common.exception.HrErrorCode;
import io.groo.core.exception.ForbiddenException;
import io.groo.core.type.DisplayType;
import io.groo.ieum.schedule.controller.dto.ScheduleApiDto;
import io.groo.ieum.schedule.domain.Schedule;
import io.groo.ieum.schedule.service.ScheduleService;
import io.groo.ieum.schedule.service.dto.ScheduleServiceDto;
import io.groo.core.security.LoginUser;
import io.groo.ieum.user.domain.User;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
public class ScheduleApiController implements ScheduleApi {
    private final ScheduleService scheduleService;
    private final MessageSource messageSource;

    @Override
    @PreAuthorize("hasAuthority('SCHEDULE:WRITE')")
    public ApiResponse registSchedule(ScheduleApiDto.RegistScheduleReq data, User loginUser, HttpServletRequest req) {
        validateScheduleOwnership(loginUser.getId(), data.getUserId());

        Long scheduleId = scheduleService.registSchedule(ScheduleServiceDto.builder()
                .userId(data.getUserId())
                .type(data.getScheduleType())
                .desc(data.getScheduleDesc())
                .startDate(data.getStartDate())
                .endDate(data.getEndDate())
                .build()
        );

        return ApiResponse.success(new ScheduleApiDto.RegistScheduleResp(scheduleId));
    }

    @Override
    @PreAuthorize("hasAuthority('SCHEDULE:WRITE')")
    public ApiResponse updateSchedule(Long scheduleId, ScheduleApiDto.UpdateScheduleReq data, User loginUser) {
        validateScheduleOwnership(loginUser.getId(), data.getUserId());

        Long newScheduleId = scheduleService.updateSchedule(
                scheduleId,
                ScheduleServiceDto.builder()
                        .userId(data.getUserId())
                        .type(data.getScheduleType())
                        .desc(data.getScheduleDesc())
                        .startDate(data.getStartDate())
                        .endDate(data.getEndDate())
                        .build(),
                loginUser.getId(),
                hasScheduleManageAuthority()
        );

        return ApiResponse.success(new ScheduleApiDto.UpdateScheduleResp(newScheduleId));
    }

    @Override
    @PreAuthorize("hasAuthority('SCHEDULE:WRITE')")
    public ApiResponse deleteSchedule(Long scheduleId, User loginUser) {
        Schedule schedule = scheduleService.checkScheduleExist(scheduleId);
        validateScheduleOwnership(loginUser.getId(), schedule.getUser().getId());

        scheduleService.deleteSchedule(scheduleId);
        return ApiResponse.success();
    }

    @Override
    @PreAuthorize("hasAuthority('SCHEDULE:READ')")
    public ApiResponse searchSchedulesByUser(String userId) {
        List<Schedule> schedules = scheduleService.searchSchedulesByUser(userId);

        List<ScheduleApiDto.SearchSchedulesByUserResp> resp = schedules.stream()
                .map(s -> new ScheduleApiDto.SearchSchedulesByUserResp(
                        s.getRowId(),
                        s.getType(),
                        getTranslatedName(s.getType()),
                        s.getDesc(),
                        s.getStartDate(),
                        s.getEndDate()
                ))
                .toList();

        return ApiResponse.success(resp);
    }

    @Override
    @PreAuthorize("hasAuthority('SCHEDULE:READ')")
    public ApiResponse searchSchedulesByPeriod(LocalDateTime startDate, LocalDateTime endDate) {
        List<Schedule> schedules = scheduleService.searchSchedulesByPeriod(startDate, endDate);

        List<ScheduleApiDto.SearchSchedulesByPeriodResp> resp = schedules.stream()
                .map(s -> new ScheduleApiDto.SearchSchedulesByPeriodResp(
                        s.getRowId(),
                        s.getUser().getId(),
                        s.getUser().getName(),
                        s.getType(),
                        getTranslatedName(s.getType()),
                        s.getDesc(),
                        s.getStartDate(),
                        s.getEndDate()
                ))
                .toList();

        return ApiResponse.success(resp);
    }

    private String getTranslatedName(DisplayType type) {
        if (type == null) return null;
        return messageSource.getMessage(type.getMessageKey(), null, LocaleContextHolder.getLocale());
    }

    /**
     * 일정 소유권 검증
     * 로그인 유저와 대상 유저가 다른 경우 SCHEDULE:MANAGE 권한이 있으면 허용
     */
    private void validateScheduleOwnership(String loginUserId, String targetUserId) {
        if (!loginUserId.equals(targetUserId) && !hasScheduleManageAuthority()) {
            log.warn("일정 접근 거부 - 로그인 유저: {}, 대상 유저: {}", loginUserId, targetUserId);
            throw new ForbiddenException(HrErrorCode.SCHEDULE_ACCESS_DENIED);
        }
    }

    /**
     * 현재 사용자가 SCHEDULE:MANAGE 권한을 가지고 있는지 확인
     */
    private boolean hasScheduleManageAuthority() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(auth -> "SCHEDULE:MANAGE".equals(auth.getAuthority()));
    }
}
