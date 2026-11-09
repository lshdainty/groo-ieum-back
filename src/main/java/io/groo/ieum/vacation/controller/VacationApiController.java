package io.groo.ieum.vacation.controller;

import io.groo.core.controller.ApiResponse;
import io.groo.ieum.common.exception.HrErrorCode;
import io.groo.core.exception.ForbiddenException;
import io.groo.core.type.DisplayType;
import io.groo.ieum.security.annotation.LoginUser;
import io.groo.ieum.user.domain.User;
import io.groo.ieum.vacation.controller.dto.VacationApiDto;
import io.groo.ieum.vacation.controller.dto.VacationPlanApiDto;
import io.groo.ieum.vacation.domain.VacationGrant;
import io.groo.ieum.vacation.domain.VacationUsage;
import io.groo.ieum.vacation.service.VacationPlanService;
import io.groo.ieum.vacation.service.VacationService;
import io.groo.ieum.vacation.service.VacationTimeFormatter;
import io.groo.ieum.vacation.service.dto.VacationApprovalServiceDto;
import io.groo.ieum.vacation.service.dto.VacationPlanServiceDto;
import io.groo.ieum.vacation.service.dto.VacationPolicyServiceDto;
import io.groo.ieum.vacation.service.dto.VacationServiceDto;
import io.groo.ieum.vacation.type.GrantMethod;
import io.groo.ieum.vacation.type.GrantStatus;
import io.groo.ieum.vacation.type.VacationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
public class VacationApiController implements VacationApi {
    private final VacationService vacationService;
    private final VacationPlanService vacationPlanService;
    private final MessageSource messageSource;
    private final VacationTimeFormatter vacationTimeFormatter;

    @Override
    @PreAuthorize("hasAuthority('VACATION:USE')")
    public ApiResponse useVacation(VacationApiDto.UseVacationReq data, @LoginUser User loginUser) {
        // 본인 확인: 로그인 유저와 요청 유저가 다르고, VACATION:MANAGE 권한이 없으면 거부
        validateVacationOwnership(loginUser.getId(), data.getUserId());

        Long vacationUsageId = vacationService.useVacation(VacationServiceDto.builder()
                        .userId(data.getUserId())
                        .type(data.getVacationType())
                        .desc(data.getVacationDesc())
                        .timeType(data.getVacationTimeType())
                        .startDate(data.getStartDate())
                        .endDate(data.getEndDate())
                        .build()
        );

        return ApiResponse.success(new VacationApiDto.UseVacationResp(vacationUsageId));
    }

    @Override
    @PreAuthorize("hasAuthority('VACATION:READ')")
    public ApiResponse getUserVacationHistory(String userId, Integer year, @LoginUser User loginUser) {
        validateVacationReadAccess(loginUser.getId(), userId);
        VacationServiceDto vacationInfo = vacationService.getUserVacationHistory(userId, year);

        // VacationGrant 정보 변환
        List<VacationApiDto.GetUserVacationHistoryResp.VacationGrantInfo> grantInfos = vacationInfo.getGrants().stream()
                .map(g -> new VacationApiDto.GetUserVacationHistoryResp.VacationGrantInfo(
                        g.getRowId(),
                        g.getType(),
                        getTranslatedName(g.getType()),
                        g.getDesc(),
                        g.getGrantTime(),
                        vacationTimeFormatter.format(g.getGrantTime()),
                        g.getRemainTime(),
                        vacationTimeFormatter.format(g.getRemainTime()),
                        g.getGrantDate(),
                        g.getExpiryDate()
                ))
                .toList();

        // VacationUsage 정보 변환
        List<VacationApiDto.GetUserVacationHistoryResp.VacationUsageInfo> usageInfos = vacationInfo.getUsages().stream()
                .map(u -> new VacationApiDto.GetUserVacationHistoryResp.VacationUsageInfo(
                        u.getRowId(),
                        u.getDesc(),
                        u.getType(),
                        getTranslatedName(u.getType()),
                        u.getUsedTime(),
                        vacationTimeFormatter.format(u.getUsedTime()),
                        u.getStartDate(),
                        u.getEndDate()
                ))
                .toList();

        return ApiResponse.success(new VacationApiDto.GetUserVacationHistoryResp(grantInfos, usageInfos));
    }

    @Override
    @PreAuthorize("hasAuthority('VACATION:READ')")
    public ApiResponse getAllUsersVacationHistory() {
        requireVacationManageOrApprove();
        List<VacationServiceDto> usersVacations = vacationService.getAllUsersVacationHistory();

        List<VacationApiDto.GetAllUsersVacationHistoryResp> resp = usersVacations.stream()
                .map(dto -> {
                    // VacationGrant 정보 변환
                    List<VacationApiDto.GetAllUsersVacationHistoryResp.VacationGrantInfo> grantInfos =
                            dto.getGrants().stream()
                                    .map(g -> new VacationApiDto.GetAllUsersVacationHistoryResp.VacationGrantInfo(
                                            g.getRowId(),
                                            g.getType(),
                                            getTranslatedName(g.getType()),
                                            g.getDesc(),
                                            g.getGrantTime(),
                                            vacationTimeFormatter.format(g.getGrantTime()),
                                            g.getRemainTime(),
                                            vacationTimeFormatter.format(g.getRemainTime()),
                                            g.getGrantDate(),
                                            g.getExpiryDate()
                                    ))
                                    .toList();

                    // VacationUsage 정보 변환
                    List<VacationApiDto.GetAllUsersVacationHistoryResp.VacationUsageInfo> usageInfos =
                            dto.getUsages().stream()
                                    .map(u -> new VacationApiDto.GetAllUsersVacationHistoryResp.VacationUsageInfo(
                                            u.getRowId(),
                                            u.getDesc(),
                                            u.getType(),
                                            getTranslatedName(u.getType()),
                                            u.getUsedTime(),
                                            vacationTimeFormatter.format(u.getUsedTime()),
                                            u.getStartDate(),
                                            u.getEndDate()
                                    ))
                                    .toList();

                    return new VacationApiDto.GetAllUsersVacationHistoryResp(
                            dto.getUser().getId(),
                            dto.getUser().getName(),
                            grantInfos,
                            usageInfos
                    );
                })
                .toList();

        return ApiResponse.success(resp);
    }

    @Override
    @PreAuthorize("hasAuthority('VACATION:READ')")
    public ApiResponse getAvailableVacations(String userId, LocalDateTime startDate, @LoginUser User loginUser) {
        validateVacationReadAccess(loginUser.getId(), userId);
        List<VacationServiceDto> availableVacations = vacationService.getAvailableVacations(userId, startDate);

        // 타입별 잔여 휴가 리스트 생성
        List<VacationApiDto.AvailableVacationByType> vacationsByType = availableVacations.stream()
                .map(dto -> new VacationApiDto.AvailableVacationByType(
                        dto.getType(),
                        getTranslatedName(dto.getType()),
                        dto.getRemainTime(),
                        vacationTimeFormatter.format(dto.getRemainTime())
                ))
                .toList();

        // 전체 잔여 휴가 시간 계산
        BigDecimal totalRemainTime = availableVacations.stream()
                .map(VacationServiceDto::getRemainTime)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        VacationApiDto.GetAvailableVacationsResp resp = new VacationApiDto.GetAvailableVacationsResp(
                totalRemainTime,
                vacationTimeFormatter.format(totalRemainTime),
                vacationsByType
        );

        return ApiResponse.success(resp);
    }

    @Override
    @PreAuthorize("hasAuthority('VACATION:USE')")
    public ApiResponse updateVacationUsage(Long vacationUsageId, VacationApiDto.UpdateVacationUsageReq data, @LoginUser User loginUser) {
        // 본인 확인: 로그인 유저와 요청 유저가 다르고, VACATION:MANAGE 권한이 없으면 거부
        validateVacationOwnership(loginUser.getId(), data.getUserId());

        Long newVacationUsageId = vacationService.updateVacationUsage(
                vacationUsageId,
                VacationServiceDto.builder()
                        .userId(data.getUserId())
                        .type(data.getVacationType())
                        .desc(data.getVacationDesc())
                        .timeType(data.getVacationTimeType())
                        .startDate(data.getStartDate())
                        .endDate(data.getEndDate())
                        .build(),
                loginUser.getId(),
                hasVacationManageAuthority()
        );

        return ApiResponse.success(new VacationApiDto.UpdateVacationUsageResp(newVacationUsageId));
    }

    @Override
    @PreAuthorize("hasAuthority('VACATION:USE')")
    public ApiResponse cancelVacationUsage(Long vacationUsageId, @LoginUser User loginUser) {
        // 휴가 사용 내역 조회하여 소유자 확인
        VacationUsage vacationUsage = vacationService.validateAndGetVacationUsage(vacationUsageId);
        validateVacationOwnership(loginUser.getId(), vacationUsage.getUser().getId());

        vacationService.cancelVacationUsage(vacationUsageId);
        return ApiResponse.success();
    }

    @Override
    @PreAuthorize("hasAuthority('VACATION:READ')")
    public ApiResponse getVacationUsagesByPeriod(LocalDateTime startDate, LocalDateTime endDate) {
        requireVacationManageOrApprove();
        List<VacationServiceDto> histories = vacationService.getVacationUsagesByPeriod(startDate, endDate);

        List<VacationApiDto.GetVacationUsagesByPeriodResp> resp = histories.stream()
                .map(dto -> new VacationApiDto.GetVacationUsagesByPeriodResp(
                        dto.getUser().getId(),
                        dto.getUser().getName(),
                        dto.getId(),
                        dto.getDesc(),
                        dto.getTimeType(),
                        getTranslatedName(dto.getTimeType()),
                        dto.getUsedTime(),
                        dto.getStartDate(),
                        dto.getEndDate()
                ))
                .toList();

        return ApiResponse.success(resp);
    }

    @Override
    @PreAuthorize("hasAuthority('VACATION:READ')")
    public ApiResponse getUserVacationUsagesByPeriod(String userId, LocalDateTime startDate, LocalDateTime endDate, @LoginUser User loginUser) {
        validateVacationReadAccess(loginUser.getId(), userId);
        List<VacationServiceDto> histories = vacationService.getUserVacationUsagesByPeriod(userId, startDate, endDate);

        List<VacationApiDto.GetUserVacationUsagesByPeriodResp> resp = histories.stream()
                .map(dto -> new VacationApiDto.GetUserVacationUsagesByPeriodResp(
                        dto.getId(),
                        dto.getDesc(),
                        dto.getTimeType(),
                        getTranslatedName(dto.getTimeType()),
                        dto.getUsedTime(),
                        dto.getStartDate(),
                        dto.getEndDate()
                ))
                .toList();

        return ApiResponse.success(resp);
    }

    @Override
    @PreAuthorize("hasAuthority('VACATION:READ')")
    public ApiResponse getUserMonthlyVacationStats(String userId, String year, @LoginUser User loginUser) {
        validateVacationReadAccess(loginUser.getId(), userId);
        List<VacationServiceDto> histories = vacationService.getUserMonthlyVacationStats(userId, year);

        List<VacationApiDto.GetUserMonthlyVacationStatsResp> resp = histories.stream()
                .map(v -> new VacationApiDto.GetUserMonthlyVacationStatsResp(
                        v.getMonth(),
                        v.getUsedTime(),
                        vacationTimeFormatter.format(v.getUsedTime())
                ))
                .toList();

        return ApiResponse.success(resp);
    }

    @Override
    @PreAuthorize("hasAuthority('VACATION:READ')")
    public ApiResponse getUserVacationStats(String userId, LocalDateTime baseDate, @LoginUser User loginUser) {
        validateVacationReadAccess(loginUser.getId(), userId);
        VacationServiceDto stats = vacationService.getUserVacationStats(userId, baseDate);

        return ApiResponse.success(new VacationApiDto.GetUserVacationStatsResp(
                stats.getRemainTime(),
                vacationTimeFormatter.format(stats.getRemainTime()),
                stats.getUsedTime(),
                vacationTimeFormatter.format(stats.getUsedTime()),
                stats.getExpectUsedTime(),
                vacationTimeFormatter.format(stats.getExpectUsedTime()),
                stats.getPrevRemainTime(),
                vacationTimeFormatter.format(stats.getPrevRemainTime()),
                stats.getPrevUsedTime(),
                vacationTimeFormatter.format(stats.getPrevUsedTime()),
                stats.getPrevExpectUsedTime(),
                vacationTimeFormatter.format(stats.getPrevExpectUsedTime()),
                stats.getRemainTimeGap(),
                vacationTimeFormatter.format(stats.getRemainTimeGap().abs()),
                stats.getUsedTimeGap(),
                vacationTimeFormatter.format(stats.getUsedTimeGap().abs())
        ));
    }

    @Override
    @PreAuthorize("hasAuthority('VACATION:MANAGE')")
    public ApiResponse createVacationPolicy(VacationApiDto.CreateVacationPolicyReq data) {
        Long vacationPolicyId = vacationService.createVacationPolicy(VacationPolicyServiceDto.builder()
                .name(data.getVacationPolicyName())
                .desc(data.getVacationPolicyDesc())
                .vacationType(data.getVacationType())
                .grantMethod(data.getGrantMethod())
                .grantTime(data.getGrantTime())
                .isFlexibleGrant(data.getIsFlexibleGrant())
                .minuteGrantYn(data.getMinuteGrantYn())
                .repeatUnit(data.getRepeatUnit())
                .repeatInterval(data.getRepeatInterval())
                .specificMonths(data.getSpecificMonths())
                .specificDays(data.getSpecificDays())
                .firstGrantDate(data.getFirstGrantDate())
                .isRecurring(data.getIsRecurring())
                .maxGrantCount(data.getMaxGrantCount())
                .effectiveType(data.getEffectiveType())
                .expirationType(data.getExpirationType())
                .approvalRequiredCount(data.getApprovalRequiredCount())
                .build()
        );

        return ApiResponse.success(new VacationApiDto.CreateVacationPolicyResp(vacationPolicyId));
    }

    @Override
    @PreAuthorize("hasAuthority('VACATION:MANAGE')")
    public ApiResponse getVacationPolicy(Long vacationPolicyId) {
        VacationPolicyServiceDto policy = vacationService.getVacationPolicy(vacationPolicyId);

        return ApiResponse.success(new VacationApiDto.GetVacationPolicyResp(
                policy.getId(),
                policy.getName(),
                policy.getDesc(),
                policy.getVacationType(),
                policy.getGrantMethod(),
                policy.getGrantTime(),
                vacationTimeFormatter.format(policy.getGrantTime()),
                policy.getIsFlexibleGrant(),
                policy.getMinuteGrantYn(),
                policy.getRepeatUnit(),
                policy.getRepeatInterval(),
                policy.getSpecificMonths(),
                policy.getSpecificDays(),
                policy.getEffectiveType(),
                policy.getExpirationType(),
                policy.getRepeatGrantDescription(),
                policy.getCanDeleted()
        ));
    }

    @Override
    @PreAuthorize("hasAuthority('VACATION:MANAGE')")
    public ApiResponse getVacationPolicies() {
        List<VacationPolicyServiceDto> policies = vacationService.getVacationPolicies();

        List<VacationApiDto.GetVacationPolicyResp> resp = policies.stream()
                .map(vp -> new VacationApiDto.GetVacationPolicyResp(
                        vp.getId(),
                        vp.getName(),
                        vp.getDesc(),
                        vp.getVacationType(),
                        vp.getGrantMethod(),
                        vp.getGrantTime(),
                        vacationTimeFormatter.format(vp.getGrantTime()),
                        vp.getIsFlexibleGrant(),
                        vp.getMinuteGrantYn(),
                        vp.getRepeatUnit(),
                        vp.getRepeatInterval(),
                        vp.getSpecificMonths(),
                        vp.getSpecificDays(),
                        vp.getEffectiveType(),
                        vp.getExpirationType(),
                        vp.getRepeatGrantDescription(),
                        vp.getCanDeleted()
                ))
                .toList();

        return ApiResponse.success(resp);
    }

    @Override
    @PreAuthorize("hasAuthority('VACATION:MANAGE')")
    public ApiResponse updateVacationPolicy(Long vacationPolicyId, VacationApiDto.UpdateVacationPolicyReq data) {
        Long updatedPolicyId = vacationService.updateVacationPolicy(
                vacationPolicyId, data.getVacationPolicyName(), data.getVacationPolicyDesc());

        return ApiResponse.success(new VacationApiDto.UpdateVacationPolicyResp(updatedPolicyId));
    }

    @Override
    @PreAuthorize("hasAuthority('VACATION:MANAGE')")
    public ApiResponse deleteVacationPolicy(Long vacationPolicyId) {
        Long deletedPolicyId = vacationService.deleteVacationPolicy(vacationPolicyId);

        return ApiResponse.success(new VacationApiDto.DeleteVacationPolicyResp(deletedPolicyId));
    }

    @Override
    @PreAuthorize("hasAuthority('VACATION:MANAGE')")
    public ApiResponse getUserAssignedVacationPolicies(String userId, GrantMethod grantMethod) {
        List<VacationPolicyServiceDto> policies = vacationService.getUserAssignedVacationPolicies(userId, grantMethod);

        List<VacationApiDto.GetUserAssignedVacationPoliciesResp> resp = policies.stream()
                .map(vp -> new VacationApiDto.GetUserAssignedVacationPoliciesResp(
                        vp.getId(),
                        vp.getName(),
                        vp.getDesc(),
                        vp.getVacationType(),
                        vp.getGrantMethod(),
                        vp.getGrantTime(),
                        vacationTimeFormatter.format(vp.getGrantTime()),
                        vp.getIsFlexibleGrant(),
                        vp.getMinuteGrantYn(),
                        vp.getRepeatUnit(),
                        vp.getRepeatInterval(),
                        vp.getSpecificMonths(),
                        vp.getSpecificDays(),
                        vp.getFirstGrantDate(),
                        vp.getIsRecurring(),
                        vp.getMaxGrantCount(),
                        vp.getApprovalRequiredCount(),
                        vp.getEffectiveType(),
                        vp.getExpirationType(),
                        vp.getRepeatGrantDescription()
                ))
                .toList();

        return ApiResponse.success(resp);
    }

    @Override
    @PreAuthorize("hasAuthority('VACATION:MANAGE')")
    public ApiResponse getUserAssignedVacationPoliciesWithFilters(String userId, VacationType vacationType, GrantMethod grantMethod) {
        List<VacationPolicyServiceDto> policies = vacationService.getUserAssignedVacationPoliciesWithFilters(
                userId, vacationType, grantMethod);

        List<VacationApiDto.GetVacationPolicyAssignmentStatusResp.VacationPolicyInfo> resp = policies.stream()
                .map(vp -> new VacationApiDto.GetVacationPolicyAssignmentStatusResp.VacationPolicyInfo(
                        vp.getId(),
                        vp.getName(),
                        vp.getDesc(),
                        vp.getVacationType(),
                        vp.getGrantMethod(),
                        vp.getGrantTime(),
                        vacationTimeFormatter.format(vp.getGrantTime()),
                        vp.getIsFlexibleGrant(),
                        vp.getMinuteGrantYn(),
                        vp.getRepeatUnit(),
                        vp.getRepeatInterval(),
                        vp.getSpecificMonths(),
                        vp.getSpecificDays(),
                        vp.getEffectiveType(),
                        vp.getExpirationType(),
                        vp.getRepeatGrantDescription()
                ))
                .toList();

        return ApiResponse.success(resp);
    }

    @Override
    @PreAuthorize("hasAuthority('VACATION:GRANT')")
    public ApiResponse manualGrantVacation(String userId, VacationApiDto.ManualGrantVacationReq data) {

        // DTO 변환
        VacationServiceDto serviceDto = VacationServiceDto.builder()
                .policyId(data.getVacationPolicyId())
                .grantTime(data.getGrantTime())
                .grantDate(data.getGrantDate())
                .expiryDate(data.getExpiryDate())
                .desc(data.getGrantDesc())
                .build();

        VacationGrant grant = vacationService.manualGrantVacation(userId, serviceDto);

        return ApiResponse.success(new VacationApiDto.ManualGrantVacationResp(
                grant.getRowId(),
                userId,
                grant.getPolicy().getRowId(),
                grant.getGrantTime(),
                grant.getGrantDate(),
                grant.getExpiryDate()
        ));
    }

    @Override
    @PreAuthorize("hasAuthority('VACATION:GRANT')")
    public ApiResponse revokeVacationGrant(Long vacationGrantId) {
        VacationGrant grant = vacationService.revokeVacationGrant(vacationGrantId);

        return ApiResponse.success(new VacationApiDto.RevokeVacationGrantResp(
                grant.getRowId(),
                grant.getUser().getId()
        ));
    }

    @Override
    @PreAuthorize("hasAuthority('VACATION:REQUEST')")
    public ApiResponse requestVacation(String userId, VacationApiDto.RequestVacationReq data, @LoginUser User loginUser) {
        // 본인 확인: 로그인 유저와 신청 유저가 다르면 거부
        validateUserIdentity(loginUser.getId(), userId, "휴가 신청");

        Long vacationGrantId = vacationService.requestVacation(userId, VacationServiceDto.builder()
                .policyId(data.getPolicyId())
                .desc(data.getDesc())
                .approverIds(data.getApproverIds())
                .grantTime(data.getGrantTime())
                .requestStartTime(data.getRequestStartTime())
                .requestEndTime(data.getRequestEndTime())
                .requestDesc(data.getRequestDesc())
                .build());

        return ApiResponse.success(new VacationApiDto.RequestVacationResp(vacationGrantId));
    }

    @Override
    @PreAuthorize("hasAuthority('VACATION:APPROVE')")
    public ApiResponse approveVacation(Long approvalId, String approverId, @LoginUser User loginUser) {
        // 본인 확인: 로그인 유저와 승인자가 다르면 거부
        validateUserIdentity(loginUser.getId(), approverId, "휴가 승인");

        Long processedApprovalId = vacationService.approveVacation(approvalId, approverId);

        return ApiResponse.success(new VacationApiDto.ApproveVacationResp(processedApprovalId));
    }

    @Override
    @PreAuthorize("hasAuthority('VACATION:APPROVE')")
    public ApiResponse rejectVacation(Long approvalId, String approverId, VacationApiDto.RejectVacationReq data, @LoginUser User loginUser) {
        // 본인 확인: 로그인 유저와 반려자가 다르면 거부
        validateUserIdentity(loginUser.getId(), approverId, "휴가 반려");

        Long processedApprovalId = vacationService.rejectVacation(
                approvalId,
                approverId,
                VacationApprovalServiceDto.builder()
                        .rejectionReason(data.getRejectionReason())
                        .build()
        );

        return ApiResponse.success(new VacationApiDto.RejectVacationResp(processedApprovalId));
    }

    @Override
    @PreAuthorize("hasAuthority('VACATION:REQUEST')")
    public ApiResponse cancelVacationRequest(Long vacationGrantId, String userId, @LoginUser User loginUser) {
        // 본인 확인: 로그인 유저와 취소 요청자가 다르면 거부
        validateUserIdentity(loginUser.getId(), userId, "휴가 신청 취소");

        Long canceledVacationGrantId = vacationService.cancelVacationRequest(vacationGrantId, userId);

        return ApiResponse.success(new VacationApiDto.CancelVacationRequestResp(canceledVacationGrantId));
    }

    @Override
    @PreAuthorize("hasAuthority('VACATION:APPROVE')")
    public ApiResponse getAllVacationsByApprover(String approverId, Integer year, GrantStatus status, @LoginUser User loginUser) {
        validateVacationOwnership(loginUser.getId(), approverId);
        List<VacationServiceDto> vacations = vacationService.getAllVacationsByApprover(approverId, year, status);

        List<VacationApiDto.GetUserRequestedVacationsResp> resp = vacations.stream()
                .map(v -> {
                    // 승인자 목록을 ApproverInfo DTO로 변환
                    List<VacationApiDto.GetUserRequestedVacationsResp.ApproverInfo> approvers = null;
                    if (v.getApprovers() != null) {
                        approvers = v.getApprovers().stream()
                                .map(approver -> new VacationApiDto.GetUserRequestedVacationsResp.ApproverInfo(
                                        approver.getId(),
                                        approver.getApproverId(),
                                        approver.getApproverName(),
                                        approver.getApprovalOrder(),
                                        approver.getApprovalStatus(),
                                        getTranslatedName(approver.getApprovalStatus()),
                                        approver.getApprovalDate(),
                                        approver.getRejectionReason()
                                ))
                                .toList();
                    }

                    return new VacationApiDto.GetUserRequestedVacationsResp(
                            v.getUserId(),
                            v.getUser().getName(),
                            v.getId(),
                            v.getPolicyId(),
                            v.getPolicyName(),
                            v.getType(),
                            getTranslatedName(v.getType()),
                            v.getDesc(),
                            v.getGrantTime(),
                            vacationTimeFormatter.format(v.getGrantTime()),
                            v.getPolicyGrantTime(),
                            vacationTimeFormatter.format(v.getPolicyGrantTime()),
                            v.getRemainTime(),
                            vacationTimeFormatter.format(v.getRemainTime()),
                            v.getGrantDate(),
                            v.getExpiryDate(),
                            v.getRequestStartTime(),
                            v.getRequestEndTime(),
                            v.getRequestDesc(),
                            v.getGrantStatus(),
                            getTranslatedName(v.getGrantStatus()),
                            v.getCreateDate(),
                            v.getCurrentApproverId(),
                            v.getCurrentApproverName(),
                            approvers
                    );
                })
                .toList();

        return ApiResponse.success(resp);
    }

    @Override
    @PreAuthorize("hasAuthority('VACATION:REQUEST')")
    public ApiResponse getUserRequestedVacations(String userId, Integer year, @LoginUser User loginUser) {
        validateVacationReadAccess(loginUser.getId(), userId);
        List<VacationServiceDto> requestedVacations = vacationService.getAllRequestedVacationsByUserId(userId, year);

        List<VacationApiDto.GetUserRequestedVacationsResp> resp = requestedVacations.stream()
                .map(v -> {
                    // 승인자 목록을 ApproverInfo DTO로 변환
                    List<VacationApiDto.GetUserRequestedVacationsResp.ApproverInfo> approvers = null;
                    if (v.getApprovers() != null) {
                        approvers = v.getApprovers().stream()
                                .map(approver -> new VacationApiDto.GetUserRequestedVacationsResp.ApproverInfo(
                                        approver.getId(),
                                        approver.getApproverId(),
                                        approver.getApproverName(),
                                        approver.getApprovalOrder(),
                                        approver.getApprovalStatus(),
                                        getTranslatedName(approver.getApprovalStatus()),
                                        approver.getApprovalDate(),
                                        approver.getRejectionReason()
                                ))
                                .toList();
                    }

                    return new VacationApiDto.GetUserRequestedVacationsResp(
                            v.getUserId(),
                            v.getUser().getName(),
                            v.getId(),
                            v.getPolicyId(),
                            v.getPolicyName(),
                            v.getType(),
                            getTranslatedName(v.getType()),
                            v.getDesc(),
                            v.getGrantTime(),
                            vacationTimeFormatter.format(v.getGrantTime()),
                            v.getPolicyGrantTime(),
                            vacationTimeFormatter.format(v.getPolicyGrantTime()),
                            v.getRemainTime(),
                            vacationTimeFormatter.format(v.getRemainTime()),
                            v.getGrantDate(),
                            v.getExpiryDate(),
                            v.getRequestStartTime(),
                            v.getRequestEndTime(),
                            v.getRequestDesc(),
                            v.getGrantStatus(),
                            getTranslatedName(v.getGrantStatus()),
                            v.getCreateDate(),
                            v.getCurrentApproverId(),
                            v.getCurrentApproverName(),
                            approvers
                    );
                })
                .toList();

        return ApiResponse.success(resp);
    }

    @Override
    @PreAuthorize("hasAuthority('VACATION:REQUEST')")
    public ApiResponse getUserRequestedVacationStats(String userId, Integer year, @LoginUser User loginUser) {
        validateVacationReadAccess(loginUser.getId(), userId);
        VacationServiceDto stats = vacationService.getRequestedVacationStatsByUserId(userId, year);

        return ApiResponse.success(new VacationApiDto.GetUserRequestedVacationStatsResp(
                stats.getTotalRequestCount(),
                stats.getCurrentMonthRequestCount(),
                stats.getChangeRate(),
                stats.getPendingCount(),
                stats.getAverageProcessingDays(),
                stats.getProgressCount(),
                stats.getApprovedCount(),
                stats.getApprovalRate(),
                stats.getRejectedCount(),
                stats.getCanceledCount(),
                stats.getAcquiredVacationTimeStr(),
                stats.getAcquiredVacationTime()
        ));
    }

    @Override
    @PreAuthorize("hasAuthority('VACATION:MANAGE')")
    public ApiResponse getVacationPolicyAssignmentStatus(String userId) {
        VacationServiceDto result = vacationService.getVacationPolicyAssignmentStatus(userId);

        // 할당된 정책 변환
        List<VacationApiDto.GetVacationPolicyAssignmentStatusResp.VacationPolicyInfo> assignedPolicies =
                result.getAssignedPolicies().stream()
                        .map(vp -> new VacationApiDto.GetVacationPolicyAssignmentStatusResp.VacationPolicyInfo(
                                vp.getId(),
                                vp.getName(),
                                vp.getDesc(),
                                vp.getVacationType(),
                                vp.getGrantMethod(),
                                vp.getGrantTime(),
                                vacationTimeFormatter.format(vp.getGrantTime()),
                                vp.getIsFlexibleGrant(),
                                vp.getMinuteGrantYn(),
                                vp.getRepeatUnit(),
                                vp.getRepeatInterval(),
                                vp.getSpecificMonths(),
                                vp.getSpecificDays(),
                                vp.getEffectiveType(),
                                vp.getExpirationType(),
                                vp.getRepeatGrantDescription()
                        ))
                        .toList();

        // 할당되지 않은 정책 변환
        List<VacationApiDto.GetVacationPolicyAssignmentStatusResp.VacationPolicyInfo> unassignedPolicies =
                result.getUnassignedPolicies().stream()
                        .map(vp -> new VacationApiDto.GetVacationPolicyAssignmentStatusResp.VacationPolicyInfo(
                                vp.getId(),
                                vp.getName(),
                                vp.getDesc(),
                                vp.getVacationType(),
                                vp.getGrantMethod(),
                                vp.getGrantTime(),
                                vacationTimeFormatter.format(vp.getGrantTime()),
                                vp.getIsFlexibleGrant(),
                                vp.getMinuteGrantYn(),
                                vp.getRepeatUnit(),
                                vp.getRepeatInterval(),
                                vp.getSpecificMonths(),
                                vp.getSpecificDays(),
                                vp.getEffectiveType(),
                                vp.getExpirationType(),
                                vp.getRepeatGrantDescription()
                        ))
                        .toList();

        return ApiResponse.success(new VacationApiDto.GetVacationPolicyAssignmentStatusResp(
                assignedPolicies,
                unassignedPolicies
        ));
    }

    // ========== 전체 유저 휴가 통계 조회 ==========

    @Override
    @PreAuthorize("hasAuthority('VACATION:MANAGE')")
    public ApiResponse getAllUsersVacationSummary(Integer year) {
        List<VacationServiceDto> userVacationSummaries = vacationService.getAllUsersVacationSummary(year);

        List<VacationApiDto.GetAllUsersVacationSummaryResp> resp = userVacationSummaries.stream()
                .map(dto -> new VacationApiDto.GetAllUsersVacationSummaryResp(
                        dto.getUser().getId(),
                        dto.getUser().getName(),
                        dto.getDepartmentName(),
                        dto.getTotalVacationDays(),
                        vacationTimeFormatter.format(dto.getTotalVacationDays()),
                        dto.getUsedVacationDays(),
                        vacationTimeFormatter.format(dto.getUsedVacationDays()),
                        dto.getScheduledVacationDays(),
                        vacationTimeFormatter.format(dto.getScheduledVacationDays()),
                        dto.getRemainingVacationDays(),
                        vacationTimeFormatter.format(dto.getRemainingVacationDays())
                ))
                .toList();

        return ApiResponse.success(resp);
    }

    private String getTranslatedName(DisplayType type) {
        if (type == null) return null;
        return messageSource.getMessage(type.getMessageKey(), null, LocaleContextHolder.getLocale());
    }

    /**
     * 휴가 소유권 검증
     * 로그인 유저와 대상 유저가 다르고, VACATION:MANAGE 권한이 없으면 예외 발생
     */
    private void validateVacationOwnership(String loginUserId, String targetUserId) {
        if (!loginUserId.equals(targetUserId) && !hasVacationManageAuthority()) {
            log.warn("휴가 접근 거부 - 로그인 유저: {}, 대상 유저: {}", loginUserId, targetUserId);
            throw new ForbiddenException(HrErrorCode.VACATION_ACCESS_DENIED);
        }
    }

    /**
     * 휴가 조회 범위 검증
     * 본인 기록이거나, 휴가 관리(VACATION:MANAGE) · 휴가 승인(VACATION:APPROVE) 권한이 있을 때만 다른 사람의 기록을 읽는다
     */
    private void validateVacationReadAccess(String loginUserId, String targetUserId) {
        if (!loginUserId.equals(targetUserId) && !hasAuthority("VACATION:MANAGE") && !hasAuthority("VACATION:APPROVE")) {
            log.warn("휴가 조회 거부 - 로그인 유저: {}, 대상 유저: {}", loginUserId, targetUserId);
            throw new ForbiddenException(HrErrorCode.VACATION_ACCESS_DENIED);
        }
    }

    /**
     * 전 직원의 휴가 기록을 한꺼번에 내리는 API 는 휴가 관리 · 휴가 승인 권한자만 부른다
     */
    private void requireVacationManageOrApprove() {
        if (!hasAuthority("VACATION:MANAGE") && !hasAuthority("VACATION:APPROVE")) {
            throw new ForbiddenException(HrErrorCode.PERMISSION_DENIED);
        }
    }

    private boolean hasAuthority(String authority) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(auth -> authority.equals(auth.getAuthority()));
    }

    /**
     * 현재 사용자가 VACATION:MANAGE 권한을 가지고 있는지 확인
     */
    private boolean hasVacationManageAuthority() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(auth -> "VACATION:MANAGE".equals(auth.getAuthority()));
    }

    /**
     * 사용자 본인 확인 검증
     * 로그인 유저와 요청 유저가 다르면 예외 발생 (MANAGE 권한 바이패스 없음)
     */
    private void validateUserIdentity(String loginUserId, String requestUserId, String action) {
        if (!loginUserId.equals(requestUserId)) {
            log.warn("{} 접근 거부 - 로그인 유저: {}, 요청 유저: {}", action, loginUserId, requestUserId);
            throw new ForbiddenException(HrErrorCode.VACATION_ACCESS_DENIED);
        }
    }

    // ========================================
    // Vacation Plan CRUD
    // ========================================

    @Override
    @PreAuthorize("hasAuthority('VACATION:MANAGE')")
    public ApiResponse createPlan(VacationPlanApiDto.CreatePlanReq data) {
        log.info("휴가 플랜 생성 요청: code={}, name={}", data.getCode(), data.getName());

        VacationPlanServiceDto result;
        if (data.getPolicyIds() != null && !data.getPolicyIds().isEmpty()) {
            result = vacationPlanService.createPlanWithPolicies(
                    data.getCode(),
                    data.getName(),
                    data.getDesc(),
                    data.getPolicyIds()
            );
        } else {
            result = vacationPlanService.createPlan(
                    data.getCode(),
                    data.getName(),
                    data.getDesc()
            );
        }

        return ApiResponse.success(VacationPlanApiDto.PlanResp.from(result));
    }

    @Override
    @PreAuthorize("hasAuthority('VACATION:MANAGE')")
    public ApiResponse getAllPlans() {
        log.info("전체 휴가 플랜 목록 조회 요청");

        List<VacationPlanServiceDto> plans = vacationPlanService.getAllPlans();
        List<VacationPlanApiDto.PlanResp> response = plans.stream()
                .map(VacationPlanApiDto.PlanResp::from)
                .toList();

        return ApiResponse.success(response);
    }

    @Override
    @PreAuthorize("hasAuthority('VACATION:MANAGE')")
    public ApiResponse getPlan(String code) {
        log.info("휴가 플랜 상세 조회 요청: code={}", code);

        VacationPlanServiceDto plan = vacationPlanService.getPlan(code);
        return ApiResponse.success(VacationPlanApiDto.PlanResp.from(plan));
    }

    @Override
    @PreAuthorize("hasAuthority('VACATION:MANAGE')")
    public ApiResponse updatePlan(String code, VacationPlanApiDto.UpdatePlanReq data) {
        log.info("휴가 플랜 수정 요청: code={}", code);

        VacationPlanServiceDto result = vacationPlanService.updatePlan(
                code,
                data.getName(),
                data.getDesc()
        );

        return ApiResponse.success(VacationPlanApiDto.PlanResp.from(result));
    }

    @Override
    @PreAuthorize("hasAuthority('VACATION:MANAGE')")
    public ApiResponse deletePlan(String code) {
        log.info("휴가 플랜 삭제 요청: code={}", code);

        vacationPlanService.deletePlan(code);
        return ApiResponse.success();
    }

    // ========================================
    // Plan-Policy 관리
    // ========================================

    @Override
    @PreAuthorize("hasAuthority('VACATION:MANAGE')")
    public ApiResponse addPolicyToPlan(String code, Long policyId) {
        log.info("플랜에 정책 추가 요청: code={}, policyId={}", code, policyId);

        vacationPlanService.addPolicyToPlan(code, policyId);
        return ApiResponse.success();
    }

    @Override
    @PreAuthorize("hasAuthority('VACATION:MANAGE')")
    public ApiResponse removePolicyFromPlan(String code, Long policyId) {
        log.info("플랜에서 정책 제거 요청: code={}, policyId={}", code, policyId);

        vacationPlanService.removePolicyFromPlan(code, policyId);
        return ApiResponse.success();
    }

    @Override
    @PreAuthorize("hasAuthority('VACATION:MANAGE')")
    public ApiResponse updatePlanPolicies(String code, VacationPlanApiDto.UpdatePlanPoliciesReq data) {
        log.info("플랜 정책 전체 업데이트 요청: code={}", code);

        vacationPlanService.updatePlanPolicies(code, data.getPolicyIds());
        return ApiResponse.success();
    }

    // ========================================
    // User-Plan 관리
    // ========================================

    @Override
    @PreAuthorize("hasAuthority('VACATION:MANAGE')")
    public ApiResponse assignPlanToUser(String userId, VacationPlanApiDto.AssignPlanReq data) {
        log.info("사용자에게 플랜 할당 요청: userId={}, planCode={}", userId, data.getPlanCode());

        vacationPlanService.assignPlanToUser(userId, data.getPlanCode());
        return ApiResponse.success();
    }

    @Override
    @PreAuthorize("hasAuthority('VACATION:MANAGE')")
    public ApiResponse assignPlansToUser(String userId, VacationPlanApiDto.AssignPlansReq data) {
        log.info("사용자에게 여러 플랜 할당 요청: userId={}, planCodes={}", userId, data.getPlanCodes());

        vacationPlanService.assignPlansToUser(userId, data.getPlanCodes());
        return ApiResponse.success();
    }

    @Override
    @PreAuthorize("hasAuthority('VACATION:MANAGE')")
    public ApiResponse revokePlanFromUser(String userId, String code) {
        log.info("사용자에게서 플랜 회수 요청: userId={}, planCode={}", userId, code);

        vacationPlanService.revokePlanFromUser(userId, code);
        return ApiResponse.success();
    }

    @Override
    @PreAuthorize("hasAuthority('VACATION:MANAGE')")
    public ApiResponse getUserPlans(String userId) {
        log.info("사용자의 플랜 목록 조회 요청: userId={}", userId);

        List<VacationPlanServiceDto> plans = vacationPlanService.getUserPlans(userId);
        List<VacationPlanApiDto.PlanResp> response = plans.stream()
                .map(VacationPlanApiDto.PlanResp::from)
                .toList();

        return ApiResponse.success(response);
    }
}
