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

import java.math.BigDecimal;
import java.util.Objects;

@RequiredArgsConstructor
public class OnRequest implements VacationPolicyStrategy {
    private final VacationPolicyRepository vacationPolicyRepository;

    @Override
    public Long registVacationPolicy(VacationPolicyServiceDto data) {
        // 신청시 부여 방식 검증
        validateOnRequestPolicy(data);

        // 반복 단위, 반복 간격, 부여시점 지정 방식, 특정월, 특정일, 첫 부여 시점을 모두 null로 설정하여 강제 저장
        // 직접 신청하는 휴가의 경우 스케줄러가 필요없음
        VacationPolicy vacationPolicy = VacationPolicy.createOnRequestPolicy(
                data.getName(),
                data.getDesc(),
                data.getVacationType(),
                data.getGrantTime(),
                data.getIsFlexibleGrant(),
                data.getMinuteGrantYn(),
                data.getApprovalRequiredCount(),
                data.getEffectiveType(),   // effectiveType
                data.getExpirationType()   // expirationType
        );

        vacationPolicyRepository.save(vacationPolicy);
        return vacationPolicy.getRowId();
    }

    /**
     * 신청시 부여 방식의 휴가 정책 검증
     * 1. 정책명 필수 검증
     * 2. 정책명 중복 검증
     * 3. isFlexibleGrant에 따른 grantTime 검증
     * 4. minuteGrantYn 필수 검증
     *
     * @param data 휴가 정책 데이터
     */
    private void validateOnRequestPolicy(VacationPolicyServiceDto data) {
        // 1. 정책명 필수 검증
        if (Objects.isNull(data.getName()) || data.getName().trim().isEmpty()) {
            throw new InvalidValueException(ErrorCode.INVALID_PARAMETER);
        }

        // 2. 정책명 중복 검증
        if (vacationPolicyRepository.existsByName(data.getName())) {
            throw new DuplicateException(HrErrorCode.VACATION_POLICY_NAME_DUPLICATE);
        }

        // 3. isFlexibleGrant 필수 검증
        if (Objects.isNull(data.getIsFlexibleGrant())) {
            throw new InvalidValueException(ErrorCode.INVALID_PARAMETER);
        }

        // 4. isFlexibleGrant에 따른 grantTime 검증
        if (YNType.isY(data.getIsFlexibleGrant())) {
            // isFlexibleGrant가 Y인 경우: grantTime은 null이어야 함 (가변 부여, 동적 계산)
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

        // 5. minuteGrantYn 필수 검증
        if (Objects.isNull(data.getMinuteGrantYn())) {
            throw new InvalidValueException(ErrorCode.INVALID_PARAMETER);
        }

        // 6. effectiveType 필수 검증
        if (Objects.isNull(data.getEffectiveType())) {
            throw new InvalidValueException(ErrorCode.INVALID_PARAMETER);
        }

        // 7. expirationType 필수 검증
        if (Objects.isNull(data.getExpirationType())) {
            throw new InvalidValueException(ErrorCode.INVALID_PARAMETER);
        }
    }

    /**
     * ON_REQUEST 방식의 부여 시간 계산
     *
     * @param policy 휴가 정책
     * @param userGrantTime 사용자가 입력한 부여 시간 (isFlexibleGrant=Y일 경우)
     * @return 계산된 부여 시간
     */
    public BigDecimal calculateGrantTime(VacationPolicy policy, BigDecimal userGrantTime) {
        // isFlexibleGrant가 N인 경우: 정책에 정의된 시간 사용 (고정 부여)
        if (YNType.isN(policy.getIsFlexibleGrant())) {
            BigDecimal policyGrantTime = policy.getGrantTime();
            if (Objects.isNull(policyGrantTime)) {
                throw new InvalidValueException(ErrorCode.INVALID_PARAMETER);
            }
            return policyGrantTime;
        }

        // isFlexibleGrant가 Y인 경우: 사용자가 입력한 시간 사용 (가변 부여)
        if (Objects.isNull(userGrantTime)) {
            throw new InvalidValueException(ErrorCode.INVALID_PARAMETER);
        }

        // 사용자 입력값 양수 검증
        if (userGrantTime.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidValueException(ErrorCode.INVALID_PARAMETER);
        }

        return userGrantTime;
    }
}
