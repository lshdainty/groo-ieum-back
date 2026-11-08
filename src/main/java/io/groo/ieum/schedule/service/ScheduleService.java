package io.groo.ieum.schedule.service;

import io.groo.core.exception.ForbiddenException;
import io.groo.ieum.schedule.domain.Schedule;
import io.groo.ieum.schedule.service.dto.ScheduleServiceDto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 일정 관리 서비스 인터페이스
 * 일정 등록, 조회, 수정, 삭제 기능을 제공합니다.
 */
public interface ScheduleService {
    /**
     * 일정을 등록합니다.
     *
     * @param data 일정 등록 정보
     * @return 등록된 일정 ID
     * @throws InvalidValueException 시작일이 종료일보다 이후인 경우
     * @throws EntityNotFoundException 사용자가 존재하지 않는 경우
     */
    Long registSchedule(ScheduleServiceDto data);

    /**
     * 사용자별 일정을 조회합니다.
     *
     * @param userId 사용자 ID
     * @return 사용자의 일정 목록
     */
    List<Schedule> searchSchedulesByUser(String userId);

    /**
     * 기간별 일정을 조회합니다.
     *
     * @param start 시작 일시
     * @param end 종료 일시
     * @return 기간 내 일정 목록
     * @throws InvalidValueException 시작일이 종료일보다 이후인 경우
     */
    List<Schedule> searchSchedulesByPeriod(LocalDateTime start, LocalDateTime end);

    /**
     * 일정을 수정합니다.
     * 기존 일정을 삭제하고 새로운 일정을 등록합니다.
     *
     * @param scheduleId 수정할 일정 ID
     * @param data 새로운 일정 정보
     * @param loginUserId 로그인한 사용자 ID
     * @param canManage SCHEDULE:MANAGE 권한 여부
     * @return 새로 등록된 일정 ID
     * @throws EntityNotFoundException 일정이 존재하지 않는 경우
     * @throws ForbiddenException 고칠 일정의 주인이나 새 대상이 로그인 유저가 아닌데 canManage 가 아닌 경우
     */
    Long updateSchedule(Long scheduleId, ScheduleServiceDto data, String loginUserId, boolean canManage);

    /**
     * 일정을 삭제합니다.
     *
     * @param scheduleId 삭제할 일정 ID
     * @throws EntityNotFoundException 일정이 존재하지 않는 경우
     * @throws BusinessRuleViolationException 종료일이 현재보다 이전인 경우
     */
    void deleteSchedule(Long scheduleId);

    /**
     * 일정이 존재하는지 확인하고 조회합니다.
     *
     * @param scheduleId 일정 ID
     * @return 일정 엔티티
     * @throws EntityNotFoundException 일정이 존재하지 않거나 삭제된 경우
     */
    Schedule checkScheduleExist(Long scheduleId);
}
