package io.groo.ieum.work.controller.dto;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;
import io.groo.ieum.work.type.CodeType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class WorkApiDto {

    // ========== 업무 내역 DTO ==========

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "업무 내역 등록 요청")
    public static class CreateWorkHistoryReq {
        @Schema(description = "업무 날짜", example = "2024-01-15")
        private LocalDate workDate;

        @Schema(description = "업무 담당자 ID", example = "user123")
        private String workUserId;

        @Schema(description = "업무 그룹 코드", example = "GROUP_A")
        private String workGroupCode;

        @Schema(description = "업무 파트 코드", example = "PART_A")
        private String workPartCode;

        @Schema(description = "업무 분류 코드", example = "CLASS_A")
        private String workClassCode;

        @Schema(description = "업무 시간", example = "8.0")
        private BigDecimal workHour;

        @Schema(description = "업무 내용", example = "프로젝트 개발")
        private String workContent;
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "업무 내역 등록 응답")
    public static class CreateWorkHistoryResp {
        @Schema(description = "생성된 업무 내역 ID", example = "1")
        private Long workHistoryId;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "업무 내역 일괄 등록 요청")
    public static class BulkCreateWorkHistoryReq {
        @Schema(description = "업무 내역 목록")
        @Size(max = 500)
        private List<CreateWorkHistoryReq> workHistories;
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "업무 내역 일괄 등록 응답")
    public static class BulkCreateWorkHistoryResp {
        @Schema(description = "생성된 업무 내역 ID 목록", example = "[1, 2, 3]")
        private List<Long> workHistoryIds;
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "업무 내역 수정 요청")
    public static class UpdateWorkHistoryReq {
        @Schema(description = "업무 날짜", example = "2024-01-15")
        private LocalDate workDate;

        @Schema(description = "업무 담당자 ID", example = "user123")
        private String workUserId;

        @Schema(description = "업무 그룹 코드", example = "GROUP_A")
        private String workGroupCode;

        @Schema(description = "업무 파트 코드", example = "PART_A")
        private String workPartCode;

        @Schema(description = "업무 분류 코드", example = "CLASS_A")
        private String workClassCode;

        @Schema(description = "업무 시간", example = "8.0")
        private BigDecimal workHour;

        @Schema(description = "업무 내용", example = "프로젝트 개발")
        private String workContent;
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "업무 내역 응답")
    public static class WorkHistoryResp {
        @Schema(description = "업무 내역 ID", example = "1")
        private Long workHistoryId;

        @Schema(description = "업무 날짜", example = "2024-01-15")
        private LocalDate workDate;

        @Schema(description = "업무 담당자 ID", example = "user123")
        private String workUserId;

        @Schema(description = "업무 담당자 이름", example = "홍길동")
        private String workUserName;

        @Schema(description = "업무 그룹 정보")
        private WorkCodeResp workGroup;

        @Schema(description = "업무 파트 정보")
        private WorkCodeResp workPart;

        @Schema(description = "업무 분류 정보")
        private WorkCodeResp workClass;

        @Schema(description = "업무 시간", example = "8.0")
        private BigDecimal workHour;

        @Schema(description = "업무 내용", example = "프로젝트 개발")
        private String workContent;
    }

    // ========== 업무 코드 DTO ==========

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "업무 코드 생성 요청")
    public static class CreateWorkCodeReq {
        @Schema(description = "업무 코드", example = "GROUP_A")
        private String workCode;

        @Schema(description = "업무 코드 이름", example = "그룹A")
        private String workCodeName;

        @Schema(description = "코드 타입", example = "GROUP")
        private CodeType codeType;

        @Schema(description = "부모 업무 코드 ID", example = "1")
        private Long parentWorkCodeId;

        @Schema(description = "정렬 순서", example = "1")
        private Integer orderSeq;
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "업무 코드 생성 응답")
    public static class CreateWorkCodeResp {
        @Schema(description = "생성된 업무 코드 ID", example = "1")
        private Long workCodeId;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "업무 코드 수정 요청")
    public static class UpdateWorkCodeReq {
        @Schema(description = "업무 코드", example = "GROUP_A")
        private String workCode;

        @Schema(description = "업무 코드 이름", example = "그룹A")
        private String workCodeName;

        @Schema(description = "부모 업무 코드 ID", example = "1")
        private Long parentWorkCodeId;

        @Schema(description = "정렬 순서", example = "1")
        private Integer orderSeq;
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "업무 코드 응답")
    public static class WorkCodeResp {
        @Schema(description = "업무 코드 ID", example = "1")
        private Long workCodeId;

        @Schema(description = "업무 코드", example = "GROUP_A")
        private String workCode;

        @Schema(description = "업무 코드 이름", example = "그룹A")
        private String workCodeName;

        @Schema(description = "코드 타입", example = "GROUP")
        private CodeType codeType;

        @Schema(description = "정렬 순서", example = "1")
        private Integer orderSeq;

        @Schema(description = "부모 업무 코드 ID", example = "1")
        private Long parentWorkCodeId;
    }

    // ========== 시스템 로그 DTO ==========

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "시스템 체크 토글 요청")
    public static class ToggleSystemCheckReq {
        @Schema(description = "시스템 코드", example = "ERP")
        private String systemCode;
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "시스템 체크 토글 응답")
    public static class ToggleSystemCheckResp {
        @Schema(description = "체크 여부 (true: 체크됨, false: 체크 해제됨)", example = "true")
        private boolean checked;

        @Schema(description = "메시지", example = "시스템 체크가 등록되었습니다.")
        private String message;
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "시스템 체크 상태 조회 응답 (단일)")
    public static class CheckSystemStatusResp {
        @Schema(description = "시스템 코드", example = "ERP")
        private String systemCode;

        @Schema(description = "오늘 체크 여부", example = "true")
        private boolean checked;
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "시스템 체크 상태 조회 응답 (배치)")
    public static class CheckSystemStatusBatchResp {
        @Schema(description = "시스템 체크 상태 목록")
        private List<CheckSystemStatusResp> statuses;
    }

    // ========== 오늘 업무 시간 확인 DTO ==========

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "오늘 업무 시간 상태 조회 응답")
    public static class TodayWorkStatusResp {
        @Schema(description = "작성한 총 업무 시간", example = "8.5")
        private BigDecimal totalHours;

        @Schema(description = "필요한 업무 시간 (기준: 8시간)", example = "8.0")
        private BigDecimal requiredHours;

        @Schema(description = "8시간 달성 여부", example = "true")
        private boolean isCompleted;
    }

    // ========== 미작성 업무 날짜 조회 DTO ==========

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "미작성 업무 날짜 조회 응답")
    public static class UnregisteredWorkDatesResp {
        @Schema(description = "미작성 업무 날짜 목록", example = "[\"2024-01-15\", \"2024-01-16\"]")
        private List<LocalDate> unregisteredDates;

        @Schema(description = "총 미작성 일수", example = "2")
        private int totalUnregisteredDays;
    }
}
