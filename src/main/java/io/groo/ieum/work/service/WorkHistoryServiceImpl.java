package io.groo.ieum.work.service;

import io.groo.core.exception.EntityNotFoundException;
import io.groo.core.exception.ForbiddenException;
import io.groo.core.exception.ErrorCode;
import io.groo.ieum.common.exception.HrErrorCode;
import io.groo.ieum.common.time.CompanyClock;
import io.groo.core.exception.InvalidValueException;
import io.groo.core.type.CountryCode;
import io.groo.ieum.holiday.domain.Holiday;
import io.groo.ieum.holiday.service.HolidayService;
import io.groo.ieum.holiday.type.HolidayType;
import io.groo.ieum.user.domain.User;
import io.groo.ieum.user.repository.UserRepository;
import io.groo.ieum.user.service.UserService;
import io.groo.ieum.vacation.domain.VacationUsage;
import io.groo.ieum.vacation.repository.VacationUsageRepository;
import io.groo.ieum.work.domain.WorkCode;
import io.groo.ieum.work.domain.WorkHistory;
import io.groo.ieum.work.repository.WorkCodeRepository;
import io.groo.ieum.work.repository.WorkHistoryRepository;
import io.groo.ieum.work.repository.dto.WorkHistorySearchCondition;
import io.groo.ieum.work.service.dto.WorkCodeServiceDto;
import io.groo.ieum.work.service.dto.WorkHistoryServiceDto;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class WorkHistoryServiceImpl implements WorkHistoryService {
    private final WorkHistoryRepository workHistoryRepository;
    private final CompanyClock companyClock;
    private final WorkCodeRepository workCodeRepository;
    private final UserService userService;
    private final HolidayService holidayService;
    private final UserRepository userRepository;
    private final VacationUsageRepository vacationUsageRepository;

    @Override
    @Transactional
    public Long createWorkHistory(WorkHistoryServiceDto data, String loginUserId, boolean canManage) {
        validateCreateOwner(data, loginUserId, canManage);
        log.debug("업무 이력 생성 시작: userId={}, date={}", data.getUserId(), data.getDate());
        User user = userService.checkUserExist(data.getUserId());
        WorkCode group = checkWorkCodeExist(data.getGroupCode());
        WorkCode part = checkWorkCodeExist(data.getPartCode());
        WorkCode classes = checkWorkCodeExist(data.getClassCode());

        WorkHistory workHistory = WorkHistory.createWorkHistory(
                data.getDate(),
                user,
                group,
                part,
                classes,
                data.getHours(),
                data.getContent());
        workHistoryRepository.save(workHistory);
        log.info("업무 이력 생성 완료: rowId={}, userId={}, hours={}", workHistory.getRowId(), data.getUserId(), data.getHours());
        return workHistory.getRowId();
    }

    @Override
    @Transactional
    public List<Long> createWorkHistories(List<WorkHistoryServiceDto> dataList, String loginUserId, boolean canManage) {
        dataList.forEach(data -> validateCreateOwner(data, loginUserId, canManage));
        log.debug("업무 이력 일괄 생성 시작: count={}", dataList.size());
        List<WorkHistory> workHistories = dataList.stream().map(data -> {
            User user = userService.checkUserExist(data.getUserId());
            WorkCode group = checkWorkCodeExist(data.getGroupCode());
            WorkCode part = checkWorkCodeExist(data.getPartCode());
            WorkCode classes = checkWorkCodeExist(data.getClassCode());

            return WorkHistory.createWorkHistory(
                    data.getDate(),
                    user,
                    group,
                    part,
                    classes,
                    data.getHours(),
                    data.getContent());
        }).collect(Collectors.toList());

        workHistoryRepository.saveAll(workHistories);
        log.info("업무 이력 일괄 생성 완료: count={}", workHistories.size());

        return workHistories.stream()
                .map(WorkHistory::getRowId)
                .collect(Collectors.toList());
    }

    /**
     * 업무 이력 목록 한 번에 볼 수 있는 최대 기간 — 시작일부터 1년 미만.
     * 목록은 페이지 없이 한꺼번에 내려가므로, 기간 없이 부르면 전 직원의 전 기간이 한 응답에 실렸다(보안 점검 IEU-8).
     * 웹 업무 보고서의 기본 기간은 최근 1개월이라 넉넉하다
     */
    private static final Period MAX_LIST_PERIOD = Period.ofYears(1);

    /**
     * 목록 조회 기간을 {@link #MAX_LIST_PERIOD} 안으로 묶는다(조건 객체에 채워 넣는다).<br>
     * 비어 있는 끝은 다른 끝(둘 다 비면 오늘)에서 1년 폭으로 채우고, 거꾸로이거나 1년 이상이면 거절한다
     */
    private void boundListPeriod(WorkHistorySearchCondition condition) {
        LocalDate start = condition.getStartDate();
        LocalDate end = condition.getEndDate();
        if (start == null && end == null) {
            end = companyClock.today();
        }
        if (start == null) {
            start = end.minus(MAX_LIST_PERIOD).plusDays(1);
        }
        if (end == null) {
            end = start.plus(MAX_LIST_PERIOD).minusDays(1);
        }
        if (end.isBefore(start) || !end.isBefore(start.plus(MAX_LIST_PERIOD))) {
            log.warn("업무 이력 목록 조회 거부 - 기간 범위 초과: start={}, end={}", start, end);
            throw new InvalidValueException(ErrorCode.INVALID_DATE_RANGE);
        }
        condition.setStartDate(start);
        condition.setEndDate(end);
    }

    @Override
    public List<WorkHistoryServiceDto> findAllWorkHistories(WorkHistorySearchCondition condition) {
        boundListPeriod(condition);
        log.debug("업무 이력 목록 조회: condition={}", condition);
        List<WorkHistory> workHistories = workHistoryRepository.findAll(condition);

        return workHistories.stream()
                .map(w -> WorkHistoryServiceDto.builder()
                        .id(w.getRowId())
                        .date(w.getDate())
                        .userId(w.getUser().getId())
                        .userName(w.getUser().getName())
                        .groupName(w.getGroup().getName())
                        .partName(w.getPart().getName())
                        .className(w.getDivision().getName())
                        .groupInfo(convertToWorkCodeDto(w.getGroup()))
                        .partInfo(convertToWorkCodeDto(w.getPart()))
                        .classInfo(convertToWorkCodeDto(w.getDivision()))
                        .hours(w.getHours())
                        .content(w.getContent())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public WorkHistoryServiceDto findWorkHistory(Long id) {
        log.debug("업무 이력 조회: id={}", id);
        WorkHistory w = checkWorkHistoryExist(id);

        return WorkHistoryServiceDto.builder()
                .id(w.getRowId())
                .date(w.getDate())
                .userId(w.getUser().getId())
                .userName(w.getUser().getName())
                .groupName(w.getGroup().getName())
                .partName(w.getPart().getName())
                .className(w.getDivision().getName())
                .groupInfo(convertToWorkCodeDto(w.getGroup()))
                .partInfo(convertToWorkCodeDto(w.getPart()))
                .classInfo(convertToWorkCodeDto(w.getDivision()))
                .hours(w.getHours())
                .content(w.getContent())
                .build();
    }

    @Override
    @Transactional
    public void updateWorkHistory(WorkHistoryServiceDto data, String loginUserId, boolean canManage) {
        log.debug("업무 이력 수정 시작: id={}", data.getId());
        WorkHistory workHistory = checkWorkHistoryExist(data.getId());
        validateWorkHistoryOwnership(workHistory, loginUserId, canManage);
        if (!canManage && !loginUserId.equals(data.getUserId())) {
            log.warn("업무 이력 수정 접근 거부 - 로그인 유저: {}, 변경할 담당자: {}", loginUserId, data.getUserId());
            throw new ForbiddenException(HrErrorCode.PERMISSION_DENIED);
        }
        User user = userService.checkUserExist(data.getUserId());
        WorkCode group = checkWorkCodeExist(data.getGroupCode());
        WorkCode part = checkWorkCodeExist(data.getPartCode());
        WorkCode classes = checkWorkCodeExist(data.getClassCode());

        workHistory.updateWorkHistory(
                data.getDate(),
                user,
                group,
                part,
                classes,
                data.getHours(),
                data.getContent());
        log.info("업무 이력 수정 완료: id={}", data.getId());
    }

    @Override
    @Transactional
    public void deleteWorkHistory(Long id, String loginUserId, boolean canManage) {
        log.debug("업무 이력 삭제 시작: id={}", id);
        WorkHistory workHistory = checkWorkHistoryExist(id);
        validateWorkHistoryOwnership(workHistory, loginUserId, canManage);
        workHistoryRepository.delete(workHistory);
        log.info("업무 이력 삭제 완료: id={}", id);
    }

    /**
     * 만들 기록의 담당자가 로그인 유저가 아니면 WORK:MANAGE 권한이 있을 때만 허용
     */
    private void validateCreateOwner(WorkHistoryServiceDto data, String loginUserId, boolean canManage) {
        if (!canManage && !loginUserId.equals(data.getUserId())) {
            log.warn("업무 이력 생성 접근 거부 - 로그인 유저: {}, 담당자: {}", loginUserId, data.getUserId());
            throw new ForbiddenException(HrErrorCode.PERMISSION_DENIED);
        }
    }

    /**
     * 업무 이력 주인 검증
     * 기록의 지금 주인이 로그인 유저가 아니면 WORK:MANAGE 권한이 있을 때만 허용
     */
    private void validateWorkHistoryOwnership(WorkHistory workHistory, String loginUserId, boolean canManage) {
        String ownerId = workHistory.getUser().getId();
        if (!canManage && !ownerId.equals(loginUserId)) {
            log.warn("업무 이력 접근 거부 - 로그인 유저: {}, 대상 유저: {}", loginUserId, ownerId);
            throw new ForbiddenException(HrErrorCode.PERMISSION_DENIED);
        }
    }

    private WorkHistory checkWorkHistoryExist(Long rowId) {
        Optional<WorkHistory> workHistory = workHistoryRepository.findByRowId(rowId);
        workHistory.orElseThrow(() -> {
            log.warn("업무 이력 조회 실패 - 존재하지 않는 이력: rowId={}", rowId);
            return new EntityNotFoundException(HrErrorCode.WORK_NOT_FOUND);
        });
        return workHistory.get();
    }

    private WorkCode checkWorkCodeExist(String code) {
        if (code == null) {
            log.warn("업무 코드 검증 실패 - 코드 미입력");
            throw new InvalidValueException(HrErrorCode.WORK_CODE_REQUIRED);
        }
        Optional<WorkCode> workCode = workCodeRepository.findByCode(code);
        workCode.orElseThrow(() -> {
            log.warn("업무 코드 조회 실패 - 존재하지 않는 코드: code={}", code);
            return new EntityNotFoundException(HrErrorCode.WORK_CODE_NOT_FOUND);
        });
        return workCode.get();
    }

    private WorkCodeServiceDto convertToWorkCodeDto(WorkCode workCode) {
        if (workCode == null) {
            return null;
        }
        return WorkCodeServiceDto.builder()
                .id(workCode.getRowId())
                .code(workCode.getCode())
                .name(workCode.getName())
                .type(workCode.getType())
                .orderSeq(workCode.getOrderSeq())
                .build();
    }

    /**
     * 엑셀 내려받기는 한 번에 최대 1년까지만 허용한다.
     * 시작일 + 1년(윤일은 {@link LocalDate#plusYears} 가 그 달 말일로 맞춘다) 당일까지가 상한이다.
     * 기간이 비었거나 역전됐거나 1년을 넘으면 INVALID_PARAMETER(400).
     */
    static void validateExcelPeriod(WorkHistorySearchCondition condition) {
        LocalDate start = condition == null ? null : condition.getStartDate();
        LocalDate end = condition == null ? null : condition.getEndDate();
        if (start == null || end == null || end.isBefore(start) || end.isAfter(start.plusYears(1))) {
            log.warn("업무 이력 엑셀 다운로드 거절 - 기간 누락/역전/1년 초과: start={}, end={}", start, end);
            throw new InvalidValueException(ErrorCode.INVALID_PARAMETER);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void downloadWorkHistoryExcel(HttpServletResponse response, WorkHistorySearchCondition condition)
            throws IOException {
        log.debug("업무 이력 엑셀 다운로드 시작: condition={}", condition);
        validateExcelPeriod(condition);
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) { // keep 100 rows in memory
            Sheet sheet = workbook.createSheet("업무 이력");

            // Header
            Row headerRow = sheet.createRow(0);
            String[] headers = { "No", "일자", "담당자", "업무분류", "업무파트", "업무구분", "소요시간", "내용" };
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
            }

            // Data Streaming
            AtomicInteger rowNum = new AtomicInteger(1);
            try (Stream<WorkHistory> workHistoryStream = workHistoryRepository.findAllStream(condition)) {
                workHistoryStream.forEach(workHistory -> {
                    Row row = sheet.createRow(rowNum.getAndIncrement());

                    // No
                    row.createCell(0).setCellValue(rowNum.get() - 1);

                    // 일자
                    row.createCell(1)
                            .setCellValue(workHistory.getDate() != null ? workHistory.getDate().toString() : "");

                    // 담당자
                    row.createCell(2)
                            .setCellValue(workHistory.getUser() != null ? workHistory.getUser().getName() : "");

                    // 업무분류 (Group)
                    row.createCell(3)
                            .setCellValue(workHistory.getGroup() != null ? workHistory.getGroup().getName() : "");

                    // 업무파트 (Part)
                    row.createCell(4)
                            .setCellValue(workHistory.getPart() != null ? workHistory.getPart().getName() : "");

                    // 업무구분 (Division)
                    row.createCell(5)
                            .setCellValue(workHistory.getDivision() != null ? workHistory.getDivision().getName() : "");

                    // 소요시간
                    row.createCell(6)
                            .setCellValue(workHistory.getHours() != null ? workHistory.getHours().toString() : "");

                    // 내용
                    row.createCell(7).setCellValue(workHistory.getContent() != null ? workHistory.getContent() : "");
                });
            }

            // Response Header Setting
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=work_history.xlsx");

            // Write to Output Stream
            workbook.write(response.getOutputStream());
            log.info("업무 이력 엑셀 다운로드 완료");

            // Dispose of temporary files
            workbook.dispose();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void downloadUnregisteredWorkHistoryExcel(HttpServletResponse response, Integer year, Integer month)
            throws IOException {
        log.debug("업무 미등록 리스트 엑셀 다운로드 시작: year={}, month={}", year, month);
        // 년월 유효성 검증
        if (year == null || month == null) {
            log.warn("업무 미등록 리스트 다운로드 실패 - 년월 미입력");
            throw new InvalidValueException(HrErrorCode.WORK_YEAR_MONTH_REQUIRED);
        }

        // 해당 년월의 시작일과 마지막일 계산
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();

        // 해당 기간의 공휴일 조회 (PUBLIC, SUBSTITUTE만 - ETC 제외)
        List<Holiday> holidays = holidayService.searchHolidaysByStartEndDate(startDate, endDate, CountryCode.KR);
        Set<LocalDate> holidayDates = holidays.stream()
                .filter(h -> h.getType() == HolidayType.PUBLIC || h.getType() == HolidayType.SUBSTITUTE)
                .map(Holiday::getDate)
                .collect(Collectors.toSet());

        // 주말과 공휴일을 제외한 근무일 리스트 생성
        List<LocalDate> workingDays = new ArrayList<>();
        LocalDate currentDate = startDate;
        while (!currentDate.isAfter(endDate)) {
            DayOfWeek dayOfWeek = currentDate.getDayOfWeek();
            if (dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY
                    && !holidayDates.contains(currentDate)) {
                workingDays.add(currentDate);
            }
            currentDate = currentDate.plusDays(1);
        }

        // 대상 유저 조회: 삭제되지 않은 유저 + 삭제됐지만 modifyDate가 해당 년월인 유저
        List<User> users = userRepository.findUsers(); // 삭제되지 않은 유저

        // 삭제됐지만 modifyDate가 해당 년월인 유저 추가
        List<User> deletedUsersInMonth = findDeletedUsersInYearMonth(year, month);
        users.addAll(deletedUsersInMonth);

        // 유저 ID 리스트 추출
        List<String> userIds = users.stream()
                .map(User::getId)
                .collect(Collectors.toList());

        // 벌크 조회: 모든 유저의 해당 기간 업무 시간 (단일 쿼리)
        Map<String, Map<LocalDate, BigDecimal>> workHoursMap = workHistoryRepository
                .findDailyWorkHoursByUsersAndPeriod(userIds, startDate, endDate);

        // 벌크 조회: 모든 유저의 해당 기간 휴가 사용 시간 (단일 쿼리)
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.plusDays(1).atStartOfDay();
        Map<String, Map<LocalDate, BigDecimal>> vacationUsedTimeMap = aggregateVacationUsageByUsersAndDate(
                vacationUsageRepository.findByUserIdsAndPeriodForDaily(userIds, startDateTime, endDateTime)
        );

        // 유저 정보 Map 생성 (스트림 처리 시 사용)
        Map<String, User> userMap = users.stream()
                .collect(Collectors.toMap(User::getId, user -> user, (existing, replacement) -> existing));

        BigDecimal requiredHours = new BigDecimal("8.0");
        BigDecimal vacationMultiplier = new BigDecimal("8.0");

        // 엑셀 파일 생성 (스트림 방식)
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            Sheet sheet = workbook.createSheet("업무 이력 미등록 리스트");

            // Header
            Row headerRow = sheet.createRow(0);
            String[] headers = { "No", "유저 ID", "유저명", "일자", "업무 등록 시간", "휴가 사용 시간", "총 등록 시간", "미등록 시간" };
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
            }

            // 스트림을 사용하여 미등록 데이터 생성 및 엑셀 작성
            AtomicInteger rowNum = new AtomicInteger(1);
            userIds.stream()
                    .flatMap(userId -> workingDays.stream()
                            .map(workDate -> {
                                // 해당 유저의 해당 날짜 업무 시간 조회
                                BigDecimal workHours = workHoursMap
                                        .getOrDefault(userId, Collections.emptyMap())
                                        .getOrDefault(workDate, BigDecimal.ZERO);

                                // 해당 유저의 해당 날짜 휴가 사용 시간 조회 (usedTime * 8시간)
                                BigDecimal vacationUsedTime = vacationUsedTimeMap
                                        .getOrDefault(userId, Collections.emptyMap())
                                        .getOrDefault(workDate, BigDecimal.ZERO);
                                BigDecimal vacationHours = vacationUsedTime.multiply(vacationMultiplier);

                                // 총 시간 계산
                                BigDecimal totalHours = workHours.add(vacationHours);

                                // 8시간 미만인 경우에만 반환
                                if (totalHours.compareTo(requiredHours) < 0) {
                                    User user = userMap.get(userId);
                                    BigDecimal missingHours = requiredHours.subtract(totalHours);
                                    return new UnregisteredWorkData(
                                            userId,
                                            user != null ? user.getName() : "",
                                            workDate,
                                            workHours,
                                            vacationHours,
                                            missingHours
                                    );
                                }
                                return null;
                            }))
                    .filter(Objects::nonNull)
                    .forEach(data -> {
                        Row row = sheet.createRow(rowNum.getAndIncrement());
                        row.createCell(0).setCellValue(rowNum.get() - 1);
                        row.createCell(1).setCellValue(data.getUserId());
                        row.createCell(2).setCellValue(data.getUserName());
                        row.createCell(3).setCellValue(data.getWorkDate().toString());
                        row.createCell(4).setCellValue(data.getWorkHours().toString());
                        row.createCell(5).setCellValue(data.getVacationHours().toString());
                        row.createCell(6).setCellValue(data.getWorkHours().add(data.getVacationHours()).toString());
                        row.createCell(7).setCellValue(data.getMissingHours().toString());
                    });

            // Response Header Setting
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition",
                    "attachment; filename=unregistered_work_history_" + year + "_" + month + ".xlsx");

            // Write to Output Stream
            workbook.write(response.getOutputStream());
            log.info("업무 미등록 리스트 엑셀 다운로드 완료: year={}, month={}", year, month);

            // Dispose of temporary files
            workbook.dispose();
        }
    }

    private List<User> findDeletedUsersInYearMonth(Integer year, Integer month) {
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth().plusDays(1); // 마지막 날 포함을 위해 +1일

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atStartOfDay();

        return userRepository.findDeletedUsersByModifyDateBetween(startDateTime, endDateTime);
    }

    private static class UnregisteredWorkData {
        private final String userId;
        private final String userName;
        private final LocalDate workDate;
        private final BigDecimal workHours;
        private final BigDecimal vacationHours;
        private final BigDecimal missingHours;

        public UnregisteredWorkData(String userId, String userName, LocalDate workDate,
                                   BigDecimal workHours, BigDecimal vacationHours, BigDecimal missingHours) {
            this.userId = userId;
            this.userName = userName;
            this.workDate = workDate;
            this.workHours = workHours;
            this.vacationHours = vacationHours;
            this.missingHours = missingHours;
        }

        public String getUserId() { return userId; }
        public String getUserName() { return userName; }
        public LocalDate getWorkDate() { return workDate; }
        public BigDecimal getWorkHours() { return workHours; }
        public BigDecimal getVacationHours() { return vacationHours; }
        public BigDecimal getMissingHours() { return missingHours; }
    }

    @Override
    public TodayWorkStatus checkTodayWorkStatus(String userId) {
        log.debug("오늘 업무 상태 확인: userId={}", userId);
        LocalDate today = companyClock.today();

        // 오늘 날짜의 업무 내역 리스트 조회
        List<WorkHistory> todayWorkHistories = workHistoryRepository.findByUserAndDate(userId, today);

        // 업무 시간 합계 계산
        BigDecimal totalHours = todayWorkHistories.stream()
                .map(WorkHistory::getHours)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal requiredHours = new BigDecimal("8.0");
        boolean isCompleted = totalHours.compareTo(requiredHours) >= 0;

        log.info("Today work status checked - userId: {}, date: {}, totalHours: {}, isCompleted: {}",
                userId, today, totalHours, isCompleted);

        return new TodayWorkStatus(totalHours, requiredHours, isCompleted);
    }

    @Override
    public List<LocalDate> getUnregisteredWorkDates(String userId, Integer year, Integer month) {
        log.debug("업무 미등록 날짜 조회 시작: userId={}, year={}, month={}", userId, year, month);
        // 년월 유효성 검증
        if (year == null || month == null) {
            log.warn("업무 미등록 날짜 조회 실패 - 년월 미입력");
            throw new InvalidValueException(HrErrorCode.WORK_YEAR_MONTH_REQUIRED);
        }

        // 사용자 존재 확인
        User user = userService.checkUserExist(userId);

        // 해당 년월의 시작일과 마지막일 계산
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();

        // 해당 기간의 공휴일 조회 (PUBLIC, SUBSTITUTE만 - ETC 제외)
        List<Holiday> holidays = holidayService.searchHolidaysByStartEndDate(startDate, endDate, CountryCode.KR);
        Set<LocalDate> holidayDates = holidays.stream()
                .filter(h -> h.getType() == HolidayType.PUBLIC || h.getType() == HolidayType.SUBSTITUTE)
                .map(Holiday::getDate)
                .collect(Collectors.toSet());

        // 주말과 공휴일을 제외한 근무일 리스트 생성
        List<LocalDate> workingDays = new ArrayList<>();
        LocalDate currentDate = startDate;
        while (!currentDate.isAfter(endDate)) {
            DayOfWeek dayOfWeek = currentDate.getDayOfWeek();
            if (dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY
                    && !holidayDates.contains(currentDate)) {
                workingDays.add(currentDate);
            }
            currentDate = currentDate.plusDays(1);
        }

        // 기간 내 날짜별 업무 시간 합계 조회 (단일 쿼리)
        Map<LocalDate, BigDecimal> dailyWorkHoursMap = workHistoryRepository
                .findDailyWorkHoursByUserAndPeriod(user.getId(), startDate, endDate);

        // 기간 내 날짜별 휴가 사용 시간 합계 조회 (단일 쿼리)
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.plusDays(1).atStartOfDay();
        Map<LocalDate, BigDecimal> dailyVacationMap = aggregateVacationUsageByDate(
                vacationUsageRepository.findByUserIdAndPeriodForDaily(user.getId(), startDateTime, endDateTime)
        );

        // 미작성 날짜 필터링
        BigDecimal requiredHours = new BigDecimal("8.0");
        BigDecimal vacationMultiplier = new BigDecimal("8.0");

        List<LocalDate> unregisteredDates = workingDays.stream()
                .filter(workDate -> {
                    BigDecimal workHours = dailyWorkHoursMap.getOrDefault(workDate, BigDecimal.ZERO);
                    BigDecimal vacationUsedTime = dailyVacationMap.getOrDefault(workDate, BigDecimal.ZERO);
                    BigDecimal vacationHours = vacationUsedTime.multiply(vacationMultiplier);
                    BigDecimal totalHours = workHours.add(vacationHours);
                    return totalHours.compareTo(requiredHours) < 0;
                })
                .collect(Collectors.toList());

        log.info("Unregistered work dates fetched - userId: {}, year: {}, month: {}, count: {}",
                userId, year, month, unregisteredDates.size());

        return unregisteredDates;
    }

    private Map<LocalDate, BigDecimal> aggregateVacationUsageByDate(List<VacationUsage> usages) {
        Map<LocalDate, BigDecimal> dailyHoursMap = new HashMap<>();
        for (VacationUsage usage : usages) {
            LocalDate date = usage.getStartDate().toLocalDate();
            BigDecimal usedTime = usage.getUsedTime() != null ? usage.getUsedTime() : BigDecimal.ZERO;
            dailyHoursMap.merge(date, usedTime, BigDecimal::add);
        }
        return dailyHoursMap;
    }

    private Map<String, Map<LocalDate, BigDecimal>> aggregateVacationUsageByUsersAndDate(List<VacationUsage> usages) {
        Map<String, Map<LocalDate, BigDecimal>> result = new HashMap<>();
        for (VacationUsage usage : usages) {
            String userId = usage.getUser().getId();
            LocalDate date = usage.getStartDate().toLocalDate();
            BigDecimal usedTime = usage.getUsedTime() != null ? usage.getUsedTime() : BigDecimal.ZERO;

            result.computeIfAbsent(userId, k -> new HashMap<>())
                    .merge(date, usedTime, BigDecimal::add);
        }
        return result;
    }
}
