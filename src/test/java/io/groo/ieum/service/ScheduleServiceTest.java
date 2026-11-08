package io.groo.ieum.service;

import io.groo.ieum.common.time.CompanyClock;
import io.groo.core.exception.BusinessRuleViolationException;
import io.groo.core.exception.EntityNotFoundException;
import io.groo.core.exception.InvalidValueException;
import io.groo.ieum.common.exception.HrErrorCode;
import io.groo.core.type.CountryCode;
import io.groo.core.type.YNType;
import io.groo.ieum.schedule.domain.Schedule;
import io.groo.ieum.schedule.repository.ScheduleRepository;
import io.groo.ieum.schedule.service.ScheduleService;
import io.groo.ieum.schedule.service.ScheduleServiceImpl;
import io.groo.ieum.schedule.service.dto.ScheduleServiceDto;
import io.groo.ieum.schedule.type.ScheduleType;
import io.groo.ieum.user.domain.User;
import io.groo.ieum.user.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@Slf4j
@ExtendWith(MockitoExtension.class)
@DisplayName("스케줄 서비스 테스트")
class ScheduleServiceTest {
    @Mock
    private ScheduleRepository scheduleRepository;
    @Mock
    private UserService userService;

    // 날짜 판정용 — @InjectMocks 가 null 을 넣으면 NPE. 실물을 주입하되 회사 조회는 비어
    // 서비스 기준(Asia/Seoul)으로 폴백한다(기존 테스트가 기대하던 KST 동작 유지).
    @Spy
    private CompanyClock companyClock = new CompanyClock(
            org.mockito.Mockito.mock(io.groo.ieum.company.repository.CompanyRepository.class),
            new io.groo.core.time.ServiceClock("Asia/Seoul"));

    @InjectMocks
    private ScheduleServiceImpl scheduleService;

    // 테스트용 User 생성 헬퍼 메소드
    private User createTestUser(String id) {
        return User.createUser(
                null, id, "테스트유저", "test@test.com",
                LocalDate.of(1990, 1, 1), "NONE", "9 ~ 18",
                LocalDate.now(), YNType.N, null, null, CountryCode.KR
        );
    }

    @Nested
    @DisplayName("스케줄 등록")
    class RegistSchedule {
        @Test
        @DisplayName("성공 - 스케줄이 정상적으로 저장된다")
        void registScheduleSuccess() {
            // given
            String userId = "test1";
            User user = createTestUser(userId);
            LocalDateTime start = LocalDateTime.now().plusDays(1);
            LocalDateTime end = start.plusHours(8);

            ScheduleServiceDto data = ScheduleServiceDto.builder()
                    .userId(userId)
                    .type(ScheduleType.EDUCATION)
                    .desc("교육")
                    .startDate(start)
                    .endDate(end)
                    .build();

            given(userService.checkUserExist(userId)).willReturn(user);
            willDoNothing().given(scheduleRepository).save(any(Schedule.class));

            // when
            scheduleService.registSchedule(data);

            // then
            then(userService).should().checkUserExist(userId);
            then(scheduleRepository).should().save(any(Schedule.class));
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 사용자로 등록하면 예외가 발생한다")
        void registScheduleFailUserNotFound() {
            // given
            String userId = "nonexistent";
            ScheduleServiceDto data = ScheduleServiceDto.builder()
                    .userId(userId)
                    .build();

            given(userService.checkUserExist(userId))
                    .willThrow(new EntityNotFoundException(HrErrorCode.USER_NOT_FOUND));

            // when & then
            assertThatThrownBy(() -> scheduleService.registSchedule(data))
                    .isInstanceOf(EntityNotFoundException.class);
            then(userService).should().checkUserExist(userId);
        }

        // 날짜 범위(시작일 > 종료일) 검증은 Controller DTO 의 @DateRange(Bean Validation)로 옮겨졌다.
        // 서비스는 더 이상 검증하지 않으므로 서비스 레벨 예외 테스트를 제거한다.
    }

    @Nested
    @DisplayName("사용자별 스케줄 조회")
    class SearchSchedulesByUser {
        @Test
        @DisplayName("성공 - 사용자의 스케줄 목록을 반환한다")
        void searchSchedulesByUserSuccess() {
            // given
            String userId = "test1";
            User user = createTestUser(userId);
            List<Schedule> schedules = List.of(
                    Schedule.createSchedule(user, "교육", ScheduleType.EDUCATION,
                            LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(2)),
                    Schedule.createSchedule(user, "출장", ScheduleType.BUSINESSTRIP,
                            LocalDateTime.now().plusDays(10), LocalDateTime.now().plusDays(11))
            );

            given(scheduleRepository.findSchedulesByUserId(userId)).willReturn(schedules);

            // when
            List<Schedule> result = scheduleService.searchSchedulesByUser(userId);

            // then
            then(scheduleRepository).should().findSchedulesByUserId(userId);
            assertThat(result).hasSize(2);
            assertThat(result).extracting("desc").containsExactly("교육", "출장");
        }
    }

    @Nested
    @DisplayName("기간별 스케줄 조회")
    class SearchSchedulesByPeriod {
        @Test
        @DisplayName("성공 - 기간 내 스케줄 목록을 반환한다")
        void searchSchedulesByPeriodSuccess() {
            // given
            LocalDateTime start = LocalDateTime.of(2025, 1, 1, 0, 0);
            LocalDateTime end = LocalDateTime.of(2025, 12, 31, 23, 59);
            User user = createTestUser("test1");

            List<Schedule> schedules = List.of(
                    Schedule.createSchedule(user, "교육", ScheduleType.EDUCATION,
                            LocalDateTime.of(2025, 4, 20, 0, 0), LocalDateTime.of(2025, 4, 20, 23, 59)),
                    Schedule.createSchedule(user, "출장", ScheduleType.BUSINESSTRIP,
                            LocalDateTime.of(2025, 7, 20, 0, 0), LocalDateTime.of(2025, 7, 20, 23, 59))
            );

            given(scheduleRepository.findSchedulesByPeriod(start, end)).willReturn(schedules);

            // when
            List<Schedule> result = scheduleService.searchSchedulesByPeriod(start, end);

            // then
            then(scheduleRepository).should().findSchedulesByPeriod(start, end);
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("실패 - 시작일이 종료일보다 늦으면 예외가 발생한다")
        void searchSchedulesByPeriodFailStartAfterEnd() {
            // given
            LocalDateTime start = LocalDateTime.now().plusDays(1);
            LocalDateTime end = start.minusDays(2);

            // when & then
            assertThatThrownBy(() -> scheduleService.searchSchedulesByPeriod(start, end))
                    .isInstanceOf(InvalidValueException.class);
        }
    }

    @Nested
    @DisplayName("스케줄 수정")
    class UpdateSchedule {
        @Test
        @DisplayName("성공 - 기존 스케줄을 삭제하고 새로운 스케줄을 등록한다")
        void updateScheduleSuccess() {
            // given
            Long scheduleId = 1L;
            String userId = "test1";
            User user = createTestUser(userId);
            LocalDateTime futureStart = LocalDateTime.now().plusDays(1);
            LocalDateTime futureEnd = futureStart.plusHours(8);

            Schedule existingSchedule = Schedule.createSchedule(user, "기존 교육", ScheduleType.EDUCATION,
                    futureStart, futureEnd);
            setScheduleId(existingSchedule, scheduleId);

            ScheduleServiceDto data = ScheduleServiceDto.builder()
                    .userId(userId)
                    .type(ScheduleType.BUSINESSTRIP)
                    .desc("출장")
                    .startDate(futureStart.plusDays(5))
                    .endDate(futureEnd.plusDays(5))
                    .build();

            given(scheduleRepository.findByRowId(scheduleId)).willReturn(Optional.of(existingSchedule));
            given(userService.checkUserExist(userId)).willReturn(user);
            willDoNothing().given(scheduleRepository).save(any(Schedule.class));

            // when
            Long newScheduleId = scheduleService.updateSchedule(scheduleId, data, userId, false);

            // then
            then(scheduleRepository).should(times(2)).findByRowId(scheduleId);
            then(userService).should().checkUserExist(userId);
            then(scheduleRepository).should().save(any(Schedule.class));
            assertThat(existingSchedule.getIsDeleted()).isEqualTo(YNType.Y);
        }

        private Schedule existing(String ownerId, Long id) {
            User owner = createTestUser(ownerId);
            LocalDateTime s = LocalDateTime.now().plusDays(1);
            Schedule sc = Schedule.createSchedule(owner, "기존", ScheduleType.EDUCATION, s, s.plusHours(8));
            setScheduleId(sc, id);
            return sc;
        }

        private ScheduleServiceDto dataFor(String userId) {
            LocalDateTime s = LocalDateTime.now().plusDays(5);
            return ScheduleServiceDto.builder().userId(userId).type(ScheduleType.BUSINESSTRIP)
                    .desc("출장").startDate(s).endDate(s.plusHours(8)).build();
        }

        @Test
        @DisplayName("실패 - 남의 일정을 SCHEDULE:MANAGE 없이 고치면 403 이고 원래 일정이 지워지지 않는다")
        void updateScheduleFailNotOwner() {
            Schedule other = existing("userB", 1L);
            given(scheduleRepository.findByRowId(1L)).willReturn(Optional.of(other));

            assertThatThrownBy(() -> scheduleService.updateSchedule(1L, dataFor("userA"), "userA", false))
                    .isInstanceOf(io.groo.core.exception.ForbiddenException.class);
            assertThat(other.getIsDeleted()).isEqualTo(YNType.N);
            then(scheduleRepository).should(never()).save(any(Schedule.class));
        }

        @Test
        @DisplayName("실패 - 내 일정을 남의 이름으로 넘기는 수정도 SCHEDULE:MANAGE 없이는 403")
        void updateScheduleFailReassign() {
            Schedule mine = existing("userA", 1L);
            given(scheduleRepository.findByRowId(1L)).willReturn(Optional.of(mine));

            assertThatThrownBy(() -> scheduleService.updateSchedule(1L, dataFor("userB"), "userA", false))
                    .isInstanceOf(io.groo.core.exception.ForbiddenException.class);
            assertThat(mine.getIsDeleted()).isEqualTo(YNType.N);
        }

        @Test
        @DisplayName("성공 - SCHEDULE:MANAGE 가 있으면 남의 일정도 고친다")
        void updateScheduleSuccessByManager() {
            Schedule other = existing("userB", 1L);
            given(scheduleRepository.findByRowId(1L)).willReturn(Optional.of(other));
            given(userService.checkUserExist("userB")).willReturn(createTestUser("userB"));

            scheduleService.updateSchedule(1L, dataFor("userB"), "admin", true);

            assertThat(other.getIsDeleted()).isEqualTo(YNType.Y);
            then(scheduleRepository).should().save(any(Schedule.class));
        }
    }

    @Nested
    @DisplayName("스케줄 삭제")
    class DeleteSchedule {
        @Test
        @DisplayName("성공 - 미래 스케줄이 삭제된다")
        void deleteScheduleSuccess() {
            // given
            Long scheduleId = 1L;
            User user = createTestUser("test1");
            LocalDateTime futureStart = LocalDateTime.now().plusDays(1);
            LocalDateTime futureEnd = futureStart.plusHours(8);

            Schedule schedule = Schedule.createSchedule(user, "교육", ScheduleType.EDUCATION,
                    futureStart, futureEnd);
            setScheduleId(schedule, scheduleId);

            given(scheduleRepository.findByRowId(scheduleId)).willReturn(Optional.of(schedule));

            // when
            scheduleService.deleteSchedule(scheduleId);

            // then
            then(scheduleRepository).should().findByRowId(scheduleId);
            assertThat(schedule.getIsDeleted()).isEqualTo(YNType.Y);
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 스케줄을 삭제하려 하면 예외가 발생한다")
        void deleteScheduleFailNotFound() {
            // given
            Long scheduleId = 999L;
            given(scheduleRepository.findByRowId(scheduleId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> scheduleService.deleteSchedule(scheduleId))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("실패 - 이미 삭제된 스케줄을 삭제하려 하면 예외가 발생한다")
        void deleteScheduleFailAlreadyDeleted() {
            // given
            Long scheduleId = 1L;
            User user = createTestUser("test1");
            LocalDateTime futureStart = LocalDateTime.now().plusDays(1);
            LocalDateTime futureEnd = futureStart.plusHours(8);

            Schedule schedule = Schedule.createSchedule(user, "교육", ScheduleType.EDUCATION,
                    futureStart, futureEnd);
            schedule.deleteSchedule();

            given(scheduleRepository.findByRowId(scheduleId)).willReturn(Optional.of(schedule));

            // when & then
            assertThatThrownBy(() -> scheduleService.deleteSchedule(scheduleId))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("실패 - 과거 스케줄을 삭제하려 하면 예외가 발생한다")
        void deleteScheduleFailPastSchedule() {
            // given
            Long scheduleId = 1L;
            User user = createTestUser("test1");
            LocalDateTime pastStart = LocalDateTime.now().minusDays(3);
            LocalDateTime pastEnd = pastStart.plusHours(8);

            Schedule schedule = Schedule.createSchedule(user, "교육", ScheduleType.EDUCATION,
                    pastStart, pastEnd);
            setScheduleId(schedule, scheduleId);

            given(scheduleRepository.findByRowId(scheduleId)).willReturn(Optional.of(schedule));

            // when & then
            assertThatThrownBy(() -> scheduleService.deleteSchedule(scheduleId))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }
    }

    @Nested
    @DisplayName("스케줄 존재 확인")
    class CheckScheduleExist {
        @Test
        @DisplayName("성공 - 존재하는 스케줄을 반환한다")
        void checkScheduleExistSuccess() {
            // given
            Long scheduleId = 1L;
            User user = createTestUser("test1");
            Schedule schedule = Schedule.createSchedule(user, "교육", ScheduleType.EDUCATION,
                    LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(2));
            setScheduleId(schedule, scheduleId);

            given(scheduleRepository.findByRowId(scheduleId)).willReturn(Optional.of(schedule));

            // when
            Schedule result = scheduleService.checkScheduleExist(scheduleId);

            // then
            assertThat(result).isEqualTo(schedule);
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 스케줄이면 예외가 발생한다")
        void checkScheduleExistFailNotFound() {
            // given
            Long scheduleId = 999L;
            given(scheduleRepository.findByRowId(scheduleId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> scheduleService.checkScheduleExist(scheduleId))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("실패 - 삭제된 스케줄이면 예외가 발생한다")
        void checkScheduleExistFailDeleted() {
            // given
            Long scheduleId = 1L;
            User user = createTestUser("test1");
            Schedule schedule = Schedule.createSchedule(user, "교육", ScheduleType.EDUCATION,
                    LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(2));
            schedule.deleteSchedule();

            given(scheduleRepository.findByRowId(scheduleId)).willReturn(Optional.of(schedule));

            // when & then
            assertThatThrownBy(() -> scheduleService.checkScheduleExist(scheduleId))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    // 테스트 헬퍼 메서드
    private void setScheduleId(Schedule schedule, Long id) {
        try {
            java.lang.reflect.Field field = Schedule.class.getDeclaredField("rowId");
            field.setAccessible(true);
            field.set(schedule, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
