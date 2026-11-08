package io.groo.ieum.vacation.service;

import io.groo.core.exception.BusinessRuleViolationException;
import io.groo.core.exception.EntityNotFoundException;
import io.groo.core.exception.ForbiddenException;
import io.groo.core.exception.ErrorCode;
import io.groo.core.exception.InvalidValueException;
import io.groo.ieum.common.exception.HrErrorCode;
import io.groo.ieum.common.time.CompanyClock;
import io.groo.core.util.TimeUtils;
import io.groo.core.type.CountryCode;
import io.groo.core.type.YNType;
import io.groo.ieum.department.domain.Department;
import io.groo.ieum.department.repository.DepartmentRepository;
import io.groo.ieum.holiday.repository.HolidayRepository;
import io.groo.ieum.holiday.type.HolidayType;
import io.groo.ieum.user.domain.User;
import io.groo.ieum.user.service.UserService;
import io.groo.ieum.vacation.domain.UserVacationPlan;
import io.groo.ieum.vacation.domain.VacationApproval;
import io.groo.ieum.vacation.domain.VacationGrant;
import io.groo.ieum.vacation.domain.VacationPolicy;
import io.groo.ieum.vacation.domain.VacationUsage;
import io.groo.ieum.vacation.domain.VacationUsageDeduction;
import io.groo.ieum.vacation.repository.UserVacationPlanRepository;
import io.groo.ieum.vacation.repository.VacationApprovalRepository;
import io.groo.ieum.vacation.repository.VacationGrantRepository;
import io.groo.ieum.vacation.repository.VacationPolicyRepository;
import io.groo.ieum.vacation.repository.VacationUsageDeductionRepository;
import io.groo.ieum.vacation.repository.VacationUsageRepository;
import io.groo.ieum.vacation.service.dto.VacationApprovalServiceDto;
import io.groo.ieum.vacation.service.dto.VacationPolicyServiceDto;
import io.groo.ieum.vacation.service.dto.VacationServiceDto;
import io.groo.ieum.vacation.service.policy.OnRequest;
import io.groo.ieum.vacation.service.policy.VacationPolicyStrategy;
import io.groo.ieum.vacation.service.policy.description.RepeatGrantDescriptionFactory;
import io.groo.ieum.vacation.service.policy.factory.VacationPolicyStrategyFactory;
import io.groo.ieum.vacation.type.ApprovalStatus;
import io.groo.ieum.vacation.type.GrantMethod;
import io.groo.ieum.vacation.type.GrantStatus;
import io.groo.ieum.vacation.type.VacationTimeType;
import io.groo.ieum.vacation.type.VacationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class VacationServiceImpl implements VacationService {
    private final VacationPolicyRepository vacationPolicyRepository;
    private final CompanyClock companyClock;
    private final UserVacationPlanRepository userVacationPlanRepository;
    private final HolidayRepository holidayRepository;
    private final UserService userService;
    private final VacationPolicyStrategyFactory vacationPolicyStrategyFactory;
    private final VacationGrantRepository vacationGrantRepository;
    private final VacationUsageRepository vacationUsageRepository;
    private final VacationUsageDeductionRepository vacationUsageDeductionRepository;
    private final VacationApprovalRepository vacationApprovalRepository;
    private final DepartmentRepository departmentRepository;
    private final RepeatGrantDescriptionFactory repeatGrantDescriptionFactory;
    private final VacationTimeFormatter vacationTimeFormatter;

    @Transactional
    @Override
    public Long useVacation(VacationServiceDto data) {
        log.debug("휴가 사용 신청 시작: userId={}, type={}, timeType={}", data.getUserId(), data.getType(), data.getTimeType());
        // 1. 사용자 검증
        User user = userService.checkUserExist(data.getUserId());

        // 2. 날짜 범위 검증은 @DateRange 어노테이션으로 Controller 레벨에서 처리

        // 3. 연차가 아닌 시간단위 휴가인 경우 유연근무제 시간 체크
        if (!(data.getTimeType().equals(VacationTimeType.DAYOFF) || data.getTimeType().equals(VacationTimeType.DEFENSE))) {
            if (!user.isBetweenWorkTime(data.getStartDate().toLocalTime(), data.getEndDate().toLocalTime())) {
                log.warn("휴가 사용 실패 - 유연근무 시간 범위 초과: userId={}, startTime={}, endTime={}", data.getUserId(), data.getStartDate().toLocalTime(), data.getEndDate().toLocalTime());
                throw new InvalidValueException(HrErrorCode.WORK_INVALID_TIME);
            }
        }

        // 4. 분단위 사용 가능 여부 검증 (시간 계산 전에 빠르게 검증)
        if (data.getTimeType().isMinuteBasedType()) {
            List<VacationPolicy> policies = vacationPolicyRepository.findByVacationType(data.getType());
            boolean isMinuteUsageAllowed = policies.stream()
                    .anyMatch(policy -> YNType.isY(policy.getMinuteGrantYn()));

            if (!isMinuteUsageAllowed) {
                log.warn("휴가 사용 실패 - 분단위 사용 불가: userId={}, vacationType={}, timeType={}",
                        data.getUserId(), data.getType(), data.getTimeType());
                throw new BusinessRuleViolationException(HrErrorCode.VACATION_MINUTE_USAGE_NOT_ALLOWED);
            }
        }

        // 5. 주말 리스트 조회
        List<LocalDate> weekDays = TimeUtils.filterByDayOfWeek(data.getStartDate(), data.getEndDate(), new int[]{6, 7});

        // 6. 공휴일 리스트 조회 (사용자의 국가 코드 기반)
        CountryCode countryCode = user.getCountryCode();
        List<LocalDate> holidays = holidayRepository.findHolidaysByStartEndDateWithType(
                data.getStartDate().toLocalDate(),
                data.getEndDate().toLocalDate(),
                HolidayType.PUBLIC,
                countryCode
        ).stream()
                .map(h -> h.getDate())
                .toList();

        weekDays = TimeUtils.mergeDates(weekDays, holidays);

        // 7. 두 날짜 간 모든 날짜 가져오기
        List<LocalDate> betweenDates = TimeUtils.getDateRange(data.getStartDate(), data.getEndDate());
        log.info("betweenDates : {}, weekDays : {}", betweenDates, weekDays);

        // 8. 사용자가 캘린더에서 선택한 날짜 중 휴일, 공휴일 제거
        betweenDates = TimeUtils.excludeDates(betweenDates, weekDays);
        log.info("remainDays : {}", betweenDates);

        // 9. 등록하려는 총 사용시간 계산
        BigDecimal totalUseTime = new BigDecimal("0.0000").add(data.getTimeType().convertToValue(betweenDates.size()));

        // 10. 사용 가능한 VacationGrant 조회 (FIFO: VacationType 일치 + 휴가 시작일이 유효기간 내 + 만료일 가까운 순)
        List<VacationGrant> availableGrants = vacationGrantRepository.findAvailableGrantsByUserIdAndTypeAndDate(
                data.getUserId(),
                data.getType(),
                data.getStartDate()  // 사용자가 사용하려는 휴가 시작일
        );

        // 11. 총 잔여 시간 계산 및 검증
        BigDecimal totalRemainTime = availableGrants.stream()
                .map(VacationGrant::getRemainTime)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalRemainTime.compareTo(totalUseTime) < 0) {
            log.warn("휴가 사용 실패 - 잔여 시간 부족: userId={}, remainTime={}, requestTime={}", data.getUserId(), totalRemainTime, totalUseTime);
            throw new BusinessRuleViolationException(HrErrorCode.VACATION_INSUFFICIENT_BALANCE);
        }

        // 12. 통합 기간 휴가 사용 내역 생성
        VacationUsage usage = VacationUsage.createVacationUsage(
                user,
                data.getDesc(),
                data.getTimeType(),
                data.getStartDate(),
                data.getEndDate(),
                totalUseTime
        );

        // 13. FIFO로 VacationGrant에서 차감
        List<VacationUsageDeduction> deductionsToSave = new ArrayList<>();
        BigDecimal remainingNeedTime = totalUseTime;

        for (VacationGrant grant : availableGrants) {
            if (remainingNeedTime.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }

            // 이 grant에서 차감 가능한 시간
            BigDecimal deductibleTime = grant.getRemainTime().min(remainingNeedTime);

            if (deductibleTime.compareTo(BigDecimal.ZERO) > 0) {
                // VacationUsageDeduction 생성
                VacationUsageDeduction deduction = VacationUsageDeduction.createVacationUsageDeduction(
                        usage,
                        grant,
                        deductibleTime
                );
                deductionsToSave.add(deduction);

                // VacationGrant의 remainTime 차감
                grant.deduct(deductibleTime);

                remainingNeedTime = remainingNeedTime.subtract(deductibleTime);
            }
        }

        // 차감이 완료되지 않았다면 예외 (이론적으로는 발생하지 않아야 함)
        if (remainingNeedTime.compareTo(BigDecimal.ZERO) > 0) {
            log.error("휴가 차감 로직 오류 - 차감 미완료: userId={}, remainingNeedTime={}", data.getUserId(), remainingNeedTime);
            throw new BusinessRuleViolationException(HrErrorCode.VACATION_INSUFFICIENT_BALANCE);
        }

        // 14. 저장
        vacationUsageRepository.save(usage);
        vacationUsageDeductionRepository.saveAll(deductionsToSave);

        log.info("휴가 사용 완료 - User: {}, Period: {} ~ {}, WorkingDays: {}, TotalUseTime: {}",
                user.getId(), data.getStartDate(), data.getEndDate(), betweenDates.size(), totalUseTime);

        return usage.getRowId();
    }

    @Override
    public VacationServiceDto getUserVacationHistory(String userId, int year) {
        log.debug("유저 휴가 내역 조회: userId={}, year={}", userId, year);
        // 유저 존재 확인
        userService.checkUserExist(userId);

        // 부여받은 내역 조회 (년도 필터링)
        List<VacationGrant> grants = vacationGrantRepository.findByUserIdAndYear(userId, year);

        // 사용한 내역 조회 (년도 필터링)
        List<VacationUsage> usages = vacationUsageRepository.findByUserIdAndYear(userId, year);

        return VacationServiceDto.builder()
                .grants(grants)
                .usages(usages)
                .build();
    }

    @Override
    public List<VacationServiceDto> getAllUsersVacationHistory() {
        log.debug("전체 유저 휴가 내역 조회 시작");
        // 모든 부여 내역 조회
        List<VacationGrant> allGrants = vacationGrantRepository.findAllWithUser();

        // 모든 사용 내역 조회
        List<VacationUsage> allUsages = vacationUsageRepository.findAllWithUser();

        // User별로 Grant 그룹핑
        Map<String, List<VacationGrant>> grantsByUser = allGrants.stream()
                .collect(Collectors.groupingBy(g -> g.getUser().getId()));

        // User별로 Usage 그룹핑
        Map<String, List<VacationUsage>> usagesByUser = allUsages.stream()
                .collect(Collectors.groupingBy(u -> u.getUser().getId()));

        // 모든 userId 수집
        Set<String> allUserIds = new HashSet<>();
        allUserIds.addAll(grantsByUser.keySet());
        allUserIds.addAll(usagesByUser.keySet());

        // User별로 VacationServiceDto 생성
        return allUserIds.stream()
                .map(userId -> {
                    List<VacationGrant> grants = grantsByUser.getOrDefault(userId, new ArrayList<>());
                    List<VacationUsage> usages = usagesByUser.getOrDefault(userId, new ArrayList<>());

                    // User 객체는 grants나 usages에서 가져오기
                    User user = null;
                    if (!grants.isEmpty()) {
                        user = grants.get(0).getUser();
                    } else if (!usages.isEmpty()) {
                        user = usages.get(0).getUser();
                    }

                    return VacationServiceDto.builder()
                            .userId(userId)
                            .user(user)
                            .grants(grants)
                            .usages(usages)
                            .build();
                })
                .toList();
    }

    @Override
    public List<VacationServiceDto> getAvailableVacations(String userId, LocalDateTime startDate) {
        // 유저 조회
        userService.checkUserExist(userId);

        // 시작 날짜를 기준으로 사용 가능한 휴가 부여 내역 조회
        List<VacationGrant> availableGrants = vacationGrantRepository.findAvailableGrantsByUserIdAndDate(userId, startDate);

        // VacationType별로 그룹화하고 remainTime 합산
        Map<VacationType, BigDecimal> remainTimeByType = availableGrants.stream()
                .collect(Collectors.groupingBy(
                        VacationGrant::getType,
                        Collectors.reducing(
                                BigDecimal.ZERO,
                                VacationGrant::getRemainTime,
                                BigDecimal::add
                        )
                ));

        // VacationServiceDto 리스트로 변환
        return remainTimeByType.entrySet().stream()
                .map(entry -> VacationServiceDto.builder()
                        .type(entry.getKey())
                        .remainTime(entry.getValue())
                        .build())
                .toList();
    }

    @Transactional
    @Override
    public void cancelVacationUsage(Long vacationUsageId) {
        log.debug("휴가 사용 취소 시작: vacationUsageId={}", vacationUsageId);
        // 1. VacationUsage 조회
        VacationUsage usage = vacationUsageRepository.findById(vacationUsageId)
                .orElseThrow(() -> {
                    log.warn("휴가 사용 취소 실패 - 휴가 사용 내역 없음: vacationUsageId={}", vacationUsageId);
                    return new EntityNotFoundException(HrErrorCode.VACATION_NOT_FOUND);
                });

        // 2. 이미 삭제된 경우 예외 처리
        if (YNType.isY(usage.getIsDeleted())) {
            log.warn("휴가 사용 취소 실패 - 이미 삭제됨: vacationUsageId={}", vacationUsageId);
            throw new BusinessRuleViolationException(HrErrorCode.VACATION_ALREADY_APPROVED);
        }

        // 3. 삭제 가능 시점 체크 (현재 시간이 사용 시작일 이전인지 확인)
        if (TimeUtils.isAfter(companyClock.now(), usage.getStartDate())) {
            log.warn("휴가 사용 취소 실패 - 시작일 이후 취소 불가: vacationUsageId={}, startDate={}", vacationUsageId, usage.getStartDate());
            throw new BusinessRuleViolationException(HrErrorCode.VACATION_CANNOT_CANCEL);
        }

        // 4. VacationUsageDeduction 조회 (차감 내역들)
        List<VacationUsageDeduction> deductions = vacationUsageDeductionRepository.findByUsageId(vacationUsageId);

        // 5. 각 차감 내역에서 차감했던 시간을 VacationGrant에 복구
        for (VacationUsageDeduction deduction : deductions) {
            VacationGrant grant = deduction.getGrant();
            grant.restore(deduction.getDeductedTime());
            log.info("VacationGrant {} 복구: {} 추가", grant.getRowId(), deduction.getDeductedTime());
        }

        // 6. VacationUsage 소프트 삭제
        usage.deleteVacationUsage();

        log.info("휴가 사용 내역 삭제 완료 - VacationUsage ID: {}, 복구된 차감 내역 수: {}", vacationUsageId, deductions.size());
    }

    @Transactional
    @Override
    public Long updateVacationUsage(Long vacationUsageId, VacationServiceDto data, String loginUserId, boolean canManage) {
        // 0. 고칠 기록의 주인 확인 (지우기 API 와 같은 규칙 · 같은 오류 코드)
        VacationUsage target = validateAndGetVacationUsage(vacationUsageId);
        String ownerId = target.getUser().getId();
        if (!canManage && !ownerId.equals(loginUserId)) {
            log.warn("휴가 사용 수정 접근 거부 - 로그인 유저: {}, 대상 유저: {}", loginUserId, ownerId);
            throw new ForbiddenException(HrErrorCode.VACATION_ACCESS_DENIED);
        }

        // 1. 기존 휴가 사용 내역 삭제
        cancelVacationUsage(vacationUsageId);

        // 2. 새로운 휴가 사용 내역 등록
        Long newVacationUsageId = useVacation(data);

        log.info("휴가 사용 내역 수정 완료 - 기존 ID: {}, 새로운 ID: {}", vacationUsageId, newVacationUsageId);

        return newVacationUsageId;
    }

    @Override
    public List<VacationServiceDto> getVacationUsagesByPeriod(LocalDateTime startDate, LocalDateTime endDate) {
        // 1. 기간에 맞는 휴가 사용 내역 조회 (startDate 기준)
        List<VacationUsage> usages = vacationUsageRepository.findByPeriodWithUser(startDate, endDate);

        if (usages.isEmpty()) {
            return List.of();
        }

        // 2. 모든 usage ID 추출
        List<Long> usageIds = usages.stream()
                .map(VacationUsage::getRowId)
                .toList();

        // 3. IN 쿼리로 한번에 모든 deduction 조회
        List<VacationUsageDeduction> allDeductions = vacationUsageDeductionRepository.findByUsageIds(usageIds);

        // 4. usageId -> VacationType 매핑 (첫 번째 grant의 type)
        Map<Long, VacationType> vacationTypeMap = allDeductions.stream()
                .collect(Collectors.toMap(
                        d -> d.getUsage().getRowId(),
                        d -> d.getGrant().getType(),
                        (existing, replacement) -> existing
                ));

        // 5. VacationServiceDto로 변환
        return usages.stream()
                .map(usage -> VacationServiceDto.builder()
                        .id(usage.getRowId())
                        .user(usage.getUser())
                        .desc(usage.getDesc())
                        .timeType(usage.getType())
                        .startDate(usage.getStartDate())
                        .endDate(usage.getEndDate())
                        .usedTime(usage.getUsedTime())
                        .type(vacationTypeMap.get(usage.getRowId()))
                        .build())
                .toList();
    }

    @Override
    public List<VacationServiceDto> getUserVacationUsagesByPeriod(String userId, LocalDateTime startDate, LocalDateTime endDate) {
        // 유저 존재 확인
        userService.checkUserExist(userId);

        // 유저의 기간에 맞는 휴가 사용 내역 조회 (startDate 기준)
        List<VacationUsage> usages = vacationUsageRepository.findByUserIdAndPeriodWithUser(userId, startDate, endDate);

        // VacationServiceDto로 변환
        return usages.stream()
                .map(usage -> VacationServiceDto.builder()
                        .id(usage.getRowId())
                        .desc(usage.getDesc())
                        .timeType(usage.getType())
                        .startDate(usage.getStartDate())
                        .endDate(usage.getEndDate())
                        .usedTime(usage.getUsedTime())
                        .build())
                .toList();
    }

    @Override
    public List<VacationServiceDto> getUserMonthlyVacationStats(String userId, String year) {
        // 유저 존재 확인
        userService.checkUserExist(userId);

        // 해당 년도의 1월 1일 ~ 12월 31일 사이의 휴가 사용 내역 조회
        LocalDateTime startDate = LocalDateTime.of(Integer.parseInt(year), 1, 1, 0, 0, 0);
        LocalDateTime endDate = LocalDateTime.of(Integer.parseInt(year), 12, 31, 23, 59, 59);

        List<VacationUsage> usages = vacationUsageRepository.findByUserIdAndPeriodWithUser(userId, startDate, endDate);

        // 월별 사용량 Map 생성 및 0 초기화 (순서 보장위해 LinkedHashMap 사용)
        Map<Integer, BigDecimal> monthlyMap = new LinkedHashMap<>();
        for (int i = 1; i <= 12; i++) {
            monthlyMap.put(i, BigDecimal.ZERO);
        }

        // 월별 사용량 집계 (startDate의 월 기준)
        for (VacationUsage usage : usages) {
            int month = usage.getStartDate().getMonthValue();
            monthlyMap.merge(month, usage.getUsedTime(), BigDecimal::add);
        }

        return monthlyMap.entrySet().stream()
                .map(e -> VacationServiceDto.builder()
                            .month(e.getKey())
                            .usedTime(e.getValue())
                            .build()
                )
                .toList();
    }

    @Override
    public VacationServiceDto getUserVacationStats(String userId, LocalDateTime baseTime) {
        // 유저 존재 확인
        userService.checkUserExist(userId);

        // 1. baseTime 기준 유효한 부여 휴가 조회
        List<VacationGrant> validGrants = vacationGrantRepository.findValidGrantsByUserIdAndBaseTime(userId, baseTime);
        List<Long> grantIds = validGrants.stream().map(VacationGrant::getRowId).toList();

        // 2. 해당 부여 휴가에 연결된 사용 내역 조회 (VacationUsageDeduction 통해)
        List<VacationUsageDeduction> deductions = vacationUsageDeductionRepository.findByGrantIds(grantIds);
        List<VacationUsage> allUsages = deductions.stream()
                .map(VacationUsageDeduction::getUsage)
                .distinct()
                .toList();

        // 3. 현재 통계 계산
        VacationServiceDto curStats = calculateStatsForBaseTime(validGrants, allUsages, baseTime);

        // 4. 이전 달 통계 계산 (이전 달의 마지막 날 23:59:59를 기준으로 계산)
        LocalDateTime prevMonthLastDay = baseTime.minusMonths(1)
                .with(TemporalAdjusters.lastDayOfMonth())
                .withHour(23)
                .withMinute(59)
                .withSecond(59);

        // 이전 달 기준 유효한 부여 휴가 조회
        List<VacationGrant> prevValidGrants = vacationGrantRepository.findValidGrantsByUserIdAndBaseTime(userId, prevMonthLastDay);
        List<Long> prevGrantIds = prevValidGrants.stream().map(VacationGrant::getRowId).toList();

        // 이전 달 부여 휴가에 연결된 사용 내역 조회
        List<VacationUsageDeduction> prevDeductions = vacationUsageDeductionRepository.findByGrantIds(prevGrantIds);
        List<VacationUsage> prevAllUsages = prevDeductions.stream()
                .map(VacationUsageDeduction::getUsage)
                .distinct()
                .toList();

        VacationServiceDto prevStats = calculateStatsForBaseTime(prevValidGrants, prevAllUsages, prevMonthLastDay);

        // 5. 잔여 휴가 Gap 계산 (이번달 잔여 - 이전달 잔여)
        BigDecimal remainTimeGap = curStats.getRemainTime().subtract(prevStats.getRemainTime());

        // 6. 사용 휴가 Gap 계산 (이번달에만 사용한 휴가 - 이전달에만 사용한 휴가)
        LocalDateTime curMonthStart = baseTime.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        BigDecimal curMonthUsedTime = curStats.getUsages().stream()
                .filter(usage -> !usage.getStartDate().isBefore(curMonthStart))
                .map(VacationUsage::getUsedTime)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDateTime prevMonthStart = prevMonthLastDay.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        BigDecimal prevMonthUsedTime = prevStats.getUsages().stream()
                .filter(usage -> !usage.getStartDate().isBefore(prevMonthStart))
                .map(VacationUsage::getUsedTime)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal usedTimeGap = curMonthUsedTime.subtract(prevMonthUsedTime);

        return VacationServiceDto.builder()
                .remainTime(curStats.getRemainTime())
                .usedTime(curStats.getUsedTime())
                .expectUsedTime(curStats.getExpectUsedTime())
                .prevRemainTime(prevStats.getRemainTime())
                .prevUsedTime(prevStats.getUsedTime())
                .prevExpectUsedTime(prevStats.getExpectUsedTime())
                .remainTimeGap(remainTimeGap)
                .usedTimeGap(usedTimeGap)
                .build();
    }

    private VacationServiceDto calculateStatsForBaseTime(List<VacationGrant> validGrants, List<VacationUsage> allUsages, LocalDateTime baseTime) {
        // 총 부여 시간 계산
        BigDecimal totalGranted = validGrants.stream()
                .map(VacationGrant::getGrantTime)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // baseTime 이전에 사용한 VacationUsage 필터링 및 합산
        List<VacationUsage> usedUsages = allUsages.stream()
                .filter(usage -> !usage.getStartDate().isAfter(baseTime))
                .toList();
        BigDecimal totalUsedTime = usedUsages.stream()
                .map(VacationUsage::getUsedTime)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // baseTime 이후 사용 예정인 VacationUsage 필터링 및 합산
        BigDecimal totalExpectUsedTime = allUsages.stream()
                .filter(usage -> usage.getStartDate().isAfter(baseTime))
                .map(VacationUsage::getUsedTime)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 잔여 시간 계산
        BigDecimal totalRemainTime = totalGranted.subtract(totalUsedTime);

        return VacationServiceDto.builder()
                .remainTime(totalRemainTime)
                .usedTime(totalUsedTime)
                .expectUsedTime(totalExpectUsedTime)
                .usages(usedUsages)
                .build();
    }

    @Override
    public VacationUsage validateAndGetVacationUsage(Long vacationUsageId) {
        Optional<VacationUsage> usage = vacationUsageRepository.findById(vacationUsageId);
        usage.orElseThrow(() -> {
            log.warn("휴가 사용 내역 조회 실패 - 존재하지 않음: vacationUsageId={}", vacationUsageId);
            return new EntityNotFoundException(HrErrorCode.VACATION_NOT_FOUND);
        });
        return usage.get();
    }

    @Transactional
    @Override
    public Long createVacationPolicy(VacationPolicyServiceDto data) {
        log.debug("휴가 정책 생성 시작: name={}, grantMethod={}", data.getName(), data.getGrantMethod());
        VacationPolicyStrategy strategy = vacationPolicyStrategyFactory.getStrategy(data.getGrantMethod());
        Long policyId = strategy.registVacationPolicy(data);
        log.info("휴가 정책 생성 완료: policyId={}, name={}", policyId, data.getName());
        return policyId;
    }

    @Override
    public VacationPolicyServiceDto getVacationPolicy(Long vacationPolicyId) {
        log.debug("휴가 정책 조회: vacationPolicyId={}", vacationPolicyId);
        VacationPolicy policy = validateAndGetVacationPolicy(vacationPolicyId);

        // 반복 부여 정책일 경우 다국어 설명 생성
        String repeatGrantDescription = null;
        if (policy.getGrantMethod() == GrantMethod.REPEAT_GRANT) {
            repeatGrantDescription = repeatGrantDescriptionFactory.generate(policy);
        }

        return VacationPolicyServiceDto.builder()
                .id(policy.getRowId())
                .name(policy.getName())
                .desc(policy.getDesc())
                .vacationType(policy.getVacationType())
                .grantMethod(policy.getGrantMethod())
                .isFlexibleGrant(policy.getIsFlexibleGrant())
                .grantTime(policy.getGrantTime())
                .minuteGrantYn(policy.getMinuteGrantYn())
                .repeatUnit(policy.getRepeatUnit())
                .repeatInterval(policy.getRepeatInterval())
                .specificMonths(policy.getSpecificMonths())
                .specificDays(policy.getSpecificDays())
                .effectiveType(policy.getEffectiveType())
                .expirationType(policy.getExpirationType())
                .repeatGrantDescription(repeatGrantDescription)
                .build();
    }

    @Override
    public List<VacationPolicyServiceDto> getVacationPolicies() {
        log.debug("전체 휴가 정책 목록 조회");
        List<VacationPolicy> policies = vacationPolicyRepository.findVacationPolicies();
        return policies.stream()
                .map(p -> {
                    // 반복 부여 정책일 경우 다국어 설명 생성
                    String repeatGrantDescription = null;
                    if (p.getGrantMethod() == GrantMethod.REPEAT_GRANT) {
                        repeatGrantDescription = repeatGrantDescriptionFactory.generate(p);
                    }

                    return VacationPolicyServiceDto.builder()
                            .id(p.getRowId())
                            .name(p.getName())
                            .desc(p.getDesc())
                            .vacationType(p.getVacationType())
                            .grantMethod(p.getGrantMethod())
                            .isFlexibleGrant(p.getIsFlexibleGrant())
                            .grantTime(p.getGrantTime())
                            .minuteGrantYn(p.getMinuteGrantYn())
                            .repeatUnit(p.getRepeatUnit())
                            .repeatInterval(p.getRepeatInterval())
                            .specificMonths(p.getSpecificMonths())
                            .specificDays(p.getSpecificDays())
                            .effectiveType(p.getEffectiveType())
                            .expirationType(p.getExpirationType())
                            .repeatGrantDescription(repeatGrantDescription)
                            .build();
                })
                .toList();
    }

    @Override
    public VacationPolicy validateAndGetVacationPolicy(Long vacationPolicyId) {
        Optional<VacationPolicy> policy = vacationPolicyRepository.findByRowId(vacationPolicyId);
        policy.orElseThrow(() -> {
            log.warn("휴가 정책 조회 실패 - 존재하지 않는 정책: vacationPolicyId={}", vacationPolicyId);
            return new EntityNotFoundException(HrErrorCode.VACATION_POLICY_NOT_FOUND);
        });
        return policy.get();
    }

    @Transactional
    @Override
    public Long deleteVacationPolicy(Long vacationPolicyId) {
        log.debug("휴가 정책 삭제 시작: vacationPolicyId={}", vacationPolicyId);
        // 1. 휴가 정책 존재 확인
        VacationPolicy vacationPolicy = validateAndGetVacationPolicy(vacationPolicyId);

        // 2. 이미 삭제된 정책인지 확인
        if (YNType.isY(vacationPolicy.getIsDeleted())) {
            log.warn("휴가 정책 삭제 실패 - 이미 삭제됨: vacationPolicyId={}", vacationPolicyId);
            throw new BusinessRuleViolationException(HrErrorCode.VACATION_ALREADY_APPROVED);
        }

        // 3. 삭제 가능 여부 확인
        if (YNType.isN(vacationPolicy.getCanDeleted())) {
            log.warn("휴가 정책 삭제 실패 - 삭제 불가 정책: vacationPolicyId={}", vacationPolicyId);
            throw new BusinessRuleViolationException(HrErrorCode.VACATION_CANNOT_CANCEL);
        }

        // 4. 휴가 정책 소프트 삭제
        vacationPolicy.deleteVacationPolicy();

        // 5. 해당 휴가 정책으로 부여된 모든 VacationGrant 회수 처리
        List<VacationGrant> grants = vacationGrantRepository.findByPolicyId(vacationPolicyId);
        int revokedGrantCount = 0;

        for (VacationGrant grant : grants) {
            // ACTIVE 상태인 grant만 회수 처리
            // EXHAUSTED(소진), EXPIRED(만료), REVOKED(이미 회수됨)는 스킵
            if (grant.getStatus() == GrantStatus.ACTIVE) {
                grant.revoke();
                revokedGrantCount++;

                log.info("Revoked vacation grant {} from user {} (remainTime: {})",
                        grant.getRowId(), grant.getUser().getId(), grant.getRemainTime());
            }
        }

        log.info("Revoked {} active vacation grants for vacation policy {}",
                revokedGrantCount, vacationPolicyId);
        log.info("Deleted vacation policy {}", vacationPolicyId);

        return vacationPolicyId;
    }

    @Override
    public List<VacationPolicyServiceDto> getUserAssignedVacationPolicies(String userId, GrantMethod grantMethod) {
        // 유저 존재 확인
        userService.checkUserExist(userId);

        // 유저에게 할당된 휴가 정책 조회 (Plan 기반)
        List<UserVacationPlan> userVacationPlans = userVacationPlanRepository.findByUserIdWithPlanAndPolicies(userId);

        // Plan에 포함된 정책들을 중복 없이 추출
        return userVacationPlans.stream()
                .filter(uvp -> YNType.isN(uvp.getIsDeleted()))
                .flatMap(uvp -> uvp.getVacationPlan().getPolicies().stream())
                .distinct()
                // grantMethod 필터링 (null이면 모두 반환)
                .filter(policy -> grantMethod == null || policy.getGrantMethod() == grantMethod)
                .map(policy -> {
                    // 반복 부여 정책일 경우 다국어 설명 생성
                    String repeatGrantDescription = null;
                    if (policy.getGrantMethod() == GrantMethod.REPEAT_GRANT) {
                        repeatGrantDescription = repeatGrantDescriptionFactory.generate(policy);
                    }

                    return VacationPolicyServiceDto.builder()
                            .id(policy.getRowId())
                            .name(policy.getName())
                            .desc(policy.getDesc())
                            .vacationType(policy.getVacationType())
                            .grantMethod(policy.getGrantMethod())
                            .grantTime(policy.getGrantTime())
                            .repeatUnit(policy.getRepeatUnit())
                            .repeatInterval(policy.getRepeatInterval())
                            .specificMonths(policy.getSpecificMonths())
                            .specificDays(policy.getSpecificDays())
                            .firstGrantDate(policy.getFirstGrantDate())
                            .isRecurring(policy.getIsRecurring())
                            .maxGrantCount(policy.getMaxGrantCount())
                            .approvalRequiredCount(policy.getApprovalRequiredCount())
                            .effectiveType(policy.getEffectiveType())
                            .expirationType(policy.getExpirationType())
                            .isFlexibleGrant(policy.getIsFlexibleGrant())
                            .minuteGrantYn(policy.getMinuteGrantYn())
                            .repeatGrantDescription(repeatGrantDescription)
                            .build();
                })
                .toList();
    }

    @Transactional
    @Override
    public VacationGrant manualGrantVacation(String userId, VacationServiceDto data) {
        // 1. 사용자 존재 확인
        User user = userService.checkUserExist(userId);

        // 2. 휴가 정책 존재 확인
        VacationPolicy policy = validateAndGetVacationPolicy(data.getPolicyId());

        // 3. 휴가 정책의 부여 방식이 MANUAL_GRANT인지 확인
        if (policy.getGrantMethod() != GrantMethod.MANUAL_GRANT) {
            log.warn("휴가 수동 부여 실패 - MANUAL_GRANT 정책이 아님: policyId={}, grantMethod={}", data.getPolicyId(), policy.getGrantMethod());
            throw new BusinessRuleViolationException(HrErrorCode.VACATION_POLICY_NOT_FOUND);
        }

        // 4. 부여 시간 결정 (가변 부여 여부에 따라 분기)
        BigDecimal grantTime;
        if (YNType.isN(policy.getIsFlexibleGrant())) {
            // 가변 부여 여부가 N인 경우: 정책에 정의된 grantTime 사용
            grantTime = policy.getGrantTime();
            if (grantTime == null || grantTime.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("휴가 수동 부여 실패 - 정책에 부여 시간 없음: policyId={}", data.getPolicyId());
                throw new InvalidValueException(ErrorCode.INVALID_PARAMETER);
            }
        } else {
            // 가변 부여 여부가 Y인 경우: 사용자가 DTO로 넘긴 grantTime 사용
            grantTime = data.getGrantTime();
            if (grantTime == null || grantTime.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("휴가 수동 부여 실패 - 부여 시간 미입력: userId={}", userId);
                throw new InvalidValueException(ErrorCode.INVALID_PARAMETER);
            }
        }

        // 5. grantDate와 expiryDate 결정 (사용자 입력 우선, 없으면 정책 기반 계산)
        LocalDateTime grantDate;
        LocalDateTime expiryDate;

        if (data.getGrantDate() != null && data.getExpiryDate() != null) {
            // 사용자가 입력한 부여일과 만료일 사용
            grantDate = data.getGrantDate();
            expiryDate = data.getExpiryDate();
            log.info("Using user-provided dates - grantDate: {}, expiryDate: {}", grantDate, expiryDate);
        } else {
            // 정책의 effectiveType, expirationType을 사용하여 계산
            LocalDateTime now = companyClock.now();
            grantDate = policy.getEffectiveType().calculateDate(now);
            expiryDate = policy.getExpirationType().calculateDate(grantDate);
            log.info("Calculated dates using policy types - grantDate: {}, expiryDate: {}", grantDate, expiryDate);
        }

        // 6. 부여일과 만료일 검증 (부여일 < 만료일)
        if (TimeUtils.isAfter(grantDate, expiryDate)) {
            log.warn("휴가 수동 부여 실패 - 부여일이 만료일보다 이후: grantDate={}, expiryDate={}", grantDate, expiryDate);
            throw new InvalidValueException(HrErrorCode.VACATION_INVALID_DATE);
        }

        // 7. VacationGrant 생성
        VacationGrant vacationGrant = VacationGrant.createVacationGrant(
                user,
                policy,
                data.getDesc() != null ? data.getDesc() : "관리자 직접 부여",
                policy.getVacationType(),
                grantTime,
                grantDate,
                expiryDate
        );

        // 8. 저장
        vacationGrantRepository.save(vacationGrant);

        log.info("Manually granted vacation: grantId={}, userId={}, policyId={}, grantTime={}, grantDate={}, expiryDate={}",
                vacationGrant.getRowId(), userId, policy.getRowId(), grantTime, grantDate, expiryDate);

        return vacationGrant;
    }


    @Transactional
    @Override
    public VacationGrant revokeVacationGrant(Long vacationGrantId) {
        // 1. VacationGrant 존재 확인
        VacationGrant grant = vacationGrantRepository.findById(vacationGrantId)
                .orElseThrow(() -> {
                    log.warn("휴가 부여 회수 실패 - 존재하지 않음: vacationGrantId={}", vacationGrantId);
                    return new EntityNotFoundException(HrErrorCode.VACATION_GRANT_NOT_FOUND);
                });

        // 2. 이미 삭제된 경우
        if (YNType.isY(grant.getIsDeleted())) {
            log.warn("휴가 부여 회수 실패 - 이미 삭제됨: vacationGrantId={}", vacationGrantId);
            throw new BusinessRuleViolationException(HrErrorCode.VACATION_ALREADY_APPROVED);
        }

        // 3. ACTIVE 상태인 경우에만 회수 가능
        if (grant.getStatus() != GrantStatus.ACTIVE) {
            log.warn("휴가 부여 회수 실패 - ACTIVE 상태 아님: vacationGrantId={}, status={}", vacationGrantId, grant.getStatus());
            throw new BusinessRuleViolationException(HrErrorCode.VACATION_CANNOT_CANCEL);
        }

        // 4. 일부라도 사용된 경우 회수 불가
        if (grant.getRemainTime().compareTo(grant.getGrantTime()) < 0) {
            log.warn("휴가 부여 회수 실패 - 일부 사용됨: vacationGrantId={}, grantTime={}, remainTime={}", vacationGrantId, grant.getGrantTime(), grant.getRemainTime());
            throw new BusinessRuleViolationException(HrErrorCode.VACATION_CANNOT_CANCEL);
        }

        // 5. 회수 처리
        grant.revoke();

        log.info("Revoked vacation grant: grantId={}, userId={}, policyId={}, grantTime={}",
                grant.getRowId(), grant.getUser().getId(), grant.getPolicy().getRowId(), grant.getGrantTime());

        return grant;
    }

    @Transactional
    @Override
    public Long requestVacation(String userId, VacationServiceDto data) {
        // 1. 사용자 검증
        User user = userService.checkUserExist(userId);

        // 2. 휴가 정책 검증
        VacationPolicy policy = validateAndGetVacationPolicy(data.getPolicyId());

        // 3. 정책이 ON_REQUEST 방식인지 확인
        if (policy.getGrantMethod() != GrantMethod.ON_REQUEST) {
            log.warn("휴가 신청 실패 - ON_REQUEST 정책이 아님: userId={}, policyId={}, grantMethod={}", userId, data.getPolicyId(), policy.getGrantMethod());
            throw new BusinessRuleViolationException(HrErrorCode.VACATION_POLICY_NOT_FOUND);
        }

        // 4. 사용자에게 해당 정책이 할당되어 있는지 확인 (Plan 기반)
        boolean hasPolicy = isUserHasVacationPolicy(userId, data.getPolicyId());
        if (!hasPolicy) {
            log.warn("휴가 신청 실패 - 정책 미할당: userId={}, policyId={}", userId, data.getPolicyId());
            throw new BusinessRuleViolationException(HrErrorCode.VACATION_POLICY_NOT_FOUND);
        }

        // 5. 신청 사유 필수 검증
        if (data.getDesc() == null || data.getDesc().trim().isEmpty()) {
            log.warn("휴가 신청 실패 - 신청 사유 미입력: userId={}, policyId={}", userId, data.getPolicyId());
            throw new InvalidValueException(ErrorCode.INVALID_PARAMETER);
        }

        // 6. 승인자 목록 검증
        List<String> approverIds = data.getApproverIds();
        Integer requiredCount = policy.getApprovalRequiredCount();

        // 가용 승인자 수 조회 (상위 부서장 목록)
        List<Department> availableApproverDepartments = departmentRepository.findApproversByUserId(userId);
        int availableApproverCount = availableApproverDepartments.size();

        // 실제 필요한 승인자 수 계산: min(정책 요구 인원, 가용 승인자 수)
        int actualRequiredCount = 0;
        if (requiredCount != null && requiredCount > 0) {
            actualRequiredCount = Math.min(requiredCount, availableApproverCount);
        }

        log.debug("승인자 검증: userId={}, policyRequired={}, available={}, actualRequired={}",
                userId, requiredCount, availableApproverCount, actualRequiredCount);

        if (actualRequiredCount > 0) {
            // 승인자가 필요하지만 미지정된 경우
            if (approverIds == null || approverIds.isEmpty()) {
                log.warn("휴가 신청 실패 - 승인자 미지정: userId={}, actualRequiredCount={}", userId, actualRequiredCount);
                throw new InvalidValueException(ErrorCode.INVALID_PARAMETER);
            }

            // 승인자 수 검증: 실제 필요 인원과 일치해야 함
            if (approverIds.size() != actualRequiredCount) {
                log.warn("휴가 신청 실패 - 승인자 수 불일치: userId={}, actualRequired={}, provided={}",
                        userId, actualRequiredCount, approverIds.size());
                throw new InvalidValueException(HrErrorCode.VACATION_APPROVER_COUNT_MISMATCH);
            }

            // 6-1. 승인자 중복 체크
            Set<String> uniqueApproverIds = new HashSet<>(approverIds);
            if (uniqueApproverIds.size() != approverIds.size()) {
                log.warn("휴가 신청 실패 - 중복된 승인자: userId={}, approverIds={}", userId, approverIds);
                throw new InvalidValueException(HrErrorCode.VACATION_DUPLICATE_APPROVER);
            }

            // 6-2. 본인을 승인자로 지정 불가
            if (approverIds.contains(userId)) {
                log.warn("휴가 신청 실패 - 본인을 승인자로 지정: userId={}", userId);
                throw new InvalidValueException(HrErrorCode.VACATION_SELF_APPROVAL_NOT_ALLOWED);
            }

            // 6-3. 승인자 모두 존재하는지 확인
            for (String approverId : approverIds) {
                userService.checkUserExist(approverId);
            }

            // 6-4. 승인자가 부서장인지 확인 (headUser로 검증)
            List<Department> departments = departmentRepository.findByUserIds(approverIds);

            // 부서장이 아닌 승인자 찾기
            Set<String> headUserIds = departments.stream()
                    .filter(dept -> dept.getHeadUser() != null)
                    .map(dept -> dept.getHeadUser().getId())
                    .collect(Collectors.toSet());

            List<String> nonHeadApprovers = approverIds.stream()
                    .filter(id -> !headUserIds.contains(id))
                    .collect(Collectors.toList());

            if (!nonHeadApprovers.isEmpty()) {
                log.warn("휴가 신청 실패 - 부서장이 아닌 승인자: userId={}, nonHeadApprovers={}", userId, nonHeadApprovers);
                throw new InvalidValueException(ErrorCode.INVALID_PARAMETER);
            }

            // 6-4-1. 승인자는 신청자의 상위 부서장 사슬 안에서만 고를 수 있다 (결재선 우회 방지)
            Set<String> chainHeadIds = availableApproverDepartments.stream()
                    .filter(dept -> dept.getHeadUser() != null)
                    .map(dept -> dept.getHeadUser().getId())
                    .collect(Collectors.toSet());

            List<String> outOfChainApprovers = approverIds.stream()
                    .filter(id -> !chainHeadIds.contains(id))
                    .collect(Collectors.toList());

            if (!outOfChainApprovers.isEmpty()) {
                log.warn("휴가 신청 실패 - 결재선에 없는 승인자: userId={}, outOfChainApprovers={}", userId, outOfChainApprovers);
                throw new InvalidValueException(ErrorCode.INVALID_PARAMETER);
            }

            // 6-5. 승인자를 부서 레벨 순서로 정렬 (레벨 오름차순: 하위 부서장 먼저)
            Map<String, Department> approverDepartmentMap = departments.stream()
                    .filter(dept -> dept.getHeadUser() != null)
                    .collect(Collectors.toMap(dept -> dept.getHeadUser().getId(), dept -> dept));

            approverIds.sort((id1, id2) -> {
                Department dept1 = approverDepartmentMap.get(id1);
                Department dept2 = approverDepartmentMap.get(id2);

                // 레벨이 작을수록(하위 부서) 먼저 승인
                return Long.compare(dept1.getLevel(), dept2.getLevel());
            });
        }

        // 7. 부여 시간 계산 (OnRequest 전략 사용)
        OnRequest onRequestStrategy = (OnRequest) vacationPolicyStrategyFactory.getStrategy(GrantMethod.ON_REQUEST);
        BigDecimal grantTime = onRequestStrategy.calculateGrantTime(policy, data.getGrantTime());

        // 8. VacationGrant 생성 (PENDING 상태)
        VacationGrant vacationGrant = VacationGrant.createPendingVacationGrant(
                user,
                policy,
                data.getDesc(),
                policy.getVacationType(),
                grantTime,
                data.getRequestStartTime(),
                data.getRequestEndTime(),
                data.getRequestDesc()
        );

        vacationGrantRepository.save(vacationGrant);

        // 9. 승인이 필요한 경우 VacationApproval 생성 (순서대로)
        // actualRequiredCount > 0: 승인이 필요한 경우
        // actualRequiredCount == 0: 가용 승인자가 없거나 정책 요구 인원이 0인 경우 → 즉시 승인
        if (actualRequiredCount > 0 && approverIds != null && !approverIds.isEmpty()) {
            List<VacationApproval> approvals = new ArrayList<>();
            int order = 1;
            for (String approverId : approverIds) {
                User approver = userService.checkUserExist(approverId);
                VacationApproval approval = VacationApproval.createVacationApproval(vacationGrant, approver, order);
                approvals.add(approval);
                order++;
            }
            vacationApprovalRepository.saveAll(approvals);

            log.info("휴가 신청 완료 - User: {}, Policy: {}, GrantId: {}, Approvers: {} (순서대로)",
                    userId, policy.getRowId(), vacationGrant.getRowId(), approverIds);
        } else {
            // 승인이 필요 없는 경우 즉시 ACTIVE 상태로 전환
            // - 정책의 approvalRequiredCount가 0 또는 null인 경우
            // - 가용 승인자가 0명인 경우 (최상위 조직장 등)
            LocalDateTime now = companyClock.now();
            LocalDateTime grantDate = policy.getEffectiveType().calculateDate(now);
            LocalDateTime expiryDate = policy.getExpirationType().calculateDate(grantDate);
            vacationGrant.approve(grantDate, expiryDate);

            if (requiredCount != null && requiredCount > 0 && availableApproverCount == 0) {
                log.info("휴가 신청 완료 (자동 승인 - 최상위 조직장) - User: {}, Policy: {}, GrantId: {}",
                        userId, policy.getRowId(), vacationGrant.getRowId());
            } else {
                log.info("휴가 신청 완료 (즉시 승인) - User: {}, Policy: {}, GrantId: {}",
                        userId, policy.getRowId(), vacationGrant.getRowId());
            }
        }

        return vacationGrant.getRowId();
    }

    @Transactional
    @Override
    public Long approveVacation(Long approvalId, String approverId) {
        // 1. VacationApproval 조회
        VacationApproval approval = vacationApprovalRepository.findByIdWithVacationGrantAndUser(approvalId)
                .orElseThrow(() -> {
                    log.warn("휴가 승인 실패 - 승인 내역 없음: approvalId={}", approvalId);
                    return new EntityNotFoundException(HrErrorCode.VACATION_NOT_FOUND);
                });

        // 2. 승인자 권한 검증
        if (!approval.getApprover().getId().equals(approverId)) {
            log.warn("휴가 승인 실패 - 권한 없음: approvalId={}, approverId={}, actualApproverId={}", approvalId, approverId, approval.getApprover().getId());
            throw new BusinessRuleViolationException(HrErrorCode.PERMISSION_DENIED);
        }

        // 3. 이미 처리된 승인인지 확인
        if (!approval.isPending()) {
            log.warn("휴가 승인 실패 - 이미 처리됨: approvalId={}, status={}", approvalId, approval.getApprovalStatus());
            throw new BusinessRuleViolationException(HrErrorCode.VACATION_ALREADY_APPROVED);
        }

        // 4. 순차 승인 검증: 이전 순서의 승인자들이 모두 승인했는지 확인
        VacationGrant vacationGrant = approval.getVacationGrant();
        List<VacationApproval> allApprovals = vacationApprovalRepository.findByVacationGrantId(vacationGrant.getRowId());

        Integer currentOrder = approval.getApprovalOrder();

        // 현재 승인자보다 앞선 순서의 승인자 중 아직 승인하지 않은 사람이 있는지 확인
        boolean hasPendingPreviousApprovals = allApprovals.stream()
                .filter(a -> a.getApprovalOrder() < currentOrder)
                .anyMatch(a -> !a.isApproved());

        if (hasPendingPreviousApprovals) {
            log.warn("휴가 승인 실패 - 이전 순서 승인 미완료: approvalId={}, currentOrder={}", approvalId, currentOrder);
            throw new BusinessRuleViolationException(HrErrorCode.VACATION_CANNOT_CANCEL);
        }

        // 5. 승인 처리
        approval.approve();

        // 6. VacationGrant의 모든 승인이 완료되었는지 확인
        boolean allApproved = allApprovals.stream().allMatch(VacationApproval::isApproved);
        int totalApprovalCount = allApprovals.size();

        if (allApproved) {
            // 모든 승인이 완료되면 VacationGrant를 ACTIVE 상태로 전환
            VacationPolicy policy = vacationGrant.getPolicy();
            LocalDateTime now = companyClock.now();
            LocalDateTime grantDate = policy.getEffectiveType().calculateDate(now);
            LocalDateTime expiryDate = policy.getExpirationType().calculateDate(grantDate);

            vacationGrant.approve(grantDate, expiryDate);

            log.info("휴가 전체 승인 완료 - VacationGrant ID: {}, Final Approver: {}, Status: ACTIVE",
                    vacationGrant.getRowId(), approverId);
        } else {
            // 승인자가 2명 이상이고 1명 이상이 승인한 경우 PROGRESS 상태로 전환
            if (totalApprovalCount >= 2) {
                vacationGrant.updateToProgress();
                log.info("휴가 승인 진행 중 - VacationGrant ID: {}, Approver: {} (순서: {}), Status: PROGRESS",
                        vacationGrant.getRowId(), approverId, currentOrder);
            }

            long pendingCount = allApprovals.stream().filter(VacationApproval::isPending).count();
            log.info("휴가 부분 승인 완료 - VacationGrant ID: {}, Approver: {} (순서: {}), 남은 승인: {}",
                    vacationGrant.getRowId(), approverId, currentOrder, pendingCount);
        }

        return approval.getRowId();
    }

    @Transactional
    @Override
    public Long rejectVacation(Long approvalId, String approverId, VacationApprovalServiceDto data) {
        // 1. VacationApproval 조회
        VacationApproval approval = vacationApprovalRepository.findByIdWithVacationGrantAndUser(approvalId)
                .orElseThrow(() -> {
                    log.warn("휴가 거부 실패 - 승인 내역 없음: approvalId={}", approvalId);
                    return new EntityNotFoundException(HrErrorCode.VACATION_NOT_FOUND);
                });

        // 2. 승인자 권한 검증
        if (!approval.getApprover().getId().equals(approverId)) {
            log.warn("휴가 거부 실패 - 권한 없음: approvalId={}, approverId={}, actualApproverId={}", approvalId, approverId, approval.getApprover().getId());
            throw new BusinessRuleViolationException(HrErrorCode.PERMISSION_DENIED);
        }

        // 3. 이미 처리된 승인인지 확인
        if (!approval.isPending()) {
            log.warn("휴가 거부 실패 - 이미 처리됨: approvalId={}, status={}", approvalId, approval.getApprovalStatus());
            throw new BusinessRuleViolationException(HrErrorCode.VACATION_ALREADY_APPROVED);
        }

        // 4. 거부 사유 필수 검증
        if (data.getRejectionReason() == null || data.getRejectionReason().trim().isEmpty()) {
            log.warn("휴가 거부 실패 - 거부 사유 미입력: approvalId={}", approvalId);
            throw new InvalidValueException(ErrorCode.INVALID_PARAMETER);
        }

        // 5. 거부 처리
        approval.reject(data.getRejectionReason());

        // 6. VacationGrant를 REJECTED 상태로 전환
        VacationGrant vacationGrant = approval.getVacationGrant();
        vacationGrant.reject();

        log.info("휴가 거부 완료 - VacationGrant ID: {}, Approver: {}, Reason: {}",
                vacationGrant.getRowId(), approverId, data.getRejectionReason());

        return approval.getRowId();
    }

    @Transactional
    @Override
    public Long cancelVacationRequest(Long vacationGrantId, String userId) {
        // 1. VacationGrant 조회
        VacationGrant vacationGrant = vacationGrantRepository.findById(vacationGrantId)
                .orElseThrow(() -> {
                    log.warn("휴가 신청 취소 실패 - 휴가 부여 내역 없음: vacationGrantId={}", vacationGrantId);
                    return new EntityNotFoundException(HrErrorCode.VACATION_GRANT_NOT_FOUND);
                });

        // 2. 신청자 권한 검증 (신청자만 취소 가능)
        if (!vacationGrant.getUser().getId().equals(userId)) {
            log.warn("휴가 신청 취소 실패 - 권한 없음: vacationGrantId={}, userId={}, actualUserId={}", vacationGrantId, userId, vacationGrant.getUser().getId());
            throw new BusinessRuleViolationException(HrErrorCode.PERMISSION_DENIED);
        }

        // 3. PENDING 상태인지 확인 (한 명도 승인하지 않은 상태에서만 취소 가능)
        if (vacationGrant.getStatus() != GrantStatus.PENDING) {
            log.warn("휴가 신청 취소 실패 - PENDING 상태 아님: vacationGrantId={}, status={}", vacationGrantId, vacationGrant.getStatus());
            throw new BusinessRuleViolationException(HrErrorCode.VACATION_CANNOT_CANCEL);
        }

        // 4. 취소 처리
        vacationGrant.cancel();

        log.info("휴가 신청 취소 완료 - VacationGrant ID: {}, User: {}",
                vacationGrantId, userId);

        return vacationGrant.getRowId();
    }

    @Override
    public List<VacationServiceDto> getAllVacationsByApprover(String approverId, Integer year, GrantStatus status) {
        // 승인자 존재 확인
        userService.checkUserExist(approverId);

        // 승인자가 포함된 모든 VacationGrant ID 조회 (년도 필터링 포함)
        List<Long> vacationGrantIds = vacationApprovalRepository.findAllVacationGrantIdsByApproverIdAndYear(approverId, year);

        if (vacationGrantIds.isEmpty()) {
            return List.of();
        }

        // VacationGrant 조회 (User, Policy 포함)
        List<VacationGrant> grants = vacationGrantRepository.findByIdsWithUserAndPolicy(vacationGrantIds);

        // 상태 필터링 (status가 null이 아닌 경우)
        if (status != null) {
            grants = grants.stream()
                    .filter(grant -> grant.getStatus() == status)
                    .toList();
        }

        if (grants.isEmpty()) {
            return List.of();
        }

        // 모든 grant ID 추출
        List<Long> grantIds = grants.stream()
                .map(VacationGrant::getRowId)
                .toList();

        // IN 쿼리로 한번에 모든 approval 조회
        List<VacationApproval> allApprovals = vacationApprovalRepository.findByVacationGrantIds(grantIds);

        // grantId -> List<VacationApprovalServiceDto> 매핑
        Map<Long, List<VacationApprovalServiceDto>> approvalMap = allApprovals.stream()
                .collect(Collectors.groupingBy(
                        approval -> approval.getVacationGrant().getRowId(),
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                list -> list.stream()
                                        .sorted((a1, a2) -> Integer.compare(a1.getApprovalOrder(), a2.getApprovalOrder()))
                                        .map(approval -> VacationApprovalServiceDto.builder()
                                                .id(approval.getRowId())
                                                .approverId(approval.getApprover().getId())
                                                .approverName(approval.getApprover().getName())
                                                .approvalOrder(approval.getApprovalOrder())
                                                .approvalStatus(approval.getApprovalStatus())
                                                .approvalDate(approval.getApprovalDate())
                                                .rejectionReason(approval.getRejectionReason())
                                                .build())
                                        .toList()
                        )
                ));

        // VacationServiceDto로 변환
        return grants.stream()
                .map(grant -> {
                    // 현재 승인 대기 중인 승인자 조회
                    User currentApprover = grant.getCurrentPendingApprover();

                    return VacationServiceDto.builder()
                            .id(grant.getRowId())
                            .userId(grant.getUser().getId())
                            .user(grant.getUser())
                            .policyId(grant.getPolicy().getRowId())
                            .policyName(grant.getPolicy().getName())
                            .type(grant.getType())
                            .desc(grant.getDesc())
                            .grantTime(grant.getGrantTime())
                            .policyGrantTime(grant.getPolicy().getGrantTime())
                            .remainTime(grant.getRemainTime())
                            .grantDate(grant.getGrantDate())
                            .expiryDate(grant.getExpiryDate())
                            .requestStartTime(grant.getRequestStartTime())
                            .requestEndTime(grant.getRequestEndTime())
                            .requestDesc(grant.getRequestDesc())
                            .grantStatus(grant.getStatus())
                            .createDate(grant.getCreateAt())
                            .currentApproverId(currentApprover != null ? currentApprover.getId() : null)
                            .currentApproverName(currentApprover != null ? currentApprover.getName() : null)
                            .approvers(approvalMap.getOrDefault(grant.getRowId(), List.of()))
                            .build();
                })
                .toList();
    }

    @Override
    public List<VacationServiceDto> getAllRequestedVacationsByUserId(String userId, Integer year) {
        // 사용자 존재 확인
        userService.checkUserExist(userId);

        // ON_REQUEST 방식의 모든 휴가 신청 내역 조회 (년도 필터링 포함)
        List<VacationGrant> grants = vacationGrantRepository.findAllRequestedVacationsByUserIdAndYear(userId, year);

        if (grants.isEmpty()) {
            return List.of();
        }

        // 모든 grant ID 추출
        List<Long> grantIds = grants.stream()
                .map(VacationGrant::getRowId)
                .toList();

        // IN 쿼리로 한번에 모든 approval 조회
        List<VacationApproval> allApprovals = vacationApprovalRepository.findByVacationGrantIds(grantIds);

        // grantId -> List<VacationApprovalServiceDto> 매핑
        Map<Long, List<VacationApprovalServiceDto>> approvalMap = allApprovals.stream()
                .collect(Collectors.groupingBy(
                        approval -> approval.getVacationGrant().getRowId(),
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                list -> list.stream()
                                        .sorted((a1, a2) -> Integer.compare(a1.getApprovalOrder(), a2.getApprovalOrder()))
                                        .map(approval -> VacationApprovalServiceDto.builder()
                                                .id(approval.getRowId())
                                                .approverId(approval.getApprover().getId())
                                                .approverName(approval.getApprover().getName())
                                                .approvalOrder(approval.getApprovalOrder())
                                                .approvalStatus(approval.getApprovalStatus())
                                                .approvalDate(approval.getApprovalDate())
                                                .rejectionReason(approval.getRejectionReason())
                                                .build())
                                        .toList()
                        )
                ));

        return grants.stream()
                .map(grant -> {
                    // 현재 승인 대기 중인 승인자 조회
                    User currentApprover = grant.getCurrentPendingApprover();

                    return VacationServiceDto.builder()
                            .id(grant.getRowId())
                            .userId(grant.getUser().getId())
                            .user(grant.getUser())
                            .policyId(grant.getPolicy().getRowId())
                            .policyName(grant.getPolicy().getName())
                            .type(grant.getType())
                            .desc(grant.getDesc())
                            .grantTime(grant.getGrantTime())
                            .policyGrantTime(grant.getPolicy().getGrantTime())
                            .remainTime(grant.getRemainTime())
                            .grantDate(grant.getGrantDate())
                            .expiryDate(grant.getExpiryDate())
                            .requestStartTime(grant.getRequestStartTime())
                            .requestEndTime(grant.getRequestEndTime())
                            .requestDesc(grant.getRequestDesc())
                            .grantStatus(grant.getStatus())
                            .createDate(grant.getCreateAt())
                            .currentApproverId(currentApprover != null ? currentApprover.getId() : null)
                            .currentApproverName(currentApprover != null ? currentApprover.getName() : null)
                            .approvers(approvalMap.getOrDefault(grant.getRowId(), List.of()))
                            .build();
                })
                .toList();
    }

    @Override
    public VacationServiceDto getRequestedVacationStatsByUserId(String userId, Integer year) {
        // 사용자 존재 확인
        userService.checkUserExist(userId);

        // ON_REQUEST 방식의 모든 휴가 신청 내역 조회 (년도 필터링 포함)
        List<VacationGrant> allGrants = vacationGrantRepository.findAllRequestedVacationsByUserIdAndYear(userId, year);

        // 현재 날짜 기준
        LocalDateTime now = companyClock.now();
        LocalDateTime startOfCurrentMonth = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime startOfPreviousMonth = startOfCurrentMonth.minusMonths(1);

        // 1. 전체 신청 건수
        long totalRequestCount = allGrants.size();

        // 2. 이번 달 신청 건수
        long currentMonthRequestCount = allGrants.stream()
                .filter(grant -> grant.getRequestStartTime() != null &&
                        grant.getRequestStartTime().isAfter(startOfCurrentMonth))
                .count();

        // 전월 신청 건수 (증감 비율 계산용)
        long previousMonthRequestCount = allGrants.stream()
                .filter(grant -> grant.getRequestStartTime() != null &&
                        grant.getRequestStartTime().isAfter(startOfPreviousMonth) &&
                        grant.getRequestStartTime().isBefore(startOfCurrentMonth))
                .count();

        // 3. 증감 비율 계산
        Double changeRate = 0.0;
        if (previousMonthRequestCount > 0) {
            changeRate = ((double) (currentMonthRequestCount - previousMonthRequestCount) / previousMonthRequestCount) * 100.0;
        } else if (currentMonthRequestCount > 0) {
            changeRate = 100.0; // 전월 0건, 이번달 1건 이상인 경우 100% 증가
        }

        // 4. 대기 건수
        long pendingCount = allGrants.stream()
                .filter(grant -> grant.getStatus() == GrantStatus.PENDING)
                .count();

        // 5. 평균 처리 기간 (일수) - ACTIVE 또는 REJECTED 상태만 계산
        List<VacationGrant> processedGrants = allGrants.stream()
                .filter(grant -> grant.getStatus() == GrantStatus.ACTIVE || grant.getStatus() == GrantStatus.REJECTED)
                .filter(grant -> grant.getRequestStartTime() != null && grant.getModifyAt() != null)
                .toList();

        Double averageProcessingDays = 0.0;
        if (!processedGrants.isEmpty()) {
            long totalProcessingSeconds = processedGrants.stream()
                    .mapToLong(grant -> {
                        Duration duration = Duration.between(
                                grant.getRequestStartTime(),
                                grant.getModifyAt()
                        );
                        return duration.toDays();
                    })
                    .sum();
            averageProcessingDays = (double) totalProcessingSeconds / processedGrants.size();
        }

        // 6. 진행 중 건수
        long progressCount = allGrants.stream()
                .filter(grant -> grant.getStatus() == GrantStatus.PROGRESS)
                .count();

        // 7. 승인 건수
        long approvedCount = allGrants.stream()
                .filter(grant -> grant.getStatus() == GrantStatus.ACTIVE)
                .count();

        // 8. 승인율 계산
        Double approvalRate = 0.0;
        if (totalRequestCount > 0) {
            approvalRate = ((double) approvedCount / totalRequestCount) * 100.0;
        }

        // 9. 반려 건수
        long rejectedCount = allGrants.stream()
                .filter(grant -> grant.getStatus() == GrantStatus.REJECTED)
                .count();

        // 10. 취소 건수
        long canceledCount = allGrants.stream()
                .filter(grant -> grant.getStatus() == GrantStatus.CANCELED)
                .count();

        // 11. 획득 휴가 시간 (승인된 건만)
        BigDecimal acquiredVacationTime = allGrants.stream()
                .filter(grant -> grant.getStatus() == GrantStatus.ACTIVE)
                .map(VacationGrant::getGrantTime)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 12. 획득 휴가 일수 문자열
        String acquiredVacationTimeStr = vacationTimeFormatter.format(acquiredVacationTime);

        return VacationServiceDto.builder()
                .totalRequestCount(totalRequestCount)
                .currentMonthRequestCount(currentMonthRequestCount)
                .changeRate(changeRate)
                .pendingCount(pendingCount)
                .averageProcessingDays(averageProcessingDays)
                .progressCount(progressCount)
                .approvedCount(approvedCount)
                .approvalRate(approvalRate)
                .rejectedCount(rejectedCount)
                .canceledCount(canceledCount)
                .acquiredVacationTime(acquiredVacationTime)
                .acquiredVacationTimeStr(acquiredVacationTimeStr)
                .grantsList(allGrants)
                .build();
    }

    @Override
    public VacationServiceDto getVacationPolicyAssignmentStatus(String userId) {
        // 1. 유저 존재 확인
        userService.checkUserExist(userId);

        // 2. 모든 휴가 정책 조회
        List<VacationPolicy> allPolicies = vacationPolicyRepository.findVacationPolicies();

        // 3. 유저에게 할당된 휴가 정책 조회 (Plan 기반)
        List<UserVacationPlan> userVacationPlans = userVacationPlanRepository.findByUserIdWithPlanAndPolicies(userId);

        // 4. 할당된 정책 ID Set 생성 (빠른 조회를 위해)
        Set<Long> assignedPolicyIds = userVacationPlans.stream()
                .filter(uvp -> YNType.isN(uvp.getIsDeleted()))
                .flatMap(uvp -> uvp.getVacationPlan().getPolicies().stream())
                .map(VacationPolicy::getRowId)
                .collect(Collectors.toSet());

        // 5. 할당된 정책과 할당되지 않은 정책 분리
        List<VacationPolicyServiceDto> assignedPolicies = allPolicies.stream()
                .filter(p -> assignedPolicyIds.contains(p.getRowId()))
                .map(this::convertToPolicyServiceDto)
                .toList();

        List<VacationPolicyServiceDto> unassignedPolicies = allPolicies.stream()
                .filter(p -> !assignedPolicyIds.contains(p.getRowId()))
                .map(this::convertToPolicyServiceDto)
                .toList();

        log.info("User {} vacation policy assignment status - assigned: {}, unassigned: {}",
                userId, assignedPolicies.size(), unassignedPolicies.size());

        return VacationServiceDto.builder()
                .assignedPolicies(assignedPolicies)
                .unassignedPolicies(unassignedPolicies)
                .build();
    }

    private VacationPolicyServiceDto convertToPolicyServiceDto(VacationPolicy policy) {
        // 반복 부여 정책일 경우 다국어 설명 생성
        String repeatGrantDescription = null;
        if (policy.getGrantMethod() == GrantMethod.REPEAT_GRANT) {
            repeatGrantDescription = repeatGrantDescriptionFactory.generate(policy);
        }

        return VacationPolicyServiceDto.builder()
                .id(policy.getRowId())
                .name(policy.getName())
                .desc(policy.getDesc())
                .vacationType(policy.getVacationType())
                .grantMethod(policy.getGrantMethod())
                .isFlexibleGrant(policy.getIsFlexibleGrant())
                .grantTime(policy.getGrantTime())
                .minuteGrantYn(policy.getMinuteGrantYn())
                .repeatUnit(policy.getRepeatUnit())
                .repeatInterval(policy.getRepeatInterval())
                .specificMonths(policy.getSpecificMonths())
                .specificDays(policy.getSpecificDays())
                .effectiveType(policy.getEffectiveType())
                .expirationType(policy.getExpirationType())
                .repeatGrantDescription(repeatGrantDescription)
                .build();
    }

    @Override
    public List<VacationPolicyServiceDto> getUserAssignedVacationPoliciesWithFilters(
            String userId, VacationType vacationType, GrantMethod grantMethod) {
        // 유저 존재 확인
        userService.checkUserExist(userId);

        // 유저에게 할당된 휴가 정책 조회 (Plan 기반)
        List<UserVacationPlan> userVacationPlans = userVacationPlanRepository.findByUserIdWithPlanAndPolicies(userId);

        // Plan에 포함된 정책들을 중복 없이 추출하고 필터링
        return userVacationPlans.stream()
                .filter(uvp -> YNType.isN(uvp.getIsDeleted()))
                .flatMap(uvp -> uvp.getVacationPlan().getPolicies().stream())
                .distinct()
                // 필터링 (null이면 모두 반환)
                .filter(policy -> vacationType == null || policy.getVacationType() == vacationType)
                .filter(policy -> grantMethod == null || policy.getGrantMethod() == grantMethod)
                .map(policy -> {
                    // 반복 부여 정책일 경우 다국어 설명 생성
                    String repeatGrantDescription = null;
                    if (policy.getGrantMethod() == GrantMethod.REPEAT_GRANT) {
                        repeatGrantDescription = repeatGrantDescriptionFactory.generate(policy);
                    }

                    return VacationPolicyServiceDto.builder()
                            .id(policy.getRowId())
                            .name(policy.getName())
                            .desc(policy.getDesc())
                            .vacationType(policy.getVacationType())
                            .grantMethod(policy.getGrantMethod())
                            .grantTime(policy.getGrantTime())
                            .isFlexibleGrant(policy.getIsFlexibleGrant())
                            .minuteGrantYn(policy.getMinuteGrantYn())
                            .repeatUnit(policy.getRepeatUnit())
                            .repeatInterval(policy.getRepeatInterval())
                            .specificMonths(policy.getSpecificMonths())
                            .specificDays(policy.getSpecificDays())
                            .effectiveType(policy.getEffectiveType())
                            .expirationType(policy.getExpirationType())
                            .repeatGrantDescription(repeatGrantDescription)
                            .build();
                })
                .toList();
    }

    @Override
    public List<VacationServiceDto> getAllUsersVacationSummary(Integer year) {
        // 년도 유효성 검증
        if (year == null) {
            log.warn("휴가 통계 조회 실패 - 년도 미입력");
            throw new InvalidValueException(ErrorCode.INVALID_PARAMETER);
        }

        // 해당 년도의 시작일과 종료일 계산
        LocalDateTime startOfYear = LocalDateTime.of(year, 1, 1, 0, 0, 0);
        LocalDateTime endOfYear = LocalDateTime.of(year, 12, 31, 23, 59, 59);

        // 모든 사용자 조회 (N+1 방지: 단일 쿼리로 조회)
        List<User> allUsers = userService.findAllUsers();

        if (allUsers.isEmpty()) {
            return List.of();
        }

        // 모든 사용자 ID 추출
        List<String> userIds = allUsers.stream()
                .map(User::getId)
                .toList();

        // IN 쿼리로 한번에 모든 휴가 데이터 조회 (N+1 방지)
        List<VacationGrant> allValidGrants = vacationGrantRepository
                .findByUserIdsAndValidPeriod(userIds, startOfYear, endOfYear);
        List<VacationUsage> allUsages = vacationUsageRepository
                .findByUserIdsAndPeriod(userIds, startOfYear, endOfYear);
        List<VacationGrant> allPendingGrants = vacationGrantRepository
                .findByUserIdsAndStatusesAndPeriod(userIds,
                        List.of(GrantStatus.PENDING, GrantStatus.PROGRESS), startOfYear, endOfYear);

        // userId 기준으로 Map 그룹핑
        Map<String, List<VacationGrant>> validGrantsMap = allValidGrants.stream()
                .collect(Collectors.groupingBy(g -> g.getUser().getId()));
        Map<String, List<VacationUsage>> usagesMap = allUsages.stream()
                .collect(Collectors.groupingBy(u -> u.getUser().getId()));
        Map<String, List<VacationGrant>> pendingGrantsMap = allPendingGrants.stream()
                .collect(Collectors.groupingBy(g -> g.getUser().getId()));

        // 각 사용자별 휴가 통계 계산 (Map lookup으로 O(1) 조회)
        return allUsers.stream()
                .map(user -> {
                    // 부서명 조회
                    String departmentName = user.getUserDepartments().stream()
                            .findFirst()
                            .map(ud -> ud.getDepartment().getName())
                            .orElse("");

                    // Map에서 해당 사용자의 휴가 데이터 조회
                    List<VacationGrant> validGrants = validGrantsMap.getOrDefault(user.getId(), List.of());
                    List<VacationUsage> usages = usagesMap.getOrDefault(user.getId(), List.of());
                    List<VacationGrant> pendingGrants = pendingGrantsMap.getOrDefault(user.getId(), List.of());

                    // 총 휴가 일수 계산 (부여받은 grantTime 합계)
                    BigDecimal totalVacationDays = validGrants.stream()
                            .map(VacationGrant::getGrantTime)
                            .filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    // 사용 휴가 일수 계산
                    BigDecimal usedVacationDays = usages.stream()
                            .map(VacationUsage::getUsedTime)
                            .filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    // 사용 예정 휴가 일수 계산
                    BigDecimal scheduledVacationDays = pendingGrants.stream()
                            .map(VacationGrant::getGrantTime)
                            .filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    // 잔여 휴가 일수 계산 (총 휴가 - 사용 휴가 - 사용 예정)
                    BigDecimal remainingVacationDays = totalVacationDays
                            .subtract(usedVacationDays)
                            .subtract(scheduledVacationDays);

                    return VacationServiceDto.builder()
                            .user(user)
                            .departmentName(departmentName)
                            .totalVacationDays(totalVacationDays)
                            .usedVacationDays(usedVacationDays)
                            .scheduledVacationDays(scheduledVacationDays)
                            .remainingVacationDays(remainingVacationDays)
                            .build();
                })
                .toList();
    }

    // ========================================
    // Helper Methods (Plan 기반)
    // ========================================

    /**
     * 사용자에게 특정 휴가 정책이 할당되어 있는지 확인 (Plan 기반)<br>
     * 사용자의 Plan에 포함된 정책인지 확인
     *
     * @param userId 사용자 ID
     * @param policyId 휴가 정책 ID
     * @return 정책 할당 여부
     */
    private boolean isUserHasVacationPolicy(String userId, Long policyId) {
        // 사용자의 Plan 목록 조회
        List<UserVacationPlan> userPlans = userVacationPlanRepository.findByUserIdWithPlanAndPolicies(userId);

        // Plan에 포함된 정책 중 해당 policyId가 있는지 확인
        return userPlans.stream()
                .filter(uvp -> YNType.isN(uvp.getIsDeleted()))
                .anyMatch(uvp -> uvp.getVacationPlan().hasPolicy(policyId));
    }

}
