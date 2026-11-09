package io.groo.ieum.service.policy;

import io.groo.core.exception.DuplicateException;
import io.groo.core.exception.InvalidValueException;
import io.groo.ieum.common.exception.HrErrorCode;
import io.groo.core.type.YNType;
import io.groo.ieum.vacation.domain.VacationPolicy;
import io.groo.ieum.vacation.repository.VacationPolicyRepository;
import io.groo.ieum.vacation.service.dto.VacationPolicyServiceDto;
import io.groo.ieum.vacation.service.policy.OnRequest;
import io.groo.ieum.vacation.type.EffectiveType;
import io.groo.ieum.vacation.type.ExpirationType;
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
@DisplayName("OnRequest 정책 테스트")
class OnRequestTest {

    @Mock
    private VacationPolicyRepository vacationPolicyRepository;

    @InjectMocks
    private OnRequest onRequest;

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
                    .approvalRequiredCount(1)
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
            Long result = onRequest.registVacationPolicy(dto);

            // then
            assertThat(result).isEqualTo(1L);
        }

        @Test
        @DisplayName("실패 - 정책명이 비어있으면 예외가 발생한다")
        void registPolicyFailEmptyName() {
            // given
            VacationPolicyServiceDto dto = VacationPolicyServiceDto.builder()
                    .name("  ")
                    .vacationType(VacationType.ANNUAL)
                    .build();

            // when & then
            assertThatThrownBy(() -> onRequest.registVacationPolicy(dto))
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
            assertThatThrownBy(() -> onRequest.registVacationPolicy(dto))
                    .isInstanceOf(DuplicateException.class)
                    .extracting("errorCode").isEqualTo(HrErrorCode.VACATION_POLICY_NAME_DUPLICATE);
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
            assertThatThrownBy(() -> onRequest.registVacationPolicy(dto))
                    .isInstanceOf(InvalidValueException.class);
        }
    }

    @Nested
    @DisplayName("부여 시간 계산")
    class CalculateGrantTime {
        @Test
        @DisplayName("성공 - 고정 부여시 정책에 정의된 시간을 반환한다")
        void calculateFixedGrantTime() {
            // given
            VacationPolicy policy = VacationPolicy.createManualGrantPolicy(
                    "연차", "연차 휴가", VacationType.ANNUAL,
                    new BigDecimal("15.0000"), YNType.N, YNType.N,
                    EffectiveType.IMMEDIATELY, ExpirationType.END_OF_YEAR
            );

            // when
            BigDecimal result = onRequest.calculateGrantTime(policy, null);

            // then
            assertThat(result).isEqualByComparingTo(new BigDecimal("15.0000"));
        }

        @Test
        @DisplayName("성공 - 가변 부여시 사용자 입력값을 반환한다")
        void calculateFlexibleGrantTime() {
            // given
            VacationPolicy policy = VacationPolicy.createManualGrantPolicy(
                    "경조휴가", "경조 휴가", VacationType.BEREAVEMENT,
                    null, YNType.Y, YNType.N,
                    EffectiveType.IMMEDIATELY, ExpirationType.ONE_MONTHS_AFTER_GRANT
            );
            BigDecimal userGrantTime = new BigDecimal("5.0000");

            // when
            BigDecimal result = onRequest.calculateGrantTime(policy, userGrantTime);

            // then
            assertThat(result).isEqualByComparingTo(new BigDecimal("5.0000"));
        }

        @Test
        @DisplayName("실패 - 고정 부여인데 정책에 grantTime이 없으면 예외가 발생한다")
        void calculateGrantTimeFailNoDefinedTime() {
            // given
            VacationPolicy policy = VacationPolicy.createManualGrantPolicy(
                    "연차", "연차 휴가", VacationType.ANNUAL,
                    null, YNType.Y, YNType.N,
                    EffectiveType.IMMEDIATELY, ExpirationType.END_OF_YEAR
            );
            ReflectionTestUtils.setField(policy, "isFlexibleGrant", YNType.N);

            // when & then
            assertThatThrownBy(() -> onRequest.calculateGrantTime(policy, null))
                    .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("실패 - 가변 부여인데 사용자 입력값이 없으면 예외가 발생한다")
        void calculateGrantTimeFailNoUserInput() {
            // given
            VacationPolicy policy = VacationPolicy.createManualGrantPolicy(
                    "경조휴가", "경조 휴가", VacationType.BEREAVEMENT,
                    null, YNType.Y, YNType.N,
                    EffectiveType.IMMEDIATELY, ExpirationType.ONE_MONTHS_AFTER_GRANT
            );

            // when & then
            assertThatThrownBy(() -> onRequest.calculateGrantTime(policy, null))
                    .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("실패 - 사용자 입력값이 0 이하면 예외가 발생한다")
        void calculateGrantTimeFailNonPositiveUserInput() {
            // given
            VacationPolicy policy = VacationPolicy.createManualGrantPolicy(
                    "경조휴가", "경조 휴가", VacationType.BEREAVEMENT,
                    null, YNType.Y, YNType.N,
                    EffectiveType.IMMEDIATELY, ExpirationType.ONE_MONTHS_AFTER_GRANT
            );

            // when & then
            assertThatThrownBy(() -> onRequest.calculateGrantTime(policy, BigDecimal.ZERO))
                    .isInstanceOf(InvalidValueException.class);
        }
    }
}
