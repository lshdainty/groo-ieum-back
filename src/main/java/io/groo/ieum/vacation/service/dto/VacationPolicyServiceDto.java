package io.groo.ieum.vacation.service.dto;

import io.groo.core.type.YNType;
import io.groo.ieum.vacation.type.EffectiveType;
import io.groo.ieum.vacation.type.ExpirationType;
import io.groo.ieum.vacation.type.GrantMethod;
import io.groo.ieum.vacation.type.RepeatUnit;
import io.groo.ieum.vacation.type.VacationType;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter @Setter
@Builder
public class VacationPolicyServiceDto {
    private Long id;
    private String name;
    private String desc;
    private VacationType vacationType;
    private GrantMethod grantMethod;
    private BigDecimal grantTime;
    private YNType isFlexibleGrant;  // 가변 부여 여부 (Y: 가변, N: 고정)
    private YNType minuteGrantYn;    // 분단위 부여 여부
    private RepeatUnit repeatUnit;
    private Integer repeatInterval;
    private Integer specificMonths;
    private Integer specificDays;
    private LocalDateTime firstGrantDate;
    private YNType isRecurring;
    private Integer maxGrantCount;
    private EffectiveType effectiveType;
    private ExpirationType expirationType;
    private Integer approvalRequiredCount;
    private YNType canDeleted;       // 삭제 가능 여부 (N: 기본 정책이라 지울 수 없음)
    private String repeatGrantDescription; // 반복 부여 정책의 한국어 설명 (예: "매년 1월 1일 부여")
}
