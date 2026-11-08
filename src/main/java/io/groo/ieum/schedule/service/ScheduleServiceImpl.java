package io.groo.ieum.schedule.service;

import io.groo.core.exception.BusinessRuleViolationException;
import io.groo.core.exception.EntityNotFoundException;
import io.groo.core.exception.ForbiddenException;
import io.groo.ieum.common.exception.HrErrorCode;
import io.groo.ieum.common.time.CompanyClock;
import io.groo.core.exception.InvalidValueException;
import io.groo.core.type.YNType;
import io.groo.core.util.TimeUtils;
import io.groo.ieum.schedule.domain.Schedule;
import io.groo.ieum.schedule.repository.ScheduleRepository;
import io.groo.ieum.schedule.service.dto.ScheduleServiceDto;
import io.groo.ieum.user.domain.User;
import io.groo.ieum.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ScheduleServiceImpl implements ScheduleService {
    private final ScheduleRepository scheduleRepository;
    private final CompanyClock companyClock;
    private final UserService userService;

    @Override
    @Transactional
    public Long registSchedule(ScheduleServiceDto data) {
        log.debug("일정 등록 시작: userId={}, type={}", data.getUserId(), data.getType());
        // 유저 조회
        User user = userService.checkUserExist(data.getUserId());

        // 날짜 범위 검증은 @DateRange 어노테이션으로 Controller 레벨에서 처리

        Schedule schedule = Schedule.createSchedule(
                user,
                data.getDesc(),
                data.getType(),
                data.getStartDate(),
                data.getEndDate()
        );

        // 휴가 등록
        scheduleRepository.save(schedule);
        log.info("일정 등록 완료: scheduleId={}, userId={}", schedule.getRowId(), data.getUserId());

        return schedule.getRowId();
    }

    @Override
    public List<Schedule> searchSchedulesByUser(String userId) {
        log.debug("사용자별 일정 조회: userId={}", userId);
        return scheduleRepository.findSchedulesByUserId(userId);
    }

    @Override
    public List<Schedule> searchSchedulesByPeriod(LocalDateTime start, LocalDateTime end) {
        log.debug("기간별 일정 조회: start={}, end={}", start, end);
        if (TimeUtils.isAfter(start, end)) {
            log.warn("일정 조회 실패 - 시작일이 종료일보다 이후: start={}, end={}", start, end);
            throw new InvalidValueException(HrErrorCode.SCHEDULE_INVALID_DATE);
        }
        return scheduleRepository.findSchedulesByPeriod(start, end);
    }

    @Override
    @Transactional
    public Long updateSchedule(Long scheduleId, ScheduleServiceDto data, String loginUserId, boolean canManage) {
        log.debug("일정 수정 시작: scheduleId={}", scheduleId);
        Schedule target = checkScheduleExist(scheduleId);
        if (!canManage && (!target.getUser().getId().equals(loginUserId) || !loginUserId.equals(data.getUserId()))) {
            log.warn("일정 수정 접근 거부 - 로그인 유저: {}, 일정 주인: {}, 변경할 대상: {}",
                    loginUserId, target.getUser().getId(), data.getUserId());
            throw new ForbiddenException(HrErrorCode.SCHEDULE_ACCESS_DENIED);
        }
        // 1. 기존 스케줄 삭제
        deleteSchedule(scheduleId);

        // 2. 새로운 스케줄 등록
        Long newScheduleId = registSchedule(data);

        log.info("스케줄 수정 완료 - 기존 ID: {}, 새로운 ID: {}", scheduleId, newScheduleId);

        return newScheduleId;
    }

    @Override
    @Transactional
    public void deleteSchedule(Long scheduleId) {
        log.debug("일정 삭제 시작: scheduleId={}", scheduleId);
        Schedule schedule = checkScheduleExist(scheduleId);

        if (schedule.getEndDate().isBefore(companyClock.now())) {
            log.warn("일정 삭제 실패 - 종료일이 현재보다 이전: scheduleId={}, endDate={}", scheduleId, schedule.getEndDate());
            throw new BusinessRuleViolationException(HrErrorCode.SCHEDULE_INVALID_DATE);
        }

        schedule.deleteSchedule();
        log.info("일정 삭제 완료: scheduleId={}", scheduleId);
    }

    @Override
    public Schedule checkScheduleExist(Long scheduleId) {
        Optional<Schedule> schedule = scheduleRepository.findByRowId(scheduleId);
        if (schedule.isEmpty() || YNType.isY(schedule.get().getIsDeleted())) {
            log.warn("일정 조회 실패 - 존재하지 않는 일정: scheduleId={}", scheduleId);
            throw new EntityNotFoundException(HrErrorCode.SCHEDULE_NOT_FOUND);
        }
        return schedule.get();
    }
}
