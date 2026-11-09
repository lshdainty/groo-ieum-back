package io.groo.ieum.vacation.controller.dto;

import io.groo.core.type.YNType;
import io.groo.core.validation.annotation.DateRange;
import io.groo.ieum.vacation.type.ApprovalStatus;
import io.groo.ieum.vacation.type.EffectiveType;
import io.groo.ieum.vacation.type.ExpirationType;
import io.groo.ieum.vacation.type.GrantMethod;
import io.groo.ieum.vacation.type.GrantStatus;
import io.groo.ieum.vacation.type.RepeatUnit;
import io.groo.ieum.vacation.type.VacationTimeType;
import io.groo.ieum.vacation.type.VacationType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class VacationApiDto {

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "휴가 사용 요청")
    @DateRange(startField = "startDate", endField = "endDate", message = "{error.vacation.invalid.date}")
    public static class UseVacationReq {
        @Schema(description = "사용자 ID", example = "user123")
        private String userId;

        @Schema(description = "휴가 유형", example = "ANNUAL")
        private VacationType vacationType;

        @Schema(description = "휴가 사용 설명", example = "개인 사유")
        private String vacationDesc;

        @Schema(description = "휴가 시간 유형", example = "ALL_DAY")
        private VacationTimeType vacationTimeType;

        @Schema(description = "시작 날짜", example = "2024-01-15T09:00:00")
        private LocalDateTime startDate;

        @Schema(description = "종료 날짜", example = "2024-01-15T18:00:00")
        private LocalDateTime endDate;
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "휴가 사용 응답")
    public static class UseVacationResp {
        @Schema(description = "휴가 사용 ID", example = "1")
        private Long vacationUsageId;
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "휴가 사용 수정 요청")
    @DateRange(startField = "startDate", endField = "endDate", message = "{error.vacation.invalid.date}")
    public static class UpdateVacationUsageReq {
        @Schema(description = "사용자 ID", example = "user123")
        private String userId;

        @Schema(description = "휴가 유형", example = "ANNUAL")
        private VacationType vacationType;

        @Schema(description = "휴가 사용 설명", example = "개인 사유")
        private String vacationDesc;

        @Schema(description = "휴가 시간 유형", example = "ALL_DAY")
        private VacationTimeType vacationTimeType;

        @Schema(description = "시작 날짜", example = "2024-01-15T09:00:00")
        private LocalDateTime startDate;

        @Schema(description = "종료 날짜", example = "2024-01-15T18:00:00")
        private LocalDateTime endDate;
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "휴가 사용 수정 응답")
    public static class UpdateVacationUsageResp {
        @Schema(description = "휴가 사용 ID", example = "1")
        private Long vacationUsageId;
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class GetUserVacationHistoryResp {
        private List<VacationGrantInfo> grants;  // 부여받은 내역
        private List<VacationUsageInfo> usages;  // 사용한 내역

        @Getter
        @AllArgsConstructor
        @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
        public static class VacationGrantInfo {
            private Long vacationGrantId;
            private VacationType vacationType;
            private String vacationTypeName;
            private String vacationGrantDesc;
            private BigDecimal grantTime;
            private String grantTimeStr;
            private BigDecimal remainTime;
            private String remainTimeStr;
            private LocalDateTime grantDate;
            private LocalDateTime expiryDate;
        }

        @Getter
        @AllArgsConstructor
        @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
        public static class VacationUsageInfo {
            private Long vacationUsageId;
            private String vacationUsageDesc;
            private VacationTimeType vacationTimeType;
            private String vacationTimeTypeName;
            private BigDecimal usedTime;
            private String usedTimeStr;
            private LocalDateTime startDate;
            private LocalDateTime endDate;
        }
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class GetAllUsersVacationHistoryResp {
        private String userId;
        private String userName;
        private List<VacationGrantInfo> grants;  // 부여받은 내역
        private List<VacationUsageInfo> usages;  // 사용한 내역

        @Getter
        @AllArgsConstructor
        @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
        public static class VacationGrantInfo {
            private Long vacationGrantId;
            private VacationType vacationType;
            private String vacationTypeName;
            private String vacationGrantDesc;
            private BigDecimal grantTime;
            private String grantTimeStr;
            private BigDecimal remainTime;
            private String remainTimeStr;
            private LocalDateTime grantDate;
            private LocalDateTime expiryDate;
        }

        @Getter
        @AllArgsConstructor
        @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
        public static class VacationUsageInfo {
            private Long vacationUsageId;
            private String vacationUsageDesc;
            private VacationTimeType vacationTimeType;
            private String vacationTimeTypeName;
            private BigDecimal usedTime;
            private String usedTimeStr;
            private LocalDateTime startDate;
            private LocalDateTime endDate;
        }
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class GetAvailableVacationsResp {
        private BigDecimal totalRemainTime;
        private String totalRemainTimeStr;
        private List<AvailableVacationByType> vacations;
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class AvailableVacationByType {
        private VacationType vacationType;
        private String vacationTypeName;
        private BigDecimal remainTime;
        private String remainTimeStr;
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class GetVacationUsagesByPeriodResp {
        private String userId;
        private String userName;
        private Long vacationUsageId;
        private String vacationUsageDesc;
        private VacationTimeType vacationTimeType;
        private String vacationTimeTypeName;
        private BigDecimal usedTime;
        private LocalDateTime startDate;
        private LocalDateTime endDate;
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class GetUserVacationUsagesByPeriodResp {
        private Long vacationUsageId;
        private String vacationUsageDesc;
        private VacationTimeType vacationTimeType;
        private String vacationTimeTypeName;
        private BigDecimal usedTime;
        private LocalDateTime startDate;
        private LocalDateTime endDate;
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class GetUserMonthlyVacationStatsResp {
        private Integer month;
        private BigDecimal usedTime;
        private String usedTimeStr;
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class GetUserVacationStatsResp {
        private BigDecimal remainTime;
        private String remainTimeStr;
        private BigDecimal usedTime;
        private String usedTimeStr;
        private BigDecimal expectUsedTime;
        private String expectUsedTimeStr;
        private BigDecimal prevRemainTime;
        private String prevRemainTimeStr;
        private BigDecimal prevUsedTime;
        private String prevUsedTimeStr;
        private BigDecimal prevExpectUsedTime;
        private String prevExpectUsedTimeStr;
        private BigDecimal remainTimeGap;
        private String remainTimeGapStr;
        private BigDecimal usedTimeGap;
        private String usedTimeGapStr;
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class GetVacationPolicyResp {
        private Long vacationPolicyId;
        private String vacationPolicyName;
        private String vacationPolicyDesc;
        private VacationType vacationType;
        private GrantMethod grantMethod;
        private BigDecimal grantTime;
        private String grantTimeStr;
        private YNType isFlexibleGrant;        // 가변 부여 여부 (Y: 가변, N: 고정)
        private YNType minuteGrantYn;          // 분단위 부여 여부
        private RepeatUnit repeatUnit;
        private Integer repeatInterval;
        private Integer specificMonths;
        private Integer specificDays;
        private EffectiveType effectiveType;
        private ExpirationType expirationType;
        private String repeatGrantDesc; // 반복 부여 정책의 한국어 설명
        private YNType canDeleted;      // 삭제 가능 여부 (N: 기본 정책이라 지울 수 없음)
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "휴가 정책 생성 요청")
    public static class CreateVacationPolicyReq {
        @Schema(description = "휴가 정책 이름", example = "연차")
        private String vacationPolicyName;

        @Schema(description = "휴가 정책 설명", example = "연간 부여되는 기본 연차")
        private String vacationPolicyDesc;

        @Schema(description = "휴가 유형", example = "ANNUAL")
        private VacationType vacationType;

        @Schema(description = "부여 방식", example = "AUTO")
        private GrantMethod grantMethod;

        @Schema(description = "부여 시간", example = "15.0")
        private BigDecimal grantTime;

        @Schema(description = "가변 부여 여부 (Y: 가변, N: 고정)", example = "N")
        private YNType isFlexibleGrant;

        @Schema(description = "분단위 부여 여부", example = "N")
        private YNType minuteGrantYn;

        @Schema(description = "반복 단위", example = "YEAR")
        private RepeatUnit repeatUnit;

        @Schema(description = "반복 간격", example = "1")
        private Integer repeatInterval;

        @Schema(description = "특정 월", example = "1")
        private Integer specificMonths;

        @Schema(description = "특정 일", example = "1")
        private Integer specificDays;

        @Schema(description = "첫 부여 시점 (반복 부여 방식에서 필수)", example = "2024-01-01T00:00:00")
        private LocalDateTime firstGrantDate;

        @Schema(description = "반복 여부 (Y: 반복, N: 1회)", example = "Y")
        private YNType isRecurring;

        @Schema(description = "최대 부여 횟수 (1회성 정책용)", example = "1")
        private Integer maxGrantCount;

        @Schema(description = "유효기간 발효일 타입", example = "GRANT_DATE")
        private EffectiveType effectiveType;

        @Schema(description = "유효기간 만료일 타입", example = "ONE_YEAR")
        private ExpirationType expirationType;

        @Schema(description = "승인 필요 횟수", example = "2")
        private Integer approvalRequiredCount;
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "휴가 정책 생성 응답")
    public static class CreateVacationPolicyResp {
        @Schema(description = "휴가 정책 ID", example = "1")
        private Long vacationPolicyId;
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class GetUserAssignedVacationPoliciesResp {
        private Long vacationPolicyId;
        private String vacationPolicyName;
        private String vacationPolicyDesc;
        private VacationType vacationType;
        private GrantMethod grantMethod;
        private BigDecimal grantTime;
        private String grantTimeStr;
        private YNType isFlexibleGrant;        // 가변 부여 여부 (Y: 가변, N: 고정)
        private YNType minuteGrantYn;          // 분단위 부여 여부
        private RepeatUnit repeatUnit;
        private Integer repeatInterval;
        private Integer specificMonths;
        private Integer specificDays;
        private LocalDateTime firstGrantDate;
        private YNType isRecurring;
        private Integer maxGrantCount;
        private Integer approvalRequiredCount;
        private EffectiveType effectiveType;
        private ExpirationType expirationType;
        private String repeatGrantDescription; // 반복 부여 정책의 한국어 설명
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "휴가 정책 이름 · 설명 수정 요청 (부여 규칙은 받지 않는다)")
    public static class UpdateVacationPolicyReq {
        @Schema(description = "휴가 정책 이름 (앞뒤 공백 제거, 1 ~ 50자)", example = "연차", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 50)
        private String vacationPolicyName;

        @Schema(description = "휴가 정책 설명 (0 ~ 1000자, 빈 글이면 지운다)", example = "연간 부여되는 기본 연차")
        @Size(max = 1000)
        private String vacationPolicyDesc;
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "휴가 정책 이름 · 설명 수정 응답")
    public static class UpdateVacationPolicyResp {
        @Schema(description = "휴가 정책 ID", example = "1")
        private Long vacationPolicyId;
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class DeleteVacationPolicyResp {
        private Long vacationPolicyId;
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "휴가 수동 부여 요청")
    @DateRange(startField = "grantDate", endField = "expiryDate", message = "{error.vacation.grant.invalid.date}")
    public static class ManualGrantVacationReq {
        @Schema(description = "휴가 정책 ID", example = "1")
        private Long vacationPolicyId;

        @Schema(description = "부여 시간", example = "15.0")
        private BigDecimal grantTime;

        @Schema(description = "부여 날짜", example = "2024-01-01T00:00:00")
        private LocalDateTime grantDate;

        @Schema(description = "만료 날짜", example = "2024-12-31T23:59:59")
        private LocalDateTime expiryDate;

        @Schema(description = "부여 설명", example = "특별 부여")
        private String grantDesc;
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "휴가 수동 부여 응답")
    public static class ManualGrantVacationResp {
        @Schema(description = "휴가 부여 ID", example = "1")
        private Long vacationGrantId;

        @Schema(description = "사용자 ID", example = "user123")
        private String userId;

        @Schema(description = "휴가 정책 ID", example = "1")
        private Long vacationPolicyId;

        @Schema(description = "부여 시간", example = "15.0")
        private BigDecimal grantTime;

        @Schema(description = "부여 날짜", example = "2024-01-01T00:00:00")
        private LocalDateTime grantDate;

        @Schema(description = "만료 날짜", example = "2024-12-31T23:59:59")
        private LocalDateTime expiryDate;
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class RevokeVacationGrantResp {
        private Long vacationGrantId;
        private String userId;
    }

    // ========== 휴가 신청 및 승인 관련 DTO ==========

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "휴가 신청 요청")
    @DateRange(startField = "requestStartTime", endField = "requestEndTime", message = "{error.vacation.request.invalid.date}")
    public static class RequestVacationReq {
        @Schema(description = "휴가 정책 ID", example = "1")
        private Long policyId;

        @Schema(description = "설명", example = "연차 신청")
        private String desc;

        @Schema(description = "승인자 ID 목록", example = "[\"admin1\", \"admin2\"]")
        private List<String> approverIds;

        @Schema(description = "부여 시간 (isFlexibleGrant=Y일 경우 필수)", example = "15.0")
        private BigDecimal grantTime;

        @Schema(description = "신청 시작 일시", example = "2024-01-15T00:00:00")
        private LocalDateTime requestStartTime;

        @Schema(description = "신청 종료 일시", example = "2024-01-20T00:00:00")
        private LocalDateTime requestEndTime;

        @Schema(description = "신청 상세 사유", example = "가족 행사")
        private String requestDesc;
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "휴가 신청 응답")
    public static class RequestVacationResp {
        @Schema(description = "휴가 부여 ID", example = "1")
        private Long vacationGrantId;
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class ApproveVacationResp {
        private Long approvalId;
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class RejectVacationReq {
        private String rejectionReason;
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class RejectVacationResp {
        private Long approvalId;
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class CancelVacationRequestResp {
        private Long vacationGrantId;
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class GetUserRequestedVacationsResp {
        private String applicantId;
        private String applicantName;
        private Long vacationGrantId;
        private Long policyId;
        private String policyName;
        private VacationType vacationType;
        private String vacationTypeName;
        private String desc;
        private BigDecimal grantTime;
        private String grantTimeStr;
        private BigDecimal policyGrantTime;
        private String policyGrantTimeStr;
        private BigDecimal remainTime;
        private String remainTimeStr;
        private LocalDateTime grantDate;
        private LocalDateTime expiryDate;
        private LocalDateTime requestStartTime;
        private LocalDateTime requestEndTime;
        private String requestDesc;
        private GrantStatus grantStatus;
        private String grantStatusName;
        private LocalDateTime createDate;  // 신청일
        private String currentApproverId;  // 현재 승인 대기 중인 승인자 ID
        private String currentApproverName;  // 현재 승인 대기 중인 승인자 이름
        private List<ApproverInfo> approvers;  // 승인자 목록 (순서대로 정렬됨)

        @Getter
        @AllArgsConstructor
        @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
        public static class ApproverInfo {
            private Long approvalId;
            private String approverId;
            private String approverName;
            private Integer approvalOrder;
            private ApprovalStatus approvalStatus;
            private String approvalStatusName;
            private LocalDateTime approvalDate;
            private String rejectionReason;
        }
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class GetUserRequestedVacationStatsResp {
        private Long totalRequestCount;
        private Long currentMonthRequestCount;
        private Double changeRate;
        private Long pendingCount;
        private Double averageProcessingDays;
        private Long progressCount;
        private Long approvedCount;
        private Double approvalRate;
        private Long rejectedCount;
        private Long canceledCount;
        private String acquiredVacationTimeStr;
        private BigDecimal acquiredVacationTime;
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class GetVacationPolicyAssignmentStatusResp {
        private List<VacationPolicyInfo> assignedPolicies;
        private List<VacationPolicyInfo> unassignedPolicies;

        @Getter
        @AllArgsConstructor
        @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
        public static class VacationPolicyInfo {
            private Long vacationPolicyId;
            private String vacationPolicyName;
            private String vacationPolicyDesc;
            private VacationType vacationType;
            private GrantMethod grantMethod;
            private BigDecimal grantTime;
            private String grantTimeStr;
            private YNType isFlexibleGrant;        // 가변 부여 여부 (Y: 가변, N: 고정)
            private YNType minuteGrantYn;          // 분단위 부여 여부
            private RepeatUnit repeatUnit;
            private Integer repeatInterval;
            private Integer specificMonths;
            private Integer specificDays;
            private EffectiveType effectiveType;
            private ExpirationType expirationType;
            private String repeatGrantDesc;        // 반복 부여 정책의 한국어 설명
        }
    }

    // ========== 전체 유저 휴가 통계 조회 DTO ==========

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "전체 유저 휴가 통계 조회 응답")
    public static class GetAllUsersVacationSummaryResp {
        @Schema(description = "사용자 ID", example = "user123")
        private String userId;

        @Schema(description = "사용자 이름", example = "홍길동")
        private String userName;

        @Schema(description = "부서명", example = "개발팀")
        private String departmentName;

        @Schema(description = "총 휴가 일수", example = "15.0")
        private BigDecimal totalVacationDays;

        @Schema(description = "총 휴가 일수 (문자열)", example = "15일")
        private String totalVacationDaysStr;

        @Schema(description = "사용 휴가 일수", example = "8.0")
        private BigDecimal usedVacationDays;

        @Schema(description = "사용 휴가 일수 (문자열)", example = "8일")
        private String usedVacationDaysStr;

        @Schema(description = "사용 예정 휴가 일수", example = "2.0")
        private BigDecimal scheduledVacationDays;

        @Schema(description = "사용 예정 휴가 일수 (문자열)", example = "2일")
        private String scheduledVacationDaysStr;

        @Schema(description = "잔여 휴가 일수", example = "5.0")
        private BigDecimal remainingVacationDays;

        @Schema(description = "잔여 휴가 일수 (문자열)", example = "5일")
        private String remainingVacationDaysStr;
    }
}
