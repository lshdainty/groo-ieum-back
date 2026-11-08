package io.groo.ieum.service;

import io.groo.ieum.common.time.CompanyClock;
import io.groo.core.exception.EntityNotFoundException;
import io.groo.core.exception.ForbiddenException;
import io.groo.core.exception.InvalidValueException;
import io.groo.core.type.CountryCode;
import io.groo.core.type.YNType;
import io.groo.ieum.holiday.domain.Holiday;
import io.groo.ieum.holiday.service.HolidayService;
import io.groo.ieum.holiday.type.HolidayType;
import io.groo.ieum.user.domain.User;
import io.groo.ieum.user.repository.UserRepository;
import io.groo.ieum.user.service.UserService;
import io.groo.ieum.vacation.repository.VacationUsageRepository;
import io.groo.ieum.work.domain.WorkCode;
import io.groo.ieum.work.domain.WorkHistory;
import io.groo.ieum.work.repository.WorkCodeRepository;
import io.groo.ieum.work.repository.WorkHistoryRepository;
import io.groo.ieum.work.repository.dto.WorkHistorySearchCondition;
import io.groo.ieum.work.service.WorkHistoryService.TodayWorkStatus;
import io.groo.ieum.work.service.WorkHistoryServiceImpl;
import io.groo.ieum.work.service.dto.WorkHistoryServiceDto;
import io.groo.ieum.work.type.CodeType;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.Mockito.never;

@Slf4j
@ExtendWith(MockitoExtension.class)
@DisplayName("업무 이력 서비스 테스트")
class WorkHistoryServiceTest {

    @Mock
    private WorkHistoryRepository workHistoryRepository;

    @Mock
    private WorkCodeRepository workCodeRepository;

    @Mock
    private UserService userService;

    @Mock
    private HolidayService holidayService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private VacationUsageRepository vacationUsageRepository;

    // 날짜 판정용 — @InjectMocks 가 null 을 넣으면 NPE. 실물을 주입하되 회사 조회는 비어
    // 서비스 기준(Asia/Seoul)으로 폴백한다(기존 테스트가 기대하던 KST 동작 유지).
    @Spy
    private CompanyClock companyClock = new CompanyClock(
            org.mockito.Mockito.mock(io.groo.ieum.company.repository.CompanyRepository.class),
            new io.groo.core.time.ServiceClock("Asia/Seoul"));

    @InjectMocks
    private WorkHistoryServiceImpl workHistoryService;

    // 테스트용 User 생성 헬퍼 메소드
    private User createTestUser(String userId) {
        return User.createUser(
                null, userId, "테스트유저", "test@test.com",
                LocalDate.of(1990, 1, 1), "NONE", "9 ~ 18",
                LocalDate.now(), YNType.N, null, null, CountryCode.KR
        );
    }

    private WorkCode createTestWorkCode(Long id, String code, String name, CodeType type) {
        WorkCode workCode = WorkCode.createWorkCode(code, name, type, null, 1);
        ReflectionTestUtils.setField(workCode, "rowId", id);
        return workCode;
    }

    private WorkHistory createTestWorkHistory(Long id, User user, WorkCode group, WorkCode part, WorkCode division) {
        WorkHistory workHistory = WorkHistory.createWorkHistory(
                LocalDate.now(), user, group, part, division, new BigDecimal("8.0000"), "테스트 업무 내용"
        );
        ReflectionTestUtils.setField(workHistory, "rowId", id);
        return workHistory;
    }

    @Nested
    @DisplayName("업무 이력 조회")
    class FindWorkHistory {
        @Test
        @DisplayName("성공 - 업무 이력을 반환한다")
        void findWorkHistorySuccess() {
            // given
            Long id = 1L;
            User user = createTestUser("user1");
            WorkCode group = createTestWorkCode(1L, "GROUP", "그룹", CodeType.LABEL);
            WorkCode part = createTestWorkCode(2L, "PART", "파트", CodeType.OPTION);
            WorkCode division = createTestWorkCode(3L, "DIVISION", "분류", CodeType.OPTION);
            WorkHistory workHistory = createTestWorkHistory(id, user, group, part, division);

            given(workHistoryRepository.findByRowId(id)).willReturn(Optional.of(workHistory));

            // when
            WorkHistoryServiceDto result = workHistoryService.findWorkHistory(id);

            // then
            assertThat(result.getId()).isEqualTo(id);
            assertThat(result.getContent()).isEqualTo("테스트 업무 내용");
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 업무 이력이면 예외가 발생한다")
        void findWorkHistoryFailNotFound() {
            // given
            Long id = 999L;
            given(workHistoryRepository.findByRowId(id)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> workHistoryService.findWorkHistory(id))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("전체 업무 이력 조회")
    class FindAllWorkHistories {
        @Test
        @DisplayName("성공 - 업무 이력 목록을 반환한다")
        void findAllWorkHistoriesSuccess() {
            // given
            User user = createTestUser("user1");
            WorkCode group = createTestWorkCode(1L, "GROUP", "그룹", CodeType.LABEL);
            WorkCode part = createTestWorkCode(2L, "PART", "파트", CodeType.OPTION);
            WorkCode division = createTestWorkCode(3L, "DIVISION", "분류", CodeType.OPTION);
            WorkHistory workHistory1 = createTestWorkHistory(1L, user, group, part, division);
            WorkHistory workHistory2 = createTestWorkHistory(2L, user, group, part, division);

            WorkHistorySearchCondition condition = new WorkHistorySearchCondition();

            given(workHistoryRepository.findAll(condition)).willReturn(List.of(workHistory1, workHistory2));

            // when
            List<WorkHistoryServiceDto> result = workHistoryService.findAllWorkHistories(condition);

            // then
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("성공 - 업무 이력이 없으면 빈 리스트를 반환한다")
        void findAllWorkHistoriesEmpty() {
            // given
            WorkHistorySearchCondition condition = new WorkHistorySearchCondition();
            given(workHistoryRepository.findAll(condition)).willReturn(List.of());

            // when
            List<WorkHistoryServiceDto> result = workHistoryService.findAllWorkHistories(condition);

            // then
            assertThat(result).isEmpty();
        }
    }

    private WorkHistoryServiceDto createDto(String userId) {
        return WorkHistoryServiceDto.builder()
                .date(LocalDate.now())
                .userId(userId)
                .groupCode("GROUP")
                .partCode("PART")
                .classCode("DIVISION")
                .hours(new BigDecimal("4.0000"))
                .content("업무")
                .build();
    }

    private void stubCodes(String... userIds) {
        WorkCode group = createTestWorkCode(1L, "GROUP", "그룹", CodeType.LABEL);
        WorkCode part = createTestWorkCode(2L, "PART", "파트", CodeType.OPTION);
        WorkCode division = createTestWorkCode(3L, "DIVISION", "분류", CodeType.OPTION);
        for (String id : userIds) {
            given(userService.checkUserExist(id)).willReturn(createTestUser(id));
        }
        given(workCodeRepository.findByCode("GROUP")).willReturn(Optional.of(group));
        given(workCodeRepository.findByCode("PART")).willReturn(Optional.of(part));
        given(workCodeRepository.findByCode("DIVISION")).willReturn(Optional.of(division));
    }

    private void assertDenied(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(ForbiddenException.class)
                .extracting(e -> ((ForbiddenException) e).getErrorCode().getCode())
                .isEqualTo("PERMISSION_001");
    }

    @Nested
    @DisplayName("업무 이력 생성")
    class CreateWorkHistory {
        @Test
        @DisplayName("실패 - 남의 이름으로 WORK:MANAGE 없이 만들면 403 이고 저장하지 않는다")
        void createWorkHistoryFailNotOwner() {
            assertDenied(() -> workHistoryService.createWorkHistory(createDto("user1"), "user2", false));
            then(workHistoryRepository).should(never()).save(any(WorkHistory.class));
        }

        @Test
        @DisplayName("성공 - WORK:MANAGE 가 있으면 남의 이름으로도 만들어진다")
        void createWorkHistorySuccessByManager() {
            stubCodes("user1");
            workHistoryService.createWorkHistory(createDto("user1"), "admin", true);
            then(workHistoryRepository).should().save(any(WorkHistory.class));
        }

        @Test
        @DisplayName("성공 - 업무 이력이 생성된다")
        void createWorkHistorySuccess() {
            // given
            String userId = "user1";
            User user = createTestUser(userId);
            WorkCode group = createTestWorkCode(1L, "GROUP", "그룹", CodeType.LABEL);
            WorkCode part = createTestWorkCode(2L, "PART", "파트", CodeType.OPTION);
            WorkCode division = createTestWorkCode(3L, "DIVISION", "분류", CodeType.OPTION);

            WorkHistoryServiceDto dto = WorkHistoryServiceDto.builder()
                    .date(LocalDate.now())
                    .userId(userId)
                    .groupCode("GROUP")
                    .partCode("PART")
                    .classCode("DIVISION")
                    .hours(new BigDecimal("8.0000"))
                    .content("테스트 업무")
                    .build();

            given(userService.checkUserExist(userId)).willReturn(user);
            given(workCodeRepository.findByCode("GROUP")).willReturn(Optional.of(group));
            given(workCodeRepository.findByCode("PART")).willReturn(Optional.of(part));
            given(workCodeRepository.findByCode("DIVISION")).willReturn(Optional.of(division));
            willDoNothing().given(workHistoryRepository).save(any(WorkHistory.class));

            // when
            Long result = workHistoryService.createWorkHistory(dto, "user1", false);

            // then
            then(workHistoryRepository).should().save(any(WorkHistory.class));
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 업무 그룹이면 예외가 발생한다")
        void createWorkHistoryFailGroupNotFound() {
            // given
            String userId = "user1";
            User user = createTestUser(userId);

            WorkHistoryServiceDto dto = WorkHistoryServiceDto.builder()
                    .date(LocalDate.now())
                    .userId(userId)
                    .groupCode("NONEXISTENT")
                    .partCode("PART")
                    .classCode("DIVISION")
                    .hours(new BigDecimal("8.0000"))
                    .content("테스트 업무")
                    .build();

            given(userService.checkUserExist(userId)).willReturn(user);
            given(workCodeRepository.findByCode("NONEXISTENT")).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> workHistoryService.createWorkHistory(dto, "user1", false))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("실패 - 업무 코드가 null이면 예외가 발생한다")
        void createWorkHistoryFailNullCode() {
            // given
            String userId = "user1";
            User user = createTestUser(userId);

            WorkHistoryServiceDto dto = WorkHistoryServiceDto.builder()
                    .date(LocalDate.now())
                    .userId(userId)
                    .groupCode(null)
                    .partCode("PART")
                    .classCode("DIVISION")
                    .hours(new BigDecimal("8.0000"))
                    .content("테스트 업무")
                    .build();

            given(userService.checkUserExist(userId)).willReturn(user);

            // when & then
            assertThatThrownBy(() -> workHistoryService.createWorkHistory(dto, "user1", false))
                    .isInstanceOf(InvalidValueException.class);
        }
    }

    @Nested
    @DisplayName("업무 이력 일괄 생성")
    class CreateWorkHistories {
        @Test
        @DisplayName("실패 - 내 줄에 남의 줄이 섞이면 WORK:MANAGE 없이는 전체 거절이고 저장 호출이 없다")
        void createWorkHistoriesFailMixedNotOwner() {
            List<WorkHistoryServiceDto> list = List.of(createDto("user2"), createDto("user1"), createDto("user2"));
            assertDenied(() -> workHistoryService.createWorkHistories(list, "user2", false));
            then(workHistoryRepository).should(never()).saveAll(any());
        }

        @Test
        @DisplayName("성공 - 내 줄만이면 WORK:MANAGE 없이도 저장된다")
        void createWorkHistoriesSuccessAllOwn() {
            stubCodes("user2");
            workHistoryService.createWorkHistories(List.of(createDto("user2"), createDto("user2")), "user2", false);
            then(workHistoryRepository).should().saveAll(any());
        }

        @Test
        @DisplayName("성공 - WORK:MANAGE 가 있으면 섞인 줄도 전체 저장된다")
        void createWorkHistoriesSuccessMixedByManager() {
            stubCodes("user1", "user2");
            workHistoryService.createWorkHistories(List.of(createDto("user1"), createDto("user2")), "admin", true);
            then(workHistoryRepository).should().saveAll(any());
        }

        @Test
        @DisplayName("성공 - 업무 이력이 일괄 생성된다")
        void createWorkHistoriesSuccess() {
            // given
            String userId = "user1";
            User user = createTestUser(userId);
            WorkCode group = createTestWorkCode(1L, "GROUP", "그룹", CodeType.LABEL);
            WorkCode part = createTestWorkCode(2L, "PART", "파트", CodeType.OPTION);
            WorkCode division = createTestWorkCode(3L, "DIVISION", "분류", CodeType.OPTION);

            WorkHistoryServiceDto dto1 = WorkHistoryServiceDto.builder()
                    .date(LocalDate.now())
                    .userId(userId)
                    .groupCode("GROUP")
                    .partCode("PART")
                    .classCode("DIVISION")
                    .hours(new BigDecimal("4.0000"))
                    .content("업무1")
                    .build();

            WorkHistoryServiceDto dto2 = WorkHistoryServiceDto.builder()
                    .date(LocalDate.now())
                    .userId(userId)
                    .groupCode("GROUP")
                    .partCode("PART")
                    .classCode("DIVISION")
                    .hours(new BigDecimal("4.0000"))
                    .content("업무2")
                    .build();

            given(userService.checkUserExist(userId)).willReturn(user);
            given(workCodeRepository.findByCode("GROUP")).willReturn(Optional.of(group));
            given(workCodeRepository.findByCode("PART")).willReturn(Optional.of(part));
            given(workCodeRepository.findByCode("DIVISION")).willReturn(Optional.of(division));
            willDoNothing().given(workHistoryRepository).saveAll(any());

            // when
            List<Long> result = workHistoryService.createWorkHistories(List.of(dto1, dto2), "user1", false);

            // then
            then(workHistoryRepository).should().saveAll(any());
        }
    }

    @Nested
    @DisplayName("업무 이력 수정")
    class UpdateWorkHistory {
        @Test
        @DisplayName("성공 - 업무 이력이 수정된다")
        void updateWorkHistorySuccess() {
            // given
            Long id = 1L;
            User user = createTestUser("user1");
            WorkCode group = createTestWorkCode(1L, "GROUP", "그룹", CodeType.LABEL);
            WorkCode part = createTestWorkCode(2L, "PART", "파트", CodeType.OPTION);
            WorkCode division = createTestWorkCode(3L, "DIVISION", "분류", CodeType.OPTION);
            WorkHistory workHistory = createTestWorkHistory(id, user, group, part, division);

            WorkHistoryServiceDto dto = WorkHistoryServiceDto.builder()
                    .id(id)
                    .date(LocalDate.now())
                    .userId("user1")
                    .groupCode("GROUP")
                    .partCode("PART")
                    .classCode("DIVISION")
                    .hours(new BigDecimal("4.0000"))
                    .content("수정된 업무 내용")
                    .build();

            given(workHistoryRepository.findByRowId(id)).willReturn(Optional.of(workHistory));
            given(userService.checkUserExist("user1")).willReturn(user);
            given(workCodeRepository.findByCode("GROUP")).willReturn(Optional.of(group));
            given(workCodeRepository.findByCode("PART")).willReturn(Optional.of(part));
            given(workCodeRepository.findByCode("DIVISION")).willReturn(Optional.of(division));

            // when
            workHistoryService.updateWorkHistory(dto, "user1", false);

            // then
            assertThat(workHistory.getHours()).isEqualByComparingTo(new BigDecimal("4.0000"));
            assertThat(workHistory.getContent()).isEqualTo("수정된 업무 내용");
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 업무 이력이면 예외가 발생한다")
        void updateWorkHistoryFailNotFound() {
            // given
            Long id = 999L;
            WorkHistoryServiceDto dto = WorkHistoryServiceDto.builder()
                    .id(id)
                    .date(LocalDate.now())
                    .userId("user1")
                    .groupCode("GROUP")
                    .partCode("PART")
                    .classCode("DIVISION")
                    .hours(new BigDecimal("4.0000"))
                    .content("수정된 업무 내용")
                    .build();

            given(workHistoryRepository.findByRowId(id)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> workHistoryService.updateWorkHistory(dto, "user1", false))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        private WorkHistoryServiceDto updateDto(Long id, String workUserId) {
            return WorkHistoryServiceDto.builder()
                    .id(id)
                    .date(LocalDate.now())
                    .userId(workUserId)
                    .groupCode("GROUP")
                    .partCode("PART")
                    .classCode("DIVISION")
                    .hours(new BigDecimal("4.0000"))
                    .content("수정된 업무 내용")
                    .build();
        }

        @Test
        @DisplayName("실패 - 남의 기록을 WORK:MANAGE 없이 수정하면 403 이고 기록이 그대로다")
        void updateWorkHistoryFailNotOwner() {
            // given
            Long id = 1L;
            User owner = createTestUser("user1");
            WorkCode group = createTestWorkCode(1L, "GROUP", "그룹", CodeType.LABEL);
            WorkCode part = createTestWorkCode(2L, "PART", "파트", CodeType.OPTION);
            WorkCode division = createTestWorkCode(3L, "DIVISION", "분류", CodeType.OPTION);
            WorkHistory workHistory = createTestWorkHistory(id, owner, group, part, division);
            String beforeContent = workHistory.getContent();
            given(workHistoryRepository.findByRowId(id)).willReturn(Optional.of(workHistory));

            // when & then
            assertThatThrownBy(() -> workHistoryService.updateWorkHistory(updateDto(id, "user1"), "user2", false))
                    .isInstanceOf(ForbiddenException.class)
                    .extracting(e -> ((ForbiddenException) e).getErrorCode().getCode())
                    .isEqualTo("PERMISSION_001");
            assertThat(workHistory.getContent()).isEqualTo(beforeContent);
            assertThat(workHistory.getUser()).isSameAs(owner);
        }

        @Test
        @DisplayName("실패 - 본인 기록의 담당자를 남으로 바꾸면 403 이고 기록이 그대로다")
        void updateWorkHistoryFailReassignToOther() {
            // given
            Long id = 1L;
            User owner = createTestUser("user1");
            WorkCode group = createTestWorkCode(1L, "GROUP", "그룹", CodeType.LABEL);
            WorkCode part = createTestWorkCode(2L, "PART", "파트", CodeType.OPTION);
            WorkCode division = createTestWorkCode(3L, "DIVISION", "분류", CodeType.OPTION);
            WorkHistory workHistory = createTestWorkHistory(id, owner, group, part, division);
            String beforeContent = workHistory.getContent();
            given(workHistoryRepository.findByRowId(id)).willReturn(Optional.of(workHistory));

            // when & then
            assertThatThrownBy(() -> workHistoryService.updateWorkHistory(updateDto(id, "user2"), "user1", false))
                    .isInstanceOf(ForbiddenException.class)
                    .extracting(e -> ((ForbiddenException) e).getErrorCode().getCode())
                    .isEqualTo("PERMISSION_001");
            assertThat(workHistory.getContent()).isEqualTo(beforeContent);
            assertThat(workHistory.getUser()).isSameAs(owner);
        }

        @Test
        @DisplayName("성공 - WORK:MANAGE 가 있으면 남의 기록도 수정된다")
        void updateWorkHistorySuccessByManager() {
            // given
            Long id = 1L;
            User owner = createTestUser("user1");
            WorkCode group = createTestWorkCode(1L, "GROUP", "그룹", CodeType.LABEL);
            WorkCode part = createTestWorkCode(2L, "PART", "파트", CodeType.OPTION);
            WorkCode division = createTestWorkCode(3L, "DIVISION", "분류", CodeType.OPTION);
            WorkHistory workHistory = createTestWorkHistory(id, owner, group, part, division);
            given(workHistoryRepository.findByRowId(id)).willReturn(Optional.of(workHistory));
            given(userService.checkUserExist("user1")).willReturn(owner);
            given(workCodeRepository.findByCode("GROUP")).willReturn(Optional.of(group));
            given(workCodeRepository.findByCode("PART")).willReturn(Optional.of(part));
            given(workCodeRepository.findByCode("DIVISION")).willReturn(Optional.of(division));

            // when
            workHistoryService.updateWorkHistory(updateDto(id, "user1"), "admin", true);

            // then
            assertThat(workHistory.getContent()).isEqualTo("수정된 업무 내용");
        }
    }

    @Nested
    @DisplayName("업무 이력 삭제")
    class DeleteWorkHistory {
        @Test
        @DisplayName("성공 - 업무 이력이 삭제된다")
        void deleteWorkHistorySuccess() {
            // given
            Long id = 1L;
            User user = createTestUser("user1");
            WorkCode group = createTestWorkCode(1L, "GROUP", "그룹", CodeType.LABEL);
            WorkCode part = createTestWorkCode(2L, "PART", "파트", CodeType.OPTION);
            WorkCode division = createTestWorkCode(3L, "DIVISION", "분류", CodeType.OPTION);
            WorkHistory workHistory = createTestWorkHistory(id, user, group, part, division);

            given(workHistoryRepository.findByRowId(id)).willReturn(Optional.of(workHistory));
            willDoNothing().given(workHistoryRepository).delete(workHistory);

            // when
            workHistoryService.deleteWorkHistory(id, "user1", false);

            // then
            then(workHistoryRepository).should().delete(workHistory);
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 업무 이력이면 예외가 발생한다")
        void deleteWorkHistoryFailNotFound() {
            // given
            Long id = 999L;
            given(workHistoryRepository.findByRowId(id)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> workHistoryService.deleteWorkHistory(id, "user1", false))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("실패 - 남의 기록을 WORK:MANAGE 없이 삭제하면 403 이고 삭제 호출이 없다")
        void deleteWorkHistoryFailNotOwner() {
            // given
            Long id = 1L;
            User owner = createTestUser("user1");
            WorkCode group = createTestWorkCode(1L, "GROUP", "그룹", CodeType.LABEL);
            WorkCode part = createTestWorkCode(2L, "PART", "파트", CodeType.OPTION);
            WorkCode division = createTestWorkCode(3L, "DIVISION", "분류", CodeType.OPTION);
            WorkHistory workHistory = createTestWorkHistory(id, owner, group, part, division);
            given(workHistoryRepository.findByRowId(id)).willReturn(Optional.of(workHistory));

            // when & then
            assertThatThrownBy(() -> workHistoryService.deleteWorkHistory(id, "user2", false))
                    .isInstanceOf(ForbiddenException.class)
                    .extracting(e -> ((ForbiddenException) e).getErrorCode().getCode())
                    .isEqualTo("PERMISSION_001");
            then(workHistoryRepository).should(never()).delete(any());
        }

        @Test
        @DisplayName("성공 - WORK:MANAGE 가 있으면 남의 기록도 삭제된다")
        void deleteWorkHistorySuccessByManager() {
            // given
            Long id = 1L;
            User owner = createTestUser("user1");
            WorkCode group = createTestWorkCode(1L, "GROUP", "그룹", CodeType.LABEL);
            WorkCode part = createTestWorkCode(2L, "PART", "파트", CodeType.OPTION);
            WorkCode division = createTestWorkCode(3L, "DIVISION", "분류", CodeType.OPTION);
            WorkHistory workHistory = createTestWorkHistory(id, owner, group, part, division);
            given(workHistoryRepository.findByRowId(id)).willReturn(Optional.of(workHistory));
            willDoNothing().given(workHistoryRepository).delete(workHistory);

            // when
            workHistoryService.deleteWorkHistory(id, "admin", true);

            // then
            then(workHistoryRepository).should().delete(workHistory);
        }
    }

    @Nested
    @DisplayName("오늘 업무 상태 확인")
    class CheckTodayWorkStatus {
        @Test
        @DisplayName("성공 - 8시간 이상 근무하면 완료 상태를 반환한다")
        void checkTodayWorkStatusCompleted() {
            // given
            String userId = "user1";
            User user = createTestUser(userId);
            WorkCode group = createTestWorkCode(1L, "GROUP", "그룹", CodeType.LABEL);
            WorkCode part = createTestWorkCode(2L, "PART", "파트", CodeType.OPTION);
            WorkCode division = createTestWorkCode(3L, "DIVISION", "분류", CodeType.OPTION);
            WorkHistory workHistory = createTestWorkHistory(1L, user, group, part, division);

            given(workHistoryRepository.findByUserAndDate(eq(userId), any(LocalDate.class)))
                    .willReturn(List.of(workHistory));

            // when
            TodayWorkStatus result = workHistoryService.checkTodayWorkStatus(userId);

            // then
            assertThat(result.isCompleted()).isTrue();
            assertThat(result.getTotalHours()).isEqualByComparingTo(new BigDecimal("8.0000"));
        }

        @Test
        @DisplayName("성공 - 8시간 미만 근무하면 미완료 상태를 반환한다")
        void checkTodayWorkStatusIncomplete() {
            // given
            String userId = "user1";
            User user = createTestUser(userId);
            WorkCode group = createTestWorkCode(1L, "GROUP", "그룹", CodeType.LABEL);
            WorkCode part = createTestWorkCode(2L, "PART", "파트", CodeType.OPTION);
            WorkCode division = createTestWorkCode(3L, "DIVISION", "분류", CodeType.OPTION);

            WorkHistory workHistory = WorkHistory.createWorkHistory(
                    LocalDate.now(), user, group, part, division, new BigDecimal("4.0000"), "테스트 업무 내용"
            );
            ReflectionTestUtils.setField(workHistory, "rowId", 1L);

            given(workHistoryRepository.findByUserAndDate(eq(userId), any(LocalDate.class)))
                    .willReturn(List.of(workHistory));

            // when
            TodayWorkStatus result = workHistoryService.checkTodayWorkStatus(userId);

            // then
            assertThat(result.isCompleted()).isFalse();
            assertThat(result.getTotalHours()).isEqualByComparingTo(new BigDecimal("4.0000"));
        }

        @Test
        @DisplayName("성공 - 업무 이력이 없으면 0시간 미완료 상태를 반환한다")
        void checkTodayWorkStatusNoHistory() {
            // given
            String userId = "user1";
            given(workHistoryRepository.findByUserAndDate(eq(userId), any(LocalDate.class)))
                    .willReturn(List.of());

            // when
            TodayWorkStatus result = workHistoryService.checkTodayWorkStatus(userId);

            // then
            assertThat(result.isCompleted()).isFalse();
            assertThat(result.getTotalHours()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Nested
    @DisplayName("업무 미등록 날짜 조회")
    class GetUnregisteredWorkDates {
        @Test
        @DisplayName("성공 - 업무 미등록 날짜 목록을 반환한다")
        void getUnregisteredWorkDatesSuccess() {
            // given
            String userId = "user1";
            Integer year = 2025;
            Integer month = 1;
            User user = createTestUser(userId);
            LocalDate startDate = LocalDate.of(2025, 1, 1);
            LocalDate endDate = LocalDate.of(2025, 1, 31);

            given(userService.checkUserExist(userId)).willReturn(user);
            given(holidayService.searchHolidaysByStartEndDate(startDate, endDate, CountryCode.KR))
                    .willReturn(List.of());
            given(workHistoryRepository.findDailyWorkHoursByUserAndPeriod(eq(userId), eq(startDate), eq(endDate)))
                    .willReturn(Map.of());
            given(vacationUsageRepository.findByUserIdAndPeriodForDaily(eq(userId), any(), any()))
                    .willReturn(List.of());

            // when
            List<LocalDate> result = workHistoryService.getUnregisteredWorkDates(userId, year, month);

            // then
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("실패 - 년도가 null이면 예외가 발생한다")
        void getUnregisteredWorkDatesFailNullYear() {
            // given
            String userId = "user1";

            // when & then
            assertThatThrownBy(() -> workHistoryService.getUnregisteredWorkDates(userId, null, 1))
                    .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("실패 - 월이 null이면 예외가 발생한다")
        void getUnregisteredWorkDatesFailNullMonth() {
            // given
            String userId = "user1";

            // when & then
            assertThatThrownBy(() -> workHistoryService.getUnregisteredWorkDates(userId, 2025, null))
                    .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("성공 - 공휴일을 제외한 근무일만 반환한다")
        void getUnregisteredWorkDatesExcludeHolidays() {
            // given
            String userId = "user1";
            Integer year = 2025;
            Integer month = 1;
            User user = createTestUser(userId);
            LocalDate startDate = LocalDate.of(2025, 1, 1);
            LocalDate endDate = LocalDate.of(2025, 1, 31);

            Holiday holiday = Holiday.createHoliday("신정", LocalDate.of(2025, 1, 1), HolidayType.PUBLIC, CountryCode.KR, YNType.N, null, YNType.Y, null);

            given(userService.checkUserExist(userId)).willReturn(user);
            given(holidayService.searchHolidaysByStartEndDate(startDate, endDate, CountryCode.KR))
                    .willReturn(List.of(holiday));
            given(workHistoryRepository.findDailyWorkHoursByUserAndPeriod(eq(userId), eq(startDate), eq(endDate)))
                    .willReturn(Map.of());
            given(vacationUsageRepository.findByUserIdAndPeriodForDaily(eq(userId), any(), any()))
                    .willReturn(List.of());

            // when
            List<LocalDate> result = workHistoryService.getUnregisteredWorkDates(userId, year, month);

            // then
            assertThat(result).doesNotContain(LocalDate.of(2025, 1, 1));
        }
    }

    @Nested
    @DisplayName("업무 이력 일괄 생성 - 추가 케이스")
    class CreateWorkHistoriesAdditional {
        @Test
        @DisplayName("성공 - 빈 리스트를 입력하면 빈 리스트를 반환한다")
        void createWorkHistoriesEmpty() {
            // given
            List<WorkHistoryServiceDto> emptyList = List.of();

            willDoNothing().given(workHistoryRepository).saveAll(any());

            // when
            List<Long> result = workHistoryService.createWorkHistories(emptyList, "user1", false);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("업무 이력 수정 - 추가 케이스")
    class UpdateWorkHistoryAdditional {
        @Test
        @DisplayName("실패 - 존재하지 않는 업무 파트면 예외가 발생한다")
        void updateWorkHistoryFailPartNotFound() {
            // given
            Long id = 1L;
            User user = createTestUser("user1");
            WorkCode group = createTestWorkCode(1L, "GROUP", "그룹", CodeType.LABEL);
            WorkCode part = createTestWorkCode(2L, "PART", "파트", CodeType.OPTION);
            WorkCode division = createTestWorkCode(3L, "DIVISION", "분류", CodeType.OPTION);
            WorkHistory workHistory = createTestWorkHistory(id, user, group, part, division);

            WorkHistoryServiceDto dto = WorkHistoryServiceDto.builder()
                    .id(id)
                    .date(LocalDate.now())
                    .userId("user1")
                    .groupCode("GROUP")
                    .partCode("NONEXISTENT_PART")
                    .classCode("DIVISION")
                    .hours(new BigDecimal("4.0000"))
                    .content("수정된 업무 내용")
                    .build();

            given(workHistoryRepository.findByRowId(id)).willReturn(Optional.of(workHistory));
            given(userService.checkUserExist("user1")).willReturn(user);
            given(workCodeRepository.findByCode("GROUP")).willReturn(Optional.of(group));
            given(workCodeRepository.findByCode("NONEXISTENT_PART")).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> workHistoryService.updateWorkHistory(dto, "user1", false))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 업무 분류면 예외가 발생한다")
        void updateWorkHistoryFailDivisionNotFound() {
            // given
            Long id = 1L;
            User user = createTestUser("user1");
            WorkCode group = createTestWorkCode(1L, "GROUP", "그룹", CodeType.LABEL);
            WorkCode part = createTestWorkCode(2L, "PART", "파트", CodeType.OPTION);
            WorkCode division = createTestWorkCode(3L, "DIVISION", "분류", CodeType.OPTION);
            WorkHistory workHistory = createTestWorkHistory(id, user, group, part, division);

            WorkHistoryServiceDto dto = WorkHistoryServiceDto.builder()
                    .id(id)
                    .date(LocalDate.now())
                    .userId("user1")
                    .groupCode("GROUP")
                    .partCode("PART")
                    .classCode("NONEXISTENT_DIVISION")
                    .hours(new BigDecimal("4.0000"))
                    .content("수정된 업무 내용")
                    .build();

            given(workHistoryRepository.findByRowId(id)).willReturn(Optional.of(workHistory));
            given(userService.checkUserExist("user1")).willReturn(user);
            given(workCodeRepository.findByCode("GROUP")).willReturn(Optional.of(group));
            given(workCodeRepository.findByCode("PART")).willReturn(Optional.of(part));
            given(workCodeRepository.findByCode("NONEXISTENT_DIVISION")).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> workHistoryService.updateWorkHistory(dto, "user1", false))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("업무 이력 생성 - 추가 케이스")
    class CreateWorkHistoryAdditional {
        @Test
        @DisplayName("실패 - 존재하지 않는 업무 파트면 예외가 발생한다")
        void createWorkHistoryFailPartNotFound() {
            // given
            String userId = "user1";
            User user = createTestUser(userId);
            WorkCode group = createTestWorkCode(1L, "GROUP", "그룹", CodeType.LABEL);

            WorkHistoryServiceDto dto = WorkHistoryServiceDto.builder()
                    .date(LocalDate.now())
                    .userId(userId)
                    .groupCode("GROUP")
                    .partCode("NONEXISTENT_PART")
                    .classCode("DIVISION")
                    .hours(new BigDecimal("8.0000"))
                    .content("테스트 업무")
                    .build();

            given(userService.checkUserExist(userId)).willReturn(user);
            given(workCodeRepository.findByCode("GROUP")).willReturn(Optional.of(group));
            given(workCodeRepository.findByCode("NONEXISTENT_PART")).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> workHistoryService.createWorkHistory(dto, "user1", false))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 업무 분류면 예외가 발생한다")
        void createWorkHistoryFailDivisionNotFound() {
            // given
            String userId = "user1";
            User user = createTestUser(userId);
            WorkCode group = createTestWorkCode(1L, "GROUP", "그룹", CodeType.LABEL);
            WorkCode part = createTestWorkCode(2L, "PART", "파트", CodeType.OPTION);

            WorkHistoryServiceDto dto = WorkHistoryServiceDto.builder()
                    .date(LocalDate.now())
                    .userId(userId)
                    .groupCode("GROUP")
                    .partCode("PART")
                    .classCode("NONEXISTENT_DIVISION")
                    .hours(new BigDecimal("8.0000"))
                    .content("테스트 업무")
                    .build();

            given(userService.checkUserExist(userId)).willReturn(user);
            given(workCodeRepository.findByCode("GROUP")).willReturn(Optional.of(group));
            given(workCodeRepository.findByCode("PART")).willReturn(Optional.of(part));
            given(workCodeRepository.findByCode("NONEXISTENT_DIVISION")).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> workHistoryService.createWorkHistory(dto, "user1", false))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("업무 미등록 날짜 조회 - 추가 케이스")
    class GetUnregisteredWorkDatesAdditional {
        @Test
        @DisplayName("성공 - 8시간 이상 등록된 날짜는 제외된다")
        void getUnregisteredWorkDatesExcludeFullWorkDays() {
            // given
            String userId = "user1";
            Integer year = 2025;
            Integer month = 1;
            User user = createTestUser(userId);
            LocalDate startDate = LocalDate.of(2025, 1, 1);
            LocalDate endDate = LocalDate.of(2025, 1, 31);

            // 1월 2일, 3일에 8시간씩 등록됨
            Map<LocalDate, BigDecimal> dailyHoursMap = Map.of(
                    LocalDate.of(2025, 1, 2), new BigDecimal("8.0000"),
                    LocalDate.of(2025, 1, 3), new BigDecimal("8.0000")
            );

            given(userService.checkUserExist(userId)).willReturn(user);
            given(holidayService.searchHolidaysByStartEndDate(startDate, endDate, CountryCode.KR))
                    .willReturn(List.of());
            given(workHistoryRepository.findDailyWorkHoursByUserAndPeriod(eq(userId), eq(startDate), eq(endDate)))
                    .willReturn(dailyHoursMap);
            given(vacationUsageRepository.findByUserIdAndPeriodForDaily(eq(userId), any(), any()))
                    .willReturn(List.of());

            // when
            List<LocalDate> result = workHistoryService.getUnregisteredWorkDates(userId, year, month);

            // then
            assertThat(result).doesNotContain(LocalDate.of(2025, 1, 2));
            assertThat(result).doesNotContain(LocalDate.of(2025, 1, 3));
        }
    }
}
