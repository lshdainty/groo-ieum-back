package io.groo.ieum.service.policy;

import io.groo.core.exception.DuplicateException;
import io.groo.core.exception.InvalidValueException;
import io.groo.ieum.common.exception.HrErrorCode;
import io.groo.core.type.YNType;
import io.groo.ieum.vacation.domain.VacationPolicy;
import io.groo.ieum.vacation.repository.VacationPolicyRepository;
import io.groo.ieum.vacation.service.dto.VacationPolicyServiceDto;
import io.groo.ieum.vacation.service.policy.RepeatGrant;
import io.groo.ieum.vacation.service.policy.description.KoreanRepeatGrantDescriptionGenerator;
import io.groo.ieum.vacation.type.EffectiveType;
import io.groo.ieum.vacation.type.ExpirationType;
import io.groo.ieum.vacation.type.RepeatUnit;
import io.groo.ieum.vacation.type.VacationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RepeatGrant 정책 테스트")
class RepeatGrantTest {

    @Mock
    private VacationPolicyRepository vacationPolicyRepository;

    @InjectMocks
    private RepeatGrant repeatGrant;

    @Nested
    @DisplayName("휴가 정책 등록")
    class RegistVacationPolicy {
        @Test
        @DisplayName("실패 - 중복된 정책명이면 VACATION_POLICY_NAME_DUPLICATE 예외가 발생한다")
        void registPolicyFailDuplicateName() {
            // given
            VacationPolicyServiceDto dto = VacationPolicyServiceDto.builder()
                    .name("연차")
                    .vacationType(VacationType.ANNUAL)
                    .build();

            given(vacationPolicyRepository.existsByName("연차")).willReturn(true);

            // when & then
            assertThatThrownBy(() -> repeatGrant.registVacationPolicy(dto))
                    .isInstanceOf(DuplicateException.class)
                    .extracting("errorCode").isEqualTo(HrErrorCode.VACATION_POLICY_NAME_DUPLICATE);
        }

        @Test
        @DisplayName("성공 - 매년 반복 정책을 등록한다")
        void registYearlyPolicySuccess() {
            // given
            VacationPolicyServiceDto dto = VacationPolicyServiceDto.builder()
                    .name("연차")
                    .desc("연차 휴가")
                    .vacationType(VacationType.ANNUAL)
                    .grantTime(new BigDecimal("15.0000"))
                    .minuteGrantYn(YNType.N)
                    .repeatUnit(RepeatUnit.YEARLY)
                    .repeatInterval(1)
                    .firstGrantDate(LocalDateTime.of(2025, 1, 1, 0, 0))
                    .effectiveType(EffectiveType.IMMEDIATELY)
                    .expirationType(ExpirationType.END_OF_YEAR)
                    .build();

            given(vacationPolicyRepository.existsByName("연차")).willReturn(false);
            willAnswer(invocation -> {
                VacationPolicy policy = invocation.getArgument(0);
                ReflectionTestUtils.setField(policy, "rowId", 1L);
                return null;
            }).given(vacationPolicyRepository).save(any(VacationPolicy.class));

            // when
            Long result = repeatGrant.registVacationPolicy(dto);

            // then
            assertThat(result).isEqualTo(1L);
        }

        @Test
        @DisplayName("실패 - grantTime이 없으면 예외가 발생한다")
        void registPolicyFailNoGrantTime() {
            // given
            VacationPolicyServiceDto dto = VacationPolicyServiceDto.builder()
                    .name("연차")
                    .vacationType(VacationType.ANNUAL)
                    .build();

            given(vacationPolicyRepository.existsByName("연차")).willReturn(false);

            // when & then
            assertThatThrownBy(() -> repeatGrant.registVacationPolicy(dto))
                    .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("실패 - grantTime이 0 이하면 예외가 발생한다")
        void registPolicyFailGrantTimeNotPositive() {
            // given
            VacationPolicyServiceDto dto = VacationPolicyServiceDto.builder()
                    .name("연차")
                    .vacationType(VacationType.ANNUAL)
                    .grantTime(BigDecimal.ZERO)
                    .build();

            given(vacationPolicyRepository.existsByName("연차")).willReturn(false);

            // when & then
            assertThatThrownBy(() -> repeatGrant.registVacationPolicy(dto))
                    .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("실패 - repeatUnit이 없으면 예외가 발생한다")
        void registPolicyFailNoRepeatUnit() {
            // given
            VacationPolicyServiceDto dto = VacationPolicyServiceDto.builder()
                    .name("연차")
                    .vacationType(VacationType.ANNUAL)
                    .grantTime(new BigDecimal("15.0000"))
                    .minuteGrantYn(YNType.N)
                    .build();

            given(vacationPolicyRepository.existsByName("연차")).willReturn(false);

            // when & then
            assertThatThrownBy(() -> repeatGrant.registVacationPolicy(dto))
                    .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("실패 - repeatInterval이 0 이하면 예외가 발생한다")
        void registPolicyFailRepeatIntervalNotPositive() {
            // given
            VacationPolicyServiceDto dto = VacationPolicyServiceDto.builder()
                    .name("연차")
                    .vacationType(VacationType.ANNUAL)
                    .grantTime(new BigDecimal("15.0000"))
                    .minuteGrantYn(YNType.N)
                    .repeatUnit(RepeatUnit.YEARLY)
                    .repeatInterval(0)
                    .build();

            given(vacationPolicyRepository.existsByName("연차")).willReturn(false);

            // when & then
            assertThatThrownBy(() -> repeatGrant.registVacationPolicy(dto))
                    .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("실패 - firstGrantDate가 없으면 예외가 발생한다")
        void registPolicyFailNoFirstGrantDate() {
            // given
            VacationPolicyServiceDto dto = VacationPolicyServiceDto.builder()
                    .name("연차")
                    .vacationType(VacationType.ANNUAL)
                    .grantTime(new BigDecimal("15.0000"))
                    .minuteGrantYn(YNType.N)
                    .repeatUnit(RepeatUnit.YEARLY)
                    .repeatInterval(1)
                    .build();

            given(vacationPolicyRepository.existsByName("연차")).willReturn(false);

            // when & then
            assertThatThrownBy(() -> repeatGrant.registVacationPolicy(dto))
                    .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("실패 - YEARLY인데 월 없이 일만 있으면 예외가 발생한다")
        void registPolicyFailYearlyDayWithoutMonth() {
            // given
            VacationPolicyServiceDto dto = VacationPolicyServiceDto.builder()
                    .name("연차")
                    .vacationType(VacationType.ANNUAL)
                    .grantTime(new BigDecimal("15.0000"))
                    .minuteGrantYn(YNType.N)
                    .repeatUnit(RepeatUnit.YEARLY)
                    .repeatInterval(1)
                    .specificDays(15)
                    .firstGrantDate(LocalDateTime.of(2025, 1, 1, 0, 0))
                    .effectiveType(EffectiveType.IMMEDIATELY)
                    .expirationType(ExpirationType.END_OF_YEAR)
                    .build();

            given(vacationPolicyRepository.existsByName("연차")).willReturn(false);

            // when & then
            assertThatThrownBy(() -> repeatGrant.registVacationPolicy(dto))
                    .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("실패 - MONTHLY인데 월이 있으면 예외가 발생한다")
        void registPolicyFailMonthlyWithMonth() {
            // given
            VacationPolicyServiceDto dto = VacationPolicyServiceDto.builder()
                    .name("월차")
                    .vacationType(VacationType.ANNUAL)
                    .grantTime(new BigDecimal("1.0000"))
                    .minuteGrantYn(YNType.N)
                    .repeatUnit(RepeatUnit.MONTHLY)
                    .repeatInterval(1)
                    .specificMonths(6)
                    .firstGrantDate(LocalDateTime.of(2025, 1, 1, 0, 0))
                    .effectiveType(EffectiveType.IMMEDIATELY)
                    .expirationType(ExpirationType.END_OF_YEAR)
                    .build();

            given(vacationPolicyRepository.existsByName("월차")).willReturn(false);

            // when & then
            assertThatThrownBy(() -> repeatGrant.registVacationPolicy(dto))
                    .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("실패 - DAILY인데 월/일이 있으면 예외가 발생한다")
        void registPolicyFailDailyWithMonthDay() {
            // given
            VacationPolicyServiceDto dto = VacationPolicyServiceDto.builder()
                    .name("일차")
                    .vacationType(VacationType.ANNUAL)
                    .grantTime(new BigDecimal("0.1250"))
                    .minuteGrantYn(YNType.N)
                    .repeatUnit(RepeatUnit.DAILY)
                    .repeatInterval(1)
                    .specificDays(1)
                    .firstGrantDate(LocalDateTime.of(2025, 1, 1, 0, 0))
                    .effectiveType(EffectiveType.IMMEDIATELY)
                    .expirationType(ExpirationType.END_OF_YEAR)
                    .build();

            given(vacationPolicyRepository.existsByName("일차")).willReturn(false);

            // when & then
            assertThatThrownBy(() -> repeatGrant.registVacationPolicy(dto))
                    .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("실패 - 월이 범위를 벗어나면 예외가 발생한다")
        void registPolicyFailInvalidMonth() {
            // given
            VacationPolicyServiceDto dto = VacationPolicyServiceDto.builder()
                    .name("연차")
                    .vacationType(VacationType.ANNUAL)
                    .grantTime(new BigDecimal("15.0000"))
                    .minuteGrantYn(YNType.N)
                    .repeatUnit(RepeatUnit.YEARLY)
                    .repeatInterval(1)
                    .specificMonths(13)
                    .firstGrantDate(LocalDateTime.of(2025, 1, 1, 0, 0))
                    .effectiveType(EffectiveType.IMMEDIATELY)
                    .expirationType(ExpirationType.END_OF_YEAR)
                    .build();

            given(vacationPolicyRepository.existsByName("연차")).willReturn(false);

            // when & then
            assertThatThrownBy(() -> repeatGrant.registVacationPolicy(dto))
                    .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("실패 - 일이 범위를 벗어나면 예외가 발생한다")
        void registPolicyFailInvalidDay() {
            // given
            VacationPolicyServiceDto dto = VacationPolicyServiceDto.builder()
                    .name("연차")
                    .vacationType(VacationType.ANNUAL)
                    .grantTime(new BigDecimal("15.0000"))
                    .minuteGrantYn(YNType.N)
                    .repeatUnit(RepeatUnit.YEARLY)
                    .repeatInterval(1)
                    .specificMonths(1)
                    .specificDays(32)
                    .firstGrantDate(LocalDateTime.of(2025, 1, 1, 0, 0))
                    .effectiveType(EffectiveType.IMMEDIATELY)
                    .expirationType(ExpirationType.END_OF_YEAR)
                    .build();

            given(vacationPolicyRepository.existsByName("연차")).willReturn(false);

            // when & then
            assertThatThrownBy(() -> repeatGrant.registVacationPolicy(dto))
                    .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("실패 - repeatInterval이 100을 초과하면 예외가 발생한다")
        void registPolicyFailRepeatIntervalTooLarge() {
            // given
            VacationPolicyServiceDto dto = VacationPolicyServiceDto.builder()
                    .name("연차")
                    .vacationType(VacationType.ANNUAL)
                    .grantTime(new BigDecimal("15.0000"))
                    .minuteGrantYn(YNType.N)
                    .repeatUnit(RepeatUnit.YEARLY)
                    .repeatInterval(101)
                    .firstGrantDate(LocalDateTime.of(2025, 1, 1, 0, 0))
                    .effectiveType(EffectiveType.IMMEDIATELY)
                    .expirationType(ExpirationType.END_OF_YEAR)
                    .build();

            given(vacationPolicyRepository.existsByName("연차")).willReturn(false);

            // when & then
            assertThatThrownBy(() -> repeatGrant.registVacationPolicy(dto))
                    .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("실패 - 1회성 부여인데 maxGrantCount가 없으면 예외가 발생한다")
        void registPolicyFailOneTimeNoMaxCount() {
            // given
            VacationPolicyServiceDto dto = VacationPolicyServiceDto.builder()
                    .name("연차")
                    .vacationType(VacationType.ANNUAL)
                    .grantTime(new BigDecimal("15.0000"))
                    .minuteGrantYn(YNType.N)
                    .repeatUnit(RepeatUnit.YEARLY)
                    .repeatInterval(1)
                    .firstGrantDate(LocalDateTime.of(2025, 1, 1, 0, 0))
                    .isRecurring(YNType.N)
                    .effectiveType(EffectiveType.IMMEDIATELY)
                    .expirationType(ExpirationType.END_OF_YEAR)
                    .build();

            given(vacationPolicyRepository.existsByName("연차")).willReturn(false);

            // when & then
            assertThatThrownBy(() -> repeatGrant.registVacationPolicy(dto))
                    .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("실패 - 반복 부여인데 maxGrantCount가 있으면 예외가 발생한다")
        void registPolicyFailRecurringWithMaxCount() {
            // given
            VacationPolicyServiceDto dto = VacationPolicyServiceDto.builder()
                    .name("연차")
                    .vacationType(VacationType.ANNUAL)
                    .grantTime(new BigDecimal("15.0000"))
                    .minuteGrantYn(YNType.N)
                    .repeatUnit(RepeatUnit.YEARLY)
                    .repeatInterval(1)
                    .firstGrantDate(LocalDateTime.of(2025, 1, 1, 0, 0))
                    .isRecurring(YNType.Y)
                    .maxGrantCount(5)
                    .effectiveType(EffectiveType.IMMEDIATELY)
                    .expirationType(ExpirationType.END_OF_YEAR)
                    .build();

            given(vacationPolicyRepository.existsByName("연차")).willReturn(false);

            // when & then
            assertThatThrownBy(() -> repeatGrant.registVacationPolicy(dto))
                    .isInstanceOf(InvalidValueException.class);
        }
    }

    @Nested
    @DisplayName("다음 부여일 계산")
    class CalculateNextGrantDate {
        @Test
        @DisplayName("성공 - 기준일이 첫 부여일 이전이면 첫 부여일을 반환한다")
        void calculateNextGrantDateBeforeFirst() {
            // given
            VacationPolicy policy = createTestRepeatPolicy(RepeatUnit.YEARLY, 1);
            LocalDate baseDate = LocalDate.of(2024, 6, 1);

            // when
            LocalDate result = repeatGrant.calculateNextGrantDate(policy, baseDate);

            // then
            assertThat(result).isEqualTo(LocalDate.of(2025, 1, 1));
        }

        @Test
        @DisplayName("성공 - 1회성 부여 정책은 첫 부여일이 지나면 null을 반환한다")
        void calculateNextGrantDateOneTimeExpired() {
            // given
            VacationPolicy policy = createTestRepeatPolicy(RepeatUnit.YEARLY, 1);
            ReflectionTestUtils.setField(policy, "isRecurring", YNType.N);
            LocalDate baseDate = LocalDate.of(2025, 1, 2);

            // when
            LocalDate result = repeatGrant.calculateNextGrantDate(policy, baseDate);

            // then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("성공 - DAILY 정책은 기준일에서 interval만큼 더한 날을 반환한다")
        void calculateNextGrantDateDaily() {
            // given
            VacationPolicy policy = createTestRepeatPolicy(RepeatUnit.DAILY, 1);
            LocalDate baseDate = LocalDate.of(2025, 6, 1);

            // when
            LocalDate result = repeatGrant.calculateNextGrantDate(policy, baseDate);

            // then
            assertThat(result).isEqualTo(LocalDate.of(2025, 6, 2));
        }

        @Test
        @DisplayName("성공 - MONTHLY 정책은 다음 달 1일을 반환한다")
        void calculateNextGrantDateMonthly() {
            // given
            VacationPolicy policy = createTestRepeatPolicy(RepeatUnit.MONTHLY, 1);
            LocalDate baseDate = LocalDate.of(2025, 6, 15);

            // when
            LocalDate result = repeatGrant.calculateNextGrantDate(policy, baseDate);

            // then
            assertThat(result).isEqualTo(LocalDate.of(2025, 7, 1));
        }

        @Test
        @DisplayName("성공 - QUARTERLY 정책은 다음 분기 시작일을 반환한다")
        void calculateNextGrantDateQuarterly() {
            // given
            VacationPolicy policy = createTestRepeatPolicy(RepeatUnit.QUARTERLY, 1);
            LocalDate baseDate = LocalDate.of(2025, 2, 15);

            // when
            LocalDate result = repeatGrant.calculateNextGrantDate(policy, baseDate);

            // then
            assertThat(result).isEqualTo(LocalDate.of(2025, 4, 1));
        }

        @Test
        @DisplayName("성공 - HALF 정책은 다음 반기 시작일을 반환한다")
        void calculateNextGrantDateHalf() {
            // given
            VacationPolicy policy = createTestRepeatPolicy(RepeatUnit.HALF, 1);
            LocalDate baseDate = LocalDate.of(2025, 3, 15);

            // when
            LocalDate result = repeatGrant.calculateNextGrantDate(policy, baseDate);

            // then
            assertThat(result).isEqualTo(LocalDate.of(2025, 7, 1));
        }

        @Test
        @DisplayName("성공 - YEARLY 정책은 다음 년도 같은 월/일을 반환한다")
        void calculateNextGrantDateYearly() {
            // given
            VacationPolicy policy = createTestRepeatPolicy(RepeatUnit.YEARLY, 1);
            LocalDate baseDate = LocalDate.of(2025, 6, 1);

            // when
            LocalDate result = repeatGrant.calculateNextGrantDate(policy, baseDate);

            // then
            assertThat(result).isEqualTo(LocalDate.of(2026, 1, 1));
        }
    }

    @Nested
    @DisplayName("한국어 설명 생성")
    class GenerateRepeatGrantDescription {
        private final KoreanRepeatGrantDescriptionGenerator koreanGenerator = new KoreanRepeatGrantDescriptionGenerator();

        @Test
        @DisplayName("성공 - 매년 정책의 설명을 생성한다")
        void generateYearlyDescription() {
            // given
            VacationPolicy policy = createTestRepeatPolicy(RepeatUnit.YEARLY, 1);

            // when
            String result = koreanGenerator.generate(policy);

            // then
            assertThat(result).contains("매년");
            assertThat(result).contains("부여");
        }

        @Test
        @DisplayName("성공 - 매월 정책의 설명을 생성한다")
        void generateMonthlyDescription() {
            // given
            VacationPolicy policy = createTestRepeatPolicy(RepeatUnit.MONTHLY, 1);

            // when
            String result = koreanGenerator.generate(policy);

            // then
            assertThat(result).contains("매월");
        }

        @Test
        @DisplayName("성공 - 2년 간격 정책의 설명을 생성한다")
        void generateBiennialDescription() {
            // given
            VacationPolicy policy = createTestRepeatPolicy(RepeatUnit.YEARLY, 2);

            // when
            String result = koreanGenerator.generate(policy);

            // then
            assertThat(result).contains("2년");
            assertThat(result).contains("간격");
        }

        @Test
        @DisplayName("성공 - null 정책이면 null을 반환한다")
        void generateNullPolicyDescription() {
            // when
            String result = koreanGenerator.generate(null);

            // then
            assertThat(result).isNull();
        }
    }

    private VacationPolicy createTestRepeatPolicy(RepeatUnit repeatUnit, Integer repeatInterval) {
        VacationPolicy policy = VacationPolicy.createRepeatGrantPolicy(
                "테스트 정책", "테스트", VacationType.ANNUAL,
                new BigDecimal("15.0000"), YNType.N,
                repeatUnit, repeatInterval, null, null,
                LocalDateTime.of(2025, 1, 1, 0, 0), YNType.Y, null,
                EffectiveType.IMMEDIATELY, ExpirationType.END_OF_YEAR
        );
        ReflectionTestUtils.setField(policy, "rowId", 1L);
        return policy;
    }
}
