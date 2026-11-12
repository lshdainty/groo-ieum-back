package io.groo.ieum.work.controller;

import io.groo.core.controller.ApiResponse;
import io.groo.core.security.LoginUser;
import io.groo.ieum.user.domain.User;
import io.groo.ieum.work.controller.dto.WorkApiDto;
import io.groo.ieum.work.repository.dto.WorkHistorySearchCondition;
import io.groo.ieum.work.service.WorkCodeService;
import io.groo.ieum.work.service.WorkHistoryService;
import io.groo.ieum.work.service.WorkSystemLogService;
import io.groo.ieum.work.service.dto.WorkCodeServiceDto;
import io.groo.ieum.work.service.dto.WorkHistoryServiceDto;
import io.groo.ieum.work.type.CodeType;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@Slf4j
public class WorkApiController implements WorkApi {
    private final WorkHistoryService workHistoryService;
    private final WorkCodeService workCodeService;
    private final WorkSystemLogService workSystemLogService;

    // ========== 업무 내역 관리 ==========

    @Override
    @PreAuthorize("hasAuthority('WORK:WRITE')")
    public ApiResponse createWorkHistory(WorkApiDto.CreateWorkHistoryReq data, User loginUser) {
        Long workHistoryId = workHistoryService.createWorkHistory(WorkHistoryServiceDto.builder()
                .date(data.getWorkDate())
                .userId(data.getWorkUserId())
                .groupCode(data.getWorkGroupCode())
                .partCode(data.getWorkPartCode())
                .classCode(data.getWorkClassCode())
                .hours(data.getWorkHour())
                .content(data.getWorkContent())
                .build(), loginUser.getId(), hasWorkManageAuthority());
        return ApiResponse.success(new WorkApiDto.CreateWorkHistoryResp(workHistoryId));
    }

    @Override
    @PreAuthorize("hasAuthority('WORK:WRITE')")
    public ApiResponse createWorkHistories(WorkApiDto.BulkCreateWorkHistoryReq data, User loginUser) {
        List<WorkHistoryServiceDto> dtos = data.getWorkHistories().stream()
                .map(req -> WorkHistoryServiceDto.builder()
                        .date(req.getWorkDate())
                        .userId(req.getWorkUserId())
                        .groupCode(req.getWorkGroupCode())
                        .partCode(req.getWorkPartCode())
                        .classCode(req.getWorkClassCode())
                        .hours(req.getWorkHour())
                        .content(req.getWorkContent())
                        .build())
                .collect(Collectors.toList());

        List<Long> workHistoryIds = workHistoryService.createWorkHistories(dtos, loginUser.getId(), hasWorkManageAuthority());
        return ApiResponse.success(new WorkApiDto.BulkCreateWorkHistoryResp(workHistoryIds));
    }

    @Override
    @PreAuthorize("hasAuthority('WORK:READ')")
    public ApiResponse findAllWorkHistories(WorkHistorySearchCondition condition) {
        List<WorkHistoryServiceDto> dtos = workHistoryService.findAllWorkHistories(condition);
        return ApiResponse.success(dtos.stream()
                .map(w -> new WorkApiDto.WorkHistoryResp(
                        w.getId(),
                        w.getDate(),
                        w.getUserId(),
                        w.getUserName(),
                        convertToWorkCodeResp(w.getGroupInfo()),
                        convertToWorkCodeResp(w.getPartInfo()),
                        convertToWorkCodeResp(w.getClassInfo()),
                        w.getHours(),
                        w.getContent()))
                .collect(Collectors.toList()));
    }

    @Override
    @PreAuthorize("hasAuthority('WORK:READ')")
    public ApiResponse findWorkHistory(Long id) {
        WorkHistoryServiceDto w = workHistoryService.findWorkHistory(id);
        return ApiResponse.success(new WorkApiDto.WorkHistoryResp(
                w.getId(),
                w.getDate(),
                w.getUserId(),
                w.getUserName(),
                convertToWorkCodeResp(w.getGroupInfo()),
                convertToWorkCodeResp(w.getPartInfo()),
                convertToWorkCodeResp(w.getClassInfo()),
                w.getHours(),
                w.getContent()));
    }

    @Override
    @PreAuthorize("hasAuthority('WORK:WRITE')")
    public ApiResponse updateWorkHistory(Long id, WorkApiDto.UpdateWorkHistoryReq data, User loginUser) {
        workHistoryService.updateWorkHistory(WorkHistoryServiceDto.builder()
                .id(id)
                .date(data.getWorkDate())
                .userId(data.getWorkUserId())
                .groupCode(data.getWorkGroupCode())
                .partCode(data.getWorkPartCode())
                .classCode(data.getWorkClassCode())
                .hours(data.getWorkHour())
                .content(data.getWorkContent())
                .build(), loginUser.getId(), hasWorkManageAuthority());
        return ApiResponse.success();
    }

    @Override
    @PreAuthorize("hasAuthority('WORK:WRITE')")
    public ApiResponse deleteWorkHistory(Long id, User loginUser) {
        workHistoryService.deleteWorkHistory(id, loginUser.getId(), hasWorkManageAuthority());
        return ApiResponse.success();
    }

    /**
     * 현재 사용자가 WORK:MANAGE 권한을 가지고 있는지 확인
     */
    private boolean hasWorkManageAuthority() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(auth -> "WORK:MANAGE".equals(auth.getAuthority()));
    }

    @Override
    @PreAuthorize("hasAuthority('WORK:READ')")
    public void downloadWorkHistoryExcel(HttpServletResponse response, WorkHistorySearchCondition condition) throws IOException {
        workHistoryService.downloadWorkHistoryExcel(response, condition);
    }

    @Override
    @PreAuthorize("hasAuthority('WORK:READ')")
    public void downloadUnregisteredWorkHistoryExcel(HttpServletResponse response, Integer year, Integer month) throws IOException {
        workHistoryService.downloadUnregisteredWorkHistoryExcel(response, year, month);
    }

    // ========== 업무 코드 관리 ==========

    @Override
    @PreAuthorize("hasAuthority('WORK:MANAGE')")
    public ApiResponse createWorkCode(WorkApiDto.CreateWorkCodeReq data) {
        Long workCodeId = workCodeService.createWorkCode(
                data.getWorkCode(),
                data.getWorkCodeName(),
                data.getCodeType(),
                data.getParentWorkCodeId(),
                data.getOrderSeq()
        );
        return ApiResponse.success(new WorkApiDto.CreateWorkCodeResp(workCodeId));
    }

    @Override
    @PreAuthorize("hasAuthority('WORK:READ')")
    public ApiResponse getWorkCodes(String parentWorkCode, Long parentWorkCodeId, Boolean parentIsNull, CodeType type) {
        List<WorkCodeServiceDto> workCodes = workCodeService.findWorkCodes(parentWorkCode, parentWorkCodeId, parentIsNull, type);
        return ApiResponse.success(workCodes.stream()
                .map(this::convertToWorkCodeResp)
                .collect(Collectors.toList()));
    }

    @Override
    @PreAuthorize("hasAuthority('WORK:MANAGE')")
    public ApiResponse updateWorkCode(Long id, WorkApiDto.UpdateWorkCodeReq data) {
        workCodeService.updateWorkCode(
                id,
                data.getWorkCode(),
                data.getWorkCodeName(),
                data.getParentWorkCodeId(),
                data.getOrderSeq()
        );
        return ApiResponse.success();
    }

    @Override
    @PreAuthorize("hasAuthority('WORK:MANAGE')")
    public ApiResponse deleteWorkCode(Long id) {
        workCodeService.deleteWorkCode(id);
        return ApiResponse.success();
    }

    // ========== 시스템 로그 관리 ==========

    /** 시스템 데일리 체크는 회사 공용 기록이라 업무 기록 권한(WORK:WRITE)이 있어야 바꾼다(보안 점검 범위 밖 관찰) */
    @Override
    @PreAuthorize("hasAuthority('WORK:WRITE')")
    public ApiResponse toggleSystemCheck(WorkApiDto.ToggleSystemCheckReq req) {
        boolean checked = workSystemLogService.toggleSystemCheck(req.getSystemCode());
        String message = checked ? "시스템 체크가 등록되었습니다." : "시스템 체크가 해제되었습니다.";
        return ApiResponse.success(new WorkApiDto.ToggleSystemCheckResp(checked, message));
    }

    @Override
    @PreAuthorize("hasAuthority('WORK:READ')")
    public ApiResponse checkSystemStatus(List<String> systemCodes) {
        Map<String, Boolean> statusMap = workSystemLogService.checkSystemStatusBatch(systemCodes);

        List<WorkApiDto.CheckSystemStatusResp> statuses = statusMap.entrySet().stream()
                .map(entry -> new WorkApiDto.CheckSystemStatusResp(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());

        return ApiResponse.success(new WorkApiDto.CheckSystemStatusBatchResp(statuses));
    }

    // ========== 오늘 업무 시간 확인 ==========

    @Override
    public ApiResponse getTodayWorkStatus(@LoginUser User user) {
        WorkHistoryService.TodayWorkStatus status = workHistoryService.checkTodayWorkStatus(user.getId());

        return ApiResponse.success(new WorkApiDto.TodayWorkStatusResp(
                status.getTotalHours(),
                status.getRequiredHours(),
                status.isCompleted()
        ));
    }

    // ========== 미작성 업무 날짜 조회 ==========

    @Override
    public ApiResponse getUnregisteredWorkDates(Integer year, Integer month, @LoginUser User user) {
        List<LocalDate> unregisteredDates = workHistoryService.getUnregisteredWorkDates(user.getId(), year, month);

        return ApiResponse.success(new WorkApiDto.UnregisteredWorkDatesResp(
                unregisteredDates,
                unregisteredDates.size()
        ));
    }

    // ========== Private Helper Methods ==========

    private WorkApiDto.WorkCodeResp convertToWorkCodeResp(WorkCodeServiceDto dto) {
        if (dto == null) {
            return null;
        }
        return new WorkApiDto.WorkCodeResp(
                dto.getId(),
                dto.getCode(),
                dto.getName(),
                dto.getType(),
                dto.getOrderSeq(),
                dto.getParentId());
    }
}
