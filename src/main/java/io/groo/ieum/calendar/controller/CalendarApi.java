package io.groo.ieum.calendar.controller;

import io.groo.core.controller.ApiResponse;
import io.groo.core.security.LoginUser;
import io.groo.ieum.user.domain.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;

@Tag(name = "Calendar", description = "캘린더 API")
public interface CalendarApi {

    @Operation(
            summary = "기간별 이벤트 조회",
            description = "특정 기간 동안의 일정 및 휴가 이벤트를 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "이벤트 조회 성공"
            )
    })
    @GetMapping("/api/v1/calendar/period")
    ApiResponse searchEventsByPeriod(
            @Parameter(description = "시작 날짜 (yyyy-MM-dd'T'HH:mm:ss)", example = "2024-01-01T00:00:00", required = true)
            @RequestParam("startDate") @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime startDate,
            @Parameter(description = "종료 날짜 (yyyy-MM-dd'T'HH:mm:ss)", example = "2024-12-31T23:59:59", required = true)
            @RequestParam("endDate") @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime endDate,
            @Parameter(hidden = true) @LoginUser User loginUser
    );
}
