package io.groo.ieum.vacation.service;

import io.groo.ieum.vacation.domain.VacationGrant;
import io.groo.ieum.vacation.domain.VacationPolicy;
import io.groo.ieum.vacation.domain.VacationUsage;
import io.groo.ieum.vacation.service.dto.VacationApprovalServiceDto;
import io.groo.ieum.vacation.service.dto.VacationPolicyServiceDto;
import io.groo.ieum.vacation.service.dto.VacationServiceDto;
import io.groo.ieum.vacation.type.GrantMethod;
import io.groo.ieum.vacation.type.GrantStatus;
import io.groo.ieum.vacation.type.VacationType;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 휴가 서비스 인터페이스
 */
public interface VacationService {

    /**
     * 휴가 사용
     *
     * @param data 휴가 사용 정보
     * @return 생성된 VacationUsage ID
     */
    Long useVacation(VacationServiceDto data);

    /**
     * 유저의 휴가 부여 및 사용 내역 조회
     *
     * @param userId 유저 아이디
     * @param year 조회할 년도
     * @return 부여받은 내역(VacationGrant)과 사용한 내역(VacationUsage)
     */
    VacationServiceDto getUserVacationHistory(String userId, int year);

    /**
     * 모든 유저의 휴가 부여 및 사용 내역 조회
     *
     * @return 모든 유저별 부여받은 내역(VacationGrant)과 사용한 내역(VacationUsage)
     */
    List<VacationServiceDto> getAllUsersVacationHistory();

    /**
     * 시작 날짜 기준으로 사용 가능한 휴가 조회 (VacationType별 그룹화)
     *
     * @param userId 유저 아이디
     * @param startDate 시작 날짜
     * @return VacationType별로 그룹화된 사용 가능한 휴가 내역
     */
    List<VacationServiceDto> getAvailableVacations(String userId, LocalDateTime startDate);

    /**
     * 휴가 사용 취소
     * - VacationUsage를 소프트 삭제
     * - VacationGrant의 remainTime 복구
     *
     * @param vacationUsageId 휴가 사용 내역 ID
     */
    void cancelVacationUsage(Long vacationUsageId);

    /**
     * 휴가 사용 수정
     * - 기존 휴가 사용 내역 삭제 후 새로운 휴가 사용 내역 등록
     *
     * - 고칠 기록의 주인이 로그인 유저가 아니면 canManage(VACATION:MANAGE)일 때만 허용
     *
     * @param vacationUsageId 수정할 휴가 사용 내역 ID
     * @param data 새로운 휴가 사용 정보
     * @param loginUserId 로그인한 사용자 ID
     * @param canManage VACATION:MANAGE 권한 여부
     * @return 새로 생성된 휴가 사용 내역 ID
     */
    Long updateVacationUsage(Long vacationUsageId, VacationServiceDto data, String loginUserId, boolean canManage);

    /**
     * 기간별 휴가 사용 내역 조회
     *
     * @param startDate 조회 시작일
     * @param endDate 조회 종료일
     * @return 기간 내 모든 사용자의 휴가 사용 내역
     */
    List<VacationServiceDto> getVacationUsagesByPeriod(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * 유저별 기간별 휴가 사용 내역 조회
     *
     * @param userId 유저 ID
     * @param startDate 조회 시작일
     * @param endDate 조회 종료일
     * @return 유저의 기간 내 휴가 사용 내역
     */
    List<VacationServiceDto> getUserVacationUsagesByPeriod(String userId, LocalDateTime startDate, LocalDateTime endDate);

    /**
     * 유저의 월별 휴가 사용 통계 조회
     *
     * @param userId 유저 ID
     * @param year 년도
     * @return 월별 휴가 사용 통계 (1~12월)
     */
    List<VacationServiceDto> getUserMonthlyVacationStats(String userId, String year);

    /**
     * 유저의 휴가 사용 통계 조회 (현재/이전달)
     *
     * @param userId 유저 ID
     * @param baseTime 기준 시간
     * @return 현재 및 이전달 휴가 통계
     */
    VacationServiceDto getUserVacationStats(String userId, LocalDateTime baseTime);

    /**
     * 휴가 사용 내역 검증 및 조회
     *
     * @param vacationUsageId 휴가 사용 내역 ID
     * @return VacationUsage 엔티티
     */
    VacationUsage validateAndGetVacationUsage(Long vacationUsageId);

    /**
     * 휴가 정책 생성
     *
     * @param data 휴가 정책 정보
     * @return 생성된 휴가 정책 ID
     */
    Long createVacationPolicy(VacationPolicyServiceDto data);

    /**
     * 휴가 정책 조회
     *
     * @param vacationPolicyId 휴가 정책 ID
     * @return 휴가 정책 정보
     */
    VacationPolicyServiceDto getVacationPolicy(Long vacationPolicyId);

    /**
     * 모든 휴가 정책 조회
     *
     * @return 휴가 정책 리스트
     */
    List<VacationPolicyServiceDto> getVacationPolicies();

    /**
     * 휴가 정책 검증 및 조회
     *
     * @param vacationPolicyId 휴가 정책 ID
     * @return VacationPolicy 엔티티
     */
    VacationPolicy validateAndGetVacationPolicy(Long vacationPolicyId);

    /**
     * 휴가 정책 삭제
     *
     * 휴가 정책을 소프트 삭제하고, 구성원에게 부여된 휴가 수량을 처리합니다.
     * - 보유한 휴가에만 영향을 주고, 이미 사용했던 혹은 사용 예정으로 신청해둔 휴가에는 영향을 주지 않습니다.
     *
     * @param vacationPolicyId 삭제할 휴가 정책 ID
     * @return 삭제된 휴가 정책 ID
     */
    Long deleteVacationPolicy(Long vacationPolicyId);

    /**
     * 휴가 정책 이름 · 설명 수정
     *
     * 부여 규칙은 바꾸지 않는다. 이미 받은 휴가와 스케줄은 그대로다.
     *
     * @param vacationPolicyId 휴가 정책 ID
     * @param name 새 이름 (앞뒤 공백 제거, 1 ~ 50자)
     * @param desc 새 설명 (0 ~ 1000자, 빈 글이면 null)
     * @return 수정된 휴가 정책 ID
     */
    Long updateVacationPolicy(Long vacationPolicyId, String name, String desc);

    /**
     * 유저에게 할당된 휴가 정책 조회 (Plan 기반)
     *
     * @param userId 유저 ID
     * @param grantMethod 부여 방법 필터 (Optional)
     * @return 유저에게 할당된 휴가 정책 리스트
     */
    List<VacationPolicyServiceDto> getUserAssignedVacationPolicies(String userId, GrantMethod grantMethod);

    /**
     * 관리자가 특정 사용자에게 휴가를 직접 부여
     *
     * @param userId 유저 ID
     * @param data 휴가 부여 정보 (정책 ID, 부여 시간, 부여일, 만료일, 사유)
     * @return 생성된 VacationGrant
     */
    VacationGrant manualGrantVacation(String userId, VacationServiceDto data);

    /**
     * 특정 휴가 부여 회수 (관리자가 직접 부여한 휴가를 취소)
     *
     * @param vacationGrantId 휴가 부여 ID
     * @return 회수된 VacationGrant
     */
    VacationGrant revokeVacationGrant(Long vacationGrantId);

    /**
     * 휴가 신청 (ON_REQUEST 방식)
     *
     * @param userId 신청자 ID
     * @param data 휴가 신청 정보 (정책 ID, 휴가 사유(desc), 승인자 ID 리스트)
     * @return 생성된 VacationGrant ID
     */
    Long requestVacation(String userId, VacationServiceDto data);

    /**
     * 휴가 승인 처리 (순차 승인)
     *
     * @param approvalId VacationApproval ID
     * @param approverId 승인자 ID
     * @return 처리된 VacationApproval ID
     */
    Long approveVacation(Long approvalId, String approverId);

    /**
     * 휴가 거부 처리
     *
     * @param approvalId VacationApproval ID
     * @param approverId 승인자 ID
     * @param data 거부 사유
     * @return 처리된 VacationApproval ID
     */
    Long rejectVacation(Long approvalId, String approverId, VacationApprovalServiceDto data);

    /**
     * 휴가 신청 취소 처리
     *
     * @param vacationGrantId 휴가 부여 ID
     * @param userId 신청자 ID
     * @return 취소된 VacationGrant ID
     */
    Long cancelVacationRequest(Long vacationGrantId, String userId);

    /**
     * 승인자에게 할당된 모든 휴가 신청 내역 조회 (상태 필터 옵션)
     * - 승인자가 처리해야 하는/처리한 모든 휴가 신청 내역 조회
     * - getUserRequestedVacations와 동일한 응답 형식
     *
     * @param approverId 승인자 ID
     * @param year 조회할 년도
     * @param status 휴가 부여 상태 필터 (Optional)
     * @return 승인자에게 할당된 휴가 신청 내역 리스트
     */
    List<VacationServiceDto> getAllVacationsByApprover(String approverId, Integer year, GrantStatus status);

    /**
     * 사용자 ID로 ON_REQUEST 방식의 모든 휴가 신청 내역 조회 (모든 상태 포함)
     * - 승인대기, 승인완료, 거부, 회수, 만료, 소진 등 모든 상태를 포함
     * - 신청일시 최신순으로 반환
     *
     * @param userId 사용자 ID
     * @param year 조회할 년도
     * @return ON_REQUEST 방식의 모든 휴가 신청 내역
     */
    List<VacationServiceDto> getAllRequestedVacationsByUserId(String userId, Integer year);

    /**
     * 사용자 ID로 ON_REQUEST 방식의 휴가 신청 통계 조회
     *
     * @param userId 사용자 ID
     * @param year 조회할 년도
     * @return 휴가 신청 통계 정보
     */
    VacationServiceDto getRequestedVacationStatsByUserId(String userId, Integer year);

    /**
     * 유저의 휴가 정책 할당 상태 조회
     * - 전체 휴가 정책 중 해당 유저에게 할당된 정책과 할당되지 않은 정책을 분리하여 반환
     *
     * @param userId 유저 ID
     * @return 할당된 정책 리스트와 할당되지 않은 정책 리스트
     */
    VacationServiceDto getVacationPolicyAssignmentStatus(String userId);

    /**
     * 유저에게 부여된 휴가 정책 조회 (필터링 옵션 포함)
     * - 휴가 타입(vacationType)과 부여 방식(grantMethod)으로 필터링 가능
     *
     * @param userId 유저 ID
     * @param vacationType 휴가 타입 필터 (Optional)
     * @param grantMethod 부여 방식 필터 (Optional)
     * @return 필터링된 휴가 정책 리스트
     */
    List<VacationPolicyServiceDto> getUserAssignedVacationPoliciesWithFilters(
            String userId, VacationType vacationType, GrantMethod grantMethod);

    /**
     * 전체 유저의 특정 년도 휴가 통계 조회<br>
     * 총 휴가, 사용 휴가, 사용 예정 휴가(승인 대기 중), 잔여 휴가를 계산하여 반환
     *
     * @param year 조회할 연도
     * @return 전체 유저별 휴가 통계
     */
    List<VacationServiceDto> getAllUsersVacationSummary(Integer year);
}
