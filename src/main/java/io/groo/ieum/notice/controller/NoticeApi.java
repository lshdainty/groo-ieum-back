package io.groo.ieum.notice.controller;

import io.groo.core.controller.ApiResponse;
import io.groo.ieum.notice.controller.dto.NoticeApiDto;
import io.groo.ieum.notice.type.NoticeType;
import io.groo.core.security.LoginUser;
import io.groo.ieum.user.domain.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Notice", description = "공지사항 관리 API")
public interface NoticeApi {

    @Operation(
            summary = "공지사항 등록",
            description = "새로운 공지사항을 등록합니다. 관리자 권한이 필요합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "공지사항 등록 성공",
                    content = @Content(schema = @Schema(implementation = NoticeApiDto.CreateNoticeResp.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (NOTICE:MANAGE 필요)"
            )
    })
    @PostMapping("/api/v1/notice")
    ApiResponse createNotice(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "공지사항 등록 정보",
                    required = true,
                    content = @Content(schema = @Schema(implementation = NoticeApiDto.CreateNoticeReq.class))
            )
            @Valid @RequestBody NoticeApiDto.CreateNoticeReq data,
            @Parameter(hidden = true) @LoginUser User loginUser
    );

    @Operation(
            summary = "공지사항 상세 조회",
            description = "공지사항 상세 정보를 조회하고 조회수를 증가시킵니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "공지사항 조회 성공",
                    content = @Content(schema = @Schema(implementation = NoticeApiDto.SearchNoticeResp.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (NOTICE:READ 필요)"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "공지사항을 찾을 수 없음"
            )
    })
    @GetMapping("/api/v1/notice/{id}")
    ApiResponse searchNotice(
            @Parameter(description = "공지사항 ID", example = "1", required = true)
            @PathVariable("id") Long noticeId
    );

    @Operation(
            summary = "공지사항 목록 조회",
            description = "공지사항 목록을 페이지네이션하여 조회합니다. 고정 공지가 먼저 표시됩니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "공지사항 목록 조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (NOTICE:READ 필요)"
            )
    })
    @GetMapping("/api/v1/notices")
    ApiResponse searchNotices(
            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기", example = "10")
            @RequestParam(defaultValue = "10") int size
    );

    @Operation(
            summary = "공지사항 유형별 조회",
            description = "공지사항을 유형별로 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "공지사항 유형별 조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (NOTICE:READ 필요)"
            )
    })
    @GetMapping("/api/v1/notices/type/{type}")
    ApiResponse searchNoticesByType(
            @Parameter(description = "공지사항 유형", example = "GENERAL", required = true)
            @PathVariable("type") NoticeType type,
            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기", example = "10")
            @RequestParam(defaultValue = "10") int size
    );

    @Operation(
            summary = "공지사항 키워드 검색",
            description = "제목에 키워드를 포함하는 공지사항을 검색합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "공지사항 검색 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (NOTICE:READ 필요)"
            )
    })
    @GetMapping("/api/v1/notices/search")
    ApiResponse searchNoticesByKeyword(
            @Parameter(description = "검색 키워드", example = "점검", required = true)
            @RequestParam String keyword,
            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기", example = "10")
            @RequestParam(defaultValue = "10") int size
    );

    @Operation(
            summary = "활성 공지사항 조회",
            description = "현재 노출 기간 내의 공지사항만 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "활성 공지사항 조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (NOTICE:READ 필요)"
            )
    })
    @GetMapping("/api/v1/notices/active")
    ApiResponse searchActiveNotices(
            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기", example = "10")
            @RequestParam(defaultValue = "10") int size
    );

    @Operation(
            summary = "고정 공지사항 조회",
            description = "상단에 고정된 공지사항만 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "고정 공지사항 조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (NOTICE:READ 필요)"
            )
    })
    @GetMapping("/api/v1/notices/pinned")
    ApiResponse searchPinnedNotices(
            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기", example = "10")
            @RequestParam(defaultValue = "10") int size
    );

    @Operation(
            summary = "공지사항 수정",
            description = "기존 공지사항을 수정합니다. 관리자 권한이 필요합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "공지사항 수정 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (NOTICE:MANAGE 필요)"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "공지사항을 찾을 수 없음"
            )
    })
    @PutMapping("/api/v1/notice/{id}")
    ApiResponse updateNotice(
            @Parameter(description = "공지사항 ID", example = "1", required = true)
            @PathVariable("id") Long noticeId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "공지사항 수정 정보",
                    required = true,
                    content = @Content(schema = @Schema(implementation = NoticeApiDto.UpdateNoticeReq.class))
            )
            @Valid @RequestBody NoticeApiDto.UpdateNoticeReq data
    );

    @Operation(
            summary = "공지사항 삭제",
            description = "공지사항을 삭제합니다. 관리자 권한이 필요합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "공지사항 삭제 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 (NOTICE:MANAGE 필요)"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "공지사항을 찾을 수 없음"
            )
    })
    @DeleteMapping("/api/v1/notice/{id}")
    ApiResponse deleteNotice(
            @Parameter(description = "공지사항 ID", example = "1", required = true)
            @PathVariable("id") Long noticeId
    );
}
