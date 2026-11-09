package io.groo.ieum.service.policy;

import io.groo.core.exception.DuplicateException;
import io.groo.core.exception.InvalidValueException;
import io.groo.ieum.common.exception.HrErrorCode;
import io.groo.core.type.YNType;
import io.groo.ieum.vacation.domain.VacationPolicy;
import io.groo.ieum.vacation.repository.VacationPolicyRepository;
import io.groo.ieum.vacation.service.dto.VacationPolicyServiceDto;
import io.groo.ieum.vacation.service.policy.ManualGrant;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ManualGrant 정책 테스트")
class ManualGrantTest {

    @Mock
    private VacationPolicyRepository vacationPolicyRepository;

    @InjectMocks
    private ManualGrant manualGrant;

    @Nested
    @DisplayName("휴가 정책 등록")
    class RegistVacationPolicy {
        @Test
        @DisplayName("성공 - 고정 부여 정책을 등록한다")
        void registFixedGrantPolicySuccess() {
            // given
            VacationPolicyServiceDto dto = VacationPolicyServiceDto.builder()
                    .name("연차")
                    .desc("연차 휴가")
                    .vacationType(VacationType.ANNUAL)
                    .grantTime(new BigDecimal("15.0000"))
                    .isFlexibleGrant(YNType.N)
                    .minuteGrantYn(YNType.N)
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
            Long result = manualGrant.registVacationPolicy(dto);

            // then
            assertThat(result).isEqualTo(1L);
            then(vacationPolicyRepository).should().save(any(VacationPolicy.class));
        }

        @Test
        @DisplayName("성공 - 가변 부여 정책을 등록한다")
        void registFlexibleGrantPolicySuccess() {
            // given
            VacationPolicyServiceDto dto = VacationPolicyServiceDto.builder()
                    .name("경조휴가")
                    .desc("경조 휴가")
                    .vacationType(VacationType.BEREAVEMENT)
                    .isFlexibleGrant(YNType.Y)
                    .minuteGrantYn(YNType.N)
                    .effectiveType(EffectiveType.IMMEDIATELY)
                    .expirationType(ExpirationType.ONE_MONTHS_AFTER_GRANT)
                    .build();

            given(vacationPolicyRepository.existsByName("경조휴가")).willReturn(false);
            willAnswer(invocation -> {
                VacationPolicy policy = invocation.getArgument(0);
                ReflectionTestUtils.setField(policy, "rowId", 1L);
                return null;
            }).given(vacationPolicyRepository).save(any(VacationPolicy.class));

            // when
            Long result = manualGrant.registVacationPolicy(dto);

            // then
            assertThat(result).isEqualTo(1L);
        }

        @Test
        @DisplayName("실패 - 정책명이 없으면 예외가 발생한다")
        void registPolicyFailNoName() {
            // given
            VacationPolicyServiceDto dto = VacationPolicyServiceDto.builder()
                    .vacationType(VacationType.ANNUAL)
                    .build();

            // when & then
            assertThatThrownBy(() -> manualGrant.registVacationPolicy(dto))
                    .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("실패 - 중복된 정책명이면 예외가 발생한다")
        void registPolicyFailDuplicateName() {
            // given
            VacationPolicyServiceDto dto = VacationPolicyServiceDto.builder()
                    .name("연차")
                    .vacationType(VacationType.ANNUAL)
                    .build();

            given(vacationPolicyRepository.existsByName("연차")).willReturn(true);

            // when & then
            assertThatThrownBy(() -> manualGrant.registVacationPolicy(dto))
                    .isInstanceOf(DuplicateException.class)
                    .extracting("errorCode").isEqualTo(HrErrorCode.VACATION_POLICY_NAME_DUPLICATE);
        }

        @Test
        @DisplayName("실패 - 스케줄 관련 필드가 있으면 예외가 발생한다")
        void registPolicyFailWithScheduleFields() {
            // given
            VacationPolicyServiceDto dto = VacationPolicyServiceDto.builder()
                    .name("연차")
                    .vacationType(VacationType.ANNUAL)
                    .repeatUnit(RepeatUnit.YEARLY)
                    .build();

            given(vacationPolicyRepository.existsByName("연차")).willReturn(false);

            // when & then
            assertThatThrownBy(() -> manualGrant.registVacationPolicy(dto))
                    .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("실패 - isFlexibleGrant가 없으면 예외가 발생한다")
        void registPolicyFailNoIsFlexibleGrant() {
            // given
            VacationPolicyServiceDto dto = VacationPolicyServiceDto.builder()
                    .name("연차")
                    .vacationType(VacationType.ANNUAL)
                    .build();

            given(vacationPolicyRepository.existsByName("연차")).willReturn(false);

            // when & then
            assertThatThrownBy(() -> manualGrant.registVacationPolicy(dto))
                    .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("실패 - 가변 부여인데 grantTime이 있으면 예외가 발생한다")
        void registPolicyFailFlexibleWithGrantTime() {
            // given
            VacationPolicyServiceDto dto = VacationPolicyServiceDto.builder()
                    .name("연차")
                    .vacationType(VacationType.ANNUAL)
                    .isFlexibleGrant(YNType.Y)
                    .grantTime(new BigDecimal("15.0000"))
                    .build();

            given(vacationPolicyRepository.existsByName("연차")).willReturn(false);

            // when & then
            assertThatThrownBy(() -> manualGrant.registVacationPolicy(dto))
                    .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("실패 - 고정 부여인데 grantTime이 없으면 예외가 발생한다")
        void registPolicyFailFixedWithoutGrantTime() {
            // given
            VacationPolicyServiceDto dto = VacationPolicyServiceDto.builder()
                    .name("연차")
                    .vacationType(VacationType.ANNUAL)
                    .isFlexibleGrant(YNType.N)
                    .build();

            given(vacationPolicyRepository.existsByName("연차")).willReturn(false);

            // when & then
            assertThatThrownBy(() -> manualGrant.registVacationPolicy(dto))
                    .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("실패 - grantTime이 0 이하면 예외가 발생한다")
        void registPolicyFailGrantTimeNotPositive() {
            // given
            VacationPolicyServiceDto dto = VacationPolicyServiceDto.builder()
                    .name("연차")
                    .vacationType(VacationType.ANNUAL)
                    .isFlexibleGrant(YNType.N)
                    .grantTime(BigDecimal.ZERO)
                    .build();

            given(vacationPolicyRepository.existsByName("연차")).willReturn(false);

            // when & then
            assertThatThrownBy(() -> manualGrant.registVacationPolicy(dto))
                    .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("실패 - minuteGrantYn이 없으면 예외가 발생한다")
        void registPolicyFailNoMinuteGrantYn() {
            // given
            VacationPolicyServiceDto dto = VacationPolicyServiceDto.builder()
                    .name("연차")
                    .vacationType(VacationType.ANNUAL)
                    .isFlexibleGrant(YNType.N)
                    .grantTime(new BigDecimal("15.0000"))
                    .build();

            given(vacationPolicyRepository.existsByName("연차")).willReturn(false);

            // when & then
            assertThatThrownBy(() -> manualGrant.registVacationPolicy(dto))
                    .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("실패 - effectiveType이 없으면 예외가 발생한다")
        void registPolicyFailNoEffectiveType() {
            // given
            VacationPolicyServiceDto dto = VacationPolicyServiceDto.builder()
                    .name("연차")
                    .vacationType(VacationType.ANNUAL)
                    .isFlexibleGrant(YNType.N)
                    .grantTime(new BigDecimal("15.0000"))
                    .minuteGrantYn(YNType.N)
                    .build();

            given(vacationPolicyRepository.existsByName("연차")).willReturn(false);

            // when & then
            assertThatThrownBy(() -> manualGrant.registVacationPolicy(dto))
                    .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("실패 - expirationType이 없으면 예외가 발생한다")
        void registPolicyFailNoExpirationType() {
            // given
            VacationPolicyServiceDto dto = VacationPolicyServiceDto.builder()
                    .name("연차")
                    .vacationType(VacationType.ANNUAL)
                    .isFlexibleGrant(YNType.N)
                    .grantTime(new BigDecimal("15.0000"))
                    .minuteGrantYn(YNType.N)
                    .effectiveType(EffectiveType.IMMEDIATELY)
                    .build();

            given(vacationPolicyRepository.existsByName("연차")).willReturn(false);

            // when & then
            assertThatThrownBy(() -> manualGrant.registVacationPolicy(dto))
                    .isInstanceOf(InvalidValueException.class);
        }
    }
}
