package io.groo.ieum.vacation.service.policy;

import io.groo.core.exception.DuplicateException;
import io.groo.core.exception.ErrorCode;
import io.groo.ieum.common.exception.HrErrorCode;
import io.groo.core.exception.InvalidValueException;
import io.groo.core.type.YNType;
import io.groo.ieum.vacation.domain.VacationPolicy;
import io.groo.ieum.vacation.repository.VacationPolicyRepository;
import io.groo.ieum.vacation.service.dto.VacationPolicyServiceDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.Objects;

@Slf4j
@RequiredArgsConstructor
public class ManualGrant implements VacationPolicyStrategy {
    private final VacationPolicyRepository vacationPolicyRepository;

    @Override
    public Long registVacationPolicy(VacationPolicyServiceDto data) {
        // 관리자 직접 부여 방식 검증
        validateManualGrantPolicy(data);

        // 반복 단위, 반복 간격, 부여시점 지정 방식, 특정월, 특정일, 첫 부여 시점을 모두 null로 설정하여 강제 저장
        // 관리자가 부여하는 휴가 정책의 경우 스케줄러가 필요없음
        VacationPolicy vacationPolicy = VacationPolicy.createManualGrantPolicy(
                data.getName(),
                data.getDesc(),
                data.getVacationType(),
                data.getGrantTime(),
                data.getIsFlexibleGrant(),
                data.getMinuteGrantYn(),
                data.getEffectiveType(),
                data.getExpirationType()
        );

        vacationPolicyRepository.save(vacationPolicy);
        return vacationPolicy.getRowId();
    }

    /**
     * 관리자 직접 부여 방식의 휴가 정책 검증
     * 1. 정책명 필수 검증
     * 2. 정책명 중복 검증
     * 3. 스케줄 관련 필드 null 검증
     * 4. isFlexibleGrant에 따른 grantTime 검증
     * 5. minuteGrantYn 필수 검증
     *
     * 참고: 관리자 직접 부여 방식은 부여 단위를 선택하지 않고 관리자가 직접 수량을 지정
     *
     * @param data 휴가 정책 데이터
     */
    private void validateManualGrantPolicy(VacationPolicyServiceDto data) {
        // 1. 정책명 필수 검증
        if (Objects.isNull(data.getName()) || data.getName().trim().isEmpty()) {
            throw new InvalidValueException(ErrorCode.INVALID_PARAMETER);
        }

        // 2. 정책명 중복 검증
        if (vacationPolicyRepository.existsByName(data.getName())) {
            throw new DuplicateException(HrErrorCode.VACATION_POLICY_NAME_DUPLICATE);
        }

        // 3. 스케줄 관련 필드는 모두 null이어야 함 (관리자가 직접 부여하므로 스케줄러 불필요)
        if (Objects.nonNull(data.getRepeatUnit()) || Objects.nonNull(data.getRepeatInterval()) ||
            Objects.nonNull(data.getSpecificMonths()) || Objects.nonNull(data.getSpecificDays())) {
            throw new InvalidValueException(ErrorCode.INVALID_PARAMETER);
        }

        // 4. isFlexibleGrant 필수 검증
        if (Objects.isNull(data.getIsFlexibleGrant())) {
            throw new InvalidValueException(ErrorCode.INVALID_PARAMETER);
        }

        // 5. isFlexibleGrant에 따른 grantTime 검증
        if (YNType.isY(data.getIsFlexibleGrant())) {
            // isFlexibleGrant가 Y인 경우: grantTime은 null이어야 함 (가변 부여)
            if (Objects.nonNull(data.getGrantTime())) {
                throw new InvalidValueException(ErrorCode.INVALID_PARAMETER);
            }
        } else {
            // isFlexibleGrant가 N인 경우: grantTime 필수 및 양수 검증 (고정 부여)
            if (Objects.isNull(data.getGrantTime())) {
                throw new InvalidValueException(ErrorCode.INVALID_PARAMETER);
            }
            if (data.getGrantTime().compareTo(BigDecimal.ZERO) <= 0) {
                throw new InvalidValueException(ErrorCode.INVALID_PARAMETER);
            }
        }

        // 6. minuteGrantYn 필수 검증
        if (Objects.isNull(data.getMinuteGrantYn())) {
            throw new InvalidValueException(ErrorCode.INVALID_PARAMETER);
        }

        // 7. effectiveType 필수 검증
        if (Objects.isNull(data.getEffectiveType())) {
            throw new InvalidValueException(ErrorCode.INVALID_PARAMETER);
        }

        // 8. expirationType 필수 검증
        if (Objects.isNull(data.getExpirationType())) {
            throw new InvalidValueException(ErrorCode.INVALID_PARAMETER);
        }
    }
}