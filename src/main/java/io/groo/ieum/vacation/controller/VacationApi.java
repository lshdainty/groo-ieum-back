package io.groo.ieum.vacation.controller;

import io.groo.core.controller.ApiResponse;
import io.groo.ieum.security.annotation.LoginUser;
import io.groo.ieum.user.domain.User;
import io.groo.ieum.vacation.controller.dto.VacationApiDto;
import io.groo.ieum.vacation.controller.dto.VacationPlanApiDto;
import io.groo.ieum.vacation.type.GrantMethod;
import io.groo.ieum.vacation.type.GrantStatus;
import io.groo.ieum.vacation.type.VacationType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Tag(name = "Vacation", description = "휴가 관리 API")
public interface VacationApi {

    @Operation(
            summary = "휴가 사용",
            description = "새로운 휴가 사용 내역을 등록합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "휴가 사용 등록 성공",
                    content = @Content(schema = @Schema(implementation = VacationApiDto.UseVacationResp.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (VACATION:USE 필요)"
            )
    })
    @PostMapping("/api/v1/vacation-usages")
    ApiResponse useVacation(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "휴가 사용 정보",
                    required = true,
                    content = @Content(schema = @Schema(implementation = VacationApiDto.UseVacationReq.class))
            )
            @Valid @RequestBody VacationApiDto.UseVacationReq data,
            @Parameter(hidden = true) @LoginUser User loginUser
    );

    @Operation(
            summary = "특정 유저의 휴가 정보 조회",
            description = "특정 유저의 휴가 부여/사용 내역을 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "휴가 정보 조회 성공",
                    content = @Content(schema = @Schema(implementation = VacationApiDto.GetUserVacationHistoryResp.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (VACATION:READ 필요)"
            )
    })
    @GetMapping("/api/v1/users/{userId}/vacations")
    ApiResponse getUserVacationHistory(
            @Parameter(description = "사용자 ID", example = "user123", required = true)
            @PathVariable("userId") String userId,
            @Parameter(description = "조회할 연도", example = "2024", required = true)
            @RequestParam("year") Integer year,
            @Parameter(hidden = true) @LoginUser User loginUser
    );

    @Operation(
            summary = "모든 유저의 휴가 정보 조회",
            description = "전체 사용자의 휴가 정보를 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "휴가 정보 조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (VACATION:READ 필요)"
            )
    })
    @GetMapping("/api/v1/vacations")
    ApiResponse getAllUsersVacationHistory();

    @Operation(
            summary = "특정 유저의 사용 가능한 휴가 조회",
            description = "특정 유저가 사용 가능한 휴가 목록을 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "사용 가능한 휴가 조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (VACATION:READ 필요)"
            )
    })
    @GetMapping("/api/v1/users/{userId}/vacations/available")
    ApiResponse getAvailableVacations(
            @Parameter(description = "사용자 ID", example = "user123", required = true)
            @PathVariable("userId") String userId,
            @Parameter(description = "시작 날짜 (yyyy-MM-dd'T'HH:mm:ss)", example = "2024-01-01T00:00:00", required = true)
            @RequestParam("startDate") @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime startDate,
            @Parameter(hidden = true) @LoginUser User loginUser
    );

    @Operation(
            summary = "휴가 사용 내역 수정",
            description = "기존 휴가 사용 내역을 수정합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "휴가 사용 내역 수정 성공",
                    content = @Content(schema = @Schema(implementation = VacationApiDto.UpdateVacationUsageResp.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (VACATION:USE 필요) 또는 본인 기록이 아님 (VACATION:MANAGE 없음)"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "휴가 사용 내역을 찾을 수 없음"
            )
    })
    @PutMapping("/api/v1/vacation-usages/{id}")
    ApiResponse updateVacationUsage(
            @Parameter(description = "휴가 사용 ID", example = "1", required = true)
            @PathVariable("id") Long vacationUsageId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "휴가 사용 수정 정보",
                    required = true,
                    content = @Content(schema = @Schema(implementation = VacationApiDto.UpdateVacationUsageReq.class))
            )
            @Valid @RequestBody VacationApiDto.UpdateVacationUsageReq data,
            @Parameter(hidden = true) @LoginUser User loginUser
    );

    @Operation(
            summary = "휴가 사용 내역 삭제",
            description = "휴가 사용 내역을 삭제합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "휴가 사용 내역 삭제 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (VACATION:USE 필요)"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "휴가 사용 내역을 찾을 수 없음"
            )
    })
    @DeleteMapping("/api/v1/vacation-usages/{id}")
    ApiResponse cancelVacationUsage(
            @Parameter(description = "휴가 사용 ID", example = "1", required = true)
            @PathVariable("id") Long vacationUsageId,
            @Parameter(hidden = true) @LoginUser User loginUser
    );

    @Operation(
            summary = "기간별 휴가 사용 내역 조회 (전체 유저)",
            description = "특정 기간 동안의 전체 사용자 휴가 사용 내역을 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "휴가 사용 내역 조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (VACATION:READ 필요)"
            )
    })
    @GetMapping("/api/v1/vacation-usages")
    ApiResponse getVacationUsagesByPeriod(
            @Parameter(description = "시작 날짜 (yyyy-MM-dd'T'HH:mm:ss)", example = "2024-01-01T00:00:00", required = true)
            @RequestParam("startDate") @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime startDate,
            @Parameter(description = "종료 날짜 (yyyy-MM-dd'T'HH:mm:ss)", example = "2024-12-31T23:59:59", required = true)
            @RequestParam("endDate") @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime endDate
    );

    @Operation(
            summary = "특정 유저의 기간별 휴가 사용 내역 조회",
            description = "특정 유저의 기간별 휴가 사용 내역을 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "휴가 사용 내역 조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (VACATION:READ 필요)"
            )
    })
    @GetMapping("/api/v1/users/{userId}/vacation-usages")
    ApiResponse getUserVacationUsagesByPeriod(
            @Parameter(description = "사용자 ID", example = "user123", required = true)
            @PathVariable("userId") String userId,
            @Parameter(description = "시작 날짜 (yyyy-MM-dd'T'HH:mm:ss)", example = "2024-01-01T00:00:00", required = true)
            @RequestParam("startDate") @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime startDate,
            @Parameter(description = "종료 날짜 (yyyy-MM-dd'T'HH:mm:ss)", example = "2024-12-31T23:59:59", required = true)
            @RequestParam("endDate") @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime endDate,
            @Parameter(hidden = true) @LoginUser User loginUser
    );

    @Operation(
            summary = "특정 유저의 월별 휴가 사용 통계 조회",
            description = "특정 유저의 월별 휴가 사용 통계를 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "월별 휴가 통계 조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (VACATION:READ 필요)"
            )
    })
    @GetMapping("/api/v1/users/{userId}/vacation-usages/monthly-stats")
    ApiResponse getUserMonthlyVacationStats(
            @Parameter(description = "사용자 ID", example = "user123", required = true)
            @PathVariable("userId") String userId,
            @Parameter(description = "조회할 연도 (YYYY)", example = "2024", required = true)
            @RequestParam("year") String year,
            @Parameter(hidden = true) @LoginUser User loginUser
    );

    @Operation(
            summary = "특정 유저의 휴가 사용 통계 조회",
            description = "특정 유저의 전체 휴가 사용 통계를 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "휴가 통계 조회 성공",
                    content = @Content(schema = @Schema(implementation = VacationApiDto.GetUserVacationStatsResp.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (VACATION:READ 필요)"
            )
    })
    @GetMapping("/api/v1/users/{userId}/vacations/stats")
    ApiResponse getUserVacationStats(
            @Parameter(description = "사용자 ID", example = "user123", required = true)
            @PathVariable("userId") String userId,
            @Parameter(description = "기준 날짜 (yyyy-MM-dd'T'HH:mm:ss)", example = "2024-01-01T00:00:00", required = true)
            @RequestParam("baseDate") @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime baseDate,
            @Parameter(hidden = true) @LoginUser User loginUser
    );

    @Operation(
            summary = "휴가 정책 등록",
            description = "새로운 휴가 정책을 등록합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "휴가 정책 등록 성공",
                    content = @Content(schema = @Schema(implementation = VacationApiDto.CreateVacationPolicyResp.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (VACATION:MANAGE 필요)"
            )
    })
    @PostMapping("/api/v1/vacation-policies")
    ApiResponse createVacationPolicy(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "휴가 정책 정보",
                    required = true,
                    content = @Content(schema = @Schema(implementation = VacationApiDto.CreateVacationPolicyReq.class))
            )
            @Valid @RequestBody VacationApiDto.CreateVacationPolicyReq data
    );

    @Operation(
            summary = "특정 휴가 정책 조회",
            description = "특정 휴가 정책의 상세 정보를 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "휴가 정책 조회 성공",
                    content = @Content(schema = @Schema(implementation = VacationApiDto.GetVacationPolicyResp.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (VACATION:MANAGE 필요)"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "휴가 정책을 찾을 수 없음"
            )
    })
    @GetMapping("/api/v1/vacation-policies/{id}")
    ApiResponse getVacationPolicy(
            @Parameter(description = "휴가 정책 ID", example = "1", required = true)
            @PathVariable("id") Long vacationPolicyId
    );

    @Operation(
            summary = "휴가 정책 목록 조회",
            description = "모든 휴가 정책 목록을 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "휴가 정책 목록 조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (VACATION:MANAGE 필요)"
            )
    })
    @GetMapping("/api/v1/vacation-policies")
    ApiResponse getVacationPolicies();

    @Operation(
            summary = "휴가 정책 삭제",
            description = "특정 휴가 정책을 삭제합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "휴가 정책 삭제 성공",
                    content = @Content(schema = @Schema(implementation = VacationApiDto.DeleteVacationPolicyResp.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (VACATION:MANAGE 필요)"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "휴가 정책을 찾을 수 없음"
            )
    })
    @DeleteMapping("/api/v1/vacation-policies/{id}")
    ApiResponse deleteVacationPolicy(
            @Parameter(description = "휴가 정책 ID", example = "1", required = true)
            @PathVariable("id") Long vacationPolicyId
    );

    @Operation(
            summary = "유저에게 할당된 휴가 정책 조회 (Plan 기반)",
            description = "특정 사용자에게 할당된 휴가 정책을 조회합니다. (Plan을 통해 할당된 정책)"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "할당된 휴가 정책 조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (VACATION:MANAGE 필요)"
            )
    })
    @GetMapping("/api/v1/users/{userId}/vacation-policies")
    ApiResponse getUserAssignedVacationPolicies(
            @Parameter(description = "사용자 ID", example = "user123", required = true)
            @PathVariable("userId") String userId,
            @Parameter(description = "부여 방식 필터", example = "AUTO")
            @RequestParam(value = "grantMethod", required = false) GrantMethod grantMethod
    );

    @Operation(
            summary = "유저에게 부여된 휴가 정책 조회 (필터링 옵션 포함)",
            description = "특정 사용자에게 부여된 휴가 정책을 필터링 옵션과 함께 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "휴가 정책 조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (VACATION:MANAGE 필요)"
            )
    })
    @GetMapping("/api/v1/users/{userId}/vacation-policies/assigned")
    ApiResponse getUserAssignedVacationPoliciesWithFilters(
            @Parameter(description = "사용자 ID", example = "user123", required = true)
            @PathVariable("userId") String userId,
            @Parameter(description = "휴가 유형 필터", example = "ANNUAL")
            @RequestParam(value = "vacationType", required = false) VacationType vacationType,
            @Parameter(description = "부여 방식 필터", example = "AUTO")
            @RequestParam(value = "grantMethod", required = false) GrantMethod grantMethod
    );

    @Operation(
            summary = "관리자가 특정 사용자에게 휴가를 직접 부여",
            description = "관리자가 특정 사용자에게 휴가를 수동으로 부여합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "휴가 부여 성공",
                    content = @Content(schema = @Schema(implementation = VacationApiDto.ManualGrantVacationResp.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (VACATION:GRANT 필요)"
            )
    })
    @PostMapping("/api/v1/users/{userId}/vacation-grants")
    ApiResponse manualGrantVacation(
            @Parameter(description = "사용자 ID", example = "user123", required = true)
            @PathVariable("userId") String userId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "휴가 부여 정보",
                    required = true,
                    content = @Content(schema = @Schema(implementation = VacationApiDto.ManualGrantVacationReq.class))
            )
            @Valid @RequestBody VacationApiDto.ManualGrantVacationReq data
    );

    @Operation(
            summary = "특정 휴가 부여 회수",
            description = "관리자가 직접 부여한 휴가를 취소합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "휴가 부여 회수 성공",
                    content = @Content(schema = @Schema(implementation = VacationApiDto.RevokeVacationGrantResp.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (VACATION:GRANT 필요)"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "휴가 부여 내역을 찾을 수 없음"
            )
    })
    @DeleteMapping("/api/v1/vacation-grants/{vacationGrantId}")
    ApiResponse revokeVacationGrant(
            @Parameter(description = "휴가 부여 ID", example = "1", required = true)
            @PathVariable("vacationGrantId") Long vacationGrantId
    );

    @Operation(
            summary = "휴가 신청 (ON_REQUEST 방식)",
            description = "승인이 필요한 휴가를 신청합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "휴가 신청 성공",
                    content = @Content(schema = @Schema(implementation = VacationApiDto.RequestVacationResp.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (VACATION:REQUEST 필요)"
            )
    })
    @PostMapping("/api/v1/users/{userId}/vacation-requests")
    ApiResponse requestVacation(
            @Parameter(description = "사용자 ID", example = "user123", required = true)
            @PathVariable("userId") String userId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "휴가 신청 정보",
                    required = true,
                    content = @Content(schema = @Schema(implementation = VacationApiDto.RequestVacationReq.class))
            )
            @Valid @RequestBody VacationApiDto.RequestVacationReq data,
            @Parameter(hidden = true) @LoginUser User loginUser
    );

    @Operation(
            summary = "휴가 승인",
            description = "휴가 신청을 승인합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "휴가 승인 성공",
                    content = @Content(schema = @Schema(implementation = VacationApiDto.ApproveVacationResp.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (VACATION:APPROVE 필요)"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "승인 내역을 찾을 수 없음"
            )
    })
    @PostMapping("/api/v1/vacation-approvals/{approvalId}/approve")
    ApiResponse approveVacation(
            @Parameter(description = "승인 ID", example = "1", required = true)
            @PathVariable("approvalId") Long approvalId,
            @Parameter(description = "승인자 ID", example = "admin123", required = true)
            @RequestParam("approverId") String approverId,
            @Parameter(hidden = true) @LoginUser User loginUser
    );

    @Operation(
            summary = "휴가 거부",
            description = "휴가 신청을 거부합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "휴가 거부 성공",
                    content = @Content(schema = @Schema(implementation = VacationApiDto.RejectVacationResp.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (VACATION:APPROVE 필요)"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "승인 내역을 찾을 수 없음"
            )
    })
    @PostMapping("/api/v1/vacation-approvals/{approvalId}/reject")
    ApiResponse rejectVacation(
            @Parameter(description = "승인 ID", example = "1", required = true)
            @PathVariable("approvalId") Long approvalId,
            @Parameter(description = "승인자 ID", example = "admin123", required = true)
            @RequestParam("approverId") String approverId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "거부 정보",
                    required = true,
                    content = @Content(schema = @Schema(implementation = VacationApiDto.RejectVacationReq.class))
            )
            @Valid @RequestBody VacationApiDto.RejectVacationReq data,
            @Parameter(hidden = true) @LoginUser User loginUser
    );

    @Operation(
            summary = "휴가 신청 취소",
            description = "신청한 휴가를 취소합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "휴가 신청 취소 성공",
                    content = @Content(schema = @Schema(implementation = VacationApiDto.CancelVacationRequestResp.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (VACATION:REQUEST 필요)"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "휴가 신청을 찾을 수 없음"
            )
    })
    @PostMapping("/api/v1/vacation-requests/{vacationGrantId}/cancel")
    ApiResponse cancelVacationRequest(
            @Parameter(description = "휴가 부여 ID", example = "1", required = true)
            @PathVariable("vacationGrantId") Long vacationGrantId,
            @Parameter(description = "사용자 ID", example = "user123", required = true)
            @RequestParam("userId") String userId,
            @Parameter(hidden = true) @LoginUser User loginUser
    );

    @Operation(
            summary = "승인자에게 할당된 모든 휴가 신청 내역 조회",
            description = "승인자에게 할당된 휴가 신청 내역을 조회합니다. 상태 필터 옵션을 사용할 수 있습니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "휴가 신청 내역 조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (VACATION:APPROVE 필요)"
            )
    })
    @GetMapping("/api/v1/users/{approverId}/vacation-approvals")
    ApiResponse getAllVacationsByApprover(
            @Parameter(description = "승인자 ID", example = "admin123", required = true)
            @PathVariable("approverId") String approverId,
            @Parameter(description = "조회할 연도", example = "2024", required = true)
            @RequestParam("year") Integer year,
            @Parameter(description = "부여 상태 필터", example = "PENDING")
            @RequestParam(value = "status", required = false) GrantStatus status,
            @Parameter(hidden = true) @LoginUser User loginUser
    );

    @Operation(
            summary = "특정 유저의 휴가 신청 내역 조회",
            description = "특정 사용자의 휴가 신청 내역을 조회합니다. (ON_REQUEST 방식, 모든 상태 포함)"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "휴가 신청 내역 조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (VACATION:REQUEST 필요)"
            )
    })
    @GetMapping("/api/v1/users/{userId}/vacation-requests")
    ApiResponse getUserRequestedVacations(
            @Parameter(description = "사용자 ID", example = "user123", required = true)
            @PathVariable("userId") String userId,
            @Parameter(description = "조회할 연도", example = "2024", required = true)
            @RequestParam("year") Integer year,
            @Parameter(hidden = true) @LoginUser User loginUser
    );

    @Operation(
            summary = "특정 유저의 휴가 신청 통계 조회",
            description = "특정 사용자의 휴가 신청 통계를 조회합니다. (ON_REQUEST 방식)"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "휴가 신청 통계 조회 성공",
                    content = @Content(schema = @Schema(implementation = VacationApiDto.GetUserRequestedVacationStatsResp.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (VACATION:REQUEST 필요)"
            )
    })
    @GetMapping("/api/v1/users/{userId}/vacation-requests/stats")
    ApiResponse getUserRequestedVacationStats(
            @Parameter(description = "사용자 ID", example = "user123", required = true)
            @PathVariable("userId") String userId,
            @Parameter(description = "조회할 연도", example = "2024", required = true)
            @RequestParam("year") Integer year,
            @Parameter(hidden = true) @LoginUser User loginUser
    );

    @Operation(
            summary = "유저의 휴가 정책 할당 상태 조회",
            description = "특정 사용자의 휴가 정책 할당 상태를 조회합니다. (할당된 정책 + 할당되지 않은 정책)"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "휴가 정책 할당 상태 조회 성공",
                    content = @Content(schema = @Schema(implementation = VacationApiDto.GetVacationPolicyAssignmentStatusResp.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (VACATION:MANAGE 필요)"
            )
    })
    @GetMapping("/api/v1/users/{userId}/vacation-policies/assignment-status")
    ApiResponse getVacationPolicyAssignmentStatus(
            @Parameter(description = "사용자 ID", example = "user123", required = true)
            @PathVariable("userId") String userId
    );

    // ========== 전체 유저 휴가 통계 조회 ==========

    @Operation(
            summary = "전체 유저 휴가 통계 조회",
            description = "모든 사용자의 특정 년도 휴가 통계를 조회합니다. 총 휴가, 사용 휴가, 사용 예정 휴가(승인 대기 중), 잔여 휴가를 반환합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "전체 유저 휴가 통계 조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (VACATION:MANAGE 필요)"
            )
    })
    @GetMapping("/api/v1/vacations/summary")
    ApiResponse getAllUsersVacationSummary(
            @Parameter(description = "조회할 연도", example = "2024", required = true)
            @RequestParam("year") Integer year
    );

    // ========================================
    // Vacation Plan CRUD
    // ========================================

    @Operation(
            summary = "휴가 플랜 생성",
            description = "새로운 휴가 플랜을 생성합니다. 정책 ID 목록을 함께 전달하면 정책이 포함된 플랜이 생성됩니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "플랜 생성 성공",
                    content = @Content(schema = @Schema(implementation = VacationPlanApiDto.PlanResp.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (VACATION:MANAGE 필요)"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "플랜 코드 중복"
            )
    })
    @PostMapping("/api/v1/vacation-plans")
    ApiResponse createPlan(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "플랜 생성 정보",
                    required = true,
                    content = @Content(schema = @Schema(implementation = VacationPlanApiDto.CreatePlanReq.class))
            )
            @Valid @RequestBody VacationPlanApiDto.CreatePlanReq data
    );

    @Operation(
            summary = "전체 휴가 플랜 목록 조회",
            description = "모든 휴가 플랜 목록을 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "플랜 목록 조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (VACATION:MANAGE 필요)"
            )
    })
    @GetMapping("/api/v1/vacation-plans")
    ApiResponse getAllPlans();

    @Operation(
            summary = "휴가 플랜 상세 조회",
            description = "특정 휴가 플랜의 상세 정보를 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "플랜 조회 성공",
                    content = @Content(schema = @Schema(implementation = VacationPlanApiDto.PlanResp.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (VACATION:MANAGE 필요)"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "플랜 없음"
            )
    })
    @GetMapping("/api/v1/vacation-plans/{code}")
    ApiResponse getPlan(
            @Parameter(description = "플랜 코드", example = "FULL_TIME", required = true)
            @PathVariable("code") String code
    );

    @Operation(
            summary = "휴가 플랜 수정",
            description = "휴가 플랜의 이름과 설명을 수정합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "플랜 수정 성공",
                    content = @Content(schema = @Schema(implementation = VacationPlanApiDto.PlanResp.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (VACATION:MANAGE 필요)"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "플랜 없음"
            )
    })
    @PutMapping("/api/v1/vacation-plans/{code}")
    ApiResponse updatePlan(
            @Parameter(description = "플랜 코드", example = "FULL_TIME", required = true)
            @PathVariable("code") String code,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "플랜 수정 정보",
                    required = true,
                    content = @Content(schema = @Schema(implementation = VacationPlanApiDto.UpdatePlanReq.class))
            )
            @Valid @RequestBody VacationPlanApiDto.UpdatePlanReq data
    );

    @Operation(
            summary = "휴가 플랜 삭제",
            description = "휴가 플랜을 삭제합니다 (소프트 삭제)."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "플랜 삭제 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (VACATION:MANAGE 필요)"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "플랜 없음"
            )
    })
    @DeleteMapping("/api/v1/vacation-plans/{code}")
    ApiResponse deletePlan(
            @Parameter(description = "플랜 코드", example = "FULL_TIME", required = true)
            @PathVariable("code") String code
    );

    // ========================================
    // Plan-Policy 관리
    // ========================================

    @Operation(
            summary = "플랜에 정책 추가",
            description = "휴가 플랜에 새로운 정책을 추가합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "정책 추가 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (VACATION:MANAGE 필요)"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "플랜 또는 정책 없음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "이미 포함된 정책"
            )
    })
    @PostMapping("/api/v1/vacation-plans/{code}/policies/{policyId}")
    ApiResponse addPolicyToPlan(
            @Parameter(description = "플랜 코드", example = "FULL_TIME", required = true)
            @PathVariable("code") String code,
            @Parameter(description = "정책 ID", example = "1", required = true)
            @PathVariable("policyId") Long policyId
    );

    @Operation(
            summary = "플랜에서 정책 제거",
            description = "휴가 플랜에서 정책을 제거합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "정책 제거 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (VACATION:MANAGE 필요)"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "플랜 또는 정책 없음"
            )
    })
    @DeleteMapping("/api/v1/vacation-plans/{code}/policies/{policyId}")
    ApiResponse removePolicyFromPlan(
            @Parameter(description = "플랜 코드", example = "FULL_TIME", required = true)
            @PathVariable("code") String code,
            @Parameter(description = "정책 ID", example = "1", required = true)
            @PathVariable("policyId") Long policyId
    );

    @Operation(
            summary = "플랜 정책 전체 업데이트",
            description = "휴가 플랜의 정책 목록을 전체 교체합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "정책 업데이트 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (VACATION:MANAGE 필요)"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "플랜 또는 정책 없음"
            )
    })
    @PutMapping("/api/v1/vacation-plans/{code}/policies")
    ApiResponse updatePlanPolicies(
            @Parameter(description = "플랜 코드", example = "FULL_TIME", required = true)
            @PathVariable("code") String code,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "정책 ID 목록",
                    required = true,
                    content = @Content(schema = @Schema(implementation = VacationPlanApiDto.UpdatePlanPoliciesReq.class))
            )
            @Valid @RequestBody VacationPlanApiDto.UpdatePlanPoliciesReq data
    );

    // ========================================
    // User-Plan 관리
    // ========================================

    @Operation(
            summary = "사용자에게 플랜 할당",
            description = "사용자에게 휴가 플랜을 할당합니다. 플랜의 REPEAT_GRANT 정책에 대한 스케줄이 자동 생성됩니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "플랜 할당 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (VACATION:MANAGE 필요)"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "사용자 또는 플랜 없음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "이미 할당된 플랜"
            )
    })
    @PostMapping("/api/v1/users/{userId}/vacation-plans")
    ApiResponse assignPlanToUser(
            @Parameter(description = "사용자 ID", example = "user123", required = true)
            @PathVariable("userId") String userId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "플랜 할당 정보",
                    required = true,
                    content = @Content(schema = @Schema(implementation = VacationPlanApiDto.AssignPlanReq.class))
            )
            @Valid @RequestBody VacationPlanApiDto.AssignPlanReq data
    );

    @Operation(
            summary = "사용자에게 여러 플랜 할당",
            description = "사용자에게 여러 휴가 플랜을 일괄 할당합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "플랜 할당 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (VACATION:MANAGE 필요)"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "사용자 또는 플랜 없음"
            )
    })
    @PostMapping("/api/v1/users/{userId}/vacation-plans/batch")
    ApiResponse assignPlansToUser(
            @Parameter(description = "사용자 ID", example = "user123", required = true)
            @PathVariable("userId") String userId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "플랜 목록 할당 정보",
                    required = true,
                    content = @Content(schema = @Schema(implementation = VacationPlanApiDto.AssignPlansReq.class))
            )
            @Valid @RequestBody VacationPlanApiDto.AssignPlansReq data
    );

    @Operation(
            summary = "사용자에게서 플랜 회수",
            description = "사용자에게서 휴가 플랜을 회수합니다. 다른 플랜에 없는 정책의 스케줄도 함께 삭제됩니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "플랜 회수 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (VACATION:MANAGE 필요)"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "사용자 또는 플랜 없음"
            )
    })
    @DeleteMapping("/api/v1/users/{userId}/vacation-plans/{code}")
    ApiResponse revokePlanFromUser(
            @Parameter(description = "사용자 ID", example = "user123", required = true)
            @PathVariable("userId") String userId,
            @Parameter(description = "플랜 코드", example = "FULL_TIME", required = true)
            @PathVariable("code") String code
    );

    @Operation(
            summary = "사용자의 플랜 목록 조회",
            description = "사용자에게 할당된 휴가 플랜 목록을 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "플랜 목록 조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (VACATION:MANAGE 필요)"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "사용자 없음"
            )
    })
    @GetMapping("/api/v1/users/{userId}/vacation-plans")
    ApiResponse getUserPlans(
            @Parameter(description = "사용자 ID", example = "user123", required = true)
            @PathVariable("userId") String userId
    );
}
