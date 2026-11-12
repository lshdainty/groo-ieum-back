package io.groo.ieum.user.domain;

import io.groo.ieum.common.domain.AuditingFieldsWithIp;
import io.groo.core.type.CountryCode;
import io.groo.ieum.department.domain.UserDepartment;
import io.groo.ieum.permission.domain.Role;
import io.groo.ieum.permission.domain.UserRole;
import io.groo.core.type.YNType;
import io.groo.ieum.vacation.domain.UserVacationPlan;
import io.groo.ieum.vacation.domain.VacationApproval;
import io.groo.ieum.vacation.domain.VacationGrant;
import io.groo.ieum.vacation.domain.VacationPlan;
import io.groo.ieum.vacation.domain.VacationPolicy;
import io.groo.ieum.vacation.domain.VacationUsage;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)  // -> protected Order() {}와 동일한 의미 (롬복으로 생성자 막기)
@Table(name = "users")
public class User extends AuditingFieldsWithIp {
    /**
     * 유저 행 아이디<br>
     * HR 내부 관리용 PK (auto increment)
     */
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "row_id")
    private Long rowId;

    /**
     * SSO 사용자 행 아이디<br>
     * SSO에서 발급한 row_id<br>
     * SSO와 HR 간 사용자 연결 키 (nullable - SSO 연동 전 사용자 존재 가능)
     */
    @Column(name = "sso_user_row_id", unique = true)
    private Long ssoUserRowId;

    /**
     * 사용자 아이디<br>
     * 비즈니스 식별자 (고유값)
     */
    @Column(name = "user_id", length = 20, unique = true, nullable = false)
    private String id;

    /**
     * 사용자명<br>
     * 사용자의 실명
     */
    @Column(name = "user_name", nullable = false, length = 20)
    private String name;

    /**
     * 사용자 이메일<br>
     * 사용자의 이메일 주소
     */
    @Column(name = "user_email", nullable = false, length = 100)
    private String email;

    /**
     * 사용자 생일<br>
     * 사용자의 생년월일
     *
     * <p>[userClock] 사용자·업무가 정한 벽시계 — 타임존 변환 금지(자정 근처 날짜가 밀린다)
     */
    @Column(name = "user_birth")
    private LocalDate birth;

    /**
     * 사용자 근무시간<br>
     * 사용자의 근무 시간대 (예: "8 ~ 17", "9 ~ 18", "10 ~ 19", "13 ~ 21")
     */
    @Column(name = "user_work_time", length = 10)
    private String workTime;

    /**
     * 사용자 입사 일자<br>
     * 사용자의 회사 입사 날짜
     *
     * <p>[userClock] 사용자·업무가 정한 벽시계 — 타임존 변환 금지(자정 근처 날짜가 밀린다)
     */
    @Column(name = "join_date")
    private LocalDate joinDate;

    /**
     * 사용자 원소속 회사<br>
     * 사용자가 원래 속한 회사 정보
     */
    @Column(name = "user_origin_company", nullable = false, length = 20)
    private String company;

    /**
     * 프로필 파일명<br>
     * 사용자의 프로필 이미지 원본 파일명
     */
    @Column(name = "profile_name", length = 50)
    private String profileName;

    /**
     * 프로필 파일 고유 UUID<br>
     * 사용자의 프로필 이미지 고유 식별자
     */
    @Column(name = "profile_uuid", length = 36)
    private String profileUUID;

    /**
     * 생일 음력 여부<br>
     * 생일의 음력/양력 여부
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "lunar_yn", nullable = false, length = 1)
    private YNType lunarYN;

    /**
     * 국가 코드<br>
     * 사용자의 국가 정보
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "country_code", nullable = false, length = 2)
    private CountryCode countryCode;

    /**
     * 삭제 여부<br>
     * Soft delete를 위한 플래그
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "is_deleted", nullable = false, length = 1)
    private YNType isDeleted;

    /**
     * 대시보드 레이아웃 정보<br>
     * 사용자 맞춤 대시보드 설정 정보 (JSON 형식)
     */
    @Lob
    @Column(name = "dashboard", length = 65535)
    private String dashboard;

    /**
     * 유저 휴가 부여 목록<br>
     * 사용자에게 부여된 휴가 내역 목록
     */
    @BatchSize(size = 100)
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<VacationGrant> vacationGrants = new ArrayList<>();

    /**
     * 유저 휴가 사용 목록<br>
     * 사용자가 사용한 휴가 내역 목록
     */
    @BatchSize(size = 100)
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<VacationUsage> vacationUsages = new ArrayList<>();

    /**
     * 유저 휴가 플랜 목록<br>
     * 사용자에게 적용된 휴가 플랜 매핑 목록
     */
    @BatchSize(size = 100)
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<UserVacationPlan> userVacationPlans = new ArrayList<>();

    /**
     * 유저 부서 목록<br>
     * 사용자가 소속된 부서 매핑 목록
     */
    @BatchSize(size = 100)
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<UserDepartment> userDepartments = new ArrayList<>();

    /**
     * 유저 휴가 승인 목록<br>
     * 사용자가 승인자로 지정된 휴가 승인 내역 목록
     */
    @BatchSize(size = 100)
    @OneToMany(mappedBy = "approver", cascade = CascadeType.ALL)
    private List<VacationApproval> vacationApprovals = new ArrayList<>();

    /**
     * 유저 역할 목록<br>
     * 사용자에게 부여된 역할 매핑 목록
     */
    @BatchSize(size = 100)
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<UserRole> userRoles = new ArrayList<>();

    /**
     * 내부 계정 표식 소속 값<br>
     * 이 소속의 사용자는 사용자 목록 · 부서원 · 휴가 내역 · 보고서 조회에서 빠진다.
     * 그래서 API 로는 어떤 경로로도 이 값을 설정하지 못한다(운영 SQL 로만 넣는다)
     */
    public static final String SYSTEM_COMPANY = "SYSTEM";

    /**
     * 소속 값이 내부 계정 표식({@link #SYSTEM_COMPANY})인지 확인<br>
     * DB 비교(`company != 'SYSTEM'`)는 대소문자를 가리지 않고 끝 공백을 무시하는 콜레이션이라
     * "system" · "SYSTEM " 도 같은 값으로 걸러진다 — 검사도 같은 기준으로 한다
     */
    public static boolean isSystemCompany(String company) {
        return company != null && SYSTEM_COMPANY.equalsIgnoreCase(company.strip());
    }

    /**
     * 유저 생성 함수<br>
     * Entity의 경우 Setter없이 Getter만 사용<br>
     * 해당 메소드를 통해 유저 생성할 것
     *
     * @param ssoUserRowId SSO에서 발급한 row_id (nullable - SSO 연동 전 사용자)
     * @return User
     */
    public static User createUser(Long ssoUserRowId, String id, String name, String email, LocalDate birth,
                                  String company, String workTime, LocalDate joinDate,
                                  YNType lunarYN, String profileName, String profileUUID, CountryCode countryCode) {
        User user = new User();
        user.ssoUserRowId = ssoUserRowId;
        user.id = id;
        user.name = name;
        user.email = email;
        user.birth = birth;
        user.company = company;
        user.workTime = workTime;
        user.joinDate = joinDate;
        user.lunarYN = lunarYN;
        user.profileName = profileName;
        user.profileUUID = profileUUID;
        user.countryCode = countryCode;
        user.isDeleted = YNType.N;
        return user;
    }

    /**
     * SSO 이벤트 기반 유저 생성 (최소 정보)<br>
     * SSO에서 사용자 생성 이벤트 수신 시 사용
     *
     * @param ssoUserRowId SSO에서 발급한 row_id
     * @param id 사용자 아이디
     * @param name 사용자명
     * @param email 이메일
     * @return User
     */
    public static User createUserFromSso(Long ssoUserRowId, String id, String name, String email) {
        User user = new User();
        user.ssoUserRowId = ssoUserRowId;
        user.id = id;
        user.name = name;
        user.email = email;
        user.isDeleted = YNType.N;
        return user;
    }

    /**
     * SSO 연동 - ssoUserRowId 설정<br>
     * 기존 사용자에 SSO 연결
     *
     * @param ssoUserRowId SSO에서 발급한 row_id
     */
    public void linkToSso(Long ssoUserRowId) {
        this.ssoUserRowId = ssoUserRowId;
    }

    /**
     * SSO 사용자 번호가 이 행의 연결 번호와 같은지 — 둘 중 하나라도 없으면 같지 않다<br>
     * 로그인 아이디는 SSO 이벤트로 바뀔 수 있는 값이라, 토큰을 이 행에 묶을 때는 번호로 대조한다(보안 점검 IEU-6)
     */
    public boolean isLinkedToSsoUser(Long ssoUserNo) {
        return ssoUserNo != null && ssoUserNo.equals(getSsoUserRowId());
    }

    /**
     * 유저 수정 함수<br>
     * Entity의 경우 Setter없이 Getter만 사용<br>
     * 해당 메소드를 통해 유저 수정할 것
     */
    public void updateUser(String userId, String name, String email, List<Role> roles, LocalDate birth,
                           String company, String workTime,
                           YNType lunarYN, String profileName, String profileUUID, String dashboard, CountryCode countryCode) {
        if (!Objects.isNull(userId)) { this.id = userId; }
        if (!Objects.isNull(name)) { this.name = name; }
        if (!Objects.isNull(email)) { this.email = email; }
        if (!Objects.isNull(roles)) {
            // 새로운 역할 코드 Set 생성 (중복 제거)
            Set<String> newRoleCodes = roles.stream()
                    .map(Role::getCode)
                    .collect(Collectors.toSet());

            // 1. 기존 활성화된 역할 중 새로운 목록에 없는 것은 soft delete
            this.userRoles.stream()
                    .filter(ur -> YNType.isN(ur.getIsDeleted()))
                    .filter(ur -> !newRoleCodes.contains(ur.getRole().getCode()))
                    .forEach(UserRole::deleteUserRole);

            // 2. 새로운 역할들을 추가 (중복 방지)
            for (Role role : roles) {
                // 이미 활성화된 역할인지 확인 (soft delete된 것은 무시)
                boolean exists = this.userRoles.stream()
                        .anyMatch(ur -> ur.getRole().getCode().equals(role.getCode())
                                && YNType.isN(ur.getIsDeleted()));

                // 활성화된 역할이 없으면 새로 생성
                if (!exists) {
                    UserRole userRole = UserRole.createUserRole(this, role);
                    this.userRoles.add(userRole);
                }
            }
        }
        if (!Objects.isNull(birth)) { this.birth = birth; }
        if (!Objects.isNull(company)) { this.company = company; }
        if (!Objects.isNull(workTime)) { this.workTime = workTime; }
        if (!Objects.isNull(lunarYN)) { this.lunarYN = lunarYN; }
        if (!Objects.isNull(profileName)) { this.profileName = profileName; }
        if (!Objects.isNull(profileUUID)) { this.profileUUID = profileUUID; }
        if (!Objects.isNull(dashboard)) { this.dashboard = dashboard; }
        if (!Objects.isNull(countryCode)) { this.countryCode = countryCode; }
    }

    /**
     * 프로필 사진 지우기<br>
     * updateUser 는 null 을 "바꾸지 않음"으로 보므로 사진을 지울 때는 이 메소드를 쓴다.<br>
     * 원본 파일은 남긴다(다른 기록이 같은 파일을 가리킬 수 있어 물리 삭제는 하지 않는다)
     */
    public void removeProfile() {
        this.profileName = null;
        this.profileUUID = null;
    }

    /**
     * 유저 삭제 함수<br>
     * Entity의 경우 Setter없이 Getter만 사용<br>
     * 해당 메소드를 통해 유저 삭제할 것
     */
    public void deleteUser() {
        this.isDeleted = YNType.Y;
    }

    /**
     * 대시보드 데이터 수정 함수<br>
     * Entity의 경우 Setter없이 Getter만 사용<br>
     * 해당 메소드를 통해 대시보드 수정할 것
     */
    public void updateDashboard(String dashboard) {
        this.dashboard = dashboard;
    }

    /* 비즈니스 편의 메소드 */
    /**
     * 사용자의 workTime에 맞춰<br>
     * start, end가 담긴 배열을 반환하는 함수
     *
     * @return [startTime, endTime] array
     */
    public List<LocalTime> convertWorkTimeToLocalTime() {
        LocalTime start = LocalTime.of(0,0);
        LocalTime end = LocalTime.of(0,0);

        switch (getWorkTime()) {
            case "8 ~ 17":
                start = LocalTime.of(8,0);
                end = LocalTime.of(17,0);
                break;
            case "9 ~ 18":
                start = LocalTime.of(9,0);
                end = LocalTime.of(18,0);
                break;
            case "10 ~ 19":
                start = LocalTime.of(10,0);
                end = LocalTime.of(19,0);
                break;
            case "13 ~ 21":
                start = LocalTime.of(13,0);
                end = LocalTime.of(21,0);
                break;
        }

        return List.of(start, end);
    }

    /**
     * 날짜가 유저의 유연근무제에 맞춰<br>
     * 정상적으로 설정되어 있는지 확인하는 함수
     *
     * @param startTime 검사 대상 시작 시간
     * @param endTime 검사 대항 종료 시간
     * @return true, false 반환
     */
    public boolean isBetweenWorkTime(LocalTime startTime, LocalTime endTime) {
        List<LocalTime> workTimes = convertWorkTimeToLocalTime();
        return ((startTime.isAfter(workTimes.get(0)) || startTime.equals(workTimes.get(0))) && startTime.isBefore(workTimes.get(1))) &&
                (endTime.isAfter(workTimes.get(0)) && (endTime.isBefore(workTimes.get(1)) || endTime.equals(workTimes.get(1))));
    }

    /* 역할 관리 편의 메소드 */

    /**
     * 역할 목록 조회<br>
     * UserRole에서 Role만 추출하여 반환
     *
     * @return 역할 리스트
     */
    public List<Role> getRoles() {
        return activeUserRoles()
                .map(UserRole::getRole)
                .toList();
    }

    /**
     * 살아 있는 역할 부여 기록<br>
     * 부여 기록이 지워졌거나, 부여 기록은 남았어도 역할 자체가 삭제된 것은 뺀다.
     * 역할 삭제가 목록에서만 사라지고 가진 사람의 권한에는 그대로 남던 것을 막는다(보안 점검 IEU-5)
     */
    private Stream<UserRole> activeUserRoles() {
        return this.userRoles.stream()
                .filter(ur -> YNType.isN(ur.getIsDeleted()))
                .filter(ur -> !YNType.isY(ur.getRole().getIsDeleted()));
    }

    /**
     * 역할 추가<br>
     * 사용자에게 새로운 역할을 추가
     *
     * @param role 추가할 역할
     */
    public void addRole(Role role) {
        boolean exists = this.userRoles.stream()
                .anyMatch(ur -> ur.getRole().getCode().equals(role.getCode())
                        && YNType.isN(ur.getIsDeleted()));

        if (!exists) {
            UserRole userRole = UserRole.createUserRole(this, role);
            this.userRoles.add(userRole);
        }
    }

    /**
     * 역할 제거<br>
     * 사용자에서 특정 역할을 제거 (Soft Delete)
     *
     * @param role 제거할 역할
     */
    public void removeRole(Role role) {
        this.userRoles.stream()
                .filter(ur -> ur.getRole().getCode().equals(role.getCode())
                        && YNType.isN(ur.getIsDeleted()))
                .forEach(UserRole::deleteUserRole);
    }

    /**
     * 모든 역할 제거<br>
     * 사용자의 모든 역할을 제거
     */
    public void clearRoles() {
        this.userRoles.clear();
    }

    /**
     * 특정 역할 보유 여부 확인<br>
     * 사용자가 특정 역할을 가지고 있는지 확인
     *
     * @param roleCode 확인할 역할 코드
     * @return 역할 보유 여부
     */
    public boolean hasRole(String roleCode) {
        return activeUserRoles()
                .anyMatch(ur -> ur.getRole().getCode().equals(roleCode));
    }

    /**
     * 모든 권한 코드 조회 (Spring Security용)<br>
     * 사용자의 역할(Role)과 해당 역할의 모든 권한(Permission)을 포함하여 반환
     *
     * @return 권한 코드 리스트 (예: ["ADMIN", "USER:READ", "USER:EDIT", ...])
     */
    public List<String> getAllAuthorities() {
        return activeUserRoles()
                .flatMap(ur -> {
                    List<String> authorities = new ArrayList<>();
                    // 1. 역할 코드 추가 (예: "ADMIN", "MANAGER", "USER")
                    authorities.add(ur.getRole().getCode());

                    // 2. 역할의 모든 권한 코드 추가 (예: "USER:READ", "USER:EDIT", ...)
                    ur.getRole().getPermissions().stream()
                            .map(permission -> permission.getCode())
                            .forEach(authorities::add);

                    return authorities.stream();
                })
                .distinct()
                .toList();
    }

    /* 휴가 플랜 관리 편의 메소드 */

    /**
     * 휴가 플랜 목록 조회<br>
     * UserVacationPlan에서 VacationPlan만 추출하여 반환
     *
     * @return 휴가 플랜 리스트
     */
    public List<VacationPlan> getVacationPlans() {
        return this.userVacationPlans.stream()
                .filter(uvp -> YNType.isN(uvp.getIsDeleted()))
                .map(UserVacationPlan::getVacationPlan)
                .filter(plan -> YNType.isN(plan.getIsDeleted()))
                .toList();
    }

    /**
     * 모든 휴가 정책 조회<br>
     * 사용자에게 할당된 모든 플랜들의 정책을 중복 제거하여 반환
     *
     * @return 휴가 정책 리스트
     */
    public List<VacationPolicy> getAllVacationPolicies() {
        return this.userVacationPlans.stream()
                .filter(uvp -> YNType.isN(uvp.getIsDeleted()))
                .map(UserVacationPlan::getVacationPlan)
                .filter(plan -> YNType.isN(plan.getIsDeleted()))
                .flatMap(plan -> plan.getPolicies().stream())
                .distinct()
                .toList();
    }

    /**
     * 특정 휴가 플랜 보유 여부 확인<br>
     * 사용자가 특정 플랜을 가지고 있는지 확인
     *
     * @param planCode 확인할 플랜 코드
     * @return 플랜 보유 여부
     */
    public boolean hasVacationPlan(String planCode) {
        return this.userVacationPlans.stream()
                .filter(uvp -> YNType.isN(uvp.getIsDeleted()))
                .map(UserVacationPlan::getVacationPlan)
                .filter(plan -> YNType.isN(plan.getIsDeleted()))
                .anyMatch(plan -> plan.getCode().equals(planCode));
    }
}
