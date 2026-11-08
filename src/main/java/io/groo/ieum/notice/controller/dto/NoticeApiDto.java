package io.groo.ieum.notice.controller.dto;

import io.groo.core.type.YNType;
import io.groo.core.validation.annotation.DateRange;
import io.groo.ieum.notice.type.NoticeType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class NoticeApiDto {

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "공지사항 등록 요청")
    @DateRange(startField = "startDate", endField = "endDate", message = "{error.notice.invalid.date}")
    public static class CreateNoticeReq {
        @Schema(description = "작성자 ID (무시됨 - 서버가 로그인 사용자로 정한다)", example = "admin", deprecated = true)
        private String writerId;

        @Schema(description = "공지사항 제목", example = "시스템 점검 안내", required = true)
        private String title;

        @Schema(description = "공지사항 내용", example = "2025년 1월 15일 시스템 점검이 예정되어 있습니다.", required = true)
        private String content;

        @Schema(description = "공지사항 유형", example = "GENERAL")
        private NoticeType noticeType;

        @Schema(description = "상단 고정 여부", example = "N")
        private YNType isPinned;

        @Schema(description = "노출 시작일", example = "2025-01-01")
        private LocalDate startDate;

        @Schema(description = "노출 종료일", example = "2025-12-31")
        private LocalDate endDate;
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "공지사항 등록 응답")
    public static class CreateNoticeResp {
        @Schema(description = "생성된 공지사항 ID", example = "1")
        private Long noticeId;
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "공지사항 수정 요청")
    @DateRange(startField = "startDate", endField = "endDate", message = "{error.notice.invalid.date}")
    public static class UpdateNoticeReq {
        @Schema(description = "공지사항 제목", example = "시스템 점검 안내 (수정)")
        private String title;

        @Schema(description = "공지사항 내용", example = "점검 시간이 변경되었습니다.")
        private String content;

        @Schema(description = "공지사항 유형", example = "URGENT")
        private NoticeType noticeType;

        @Schema(description = "상단 고정 여부", example = "Y")
        private YNType isPinned;

        @Schema(description = "노출 시작일", example = "2025-01-01")
        private LocalDate startDate;

        @Schema(description = "노출 종료일", example = "2025-12-31")
        private LocalDate endDate;
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "공지사항 상세 조회 응답")
    public static class SearchNoticeResp {
        @Schema(description = "공지사항 ID", example = "1")
        private Long noticeId;

        @Schema(description = "작성자 ID", example = "admin")
        private String writerId;

        @Schema(description = "작성자 이름", example = "관리자")
        private String writerName;

        @Schema(description = "공지사항 제목", example = "시스템 점검 안내")
        private String title;

        @Schema(description = "공지사항 내용", example = "2025년 1월 15일 시스템 점검이 예정되어 있습니다.")
        private String content;

        @Schema(description = "공지사항 유형", example = "GENERAL")
        private NoticeType noticeType;

        @Schema(description = "공지사항 유형 이름", example = "일반")
        private String noticeTypeName;

        @Schema(description = "상단 고정 여부", example = "N")
        private YNType isPinned;

        @Schema(description = "조회수", example = "100")
        private Long viewCount;

        @Schema(description = "노출 시작일", example = "2025-01-01")
        private LocalDate startDate;

        @Schema(description = "노출 종료일", example = "2025-12-31")
        private LocalDate endDate;

        @Schema(description = "생성일", example = "2025-01-01T09:00:00")
        private LocalDateTime createDate;

        @Schema(description = "수정일", example = "2025-01-02T10:00:00")
        private LocalDateTime modifyDate;
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "공지사항 목록 조회 응답")
    public static class SearchNoticesResp {
        @Schema(description = "공지사항 ID", example = "1")
        private Long noticeId;

        @Schema(description = "작성자 ID", example = "admin")
        private String writerId;

        @Schema(description = "작성자 이름", example = "관리자")
        private String writerName;

        @Schema(description = "공지사항 제목", example = "시스템 점검 안내")
        private String title;

        @Schema(description = "공지사항 유형", example = "GENERAL")
        private NoticeType noticeType;

        @Schema(description = "공지사항 유형 이름", example = "일반")
        private String noticeTypeName;

        @Schema(description = "상단 고정 여부", example = "N")
        private YNType isPinned;

        @Schema(description = "조회수", example = "100")
        private Long viewCount;

        @Schema(description = "노출 시작일", example = "2025-01-01")
        private LocalDate startDate;

        @Schema(description = "노출 종료일", example = "2025-12-31")
        private LocalDate endDate;

        @Schema(description = "생성일", example = "2025-01-01T09:00:00")
        private LocalDateTime createDate;
    }
}
