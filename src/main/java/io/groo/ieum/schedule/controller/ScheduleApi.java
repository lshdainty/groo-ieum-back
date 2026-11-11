package io.groo.ieum.schedule.controller;

import io.groo.core.controller.ApiResponse;
import io.groo.ieum.schedule.controller.dto.ScheduleApiDto;
import io.groo.core.security.LoginUser;
import io.groo.ieum.user.domain.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Tag(name = "Schedule", description = "일정 관리 API")
public interface ScheduleApi {

    @Operation(
            summary = "일정 등록",
            description = "새로운 일정을 등록합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "일정 등록 성공",
                    content = @Content(schema = @Schema(implementation = ScheduleApiDto.RegistScheduleResp.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (SCHEDULE:WRITE 필요)"
            )
    })
    @PostMapping("/api/v1/schedule")
    ApiResponse registSchedule(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "일정 등록 정보",
                    required = true,
                    content = @Content(schema = @Schema(implementation = ScheduleApiDto.RegistScheduleReq.class))
            )
            @Valid @RequestBody ScheduleApiDto.RegistScheduleReq data,
            @Parameter(hidden = true) @LoginUser User loginUser,
            HttpServletRequest req
    );

    @Operation(
            summary = "일정 수정",
            description = "기존 일정을 수정합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "일정 수정 성공",
                    content = @Content(schema = @Schema(implementation = ScheduleApiDto.UpdateScheduleResp.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (SCHEDULE:WRITE 필요)"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "일정을 찾을 수 없음"
            )
    })
    @PutMapping("/api/v1/schedule/{id}")
    ApiResponse updateSchedule(
            @Parameter(description = "일정 ID", example = "1", required = true)
            @PathVariable("id") Long scheduleId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "일정 수정 정보",
                    required = true,
                    content = @Content(schema = @Schema(implementation = ScheduleApiDto.UpdateScheduleReq.class))
            )
            @Valid @RequestBody ScheduleApiDto.UpdateScheduleReq data,
            @Parameter(hidden = true) @LoginUser User loginUser
    );

    @Operation(
            summary = "일정 삭제",
            description = "기존 일정을 삭제합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "일정 삭제 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (SCHEDULE:WRITE 필요)"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "일정을 찾을 수 없음"
            )
    })
    @DeleteMapping("/api/v1/schedule/{id}")
    ApiResponse deleteSchedule(
            @Parameter(description = "일정 ID", example = "1", required = true)
            @PathVariable("id") Long scheduleId,
            @Parameter(hidden = true) @LoginUser User loginUser
    );

    @Operation(
            summary = "사용자별 일정 조회",
            description = "특정 사용자의 모든 일정을 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "일정 조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (SCHEDULE:READ 필요)"
            )
    })
    @GetMapping("/api/v1/schedules/user/{userNo}")
    ApiResponse searchSchedulesByUser(
            @Parameter(description = "사용자 ID", example = "admin", required = true)
            @PathVariable("userNo") String userId
    );

    @Operation(
            summary = "기간별 일정 조회",
            description = "특정 기간의 모든 일정을 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "일정 조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (SCHEDULE:READ 필요)"
            )
    })
    @GetMapping("/api/v1/schedules/period")
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    ApiResponse searchSchedulesByPeriod(
            @Parameter(description = "시작 일시 (yyyy-MM-dd'T'HH:mm:ss)", example = "2024-01-01T00:00:00", required = true)
            @RequestParam("startDate") LocalDateTime startDate,
            @Parameter(description = "종료 일시 (yyyy-MM-dd'T'HH:mm:ss)", example = "2024-12-31T23:59:59", required = true)
            @RequestParam("endDate") LocalDateTime endDate
    );
}
