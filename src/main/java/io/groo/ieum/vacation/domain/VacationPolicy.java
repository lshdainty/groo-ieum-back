package io.groo.ieum.vacation.domain;

import io.groo.ieum.common.domain.AuditingFieldsWithIp;
import io.groo.core.type.YNType;
import io.groo.ieum.vacation.type.EffectiveType;
import io.groo.ieum.vacation.type.ExpirationType;
import io.groo.ieum.vacation.type.GrantMethod;
import io.groo.ieum.vacation.type.RepeatUnit;
import io.groo.ieum.vacation.type.VacationType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)  // -> protected Order() {}와 동일한 의미 (롬복으로 생성자 막기)
@Table(name = "vacation_policy")
public class VacationPolicy extends AuditingFieldsWithIp {
    /**
     * 행 아이디<br>
     * 테이블 관리용 seq
     */
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "row_id")
    private Long rowId;

    /**
     * 휴가 정책 이름<br>
     * 정책을 구분할 수 있는 이름
     */
    @Column(name = "vacation_policy_name", length = 50)
    private String name;

    /**
     * 휴가 정책 설명<br>
     * 사용자가 휴가를 신청하거나 할때<br>
     * 확인할 수 있는 정책 내용
     */
    @Column(name = "vacation_policy_desc", length = 1000)
    private String desc;

    /**
     * 휴가 타입<br>
     * vacation 테이블에서 그룹화하여<br>
     * 휴가 일수를 관리하기 위한 휴가 타입
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "vacation_type", nullable = false, length = 15)
    private VacationType  vacationType;

    /**
     * 휴가 부여 방법<br>
     * 타입에 따라서 휴가가 부여되는<br>
     * 방식이 결정된다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "grant_method", nullable = false, length = 15)
    private GrantMethod grantMethod;

    /**
     * 가변 부여 여부<br>
     * N면 휴가 부여시 grantTime으로 자동 입력<br>
     * Y이면 휴가 부여시 관리자 혹은 OT 시간차에 의해 계산된 시간이 들어감<br>
     * 해당 값이 N이면 무조건 grantTime은 0 이상이어야하고 Y이면 무조건 null이어야함
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "is_flexible_grant", nullable = false, length = 1)
    private YNType isFlexibleGrant;

    /**
     * 휴가 부여 기준 시간<br>
     * 정책에 설정된 휴가 부여 시간
     */
    @Column(name = "grant_time", precision = 7, scale = 4)
    private BigDecimal grantTime;

    /**
     * 분단위 부여 여부<br>
     * 특정 부서의 경우 휴가 부여를 분단위로 할 수 있다.<br>
     * 신청 시 부여일 때 OT의 경우 계산된 값을 통해 부여하는데 해당 값을 통해<br>
     * 분 단위를 버리지않고 활용하여 휴가에 활용한다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "minute_grant_yn", nullable = false, length = 1)
    private YNType minuteGrantYn;

    /**
     * 휴가 부여 반복 단위<br>
     * 반복 부여일 경우에 사용되는 값<br>
     * 해당 단위에 따라 스케줄링되어 휴가가 자동 부여됨
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "repeat_unit", length = 10)
    private RepeatUnit repeatUnit;

    /**
     * 휴가 부여 반복 간격<br>
     * 반복 부여일 경우에 사용되는 값<br>
     * 해당 값에 따라 스케줄링되어 휴가가 자동 부여됨<br>
     * 예) 2년 간격
     */
    @Column(name = "repeat_Interval")
    private Integer repeatInterval;

    /**
     * 특정 월 지정<br>
     * 반복 부여일 경우에 사용되는 값<br>
     * 해당 값에 따라 스케줄링되어 휴가가 자동 부여됨<br>
     * 예) 매년 10월 16일
     */
    @Column(name = "specific_months")
    private Integer specificMonths;

    /**
     * 특정 일 지정<br>
     * 반복 부여일 경우에 사용되는 값<br>
     * 해당 값에 따라 스케줄링되어 휴가가 자동 부여됨<br>
     * 예) 매년 10월 16일
     */
    @Column(name = "specific_days")
    private Integer specificDays;

    /**
     * 첫 부여 일시<br>
     * 정책 생성 시점과는 다른 값<br>
     * 정책은 25년에 생성했지만 정책 실행은 26년도부터 가능하도록 할 때 사용
     *
     * <p>[userClock] 사용자·업무가 정한 벽시계 — 타임존 변환 금지(자정 근처 날짜가 밀린다)
     */
    @Column(name = "first_grant_date")
    private LocalDateTime firstGrantDate;

    /**
     * 반복 부여 여부<br>
     * Y: 계속 반복 부여 (매년, 매월 등)<br>
     * N: 1회만 부여 (N년 후 1회 부여)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "is_recurring", length = 1)
    private YNType isRecurring;

    /**
     * 최대 부여 횟수<br>
     * isRecurring = N일 때 사용<br>
     * null이면 제한 없음, 1이면 1회만 부여<br>
     * 예: "7년 후 1회 부여" → maxGrantCount = 1
     */
    @Column(name = "max_grant_count")
    private Integer maxGrantCount;

    /**
     * 승인 처리 필요 인원수<br>
     * grantMethod = ON_REQUEST일 경우에 사용되는 값<br>
     * 사용자가 휴가를 신청했을 때 승인이 필요한 인원 수<br>
     * null 또는 0: 승인 없이 즉시 부여<br>
     * 1 이상: 지정된 인원 수만큼 승인이 필요<br>
     * 예) 2명의 승인이 필요한 경우 approvalRequiredCount = 2
     */
    @Column(name = "approval_required_count")
    private Integer approvalRequiredCount;

    /**
     * 유효기간 발효일 타입<br>
     * 휴가 생성 시 추가되는 유효기간을 계산하기 위한 타입<br>
     * 지금은 간단하게 당해년도 1월 1일, 생성 즉시 2개만 생성
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "effective_type", length = 30)
    private EffectiveType effectiveType;

    /**
     * 유효기간 만료일 타입<br>
     * 휴가 생성 시 추가되는 유효기간을 계산하기 위한 타입<br>
     * 지금은 간단하게 당해년도 12월 31일, +1~12개월 까지만 생성
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "expiration_type", length = 30)
    private ExpirationType expirationType;

    /**
     * 삭제 가능 여부
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "can_deleted", nullable = false, length = 1)
    private YNType canDeleted;

    /**
     * 삭제 여부
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "is_deleted", nullable = false, length = 1)
    private YNType isDeleted;

    /**
     * 플랜-정책 매핑 목록<br>
     * 해당 정책이 속한 플랜 매핑 리스트
     */
    @BatchSize(size = 100)
    @OneToMany(mappedBy = "vacationPolicy", cascade = CascadeType.ALL)
    private List<VacationPlanPolicy> vacationPlanPolicies = new ArrayList<>();

    /**
     * 휴가 부여 스케줄 목록<br>
     * 해당 정책의 자동 부여 스케줄 리스트
     */
    @BatchSize(size = 100)
    @OneToMany(mappedBy = "vacationPolicy", cascade = CascadeType.ALL)
    private List<VacationGrantSchedule> grantSchedules = new ArrayList<>();

    @BatchSize(size = 100)
    @OneToMany(mappedBy = "policy", cascade = CascadeType.ALL)
    private List<VacationGrant> vacationGrants = new ArrayList<>();

    /**
     * 이름 · 설명 수정<br>
     * 부여 규칙(방법 · 시간 · 반복 · 유효기간 등)은 이미 받은 휴가와 스케줄에 묶여 있어 바꾸지 않는다.
     */
    public void updateNameAndDesc(String name, String desc) {
        this.name = name;
        this.desc = desc;
    }

    // 개발용(테스트 데이터 만들때만 사용 운영에선 삭제 예정)
    public void updateCantDeleted() {
        this.canDeleted = YNType.N;
        this.isDeleted = YNType.N;
    }

    // 개발용(테스트 데이터 만들때만 사용 운영에선 삭제 예정)
    public void updateCanDeleted() {
        this.canDeleted = YNType.Y;
        this.isDeleted = YNType.N;
    }

    /**
     * grantTime 검증 및 설정 편의 메서드<br>
     * isFlexibleGrant가 Y면 grantTime을 null로 강제 설정<br>
     * isFlexibleGrant가 N이면 grantTime을 넘겨받은 값으로 설정
     *
     * @param grantTime 부여 시간
     * @param isFlexibleGrant 가변 부여 여부
     */
    public void validateAndSetGrantTime(BigDecimal grantTime, YNType isFlexibleGrant) {
        if (YNType.isY(isFlexibleGrant)) {
            this.grantTime = null;
            this.isFlexibleGrant = YNType.Y;
        } else {

            this.grantTime = grantTime;
            this.isFlexibleGrant = YNType.N;
        }
    }

    /**
     * 신청시 부여(ON_REQUEST) 휴가 정책 생성 함수<br>
     * 사용자가 휴가를 신청하면 승인 후 부여되는 정책<br>
     * 스케줄러 관련 필드는 사용하지 않음
     *
     * @param name 정책명
     * @param desc 정책 설명
     * @param vacationType 휴가 타입
     * @param grantTime 부여 시간 (isFlexibleGrant N일 경우 필수)
     * @param isFlexibleGrant 가변 부여 여부
     * @param minuteGrantYn 분단위 부여 여부
     * @param approvalRequiredCount 승인 필요 인원수 (null 또는 0이면 즉시 부여)
     * @param effectiveType 유효기간 발효일 타입
     * @param expirationType 유효기간 만료일 타입
     * @return VacationPolicy
     */
    public static VacationPolicy createOnRequestPolicy(String name, String desc, VacationType vacationType, BigDecimal grantTime, YNType isFlexibleGrant, YNType minuteGrantYn, Integer approvalRequiredCount, EffectiveType effectiveType, ExpirationType expirationType) {
        VacationPolicy vacationPolicy = new VacationPolicy();
        vacationPolicy.name = name;
        vacationPolicy.desc = desc;
        vacationPolicy.vacationType = vacationType;
        vacationPolicy.grantMethod = GrantMethod.ON_REQUEST;
        // grantTime 검증 및 설정
        vacationPolicy.validateAndSetGrantTime(grantTime, isFlexibleGrant);
        vacationPolicy.minuteGrantYn = minuteGrantYn;
        vacationPolicy.approvalRequiredCount = approvalRequiredCount;
        vacationPolicy.effectiveType = effectiveType;
        vacationPolicy.expirationType = expirationType;
        // 스케줄러 관련 필드는 모두 null
        vacationPolicy.repeatUnit = null;
        vacationPolicy.repeatInterval = null;
        vacationPolicy.specificMonths = null;
        vacationPolicy.specificDays = null;
        vacationPolicy.firstGrantDate = null;
        vacationPolicy.isRecurring = null;
        vacationPolicy.maxGrantCount = null;
        // 기본 필드
        vacationPolicy.canDeleted = YNType.Y;
        vacationPolicy.isDeleted = YNType.N;
        return vacationPolicy;
    }

    /**
     * 관리자 직접 부여(MANUAL_GRANT) 휴가 정책 생성 함수<br>
     * 관리자가 직접 휴가를 부여하는 정책<br>
     * 스케줄러 관련 필드와 승인 필드는 사용하지 않음
     *
     * @param name 정책명
     * @param desc 정책 설명
     * @param vacationType 휴가 타입
     * @param grantTime 부여 시간 (isFlexibleGrant N일 경우 필수, 관리자가 직접 지정할 수도 있음)
     * @param isFlexibleGrant 가변 부여 여부
     * @param minuteGrantYn 분단위 부여 여부
     * @param effectiveType 유효기간 발효일 타입
     * @param expirationType 유효기간 만료일 타입
     * @return VacationPolicy
     */
    public static VacationPolicy createManualGrantPolicy(String name, String desc, VacationType vacationType, BigDecimal grantTime, YNType isFlexibleGrant, YNType minuteGrantYn, EffectiveType effectiveType, ExpirationType expirationType) {
        VacationPolicy vacationPolicy = new VacationPolicy();
        vacationPolicy.name = name;
        vacationPolicy.desc = desc;
        vacationPolicy.vacationType = vacationType;
        vacationPolicy.grantMethod = GrantMethod.MANUAL_GRANT;
        // grantTime 검증 및 설정
        vacationPolicy.validateAndSetGrantTime(grantTime, isFlexibleGrant);
        vacationPolicy.minuteGrantYn = minuteGrantYn;
        vacationPolicy.effectiveType = effectiveType;
        vacationPolicy.expirationType = expirationType;
        // 스케줄러 관련 필드는 모두 null
        vacationPolicy.repeatUnit = null;
        vacationPolicy.repeatInterval = null;
        vacationPolicy.specificMonths = null;
        vacationPolicy.specificDays = null;
        vacationPolicy.firstGrantDate = null;
        vacationPolicy.isRecurring = null;
        vacationPolicy.maxGrantCount = null;
        // 승인 관련 필드는 null
        vacationPolicy.approvalRequiredCount = null;
        // 기본 필드
        vacationPolicy.canDeleted = YNType.Y;
        vacationPolicy.isDeleted = YNType.N;
        return vacationPolicy;
    }

    /**
     * 반복 부여(REPEAT_GRANT) 휴가 정책 생성 함수<br>
     * 스케줄러에 의해 자동으로 반복 부여되는 정책<br>
     * 모든 스케줄 관련 필드를 사용
     *
     * @param name 정책명
     * @param desc 정책 설명
     * @param vacationType 휴가 타입
     * @param grantTime 부여 시간
     * @param minuteGrantYn 분단위 부여 여부
     * @param repeatUnit 반복 단위
     * @param repeatInterval 반복 간격
     * @param specificMonths 특정 월 (선택)
     * @param specificDays 특정 일 (선택)
     * @param firstGrantDate 첫 부여 시점
     * @param isRecurring 반복 여부
     * @param maxGrantCount 최대 부여 횟수 (1회성일 경우 필수)
     * @param effectiveType 유효기간 발효일 타입
     * @param expirationType 유효기간 만료일 타입
     * @return VacationPolicy
     */
    public static VacationPolicy createRepeatGrantPolicy(String name, String desc, VacationType vacationType, BigDecimal grantTime, YNType minuteGrantYn, RepeatUnit repeatUnit, Integer repeatInterval, Integer specificMonths, Integer specificDays, LocalDateTime firstGrantDate, YNType isRecurring, Integer maxGrantCount, EffectiveType effectiveType, ExpirationType expirationType) {
        VacationPolicy vacationPolicy = new VacationPolicy();
        vacationPolicy.name = name;
        vacationPolicy.desc = desc;
        vacationPolicy.vacationType = vacationType;
        vacationPolicy.grantMethod = GrantMethod.REPEAT_GRANT;
        // 스케줄러에 의한 휴가 생성이므로 가변부여는 없음 무조건 정책 시간이 존재해야함
        vacationPolicy.grantTime = grantTime;
        vacationPolicy.isFlexibleGrant = YNType.N;
        vacationPolicy.minuteGrantYn = minuteGrantYn;
        vacationPolicy.repeatUnit = repeatUnit;
        vacationPolicy.repeatInterval = repeatInterval;
        vacationPolicy.specificMonths = specificMonths;
        vacationPolicy.specificDays = specificDays;
        vacationPolicy.firstGrantDate = firstGrantDate;
        vacationPolicy.isRecurring = isRecurring;
        vacationPolicy.maxGrantCount = maxGrantCount;
        vacationPolicy.effectiveType = effectiveType;
        vacationPolicy.expirationType = expirationType;
        // 승인 관련 필드는 null
        vacationPolicy.approvalRequiredCount = null;
        // 기본 필드
        vacationPolicy.canDeleted = YNType.Y;
        vacationPolicy.isDeleted = YNType.N;
        return vacationPolicy;
    }

    /**
     * 휴가 정책 삭제 함수<br>
     * Entity의 경우 Setter없이 Getter만 사용<br>
     * 해당 메소드를 통해 휴가 정책 삭제할 것
     */
    public void deleteVacationPolicy() {
        if (YNType.isN(this.canDeleted)) {
            return;
        }
        this.isDeleted = YNType.Y;
    }
}
