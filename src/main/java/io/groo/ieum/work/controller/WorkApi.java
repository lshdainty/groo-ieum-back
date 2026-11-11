package io.groo.ieum.work.controller;

import io.groo.core.controller.ApiResponse;
import io.groo.core.security.LoginUser;
import io.groo.ieum.user.domain.User;
import io.groo.ieum.work.controller.dto.WorkApiDto;
import io.groo.ieum.work.repository.dto.WorkHistorySearchCondition;
import io.groo.ieum.work.type.CodeType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@Tag(name = "Work", description = "업무 관리 API")
public interface WorkApi {

    // ========== 업무 내역 관리 ==========

    @Operation(
            summary = "업무 내역 등록",
            description = "새로운 업무 내역을 등록합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "업무 내역 등록 성공",
                    content = @Content(schema = @Schema(implementation = WorkApiDto.CreateWorkHistoryResp.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (WORK:WRITE 필요)"
            )
    })
    @PostMapping("/api/v1/work-histories")
    ApiResponse createWorkHistory(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "업무 내역 등록 정보",
                    required = true,
                    content = @Content(schema = @Schema(implementation = WorkApiDto.CreateWorkHistoryReq.class))
            )
            @Valid @RequestBody WorkApiDto.CreateWorkHistoryReq data,
            @Parameter(hidden = true) @LoginUser User loginUser
    );

    @Operation(
            summary = "업무 내역 일괄 등록",
            description = "여러 업무 내역을 일괄 등록합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "업무 내역 일괄 등록 성공",
                    content = @Content(schema = @Schema(implementation = WorkApiDto.BulkCreateWorkHistoryResp.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (WORK:WRITE 필요)"
            )
    })
    @PostMapping("/api/v1/work-histories/bulk")
    ApiResponse createWorkHistories(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "업무 내역 일괄 등록 정보",
                    required = true,
                    content = @Content(schema = @Schema(implementation = WorkApiDto.BulkCreateWorkHistoryReq.class))
            )
            @Valid @RequestBody WorkApiDto.BulkCreateWorkHistoryReq data,
            @Parameter(hidden = true) @LoginUser User loginUser
    );

    @Operation(
            summary = "업무 내역 목록 조회",
            description = "검색 조건에 따라 업무 내역 목록을 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "업무 내역 조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (WORK:READ 필요)"
            )
    })
    @GetMapping("/api/v1/work-histories")
    ApiResponse findAllWorkHistories(@ModelAttribute WorkHistorySearchCondition condition);

    @Operation(
            summary = "업무 내역 단건 조회",
            description = "특정 업무 내역의 상세 정보를 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "업무 내역 조회 성공",
                    content = @Content(schema = @Schema(implementation = WorkApiDto.WorkHistoryResp.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (WORK:READ 필요)"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "업무 내역을 찾을 수 없음"
            )
    })
    @GetMapping("/api/v1/work-histories/{id}")
    ApiResponse findWorkHistory(
            @Parameter(description = "업무 내역 ID", example = "1", required = true)
            @PathVariable("id") Long id
    );

    @Operation(
            summary = "업무 내역 수정",
            description = "기존 업무 내역을 수정합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "업무 내역 수정 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (WORK:WRITE 필요) 또는 본인 기록이 아님 (WORK:MANAGE 없음)"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "업무 내역을 찾을 수 없음"
            )
    })
    @PutMapping("/api/v1/work-histories/{id}")
    ApiResponse updateWorkHistory(
            @Parameter(description = "업무 내역 ID", example = "1", required = true)
            @PathVariable("id") Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "업무 내역 수정 정보",
                    required = true,
                    content = @Content(schema = @Schema(implementation = WorkApiDto.UpdateWorkHistoryReq.class))
            )
            @Valid @RequestBody WorkApiDto.UpdateWorkHistoryReq data,
            @Parameter(hidden = true) @LoginUser User loginUser
    );

    @Operation(
            summary = "업무 내역 삭제",
            description = "기존 업무 내역을 삭제합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "업무 내역 삭제 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (WORK:WRITE 필요) 또는 본인 기록이 아님 (WORK:MANAGE 없음)"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "업무 내역을 찾을 수 없음"
            )
    })
    @DeleteMapping("/api/v1/work-histories/{id}")
    ApiResponse deleteWorkHistory(
            @Parameter(description = "업무 내역 ID", example = "1", required = true)
            @PathVariable("id") Long id,
            @Parameter(hidden = true) @LoginUser User loginUser
    );

    @Operation(
            summary = "업무 내역 엑셀 다운로드",
            description = "검색 조건에 따른 업무 내역을 엑셀 파일로 다운로드합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "엑셀 다운로드 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (WORK:READ 필요)"
            )
    })
    @GetMapping("/api/v1/work-histories/excel/download")
    void downloadWorkHistoryExcel(
            HttpServletResponse response,
            @ModelAttribute WorkHistorySearchCondition condition
    ) throws IOException;

    @Operation(
            summary = "미등록 업무 시간 엑셀 다운로드",
            description = "특정 연월의 미등록 업무 시간을 엑셀 파일로 다운로드합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "엑셀 다운로드 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (WORK:READ 필요)"
            )
    })
    @GetMapping("/api/v1/work-histories/unregistered-hours/download")
    void downloadUnregisteredWorkHistoryExcel(
            HttpServletResponse response,
            @Parameter(description = "연도", example = "2024", required = true)
            @RequestParam("year") Integer year,
            @Parameter(description = "월", example = "1", required = true)
            @RequestParam("month") Integer month
    ) throws IOException;

    // ========== 업무 코드 관리 ==========

    @Operation(
            summary = "업무 코드 생성",
            description = "새로운 업무 코드를 생성합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "업무 코드 생성 성공",
                    content = @Content(schema = @Schema(implementation = WorkApiDto.CreateWorkCodeResp.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (WORK:MANAGE 필요)"
            )
    })
    @PostMapping("/api/v1/work-codes")
    ApiResponse createWorkCode(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "업무 코드 생성 정보",
                    required = true,
                    content = @Content(schema = @Schema(implementation = WorkApiDto.CreateWorkCodeReq.class))
            )
            @Valid @RequestBody WorkApiDto.CreateWorkCodeReq data
    );

    @Operation(
            summary = "업무 코드 조회",
            description = "검색 조건에 따라 업무 코드 목록을 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "업무 코드 조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (WORK:READ 필요)"
            )
    })
    @GetMapping("/api/v1/work-codes")
    ApiResponse getWorkCodes(
            @Parameter(description = "부모 업무 코드", example = "GROUP_A")
            @RequestParam(value = "parent_work_code", required = false) String parentWorkCode,
            @Parameter(description = "부모 업무 코드 ID", example = "1")
            @RequestParam(value = "parent_work_code_id", required = false) Long parentWorkCodeId,
            @Parameter(description = "부모 코드가 null인 항목만 조회", example = "true")
            @RequestParam(value = "parent_is_null", required = false) Boolean parentIsNull,
            @Parameter(description = "코드 타입", example = "GROUP")
            @RequestParam(value = "type", required = false) CodeType type
    );

    @Operation(
            summary = "업무 코드 수정",
            description = "기존 업무 코드를 수정합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "업무 코드 수정 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (WORK:MANAGE 필요)"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "업무 코드를 찾을 수 없음"
            )
    })
    @PutMapping("/api/v1/work-codes/{id}")
    ApiResponse updateWorkCode(
            @Parameter(description = "업무 코드 ID", example = "1", required = true)
            @PathVariable("id") Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "업무 코드 수정 정보",
                    required = true,
                    content = @Content(schema = @Schema(implementation = WorkApiDto.UpdateWorkCodeReq.class))
            )
            @Valid @RequestBody WorkApiDto.UpdateWorkCodeReq data
    );

    @Operation(
            summary = "업무 코드 삭제 (Soft Delete)",
            description = "기존 업무 코드를 논리 삭제합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "업무 코드 삭제 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (WORK:MANAGE 필요)"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "업무 코드를 찾을 수 없음"
            )
    })
    @DeleteMapping("/api/v1/work-codes/{id}")
    ApiResponse deleteWorkCode(
            @Parameter(description = "업무 코드 ID", example = "1", required = true)
            @PathVariable("id") Long id
    );

    // ========== 시스템 로그 관리 ==========

    @Operation(
            summary = "시스템 체크 토글",
            description = "오늘 날짜로 시스템 체크 상태를 토글합니다. 이미 체크되어 있으면 삭제, 없으면 생성합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "시스템 체크 토글 성공",
                    content = @Content(schema = @Schema(implementation = WorkApiDto.ToggleSystemCheckResp.class))
            )
    })
    @PostMapping("/api/v1/work/system-logs")
    ApiResponse toggleSystemCheck(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "시스템 코드",
                    required = true,
                    content = @Content(schema = @Schema(implementation = WorkApiDto.ToggleSystemCheckReq.class))
            )
            @Valid @RequestBody WorkApiDto.ToggleSystemCheckReq req
    );

    @Operation(
            summary = "오늘 시스템 체크 상태 조회 (배치)",
            description = "여러 시스템의 오늘 체크 상태를 한 번에 조회합니다. 시스템 코드를 배열로 받아 각각의 상태를 반환합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "시스템 체크 상태 조회 성공",
                    content = @Content(schema = @Schema(implementation = WorkApiDto.CheckSystemStatusBatchResp.class))
            )
    })
    @GetMapping("/api/v1/work/system-logs/status")
    ApiResponse checkSystemStatus(
            @Parameter(description = "시스템 코드 목록", example = "ERP,MES,WMS", required = true)
            @RequestParam("system_codes") List<String> systemCodes
    );

    // ========== 오늘 업무 시간 확인 ==========

    @Operation(
            summary = "오늘 업무 시간 확인",
            description = "오늘 날짜 기준 로그인한 사용자의 업무 시간을 확인합니다. 8시간 이상 작성했는지 여부를 반환합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "업무 시간 확인 성공",
                    content = @Content(schema = @Schema(implementation = WorkApiDto.TodayWorkStatusResp.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 필요 (로그인되지 않음)"
            )
    })
    @GetMapping("/api/v1/work-histories/today/status")
    ApiResponse getTodayWorkStatus(
            @Parameter(hidden = true) @LoginUser User user
    );

    // ========== 미작성 업무 날짜 조회 ==========

    @Operation(
            summary = "미작성 업무 날짜 조회",
            description = "로그인한 사용자 기준으로 특정 년/월의 미작성 업무 날짜 목록을 조회합니다. 주말, 공휴일, 휴가 시간을 제외하고 8시간 미만 작성한 날짜를 반환합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "미작성 업무 날짜 조회 성공",
                    content = @Content(schema = @Schema(implementation = WorkApiDto.UnregisteredWorkDatesResp.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청 (년/월 파라미터 누락 또는 형식 오류)"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 필요 (로그인되지 않음)"
            )
    })
    @GetMapping("/api/v1/work-histories/unregistered-dates")
    ApiResponse getUnregisteredWorkDates(
            @Parameter(description = "조회할 연도", example = "2024", required = true)
            @RequestParam("year") Integer year,
            @Parameter(description = "조회할 월", example = "1", required = true)
            @RequestParam("month") Integer month,
            @Parameter(hidden = true) @LoginUser User user
    );
}
