package io.groo.ieum.vacation.service.dto;

import io.groo.ieum.user.domain.User;
import io.groo.ieum.vacation.domain.VacationGrant;
import io.groo.ieum.vacation.domain.VacationUsage;
import io.groo.ieum.vacation.type.GrantStatus;
import io.groo.ieum.vacation.type.VacationTimeType;
import io.groo.ieum.vacation.type.VacationType;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter @Setter
@Builder
public class VacationServiceDto {
    // 휴가 아이디
    private Long id;
    // 사용자 아이디
    private String userId;
    // 휴가 사유
    private String desc;
    // 휴가 타입
    private VacationType type;
    // 휴가 잔여 시간
    private BigDecimal remainTime;
    // 휴가 발생 시간
    private LocalDateTime occurDate;
    // 휴가 소멸 시간
    private LocalDateTime expiryDate;
    // 휴가 이력 삭제 여부
    private String isDeleted;

    // 휴가를 사용한 유저 객체 정보
    private User user;

    // 휴가에 속한 히스토리 아이디 리스트
    private List<Long> historyIds;
    // 휴가에 속한 히스토리 아이디
    private Long historyId;

    // 휴가 부여 시간
    private BigDecimal grantTime;
    // 휴가 부여 시작일 (관리자 부여용)
    private LocalDateTime grantDate;
    // 휴가 정책 ID (관리자 부여용)
    private Long policyId;
    // 휴가 정책명
    private String policyName;
    // 신청일 (DB의 create_date)
    private LocalDateTime createDate;

    // 휴가 사용 시작 시간
    private LocalDateTime startDate;
    // 휴가 사용 종료 시간
    private LocalDateTime endDate;
    // 휴가 시간 타입
    private VacationTimeType timeType;

    // 월별 통계 월
    private int month;
    // 휴가 사용 시간
    private BigDecimal usedTime;

    // 휴가 사용 예정 시간
    private BigDecimal expectUsedTime;
    // 이전달 휴가 잔여 시간
    private BigDecimal prevRemainTime;
    // 이전달 휴가 사용 시간
    private BigDecimal prevUsedTime;
    // 이전달 휴가 사용 예정 시간
    private BigDecimal prevExpectUsedTime;
    // 잔여 휴가 증감 (이번달 - 이전달)
    private BigDecimal remainTimeGap;
    // 사용 휴가 증감 (이번달 - 이전달)
    private BigDecimal usedTimeGap;

    // 부여받은 휴가 내역 리스트
    private List<VacationGrant> grants;
    // 사용한 휴가 내역 리스트
    private List<VacationUsage> usages;
    // 사용 내역 id -> 휴가 종류 (첫 차감 부여의 종류, 차감이 없으면 키 없음)
    private Map<Long, VacationType> usageVacationTypes;

    // 휴가 신청 사유 (ON_REQUEST 방식)
    private String requestReason;
    // 승인자 ID 리스트 (ON_REQUEST 방식)
    private List<String> approverIds;
    // 휴가 부여 상태
    private GrantStatus grantStatus;
    // 휴가 신청 시작 일시 (OT 시작 시간, 결혼/출산 일자 등)
    private LocalDateTime requestStartTime;
    // 휴가 신청 종료 일시 (OT 종료 시간, OT가 아니면 null)
    private LocalDateTime requestEndTime;
    // 휴가 신청 상세 사유 (ON_REQUEST 방식)
    private String requestDesc;
    // 현재 승인 대기 중인 승인자 ID
    private String currentApproverId;
    // 현재 승인 대기 중인 승인자 이름
    private String currentApproverName;
    // 승인자 목록 (순서대로 정렬됨)
    private List<VacationApprovalServiceDto> approvers;
    // 휴가 정책 기준 부여 시간
    private BigDecimal policyGrantTime;

    // ========== 휴가 신청 통계 관련 필드 ==========
    // 1. 전체 신청 건수
    private Long totalRequestCount;
    // 2. 이번 달 신청 건수
    private Long currentMonthRequestCount;
    // 3. 증감 비율 (전월 대비 이번달)
    private Double changeRate;
    // 4. 대기 건수
    private Long pendingCount;
    // 5. 평균 처리 기간 (일수)
    private Double averageProcessingDays;
    // 6. 진행 중 건수
    private Long progressCount;
    // 7. 승인 건수
    private Long approvedCount;
    // 8. 승인율 (%)
    private Double approvalRate;
    // 9. 반려 건수
    private Long rejectedCount;
    // 10. 취소 건수
    private Long canceledCount;
    // 11. 획득 휴가 일수 (문자열 형태: "1일 2시간")
    private String acquiredVacationTimeStr;
    // 12. 획득 휴가 시간 (BigDecimal)
    private BigDecimal acquiredVacationTime;
    // 13. 조회된 grants 리스트
    private List<VacationGrant> grantsList;

    // ========== 휴가 정책 할당 상태 관련 필드 ==========
    // 할당된 휴가 정책 리스트
    private List<VacationPolicyServiceDto> assignedPolicies;
    // 할당되지 않은 휴가 정책 리스트
    private List<VacationPolicyServiceDto> unassignedPolicies;

    // ========== 전체 유저 휴가 통계 관련 필드 ==========
    // 부서명
    private String departmentName;
    // 총 휴가 일수 (부여받은 전체 휴가)
    private BigDecimal totalVacationDays;
    // 사용 휴가 일수
    private BigDecimal usedVacationDays;
    // 사용 예정 휴가 일수 (승인 대기 중인 휴가)
    private BigDecimal scheduledVacationDays;
    // 잔여 휴가 일수
    private BigDecimal remainingVacationDays;

    @Override
    public String toString() {
        return "VacationServiceDto{" +
                "id: " + id +
                ", desc: '" + desc + '\'' +
                ", type: " + type +
                ", remainTime: " + remainTime +
                ", occurDate: " + occurDate +
                ", expiryDate: " + expiryDate +
                ", historyIds: " + historyIds +
                ", grantTime: " + grantTime +
                ", startDate: " + startDate +
                ", endDate: " + endDate +
                ", timeType: " + timeType +
                ", isDeleted: '" + isDeleted + '\'' +
                ", grants: " + grants +
                ", usages: " + usages +
                '}';
    }
}
