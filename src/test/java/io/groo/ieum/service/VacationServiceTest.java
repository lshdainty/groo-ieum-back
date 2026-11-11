package io.groo.ieum.service;

import io.groo.ieum.common.time.CompanyClock;
import io.groo.core.exception.BusinessRuleViolationException;
import io.groo.core.exception.DuplicateException;
import io.groo.core.exception.EntityNotFoundException;
import io.groo.core.exception.ForbiddenException;
import io.groo.ieum.common.exception.HrErrorCode;
import io.groo.core.exception.InvalidValueException;
import io.groo.core.type.CountryCode;
import io.groo.core.type.YNType;
import io.groo.ieum.department.domain.Department;
import io.groo.ieum.department.repository.DepartmentRepository;
import io.groo.ieum.holiday.repository.HolidayRepository;
import io.groo.ieum.vacation.domain.UserVacationPlan;
import io.groo.ieum.vacation.domain.VacationGrant;
import io.groo.ieum.vacation.domain.VacationPlan;
import io.groo.ieum.vacation.domain.VacationPlanPolicy;
import io.groo.ieum.vacation.domain.VacationPolicy;
import io.groo.ieum.vacation.domain.VacationUsage;
import io.groo.ieum.vacation.domain.VacationUsageDeduction;
import io.groo.ieum.vacation.repository.UserVacationPlanRepository;
import io.groo.ieum.vacation.repository.VacationApprovalRepository;
import io.groo.ieum.vacation.repository.VacationGrantRepository;
import io.groo.ieum.vacation.repository.VacationPolicyRepository;
import io.groo.ieum.vacation.repository.VacationUsageDeductionRepository;
import io.groo.ieum.vacation.repository.VacationUsageRepository;
import io.groo.ieum.vacation.type.EffectiveType;
import io.groo.ieum.vacation.type.ExpirationType;
import io.groo.ieum.vacation.type.GrantMethod;
import io.groo.ieum.vacation.type.GrantStatus;
import io.groo.ieum.vacation.type.VacationTimeType;
import io.groo.ieum.vacation.type.VacationType;
import io.groo.ieum.user.domain.User;
import io.groo.ieum.user.service.UserService;
import io.groo.ieum.vacation.service.VacationService;
import io.groo.ieum.vacation.service.VacationServiceImpl;
import io.groo.ieum.vacation.service.VacationTimeFormatter;
import io.groo.ieum.vacation.service.dto.VacationApprovalServiceDto;
import io.groo.ieum.vacation.service.dto.VacationPolicyServiceDto;
import io.groo.ieum.vacation.service.dto.VacationServiceDto;
import io.groo.ieum.vacation.service.policy.ManualGrant;
import io.groo.ieum.vacation.service.policy.OnRequest;
import io.groo.ieum.vacation.service.policy.description.RepeatGrantDescriptionFactory;
import io.groo.ieum.vacation.service.policy.factory.VacationPolicyStrategyFactory;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@Slf4j
@ExtendWith(MockitoExtension.class)
@DisplayName("휴가 서비스 테스트")
class VacationServiceTest {

    @Mock
    private VacationPolicyRepository vacationPolicyRepository;

    @Mock
    private UserVacationPlanRepository userVacationPlanRepository;

    @Mock
    private HolidayRepository holidayRepository;

    @Mock
    private UserService userService;

    @Mock
    private VacationPolicyStrategyFactory vacationPolicyStrategyFactory;

    @Mock
    private VacationGrantRepository vacationGrantRepository;

    @Mock
    private VacationUsageRepository vacationUsageRepository;

    @Mock
    private VacationUsageDeductionRepository vacationUsageDeductionRepository;

    @Mock
    private VacationApprovalRepository vacationApprovalRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private RepeatGrantDescriptionFactory repeatGrantDescriptionFactory;

    @Mock
    private VacationTimeFormatter vacationTimeFormatter;

    // 날짜 판정용 — @InjectMocks 가 null 을 넣으면 NPE. 실물을 주입하되 회사 조회는 비어
    // 서비스 기준(Asia/Seoul)으로 폴백한다(기존 테스트가 기대하던 KST 동작 유지).
    @Spy
    private CompanyClock companyClock = new CompanyClock(
            org.mockito.Mockito.mock(io.groo.ieum.company.repository.CompanyRepository.class),
            new io.groo.core.time.ServiceClock("Asia/Seoul"));

    @InjectMocks
    private VacationServiceImpl vacationService;

    @Nested
    @DisplayName("유저 휴가 내역 조회")
    class GetUserVacationHistory {
        @Test
        @DisplayName("성공 - 유저의 휴가 부여 및 사용 내역을 반환한다")
        void getUserVacationHistorySuccess() {
            // given
            String userId = "user1";
            int year = 2024;
            User user = createTestUser(userId);

            VacationGrant grant = createTestGrant(user);
            VacationUsage usage = createTestUsage(user);

            given(userService.checkUserExist(userId)).willReturn(user);
            given(vacationGrantRepository.findByUserIdAndYear(userId, year)).willReturn(List.of(grant));
            given(vacationUsageRepository.findByUserIdAndYear(userId, year)).willReturn(List.of(usage));

            // when
            VacationServiceDto result = vacationService.getUserVacationHistory(userId, year);

            // then
            assertThat(result.getGrants()).hasSize(1);
            assertThat(result.getUsages()).hasSize(1);
            then(userService).should().checkUserExist(userId);
        }

        @Test
        @DisplayName("성공 - 부여 및 사용 내역이 없으면 빈 리스트를 반환한다")
        void getUserVacationHistoryEmpty() {
            // given
            String userId = "user1";
            int year = 2024;
            User user = createTestUser(userId);

            given(userService.checkUserExist(userId)).willReturn(user);
            given(vacationGrantRepository.findByUserIdAndYear(userId, year)).willReturn(List.of());
            given(vacationUsageRepository.findByUserIdAndYear(userId, year)).willReturn(List.of());

            // when
            VacationServiceDto result = vacationService.getUserVacationHistory(userId, year);

            // then
            assertThat(result.getGrants()).isEmpty();
            assertThat(result.getUsages()).isEmpty();
        }
    }

    @Nested
    @DisplayName("모든 유저 휴가 내역 조회")
    class GetAllUsersVacationHistory {
        @Test
        @DisplayName("성공 - 모든 유저의 휴가 내역을 반환한다")
        void getAllUsersVacationHistorySuccess() {
            // given
            User user1 = createTestUser("user1");
            User user2 = createTestUser("user2");

            VacationGrant grant1 = createTestGrant(user1);
            VacationGrant grant2 = createTestGrant(user2);

            given(vacationGrantRepository.findAllWithUser()).willReturn(List.of(grant1, grant2));
            given(vacationUsageRepository.findAllWithUser()).willReturn(List.of());

            // when
            List<VacationServiceDto> result = vacationService.getAllUsersVacationHistory();

            // then
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("성공 - 내역이 없으면 빈 리스트를 반환한다")
        void getAllUsersVacationHistoryEmpty() {
            // given
            given(vacationGrantRepository.findAllWithUser()).willReturn(List.of());
            given(vacationUsageRepository.findAllWithUser()).willReturn(List.of());

            // when
            List<VacationServiceDto> result = vacationService.getAllUsersVacationHistory();

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("사용 가능한 휴가 조회")
    class GetAvailableVacations {
        @Test
        @DisplayName("성공 - 시작일 기준 사용 가능한 휴가를 VacationType별로 그룹화하여 반환한다")
        void getAvailableVacationsSuccess() {
            // given
            String userId = "user1";
            LocalDateTime startDate = LocalDateTime.of(2025, 6, 1, 0, 0);
            User user = createTestUser(userId);

            VacationGrant grant1 = createTestGrant(user, VacationType.ANNUAL, new BigDecimal("10.0000"));
            VacationGrant grant2 = createTestGrant(user, VacationType.ANNUAL, new BigDecimal("5.0000"));

            given(userService.checkUserExist(userId)).willReturn(user);
            given(vacationGrantRepository.findAvailableGrantsByUserIdAndDate(userId, startDate))
                    .willReturn(List.of(grant1, grant2));

            // when
            List<VacationServiceDto> result = vacationService.getAvailableVacations(userId, startDate);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getType()).isEqualTo(VacationType.ANNUAL);
            assertThat(result.get(0).getRemainTime()).isEqualByComparingTo(new BigDecimal("15.0000"));
        }

        @Test
        @DisplayName("성공 - 사용 가능한 휴가가 없으면 빈 리스트를 반환한다")
        void getAvailableVacationsEmpty() {
            // given
            String userId = "user1";
            LocalDateTime startDate = LocalDateTime.of(2025, 6, 1, 0, 0);
            User user = createTestUser(userId);

            given(userService.checkUserExist(userId)).willReturn(user);
            given(vacationGrantRepository.findAvailableGrantsByUserIdAndDate(userId, startDate))
                    .willReturn(List.of());

            // when
            List<VacationServiceDto> result = vacationService.getAvailableVacations(userId, startDate);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("휴가 사용 취소")
    class CancelVacationUsage {
        @Test
        @DisplayName("성공 - 휴가 사용 내역이 취소되고 VacationGrant에 시간이 복구된다")
        void cancelVacationUsageSuccess() {
            // given
            Long usageId = 1L;
            User user = createTestUser("user1");
            VacationUsage usage = createTestUsage(user);
            ReflectionTestUtils.setField(usage, "rowId", usageId);
            ReflectionTestUtils.setField(usage, "startDate", LocalDateTime.now().plusDays(1));

            VacationGrant grant = createTestGrant(user);
            ReflectionTestUtils.setField(grant, "rowId", 1L);

            VacationUsageDeduction deduction = VacationUsageDeduction.createVacationUsageDeduction(
                    usage, grant, new BigDecimal("1.0000"));

            given(vacationUsageRepository.findById(usageId)).willReturn(Optional.of(usage));
            given(vacationUsageDeductionRepository.findByUsageId(usageId)).willReturn(List.of(deduction));

            // when
            vacationService.cancelVacationUsage(usageId);

            // then
            assertThat(usage.getIsDeleted()).isEqualTo(YNType.Y);
            then(vacationUsageRepository).should().findById(usageId);
            then(vacationUsageDeductionRepository).should().findByUsageId(usageId);
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 휴가 사용 내역이면 예외가 발생한다")
        void cancelVacationUsageFailNotFound() {
            // given
            Long usageId = 999L;
            given(vacationUsageRepository.findById(usageId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> vacationService.cancelVacationUsage(usageId))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("실패 - 이미 삭제된 휴가 사용 내역이면 예외가 발생한다")
        void cancelVacationUsageFailAlreadyDeleted() {
            // given
            Long usageId = 1L;
            User user = createTestUser("user1");
            VacationUsage usage = createTestUsage(user);
            ReflectionTestUtils.setField(usage, "rowId", usageId);
            usage.deleteVacationUsage();

            given(vacationUsageRepository.findById(usageId)).willReturn(Optional.of(usage));

            // when & then
            assertThatThrownBy(() -> vacationService.cancelVacationUsage(usageId))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }
    }

    @Nested
    @DisplayName("휴가 정책 조회")
    class GetVacationPolicy {
        @Test
        @DisplayName("성공 - 휴가 정책을 반환한다")
        void getVacationPolicySuccess() {
            // given
            Long policyId = 1L;
            VacationPolicy policy = createTestPolicy();
            ReflectionTestUtils.setField(policy, "rowId", policyId);

            given(vacationPolicyRepository.findByRowId(policyId))
                    .willReturn(Optional.of(policy));

            // when
            VacationPolicyServiceDto result = vacationService.getVacationPolicy(policyId);

            // then
            assertThat(result.getId()).isEqualTo(policyId);
            assertThat(result.getName()).isEqualTo("연차");
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 휴가 정책이면 예외가 발생한다")
        void getVacationPolicyFailNotFound() {
            // given
            Long policyId = 999L;
            given(vacationPolicyRepository.findByRowId(policyId))
                    .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> vacationService.getVacationPolicy(policyId))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("휴가 정책 목록 조회")
    class GetVacationPolicies {
        @Test
        @DisplayName("성공 - 휴가 정책 목록을 반환한다")
        void getVacationPoliciesSuccess() {
            // given
            VacationPolicy policy1 = createTestPolicy();
            VacationPolicy policy2 = createTestPolicy();
            ReflectionTestUtils.setField(policy1, "rowId", 1L);
            ReflectionTestUtils.setField(policy2, "rowId", 2L);

            given(vacationPolicyRepository.findVacationPolicies())
                    .willReturn(List.of(policy1, policy2));

            // when
            List<VacationPolicyServiceDto> result = vacationService.getVacationPolicies();

            // then
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("성공 - 정책이 없으면 빈 리스트를 반환한다")
        void getVacationPoliciesEmpty() {
            // given
            given(vacationPolicyRepository.findVacationPolicies()).willReturn(List.of());

            // when
            List<VacationPolicyServiceDto> result = vacationService.getVacationPolicies();

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("휴가 정책 응답의 삭제 가능 여부(can_deleted)")
    class VacationPolicyCanDeleted {
        @Test
        @DisplayName("성공 - 목록 · 단건 모두 정책의 canDeleted 값을 그대로 싣는다 (직접 부여 기본 정책 N · 그 밖 Y)")
        void carriesCanDeleted() {
            VacationPolicy basic = createTestPolicy();
            basic.updateCantDeleted();
            ReflectionTestUtils.setField(basic, "rowId", 1L);
            VacationPolicy custom = createTestPolicy();
            custom.updateCanDeleted();
            ReflectionTestUtils.setField(custom, "rowId", 2L);
            given(vacationPolicyRepository.findVacationPolicies()).willReturn(List.of(basic, custom));
            given(vacationPolicyRepository.findByRowId(1L)).willReturn(Optional.of(basic));
            given(vacationPolicyRepository.findByRowId(2L)).willReturn(Optional.of(custom));

            List<VacationPolicyServiceDto> list = vacationService.getVacationPolicies();

            assertThat(list).extracting(VacationPolicyServiceDto::getCanDeleted).containsExactly(YNType.N, YNType.Y);
            assertThat(vacationService.getVacationPolicy(1L).getCanDeleted()).isEqualTo(YNType.N);
            assertThat(vacationService.getVacationPolicy(2L).getCanDeleted()).isEqualTo(YNType.Y);
        }
    }

    @Nested
    @DisplayName("휴가 정책 수정")
    class UpdateVacationPolicy {
        private VacationPolicy policyOf(Long id, String name) {
            VacationPolicy policy = createTestPolicy();
            ReflectionTestUtils.setField(policy, "rowId", id);
            policy.updateNameAndDesc(name, "연차 휴가");
            return policy;
        }

        @Test
        @DisplayName("성공 - 이름 · 설명만 바뀌고 부여 규칙과 삭제 가능 여부는 그대로다")
        void updateKeepsGrantRules() {
            VacationPolicy policy = policyOf(1L, "연차");
            policy.updateCanDeleted();
            given(vacationPolicyRepository.findByRowId(1L)).willReturn(Optional.of(policy));
            given(vacationPolicyRepository.existsByNameAndRowIdNot("새 연차", 1L)).willReturn(false);
            var beforeMethod = policy.getGrantMethod();
            var beforeTime = policy.getGrantTime();
            var beforeType = policy.getVacationType();
            var beforeEffective = policy.getEffectiveType();
            var beforeExpiration = policy.getExpirationType();
            var beforeRepeatUnit = policy.getRepeatUnit();
            var beforeFlexible = policy.getIsFlexibleGrant();
            var beforeCanDeleted = policy.getCanDeleted();

            Long result = vacationService.updateVacationPolicy(1L, "  새 연차  ", "새 설명");

            assertThat(result).isEqualTo(1L);
            assertThat(policy.getName()).isEqualTo("새 연차");
            assertThat(policy.getDesc()).isEqualTo("새 설명");
            assertThat(policy.getGrantMethod()).isEqualTo(beforeMethod);
            assertThat(policy.getGrantTime()).isEqualByComparingTo(beforeTime);
            assertThat(policy.getVacationType()).isEqualTo(beforeType);
            assertThat(policy.getEffectiveType()).isEqualTo(beforeEffective);
            assertThat(policy.getExpirationType()).isEqualTo(beforeExpiration);
            assertThat(policy.getRepeatUnit()).isEqualTo(beforeRepeatUnit);
            assertThat(policy.getIsFlexibleGrant()).isEqualTo(beforeFlexible);
            assertThat(policy.getCanDeleted()).isEqualTo(beforeCanDeleted);
        }

        @Test
        @DisplayName("성공 - 자기 이름 그대로 저장해도 통과한다 (자기 자신은 중복 검사에서 제외)")
        void updateSameNameOfSelf() {
            VacationPolicy policy = policyOf(1L, "연차");
            given(vacationPolicyRepository.findByRowId(1L)).willReturn(Optional.of(policy));
            given(vacationPolicyRepository.existsByNameAndRowIdNot("연차", 1L)).willReturn(false);

            vacationService.updateVacationPolicy(1L, "연차", "설명만 바뀜");

            assertThat(policy.getName()).isEqualTo("연차");
            assertThat(policy.getDesc()).isEqualTo("설명만 바뀜");
        }

        @Test
        @DisplayName("성공 - 설명이 빈 글이면 null 로 둔다")
        void updateBlankDescBecomesNull() {
            VacationPolicy policy = policyOf(1L, "연차");
            given(vacationPolicyRepository.findByRowId(1L)).willReturn(Optional.of(policy));
            given(vacationPolicyRepository.existsByNameAndRowIdNot("연차", 1L)).willReturn(false);

            vacationService.updateVacationPolicy(1L, "연차", "   ");

            assertThat(policy.getDesc()).isNull();
        }

        @Test
        @DisplayName("성공 - 이미 받은 휴가(VacationGrant)는 정책을 고쳐도 그대로다")
        void updateDoesNotTouchGrants() {
            VacationPolicy policy = policyOf(1L, "연차");
            User user = User.createUser(
                    null, "u1", "테스트유저", "t@test.com", LocalDate.of(1990, 1, 1), "NONE", "9 ~ 18",
                    LocalDate.now(), YNType.N, null, null, CountryCode.KR);
            VacationGrant grant = VacationGrant.createVacationGrant(
                    user, policy, "부여", VacationType.ANNUAL, new BigDecimal("15.0000"),
                    java.time.LocalDateTime.of(2030, 1, 1, 0, 0), java.time.LocalDateTime.of(2030, 12, 31, 23, 59));
            BigDecimal grantTime = grant.getGrantTime();
            GrantStatus status = grant.getStatus();
            BigDecimal remain = grant.getRemainTime();
            given(vacationPolicyRepository.findByRowId(1L)).willReturn(Optional.of(policy));
            given(vacationPolicyRepository.existsByNameAndRowIdNot("새 연차", 1L)).willReturn(false);

            vacationService.updateVacationPolicy(1L, "새 연차", "새 설명");

            assertThat(grant.getGrantTime()).isEqualByComparingTo(grantTime);
            assertThat(grant.getStatus()).isEqualTo(status);
            assertThat(grant.getRemainTime()).isEqualByComparingTo(remain);
            assertThat(grant.getPolicy().getName()).isEqualTo("새 연차");
        }

        @Test
        @DisplayName("실패 - 없는 정책이면 VACATION_POLICY_NOT_FOUND")
        void updateNotFound() {
            given(vacationPolicyRepository.findByRowId(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> vacationService.updateVacationPolicy(999L, "연차", null))
                    .isInstanceOf(EntityNotFoundException.class)
                    .extracting("errorCode").isEqualTo(HrErrorCode.VACATION_POLICY_NOT_FOUND);
        }

        @Test
        @DisplayName("실패 - 지운 정책도 같은 404 이고 이름은 바뀌지 않는다")
        void updateDeletedPolicy() {
            VacationPolicy policy = policyOf(1L, "연차");
            policy.deleteVacationPolicy();
            given(vacationPolicyRepository.findByRowId(1L)).willReturn(Optional.of(policy));

            assertThatThrownBy(() -> vacationService.updateVacationPolicy(1L, "새 이름", null))
                    .isInstanceOf(EntityNotFoundException.class)
                    .extracting("errorCode").isEqualTo(HrErrorCode.VACATION_POLICY_NOT_FOUND);
            assertThat(policy.getName()).isEqualTo("연차");
        }

        @Test
        @DisplayName("실패 - 지우지 않은 다른 정책과 같은 이름이면 VACATION_POLICY_NAME_DUPLICATE")
        void updateDuplicateName() {
            VacationPolicy policy = policyOf(1L, "연차");
            given(vacationPolicyRepository.findByRowId(1L)).willReturn(Optional.of(policy));
            given(vacationPolicyRepository.existsByNameAndRowIdNot("월차", 1L)).willReturn(true);

            assertThatThrownBy(() -> vacationService.updateVacationPolicy(1L, "월차", null))
                    .isInstanceOf(DuplicateException.class)
                    .extracting("errorCode").isEqualTo(HrErrorCode.VACATION_POLICY_NAME_DUPLICATE);
            assertThat(policy.getName()).isEqualTo("연차");
        }

        @Test
        @DisplayName("성공 - 지운 정책과 같은 이름은 통과한다 (중복 검사는 지우지 않은 정책만 센다)")
        void updateSameNameAsDeletedPolicy() {
            VacationPolicy policy = policyOf(1L, "연차");
            given(vacationPolicyRepository.findByRowId(1L)).willReturn(Optional.of(policy));
            // 지운 정책은 existsByNameAndRowIdNot 이 세지 않으므로 false
            given(vacationPolicyRepository.existsByNameAndRowIdNot("삭제정책", 1L)).willReturn(false);

            vacationService.updateVacationPolicy(1L, "삭제정책", null);

            assertThat(policy.getName()).isEqualTo("삭제정책");
        }

        @Test
        @DisplayName("실패 - 빈 이름 · 공백 이름 · null 이름은 INVALID_PARAMETER")
        void updateBlankName() {
            VacationPolicy policy = policyOf(1L, "연차");
            given(vacationPolicyRepository.findByRowId(1L)).willReturn(Optional.of(policy));

            for (String bad : new String[]{"", "   ", null}) {
                assertThatThrownBy(() -> vacationService.updateVacationPolicy(1L, bad, null))
                        .isInstanceOf(InvalidValueException.class);
            }
            assertThat(policy.getName()).isEqualTo("연차");
        }

        @Test
        @DisplayName("경계 - 이름 50자는 통과하고 51자는 거절한다 (앞뒤 공백은 자른 뒤 센다)")
        void updateNameLengthBoundary() {
            VacationPolicy policy = policyOf(1L, "연차");
            String fifty = "가".repeat(50);
            given(vacationPolicyRepository.findByRowId(1L)).willReturn(Optional.of(policy));
            given(vacationPolicyRepository.existsByNameAndRowIdNot(fifty, 1L)).willReturn(false);

            vacationService.updateVacationPolicy(1L, " " + fifty + " ", null);
            assertThat(policy.getName()).isEqualTo(fifty);

            assertThatThrownBy(() -> vacationService.updateVacationPolicy(1L, "가".repeat(51), null))
                    .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("경계 - 설명 1000자는 통과하고 1001자는 거절한다")
        void updateDescLengthBoundary() {
            VacationPolicy policy = policyOf(1L, "연차");
            given(vacationPolicyRepository.findByRowId(1L)).willReturn(Optional.of(policy));
            given(vacationPolicyRepository.existsByNameAndRowIdNot("연차", 1L)).willReturn(false);

            vacationService.updateVacationPolicy(1L, "연차", "가".repeat(1000));
            assertThat(policy.getDesc()).hasSize(1000);

            assertThatThrownBy(() -> vacationService.updateVacationPolicy(1L, "연차", "가".repeat(1001)))
                    .isInstanceOf(InvalidValueException.class);
        }
    }

    @Nested
    @DisplayName("휴가 정책 삭제")
    class DeleteVacationPolicy {
        @Test
        @DisplayName("성공 - 휴가 정책이 삭제된다")
        void deleteVacationPolicySuccess() {
            // given
            Long policyId = 1L;
            VacationPolicy policy = createTestPolicy();
            ReflectionTestUtils.setField(policy, "rowId", policyId);

            given(vacationPolicyRepository.findByRowId(policyId))
                    .willReturn(Optional.of(policy));
            given(vacationGrantRepository.findByPolicyId(policyId))
                    .willReturn(List.of());

            // when
            Long result = vacationService.deleteVacationPolicy(policyId);

            // then
            assertThat(result).isEqualTo(policyId);
            assertThat(policy.getIsDeleted()).isEqualTo(YNType.Y);
        }

        @Test
        @DisplayName("실패 - 이미 삭제된 정책이면 예외가 발생한다")
        void deleteVacationPolicyFailAlreadyDeleted() {
            // given
            Long policyId = 1L;
            VacationPolicy policy = createTestPolicy();
            ReflectionTestUtils.setField(policy, "rowId", policyId);
            policy.deleteVacationPolicy();

            given(vacationPolicyRepository.findByRowId(policyId))
                    .willReturn(Optional.of(policy));

            // when & then
            assertThatThrownBy(() -> vacationService.deleteVacationPolicy(policyId))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }

        @Test
        @DisplayName("실패 - 삭제 불가능한 정책이면 예외가 발생한다")
        void deleteVacationPolicyFailCannotDelete() {
            // given
            Long policyId = 1L;
            VacationPolicy policy = createTestPolicy();
            ReflectionTestUtils.setField(policy, "rowId", policyId);
            ReflectionTestUtils.setField(policy, "canDeleted", YNType.N);

            given(vacationPolicyRepository.findByRowId(policyId))
                    .willReturn(Optional.of(policy));

            // when & then
            assertThatThrownBy(() -> vacationService.deleteVacationPolicy(policyId))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }
    }

    @Nested
    @DisplayName("유저에게 할당된 휴가 정책 조회")
    class GetUserAssignedVacationPolicies {
        @Test
        @DisplayName("성공 - 유저에게 할당된 휴가 정책 목록을 반환한다")
        void getUserAssignedVacationPoliciesSuccess() {
            // given
            String userId = "user1";
            User user = createTestUser(userId);
            VacationPolicy policy = createTestPolicy();
            ReflectionTestUtils.setField(policy, "rowId", 1L);

            VacationPlan plan = VacationPlan.createPlan("DEFAULT", "기본 플랜", "테스트 플랜");
            ReflectionTestUtils.setField(plan, "rowId", 1L);
            VacationPlanPolicy planPolicy = VacationPlanPolicy.createPlanPolicy(plan, policy, 1, YNType.N);
            ReflectionTestUtils.setField(planPolicy, "rowId", 1L);
            // Plan에 PlanPolicy 추가
            ReflectionTestUtils.setField(plan, "vacationPlanPolicies", List.of(planPolicy));
            UserVacationPlan userPlan = UserVacationPlan.createUserVacationPlan(user, plan);
            ReflectionTestUtils.setField(userPlan, "rowId", 1L);

            given(userService.checkUserExist(userId)).willReturn(user);
            given(userVacationPlanRepository.findByUserIdWithPlanAndPolicies(userId)).willReturn(List.of(userPlan));

            // when
            List<VacationPolicyServiceDto> result = vacationService.getUserAssignedVacationPolicies(userId, null);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("연차");
        }

        @Test
        @DisplayName("성공 - 할당된 정책이 없으면 빈 리스트를 반환한다")
        void getUserAssignedVacationPoliciesEmpty() {
            // given
            String userId = "user1";
            User user = createTestUser(userId);

            given(userService.checkUserExist(userId)).willReturn(user);
            given(userVacationPlanRepository.findByUserIdWithPlanAndPolicies(userId)).willReturn(List.of());

            // when
            List<VacationPolicyServiceDto> result = vacationService.getUserAssignedVacationPolicies(userId, null);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("휴가 부여 회수")
    class RevokeVacationGrant {
        @Test
        @DisplayName("성공 - 휴가 부여가 회수된다")
        void revokeVacationGrantSuccess() {
            // given
            Long grantId = 1L;
            User user = createTestUser("user1");
            VacationGrant grant = createTestGrant(user);
            ReflectionTestUtils.setField(grant, "rowId", grantId);

            given(vacationGrantRepository.findById(grantId)).willReturn(Optional.of(grant));

            // when
            VacationGrant result = vacationService.revokeVacationGrant(grantId);

            // then
            assertThat(result.getStatus()).isEqualTo(GrantStatus.REVOKED);
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 휴가 부여면 예외가 발생한다")
        void revokeVacationGrantFailNotFound() {
            // given
            Long grantId = 999L;
            given(vacationGrantRepository.findById(grantId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> vacationService.revokeVacationGrant(grantId))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("실패 - ACTIVE 상태가 아니면 예외가 발생한다")
        void revokeVacationGrantFailNotActive() {
            // given
            Long grantId = 1L;
            User user = createTestUser("user1");
            VacationGrant grant = createTestGrant(user);
            ReflectionTestUtils.setField(grant, "rowId", grantId);
            grant.revoke(); // REVOKED 상태로 변경

            given(vacationGrantRepository.findById(grantId)).willReturn(Optional.of(grant));

            // when & then
            assertThatThrownBy(() -> vacationService.revokeVacationGrant(grantId))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }

        @Test
        @DisplayName("실패 - 일부 사용된 경우 예외가 발생한다")
        void revokeVacationGrantFailPartiallyUsed() {
            // given
            Long grantId = 1L;
            User user = createTestUser("user1");
            VacationGrant grant = createTestGrant(user);
            ReflectionTestUtils.setField(grant, "rowId", grantId);
            grant.deduct(new BigDecimal("1.0000")); // 일부 사용

            given(vacationGrantRepository.findById(grantId)).willReturn(Optional.of(grant));

            // when & then
            assertThatThrownBy(() -> vacationService.revokeVacationGrant(grantId))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }
    }

    @Nested
    @DisplayName("휴가 신청 취소")
    class CancelVacationRequest {
        @Test
        @DisplayName("성공 - 휴가 신청이 취소된다")
        void cancelVacationRequestSuccess() {
            // given
            Long grantId = 1L;
            String userId = "user1";
            User user = createTestUser(userId);

            VacationPolicy policy = createTestPolicy();
            ReflectionTestUtils.setField(policy, "rowId", 1L);
            ReflectionTestUtils.setField(policy, "grantMethod", GrantMethod.ON_REQUEST);

            VacationGrant grant = VacationGrant.createPendingVacationGrant(
                    user, policy, "신청 사유", VacationType.ANNUAL, new BigDecimal("1.0000"),
                    LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(2), "상세 사유"
            );
            ReflectionTestUtils.setField(grant, "rowId", grantId);

            given(vacationGrantRepository.findById(grantId)).willReturn(Optional.of(grant));

            // when
            Long result = vacationService.cancelVacationRequest(grantId, userId);

            // then
            assertThat(result).isEqualTo(grantId);
            assertThat(grant.getStatus()).isEqualTo(GrantStatus.CANCELED);
        }

        @Test
        @DisplayName("실패 - 신청자가 아니면 예외가 발생한다")
        void cancelVacationRequestFailNotAuthorized() {
            // given
            Long grantId = 1L;
            String userId = "user1";
            String anotherUserId = "user2";
            User user = createTestUser(userId);

            VacationPolicy policy = createTestPolicy();
            ReflectionTestUtils.setField(policy, "rowId", 1L);
            ReflectionTestUtils.setField(policy, "grantMethod", GrantMethod.ON_REQUEST);

            VacationGrant grant = VacationGrant.createPendingVacationGrant(
                    user, policy, "신청 사유", VacationType.ANNUAL, new BigDecimal("1.0000"),
                    LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(2), "상세 사유"
            );
            ReflectionTestUtils.setField(grant, "rowId", grantId);

            given(vacationGrantRepository.findById(grantId)).willReturn(Optional.of(grant));

            // when & then
            assertThatThrownBy(() -> vacationService.cancelVacationRequest(grantId, anotherUserId))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }
    }

    @Nested
    @DisplayName("기간별 휴가 사용 내역 조회")
    class GetVacationUsagesByPeriod {
        @Test
        @DisplayName("성공 - 기간 내 휴가 사용 내역을 반환한다")
        void getVacationUsagesByPeriodSuccess() {
            // given
            LocalDateTime startDate = LocalDateTime.of(2025, 1, 1, 0, 0);
            LocalDateTime endDate = LocalDateTime.of(2025, 1, 31, 23, 59);

            User user = createTestUser("user1");
            VacationUsage usage = createTestUsage(user);
            ReflectionTestUtils.setField(usage, "rowId", 1L);

            given(vacationUsageRepository.findByPeriodWithUser(startDate, endDate))
                    .willReturn(List.of(usage));
            given(vacationUsageDeductionRepository.findByUsageIds(anyList())).willReturn(List.of());

            // when
            List<VacationServiceDto> result = vacationService.getVacationUsagesByPeriod(startDate, endDate);

            // then
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("성공 - 기간 내 내역이 없으면 빈 리스트를 반환한다")
        void getVacationUsagesByPeriodEmpty() {
            // given
            LocalDateTime startDate = LocalDateTime.of(2025, 1, 1, 0, 0);
            LocalDateTime endDate = LocalDateTime.of(2025, 1, 31, 23, 59);

            given(vacationUsageRepository.findByPeriodWithUser(startDate, endDate))
                    .willReturn(List.of());

            // when
            List<VacationServiceDto> result = vacationService.getVacationUsagesByPeriod(startDate, endDate);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("유저별 기간별 휴가 사용 내역 조회")
    class GetUserVacationUsagesByPeriod {
        @Test
        @DisplayName("성공 - 유저의 기간 내 휴가 사용 내역을 반환한다")
        void getUserVacationUsagesByPeriodSuccess() {
            // given
            String userId = "user1";
            LocalDateTime startDate = LocalDateTime.of(2025, 1, 1, 0, 0);
            LocalDateTime endDate = LocalDateTime.of(2025, 1, 31, 23, 59);

            User user = createTestUser(userId);
            VacationUsage usage = createTestUsage(user);

            given(userService.checkUserExist(userId)).willReturn(user);
            given(vacationUsageRepository.findByUserIdAndPeriodWithUser(userId, startDate, endDate))
                    .willReturn(List.of(usage));

            // when
            List<VacationServiceDto> result = vacationService.getUserVacationUsagesByPeriod(userId, startDate, endDate);

            // then
            assertThat(result).hasSize(1);
        }
    }

    @Nested
    @DisplayName("유저 월별 휴가 사용 통계 조회")
    class GetUserMonthlyVacationStats {
        @Test
        @DisplayName("성공 - 12개월 통계를 반환한다")
        void getUserMonthlyVacationStatsSuccess() {
            // given
            String userId = "user1";
            String year = "2025";
            User user = createTestUser(userId);

            given(userService.checkUserExist(userId)).willReturn(user);
            given(vacationUsageRepository.findByUserIdAndPeriodWithUser(eq(userId), any(), any()))
                    .willReturn(List.of());

            // when
            List<VacationServiceDto> result = vacationService.getUserMonthlyVacationStats(userId, year);

            // then
            assertThat(result).hasSize(12);
            assertThat(result.get(0).getMonth()).isEqualTo(1);
            assertThat(result.get(11).getMonth()).isEqualTo(12);
        }
    }

    @Nested
    @DisplayName("승인자별 휴가 신청 목록 조회")
    class GetAllVacationsByApprover {
        @Test
        @DisplayName("성공 - 승인자가 처리해야 할 휴가 목록을 반환한다")
        void getAllVacationsByApproverSuccess() {
            // given
            String approverId = "approver1";
            Integer year = 2025;
            User approver = createTestUser(approverId);

            given(userService.checkUserExist(approverId)).willReturn(approver);
            given(vacationApprovalRepository.findAllVacationGrantIdsByApproverIdAndYear(approverId, year))
                    .willReturn(List.of());

            // when
            List<VacationServiceDto> result = vacationService.getAllVacationsByApprover(approverId, year, null);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("유저별 신청 휴가 목록 조회")
    class GetAllRequestedVacationsByUserId {
        @Test
        @DisplayName("성공 - 유저가 신청한 휴가 목록을 반환한다")
        void getAllRequestedVacationsByUserIdSuccess() {
            // given
            String userId = "user1";
            Integer year = 2025;
            User user = createTestUser(userId);

            given(userService.checkUserExist(userId)).willReturn(user);
            given(vacationGrantRepository.findAllRequestedVacationsByUserIdAndYear(userId, year))
                    .willReturn(List.of());

            // when
            List<VacationServiceDto> result = vacationService.getAllRequestedVacationsByUserId(userId, year);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("유저 휴가 통계 조회")
    class GetUserVacationStats {
        @Test
        @DisplayName("성공 - 유저의 휴가 통계를 반환한다")
        void getUserVacationStatsSuccess() {
            // given
            String userId = "user1";
            User user = createTestUser(userId);
            LocalDateTime baseTime = LocalDateTime.now();

            given(userService.checkUserExist(userId)).willReturn(user);
            given(vacationGrantRepository.findValidGrantsByUserIdAndBaseTime(eq(userId), any()))
                    .willReturn(List.of());
            given(vacationUsageDeductionRepository.findByGrantIds(any()))
                    .willReturn(List.of());

            // when
            VacationServiceDto result = vacationService.getUserVacationStats(userId, baseTime);

            // then
            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("유저별 신청 휴가 통계 조회")
    class GetRequestedVacationStatsByUserId {
        @Test
        @DisplayName("성공 - 신청 휴가 상태별 통계를 반환한다")
        void getRequestedVacationStatsByUserIdSuccess() {
            // given
            String userId = "user1";
            Integer year = 2025;
            User user = createTestUser(userId);

            given(userService.checkUserExist(userId)).willReturn(user);
            given(vacationGrantRepository.findAllRequestedVacationsByUserIdAndYear(userId, year))
                    .willReturn(List.of());

            // when
            VacationServiceDto result = vacationService.getRequestedVacationStatsByUserId(userId, year);

            // then
            assertThat(result).isNotNull();
        }

        @ParameterizedTest(name = "{0} 하나는 승인 {1}")
        @CsvSource({
                "ACTIVE,1", "EXHAUSTED,1", "EXPIRED,1",
                "PENDING,0", "PROGRESS,0", "REJECTED,0", "CANCELED,0", "REVOKED,0"
        })
        @DisplayName("승인 수는 목록 배지가 승인으로 그리는 상태(활성 · 소진 · 만료)만 센다")
        void approvedCountByStatus(GrantStatus status, long expectedApproved) {
            String userId = "user1";
            User user = createTestUser(userId);
            given(userService.checkUserExist(userId)).willReturn(user);
            given(vacationGrantRepository.findAllRequestedVacationsByUserIdAndYear(userId, 2025))
                    .willReturn(List.of(statGrant(user, status, "2.0000")));
            given(vacationTimeFormatter.format(any(BigDecimal.class))).willReturn("x");

            VacationServiceDto result = vacationService.getRequestedVacationStatsByUserId(userId, 2025);

            assertThat(result.getApprovedCount()).isEqualTo(expectedApproved);
            assertThat(result.getApprovalRate()).isEqualTo(expectedApproved * 100.0);
            assertThat(result.getAcquiredVacationTime())
                    .isEqualByComparingTo(expectedApproved == 1 ? new BigDecimal("2.0000") : BigDecimal.ZERO);
        }

        @Test
        @DisplayName("섞인 아홉 건 - 활성 1 · 소진 1 · 만료 1 이면 승인 3, 승인율 33.3%, 받은 휴가는 셋의 합")
        void approvedCountMixed() {
            String userId = "user1";
            User user = createTestUser(userId);
            given(userService.checkUserExist(userId)).willReturn(user);
            given(vacationGrantRepository.findAllRequestedVacationsByUserIdAndYear(userId, 2025))
                    .willReturn(List.of(
                            statGrant(user, GrantStatus.ACTIVE, "1.0000"),
                            statGrant(user, GrantStatus.EXHAUSTED, "2.0000"),
                            statGrant(user, GrantStatus.EXPIRED, "3.0000"),
                            statGrant(user, GrantStatus.PENDING, "10.0000"),
                            statGrant(user, GrantStatus.PROGRESS, "10.0000"),
                            statGrant(user, GrantStatus.REJECTED, "10.0000"),
                            statGrant(user, GrantStatus.CANCELED, "10.0000"),
                            statGrant(user, GrantStatus.REVOKED, "10.0000"),
                            statGrant(user, GrantStatus.PENDING, "10.0000")));
            given(vacationTimeFormatter.format(any(BigDecimal.class))).willReturn("x");

            VacationServiceDto result = vacationService.getRequestedVacationStatsByUserId(userId, 2025);

            assertThat(result.getTotalRequestCount()).isEqualTo(9);
            assertThat(result.getApprovedCount()).isEqualTo(3);
            assertThat(result.getApprovalRate()).isCloseTo(33.33, org.assertj.core.data.Offset.offset(0.01));
            assertThat(result.getAcquiredVacationTime()).isEqualByComparingTo(new BigDecimal("6.0000"));
        }
    }

    @Nested
    @DisplayName("필터 조건으로 유저 할당 정책 조회")
    class GetUserAssignedVacationPoliciesWithFilters {
        @Test
        @DisplayName("성공 - 필터 조건으로 정책을 조회한다")
        void getUserAssignedVacationPoliciesWithFiltersSuccess() {
            // given
            String userId = "user1";
            User user = createTestUser(userId);

            given(userService.checkUserExist(userId)).willReturn(user);
            given(userVacationPlanRepository.findByUserIdWithPlanAndPolicies(userId))
                    .willReturn(List.of());

            // when
            List<VacationPolicyServiceDto> result = vacationService.getUserAssignedVacationPoliciesWithFilters(
                    userId, VacationType.ANNUAL, GrantMethod.MANUAL_GRANT
            );

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("사용 가능한 휴가 조회")
    class GetAvailableVacationsTest {
        @Test
        @DisplayName("성공 - 사용 가능한 휴가를 조회한다")
        void getAvailableVacationsSuccess() {
            // given
            String userId = "user1";
            User user = createTestUser(userId);
            VacationGrant grant = createTestGrant(user);
            ReflectionTestUtils.setField(grant, "rowId", 1L);
            LocalDateTime startDate = LocalDateTime.now();

            given(vacationGrantRepository.findAvailableGrantsByUserIdAndDate(userId, startDate))
                    .willReturn(List.of(grant));

            // when
            List<VacationServiceDto> result = vacationService.getAvailableVacations(userId, startDate);

            // then
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("성공 - 사용 가능한 휴가가 없으면 빈 목록을 반환한다")
        void getAvailableVacationsEmpty() {
            // given
            String userId = "user1";
            LocalDateTime startDate = LocalDateTime.now();

            given(vacationGrantRepository.findAvailableGrantsByUserIdAndDate(userId, startDate))
                    .willReturn(List.of());

            // when
            List<VacationServiceDto> result = vacationService.getAvailableVacations(userId, startDate);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("유저 휴가 사용 내역 조회")
    class GetUserVacationUsages {
        @Test
        @DisplayName("성공 - 기간별 휴가 사용 내역을 조회한다")
        void getUserVacationUsagesByPeriodSuccess() {
            // given
            String userId = "user1";
            LocalDateTime startDate = LocalDateTime.of(2025, 1, 1, 0, 0);
            LocalDateTime endDate = LocalDateTime.of(2025, 12, 31, 23, 59);

            given(vacationUsageRepository.findByUserIdAndPeriodWithUser(userId, startDate, endDate))
                    .willReturn(List.of());

            // when
            List<VacationServiceDto> result = vacationService.getUserVacationUsagesByPeriod(userId, startDate, endDate);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("휴가 승인")
    class ApproveVacation {
        @Test
        @DisplayName("실패 - 존재하지 않는 승인이면 예외가 발생한다")
        void approveVacationFailNotFound() {
            // given
            Long approvalId = 999L;
            String approverId = "approver1";

            given(vacationApprovalRepository.findByIdWithVacationGrantAndUser(approvalId))
                    .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> vacationService.approveVacation(approvalId, approverId))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("휴가 거부")
    class RejectVacation {
        @Test
        @DisplayName("실패 - 존재하지 않는 승인이면 예외가 발생한다")
        void rejectVacationFailNotFound() {
            // given
            Long approvalId = 999L;
            String approverId = "approver1";

            given(vacationApprovalRepository.findByIdWithVacationGrantAndUser(approvalId))
                    .willReturn(Optional.empty());

            VacationApprovalServiceDto data = VacationApprovalServiceDto.builder()
                    .rejectionReason("거부 사유")
                    .build();

            // when & then
            assertThatThrownBy(() -> vacationService.rejectVacation(approvalId, approverId, data))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("휴가 정책 생성")
    class CreateVacationPolicy {
        @Test
        @DisplayName("성공 - 휴가 정책을 생성한다")
        void createVacationPolicySuccess() {
            // given
            VacationPolicyServiceDto dto = VacationPolicyServiceDto.builder()
                    .name("테스트정책")
                    .desc("테스트 설명")
                    .vacationType(VacationType.ANNUAL)
                    .grantMethod(GrantMethod.MANUAL_GRANT)
                    .grantTime(new BigDecimal("15.0000"))
                    .isFlexibleGrant(YNType.N)
                    .minuteGrantYn(YNType.N)
                    .effectiveType(EffectiveType.IMMEDIATELY)
                    .expirationType(ExpirationType.END_OF_YEAR)
                    .build();

            ManualGrant mockStrategy = mock(ManualGrant.class);

            given(vacationPolicyStrategyFactory.getStrategy(GrantMethod.MANUAL_GRANT))
                    .willReturn(mockStrategy);
            given(mockStrategy.registVacationPolicy(dto)).willReturn(1L);

            // when
            Long result = vacationService.createVacationPolicy(dto);

            // then
            assertThat(result).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("유저별 모든 신청 휴가 조회")
    class GetAllRequestedVacationsByUserIdTest {
        @Test
        @DisplayName("성공 - 유저별 신청 휴가 목록을 조회한다")
        void getAllRequestedVacationsByUserIdSuccess() {
            // given
            String userId = "user1";
            Integer year = 2025;

            given(vacationGrantRepository.findAllRequestedVacationsByUserIdAndYear(userId, year)).willReturn(List.of());

            // when
            List<VacationServiceDto> result = vacationService.getAllRequestedVacationsByUserId(userId, year);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("승인자별 모든 휴가 조회")
    class GetAllVacationsByApproverTest {
        @Test
        @DisplayName("성공 - 승인자별 모든 휴가 목록을 조회한다")
        void getAllVacationsByApproverSuccess() {
            // given
            String approverId = "approver1";
            Integer year = 2025;
            GrantStatus status = GrantStatus.PENDING;

            given(vacationApprovalRepository.findAllVacationGrantIdsByApproverIdAndYear(approverId, year))
                    .willReturn(List.of());

            // when
            List<VacationServiceDto> result = vacationService.getAllVacationsByApprover(approverId, year, status);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("휴가 사용 기록 조회")
    class GetVacationUsagesTest {
        @Test
        @DisplayName("성공 - 휴가 사용 기록을 조회한다")
        void getVacationUsagesSuccess() {
            // given
            String userId = "user1";
            LocalDateTime start = LocalDateTime.of(2025, 1, 1, 0, 0);
            LocalDateTime end = LocalDateTime.of(2025, 12, 31, 23, 59);
            User user = createTestUser(userId);
            VacationUsage usage = createTestUsage(user);
            ReflectionTestUtils.setField(usage, "rowId", 1L);

            given(vacationUsageRepository.findByUserIdAndPeriodWithUser(userId, start, end))
                    .willReturn(List.of(usage));

            // when
            List<VacationServiceDto> result = vacationService.getUserVacationUsagesByPeriod(userId, start, end);

            // then
            assertThat(result).hasSize(1);
        }
    }

    @Nested
    @DisplayName("수동 휴가 부여")
    class ManualGrantVacation {
        @Test
        @DisplayName("성공 - 고정 부여 시간으로 휴가가 부여된다")
        void manualGrantVacationWithFixedTime() {
            // given
            String userId = "user1";
            Long policyId = 1L;
            User user = createTestUser(userId);
            VacationPolicy policy = VacationPolicy.createManualGrantPolicy(
                    "수동부여", "수동부여", VacationType.ANNUAL,
                    new BigDecimal("3.0000"), YNType.N, YNType.N,
                    EffectiveType.IMMEDIATELY, ExpirationType.END_OF_YEAR
            );
            ReflectionTestUtils.setField(policy, "rowId", policyId);

            VacationServiceDto data = VacationServiceDto.builder()
                    .policyId(policyId)
                    .desc("관리자 직접 부여")
                    .grantDate(LocalDateTime.of(2025, 1, 1, 0, 0))
                    .expiryDate(LocalDateTime.of(2025, 12, 31, 23, 59))
                    .build();

            given(userService.checkUserExist(userId)).willReturn(user);
            given(vacationPolicyRepository.findByRowId(policyId)).willReturn(Optional.of(policy));
            willDoNothing().given(vacationGrantRepository).save(any(VacationGrant.class));

            // when
            VacationGrant result = vacationService.manualGrantVacation(userId, data);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getGrantTime()).isEqualByComparingTo(new BigDecimal("3.0000"));
            then(vacationGrantRepository).should().save(any(VacationGrant.class));
        }

        @Test
        @DisplayName("성공 - 가변 부여 시간으로 휴가가 부여된다")
        void manualGrantVacationWithFlexibleTime() {
            // given
            String userId = "user1";
            Long policyId = 1L;
            User user = createTestUser(userId);
            VacationPolicy policy = VacationPolicy.createManualGrantPolicy(
                    "수동부여", "수동부여", VacationType.ANNUAL,
                    null, YNType.Y, YNType.N,
                    EffectiveType.IMMEDIATELY, ExpirationType.END_OF_YEAR
            );
            ReflectionTestUtils.setField(policy, "rowId", policyId);

            VacationServiceDto data = VacationServiceDto.builder()
                    .policyId(policyId)
                    .desc("관리자 직접 부여")
                    .grantTime(new BigDecimal("5.0000"))
                    .grantDate(LocalDateTime.of(2025, 1, 1, 0, 0))
                    .expiryDate(LocalDateTime.of(2025, 12, 31, 23, 59))
                    .build();

            given(userService.checkUserExist(userId)).willReturn(user);
            given(vacationPolicyRepository.findByRowId(policyId)).willReturn(Optional.of(policy));
            willDoNothing().given(vacationGrantRepository).save(any(VacationGrant.class));

            // when
            VacationGrant result = vacationService.manualGrantVacation(userId, data);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getGrantTime()).isEqualByComparingTo(new BigDecimal("5.0000"));
        }

        @Test
        @DisplayName("실패 - MANUAL_GRANT 정책이 아니면 예외가 발생한다")
        void manualGrantVacationFailNotManualGrant() {
            // given
            String userId = "user1";
            Long policyId = 1L;
            User user = createTestUser(userId);
            VacationPolicy policy = VacationPolicy.createOnRequestPolicy(
                    "신청부여", "신청부여", VacationType.ANNUAL,
                    new BigDecimal("1.0000"), YNType.N, YNType.N, 1,
                    EffectiveType.IMMEDIATELY, ExpirationType.END_OF_YEAR
            );
            ReflectionTestUtils.setField(policy, "rowId", policyId);

            VacationServiceDto data = VacationServiceDto.builder()
                    .policyId(policyId)
                    .build();

            given(userService.checkUserExist(userId)).willReturn(user);
            given(vacationPolicyRepository.findByRowId(policyId)).willReturn(Optional.of(policy));

            // when & then
            assertThatThrownBy(() -> vacationService.manualGrantVacation(userId, data))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }

        @Test
        @DisplayName("실패 - 가변 부여 정책에서 grantTime이 없으면 예외가 발생한다")
        void manualGrantVacationFailNoGrantTime() {
            // given
            String userId = "user1";
            Long policyId = 1L;
            User user = createTestUser(userId);
            VacationPolicy policy = VacationPolicy.createManualGrantPolicy(
                    "수동부여", "수동부여", VacationType.ANNUAL,
                    null, YNType.Y, YNType.N,
                    EffectiveType.IMMEDIATELY, ExpirationType.END_OF_YEAR
            );
            ReflectionTestUtils.setField(policy, "rowId", policyId);

            VacationServiceDto data = VacationServiceDto.builder()
                    .policyId(policyId)
                    .desc("관리자 직접 부여")
                    .grantDate(LocalDateTime.of(2025, 1, 1, 0, 0))
                    .expiryDate(LocalDateTime.of(2025, 12, 31, 23, 59))
                    .build();

            given(userService.checkUserExist(userId)).willReturn(user);
            given(vacationPolicyRepository.findByRowId(policyId)).willReturn(Optional.of(policy));

            // when & then
            assertThatThrownBy(() -> vacationService.manualGrantVacation(userId, data))
                    .isInstanceOf(io.groo.core.exception.InvalidValueException.class);
        }
    }

    @Nested
    @DisplayName("휴가 정책 할당 상태 조회")
    class GetVacationPolicyAssignmentStatus {
        @Test
        @DisplayName("성공 - 할당된 정책과 미할당 정책을 구분하여 반환한다")
        void getVacationPolicyAssignmentStatusSuccess() {
            // given
            String userId = "user1";
            User user = createTestUser(userId);

            VacationPolicy policy1 = createTestPolicy();
            ReflectionTestUtils.setField(policy1, "rowId", 1L);
            VacationPolicy policy2 = createTestPolicy();
            ReflectionTestUtils.setField(policy2, "rowId", 2L);

            VacationPlan plan = VacationPlan.createPlan("DEFAULT", "기본 플랜", "테스트 플랜");
            ReflectionTestUtils.setField(plan, "rowId", 1L);
            VacationPlanPolicy planPolicy = VacationPlanPolicy.createPlanPolicy(plan, policy1, 1, YNType.N);
            ReflectionTestUtils.setField(planPolicy, "rowId", 1L);
            // Plan에 PlanPolicy 추가
            ReflectionTestUtils.setField(plan, "vacationPlanPolicies", List.of(planPolicy));
            UserVacationPlan userPlan = UserVacationPlan.createUserVacationPlan(user, plan);
            ReflectionTestUtils.setField(userPlan, "rowId", 1L);

            given(userService.checkUserExist(userId)).willReturn(user);
            given(vacationPolicyRepository.findVacationPolicies()).willReturn(List.of(policy1, policy2));
            given(userVacationPlanRepository.findByUserIdWithPlanAndPolicies(userId)).willReturn(List.of(userPlan));

            // when
            VacationServiceDto result = vacationService.getVacationPolicyAssignmentStatus(userId);

            // then
            assertThat(result.getAssignedPolicies()).hasSize(1);
            assertThat(result.getUnassignedPolicies()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("휴가 사용 내역 조회 및 검증")
    class ValidateAndGetVacationUsage {
        @Test
        @DisplayName("성공 - 휴가 사용 내역을 반환한다")
        void validateAndGetVacationUsageSuccess() {
            // given
            Long usageId = 1L;
            User user = createTestUser("user1");
            VacationUsage usage = createTestUsage(user);
            ReflectionTestUtils.setField(usage, "rowId", usageId);

            given(vacationUsageRepository.findById(usageId)).willReturn(Optional.of(usage));

            // when
            VacationUsage result = vacationService.validateAndGetVacationUsage(usageId);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getRowId()).isEqualTo(usageId);
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 휴가 사용 내역이면 예외가 발생한다")
        void validateAndGetVacationUsageFailNotFound() {
            // given
            Long usageId = 999L;
            given(vacationUsageRepository.findById(usageId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> vacationService.validateAndGetVacationUsage(usageId))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("휴가 사용 내역 수정")
    class UpdateVacationUsage {
        @Test
        @DisplayName("실패 - 존재하지 않는 휴가 사용 내역이면 예외가 발생한다")
        void updateVacationUsageFailNotFound() {
            // given
            Long usageId = 999L;
            VacationServiceDto data = VacationServiceDto.builder()
                    .userId("user1")
                    .type(VacationType.ANNUAL)
                    .timeType(VacationTimeType.DAYOFF)
                    .startDate(LocalDateTime.now().plusDays(3))
                    .endDate(LocalDateTime.now().plusDays(3).plusHours(8))
                    .desc("수정된 휴가")
                    .build();

            given(vacationUsageRepository.findById(usageId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> vacationService.updateVacationUsage(usageId, data, "user1", false))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("실패 - 이미 삭제된 휴가 사용 내역이면 예외가 발생한다")
        void updateVacationUsageFailAlreadyDeleted() {
            // given
            Long usageId = 1L;
            User user = createTestUser("user1");
            VacationUsage usage = createTestUsage(user);
            ReflectionTestUtils.setField(usage, "rowId", usageId);
            usage.deleteVacationUsage();

            VacationServiceDto data = VacationServiceDto.builder()
                    .userId(user.getId())
                    .type(VacationType.ANNUAL)
                    .timeType(VacationTimeType.DAYOFF)
                    .startDate(LocalDateTime.now().plusDays(3))
                    .endDate(LocalDateTime.now().plusDays(3).plusHours(8))
                    .desc("수정된 휴가")
                    .build();

            given(vacationUsageRepository.findById(usageId)).willReturn(Optional.of(usage));

            // when & then
            assertThatThrownBy(() -> vacationService.updateVacationUsage(usageId, data, "user1", false))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }

        @Test
        @DisplayName("실패 - 과거 시작일은 수정할 수 없다")
        void updateVacationUsageFailPastStartDate() {
            // given
            Long usageId = 1L;
            User user = createTestUser("user1");
            VacationUsage usage = createTestUsage(user);
            ReflectionTestUtils.setField(usage, "rowId", usageId);
            ReflectionTestUtils.setField(usage, "startDate", LocalDateTime.now().minusDays(1));

            VacationServiceDto data = VacationServiceDto.builder()
                    .userId(user.getId())
                    .type(VacationType.ANNUAL)
                    .timeType(VacationTimeType.DAYOFF)
                    .startDate(LocalDateTime.now().plusDays(3))
                    .endDate(LocalDateTime.now().plusDays(3).plusHours(8))
                    .desc("수정된 휴가")
                    .build();

            given(vacationUsageRepository.findById(usageId)).willReturn(Optional.of(usage));

            // when & then
            assertThatThrownBy(() -> vacationService.updateVacationUsage(usageId, data, "user1", false))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }

        private VacationServiceDto updateData(String userId) {
            return VacationServiceDto.builder()
                    .userId(userId)
                    .type(VacationType.ANNUAL)
                    .timeType(VacationTimeType.DAYOFF)
                    .startDate(LocalDateTime.of(2030, 6, 3, 9, 0))
                    .endDate(LocalDateTime.of(2030, 6, 3, 18, 0))
                    .desc("수정된 휴가")
                    .build();
        }

        /** 주인 user1 의 미래 휴가 사용(1일) + 차감 내역. 부여는 14일 남은 상태(1일 차감된 뒤). */
        private VacationGrant stubFutureUsageOwnedByUser1(Long usageId, VacationUsage usage) {
            User owner = usage.getUser();
            ReflectionTestUtils.setField(usage, "rowId", usageId);
            ReflectionTestUtils.setField(usage, "startDate", LocalDateTime.now().plusDays(1));
            VacationGrant grant = createTestGrant(owner, VacationType.ANNUAL, new BigDecimal("14.0000"));
            ReflectionTestUtils.setField(grant, "rowId", 1L);
            VacationUsageDeduction deduction = VacationUsageDeduction.createVacationUsageDeduction(
                    usage, grant, new BigDecimal("1.0000"));
            given(vacationUsageRepository.findById(usageId)).willReturn(Optional.of(usage));
            lenient().when(vacationUsageDeductionRepository.findByUsageId(usageId)).thenReturn(List.of(deduction));
            return grant;
        }

        @Test
        @DisplayName("성공 - 본인 기록이면 지우고 새로 쓴다 (부여는 되돌렸다가 다시 차감)")
        void updateVacationUsageSuccessOwner() {
            // given
            Long usageId = 1L;
            User owner = createTestUser("user1");
            VacationUsage usage = createTestUsage(owner);
            VacationGrant grant = stubFutureUsageOwnedByUser1(usageId, usage);
            given(userService.checkUserExist("user1")).willReturn(owner);
            given(holidayRepository.findHolidaysByStartEndDateWithType(any(), any(), any(), any())).willReturn(List.of());
            given(vacationGrantRepository.findAvailableGrantsByUserIdAndTypeAndDate(eq("user1"), eq(VacationType.ANNUAL), any()))
                    .willReturn(List.of(grant));

            // when
            vacationService.updateVacationUsage(usageId, updateData("user1"), "user1", false);

            // then
            assertThat(usage.getIsDeleted()).isEqualTo(YNType.Y);
            assertThat(grant.getRemainTime()).isEqualByComparingTo(new BigDecimal("14.0000"));
            then(vacationUsageRepository).should().save(any(VacationUsage.class));
        }

        @Test
        @DisplayName("성공 - VACATION:MANAGE 가 있으면 남의 기록도 지우고 새로 쓴다")
        void updateVacationUsageSuccessManager() {
            // given
            Long usageId = 1L;
            User owner = createTestUser("user1");
            VacationUsage usage = createTestUsage(owner);
            VacationGrant grant = stubFutureUsageOwnedByUser1(usageId, usage);
            given(userService.checkUserExist("user1")).willReturn(owner);
            given(holidayRepository.findHolidaysByStartEndDateWithType(any(), any(), any(), any())).willReturn(List.of());
            given(vacationGrantRepository.findAvailableGrantsByUserIdAndTypeAndDate(eq("user1"), eq(VacationType.ANNUAL), any()))
                    .willReturn(List.of(grant));

            // when
            vacationService.updateVacationUsage(usageId, updateData("user1"), "admin", true);

            // then
            assertThat(usage.getIsDeleted()).isEqualTo(YNType.Y);
            then(vacationUsageRepository).should().save(any(VacationUsage.class));
        }

        @Test
        @DisplayName("실패 - 남의 기록이면 403(VACATION_013)이고 기록 · 부여 남은 시간 · 새 사용이 그대로다")
        void updateVacationUsageFailNotOwner() {
            // given
            Long usageId = 1L;
            User owner = createTestUser("user1");
            VacationUsage usage = createTestUsage(owner);
            VacationGrant grant = stubFutureUsageOwnedByUser1(usageId, usage);

            // when & then - 본문 user_id 를 자기 id(user2)로 보내도 고칠 기록의 주인이 아니면 막힌다
            assertThatThrownBy(() -> vacationService.updateVacationUsage(usageId, updateData("user2"), "user2", false))
                    .isInstanceOf(ForbiddenException.class)
                    .extracting(e -> ((ForbiddenException) e).getErrorCode().getCode())
                    .isEqualTo("VACATION_013");
            assertThat(usage.getIsDeleted()).isEqualTo(YNType.N);
            assertThat(grant.getRemainTime()).isEqualByComparingTo(new BigDecimal("14.0000"));
            then(vacationUsageRepository).should(never()).save(any(VacationUsage.class));
            then(vacationUsageDeductionRepository).should(never()).findByUsageId(any());
        }
    }

    @Nested
    @DisplayName("전체 유저 휴가 요약 조회")
    class GetAllUsersVacationSummary {
        @Test
        @DisplayName("실패 - 년도가 null이면 예외가 발생한다")
        void getAllUsersVacationSummaryFailNullYear() {
            // when & then
            assertThatThrownBy(() -> vacationService.getAllUsersVacationSummary(null))
                    .isInstanceOf(io.groo.core.exception.InvalidValueException.class);
        }
    }

    @Nested
    @DisplayName("휴가 부여 내역 조회 - deduction 존재 시 VacationType 설정")
    class GetVacationUsagesByPeriodWithDeduction {
        @Test
        @DisplayName("성공 - deduction이 있으면 VacationType을 설정하여 반환한다")
        void getVacationUsagesByPeriodWithDeductionSuccess() {
            // given
            LocalDateTime startDate = LocalDateTime.of(2025, 1, 1, 0, 0);
            LocalDateTime endDate = LocalDateTime.of(2025, 1, 31, 23, 59);

            User user = createTestUser("user1");
            VacationUsage usage = createTestUsage(user);
            ReflectionTestUtils.setField(usage, "rowId", 1L);

            VacationGrant grant = createTestGrant(user);
            ReflectionTestUtils.setField(grant, "rowId", 1L);

            VacationUsageDeduction deduction = VacationUsageDeduction.createVacationUsageDeduction(
                    usage, grant, new BigDecimal("1.0000"));

            given(vacationUsageRepository.findByPeriodWithUser(startDate, endDate))
                    .willReturn(List.of(usage));
            given(vacationUsageDeductionRepository.findByUsageIds(anyList())).willReturn(List.of(deduction));

            // when
            List<VacationServiceDto> result = vacationService.getVacationUsagesByPeriod(startDate, endDate);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getType()).isEqualTo(VacationType.ANNUAL);
        }
    }

    @Nested
    @DisplayName("휴가 기록 사용 줄의 휴가 종류")
    class UsageVacationTypes {
        private VacationUsage usage(User user, long id) {
            VacationUsage u = createTestUsage(user);
            ReflectionTestUtils.setField(u, "rowId", id);
            return u;
        }

        private VacationGrant grant(User user, VacationType type, long id) {
            VacationGrant g = createTestGrant(user, type, new BigDecimal("5.0000"));
            ReflectionTestUtils.setField(g, "rowId", id);
            return g;
        }

        private VacationUsageDeduction deduction(VacationUsage u, VacationGrant g) {
            return VacationUsageDeduction.createVacationUsageDeduction(u, g, new BigDecimal("1.0000"));
        }

        @Test
        @DisplayName("성공 - 사용마다 첫 차감 부여의 종류가 실리고 차감 질의는 한 번이다")
        void historyCarriesFirstDeductionTypeWithSingleQuery() {
            User user = createTestUser("user1");
            VacationUsage u1 = usage(user, 1L);
            VacationUsage u2 = usage(user, 2L);
            VacationGrant annual = grant(user, VacationType.ANNUAL, 11L);
            VacationGrant overtime = grant(user, VacationType.OVERTIME, 12L);

            given(userService.checkUserExist("user1")).willReturn(user);
            given(vacationGrantRepository.findByUserIdAndYear("user1", 2025)).willReturn(List.of());
            given(vacationUsageRepository.findByUserIdAndYear("user1", 2025)).willReturn(List.of(u1, u2));
            // u1 은 부여 둘에서 차감(연차가 먼저), u2 는 연장
            given(vacationUsageDeductionRepository.findByUsageIds(List.of(1L, 2L))).willReturn(List.of(
                    deduction(u1, annual), deduction(u1, overtime), deduction(u2, overtime)));

            VacationServiceDto result = vacationService.getUserVacationHistory("user1", 2025);

            assertThat(result.getUsageVacationTypes())
                    .containsEntry(1L, VacationType.ANNUAL)
                    .containsEntry(2L, VacationType.OVERTIME);
            then(vacationUsageDeductionRepository).should(times(1)).findByUsageIds(anyList());
        }

        @Test
        @DisplayName("성공 - 사용이 없으면 빈 맵이고 차감을 읽지 않는다")
        void historyWithoutUsagesDoesNotQueryDeductions() {
            User user = createTestUser("user1");
            given(userService.checkUserExist("user1")).willReturn(user);
            given(vacationGrantRepository.findByUserIdAndYear("user1", 2025)).willReturn(List.of());
            given(vacationUsageRepository.findByUserIdAndYear("user1", 2025)).willReturn(List.of());

            VacationServiceDto result = vacationService.getUserVacationHistory("user1", 2025);

            assertThat(result.getUsageVacationTypes()).isEmpty();
            then(vacationUsageDeductionRepository).should(never()).findByUsageIds(anyList());
        }

        @Test
        @DisplayName("성공 - 차감이 없는 사용은 종류가 없다")
        void usageWithoutDeductionHasNoType() {
            User user = createTestUser("user1");
            VacationUsage u1 = usage(user, 1L);
            given(userService.checkUserExist("user1")).willReturn(user);
            given(vacationGrantRepository.findByUserIdAndYear("user1", 2025)).willReturn(List.of());
            given(vacationUsageRepository.findByUserIdAndYear("user1", 2025)).willReturn(List.of(u1));
            given(vacationUsageDeductionRepository.findByUsageIds(anyList())).willReturn(List.of());

            VacationServiceDto result = vacationService.getUserVacationHistory("user1", 2025);

            assertThat(result.getUsageVacationTypes()).doesNotContainKey(1L);
        }

        @Test
        @DisplayName("성공 - 전체 사용자 기록도 차감 질의 한 번으로 종류를 싣는다")
        void allUsersHistoryCarriesTypeWithSingleQuery() {
            User a = createTestUser("a");
            User b = createTestUser("b");
            VacationUsage ua = usage(a, 1L);
            VacationUsage ub = usage(b, 2L);
            given(vacationGrantRepository.findAllWithUser()).willReturn(List.of());
            given(vacationUsageRepository.findAllWithUser()).willReturn(List.of(ua, ub));
            given(vacationUsageDeductionRepository.findByUsageIds(anyList())).willReturn(List.of(
                    deduction(ua, grant(a, VacationType.WEDDING, 21L)),
                    deduction(ub, grant(b, VacationType.HEALTH, 22L))));

            List<VacationServiceDto> result = vacationService.getAllUsersVacationHistory();

            assertThat(result).hasSize(2);
            assertThat(result).allSatisfy(r -> assertThat(r.getUsageVacationTypes())
                    .containsEntry(1L, VacationType.WEDDING).containsEntry(2L, VacationType.HEALTH));
            then(vacationUsageDeductionRepository).should(times(1)).findByUsageIds(anyList());
        }

        @Test
        @DisplayName("성공 - 기록과 캘린더가 같은 사용에 같은 종류를 준다")
        void historyAndCalendarAgree() {
            User user = createTestUser("user1");
            VacationUsage u1 = usage(user, 1L);
            List<VacationUsageDeduction> deductions = List.of(
                    deduction(u1, grant(user, VacationType.BEREAVEMENT, 31L)),
                    deduction(u1, grant(user, VacationType.ANNUAL, 32L)));
            LocalDateTime start = LocalDateTime.of(2025, 1, 1, 0, 0);
            LocalDateTime end = LocalDateTime.of(2025, 12, 31, 0, 0);
            given(userService.checkUserExist("user1")).willReturn(user);
            given(vacationGrantRepository.findByUserIdAndYear("user1", 2025)).willReturn(List.of());
            given(vacationUsageRepository.findByUserIdAndYear("user1", 2025)).willReturn(List.of(u1));
            given(vacationUsageRepository.findByPeriodWithUser(start, end)).willReturn(List.of(u1));
            given(vacationUsageDeductionRepository.findByUsageIds(anyList())).willReturn(deductions);

            VacationType fromHistory = vacationService.getUserVacationHistory("user1", 2025)
                    .getUsageVacationTypes().get(1L);
            VacationType fromCalendar = vacationService.getVacationUsagesByPeriod(start, end).get(0).getType();

            assertThat(fromHistory).isEqualTo(VacationType.BEREAVEMENT).isEqualTo(fromCalendar);
        }
    }

    @Nested
    @DisplayName("휴가 사용")
    class UseVacation {
        @Test
        @DisplayName("성공 - 분단위 사용이 허용된 정책이 있으면 HALFTIMEOFF 휴가를 사용할 수 있다")
        void useVacationWithMinuteTypeSuccess() {
            // given
            String userId = "user1";
            User user = createTestUser(userId);

            LocalDateTime startDate = LocalDateTime.of(2025, 6, 2, 9, 0);
            LocalDateTime endDate = LocalDateTime.of(2025, 6, 2, 9, 30);

            VacationServiceDto data = VacationServiceDto.builder()
                    .userId(userId)
                    .type(VacationType.ANNUAL)
                    .timeType(VacationTimeType.HALFTIMEOFF)
                    .startDate(startDate)
                    .endDate(endDate)
                    .desc("30분 휴가")
                    .build();

            // 분단위 부여 허용 정책
            VacationPolicy policyWithMinute = VacationPolicy.createManualGrantPolicy(
                    "연차(분단위)", "연차 휴가", VacationType.ANNUAL,
                    new BigDecimal("15.0000"), YNType.N, YNType.Y,
                    EffectiveType.IMMEDIATELY, ExpirationType.END_OF_YEAR
            );
            ReflectionTestUtils.setField(policyWithMinute, "rowId", 1L);

            VacationGrant grant = VacationGrant.createVacationGrant(
                    user, policyWithMinute, "휴가 부여", VacationType.ANNUAL, new BigDecimal("15.0000"),
                    LocalDateTime.of(2025, 1, 1, 0, 0),
                    LocalDateTime.of(2025, 12, 31, 23, 59)
            );
            ReflectionTestUtils.setField(grant, "rowId", 1L);

            given(userService.checkUserExist(userId)).willReturn(user);
            // 분단위 검증이 먼저 실행됨
            given(vacationPolicyRepository.findByVacationType(VacationType.ANNUAL))
                    .willReturn(List.of(policyWithMinute));
            // 분단위 검증 통과 후 공휴일 조회
            given(holidayRepository.findHolidaysByStartEndDateWithType(any(), any(), any(), any()))
                    .willReturn(List.of());
            given(vacationGrantRepository.findAvailableGrantsByUserIdAndTypeAndDate(eq(userId), eq(VacationType.ANNUAL), any()))
                    .willReturn(List.of(grant));
            willDoNothing().given(vacationUsageRepository).save(any(VacationUsage.class));
            willDoNothing().given(vacationUsageDeductionRepository).saveAll(anyList());

            // when
            Long result = vacationService.useVacation(data);

            // then
            assertThat(result).isNull(); // 저장 후 ID 반환되지만 mock이므로 null
            then(vacationUsageRepository).should().save(any(VacationUsage.class));
        }

        @Test
        @DisplayName("실패 - 분단위 사용이 허용되지 않으면 HALFTIMEOFF 휴가 사용 시 예외가 발생한다")
        void useVacationWithMinuteTypeFailNotAllowed() {
            // given
            String userId = "user1";
            User user = createTestUser(userId);

            LocalDateTime startDate = LocalDateTime.of(2025, 6, 2, 9, 0);
            LocalDateTime endDate = LocalDateTime.of(2025, 6, 2, 9, 30);

            VacationServiceDto data = VacationServiceDto.builder()
                    .userId(userId)
                    .type(VacationType.ANNUAL)
                    .timeType(VacationTimeType.HALFTIMEOFF)
                    .startDate(startDate)
                    .endDate(endDate)
                    .desc("30분 휴가")
                    .build();

            // 분단위 부여 불허 정책
            VacationPolicy policyWithoutMinute = VacationPolicy.createManualGrantPolicy(
                    "연차", "연차 휴가", VacationType.ANNUAL,
                    new BigDecimal("15.0000"), YNType.N, YNType.N,
                    EffectiveType.IMMEDIATELY, ExpirationType.END_OF_YEAR
            );
            ReflectionTestUtils.setField(policyWithoutMinute, "rowId", 1L);

            given(userService.checkUserExist(userId)).willReturn(user);
            // 분단위 검증이 먼저 실행되어 실패하므로 holidayRepository는 호출되지 않음
            given(vacationPolicyRepository.findByVacationType(VacationType.ANNUAL))
                    .willReturn(List.of(policyWithoutMinute));

            // when & then
            assertThatThrownBy(() -> vacationService.useVacation(data))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }

        @Test
        @DisplayName("성공 - 분단위 사용이 허용된 정책이 하나라도 있으면 HALFTIMEOFF 사용 가능")
        void useVacationWithMinuteTypeSuccessWhenAnyPolicyAllows() {
            // given
            String userId = "user1";
            User user = createTestUser(userId);

            LocalDateTime startDate = LocalDateTime.of(2025, 6, 2, 9, 0);
            LocalDateTime endDate = LocalDateTime.of(2025, 6, 2, 9, 30);

            VacationServiceDto data = VacationServiceDto.builder()
                    .userId(userId)
                    .type(VacationType.ANNUAL)
                    .timeType(VacationTimeType.HALFTIMEOFF)
                    .startDate(startDate)
                    .endDate(endDate)
                    .desc("30분 휴가")
                    .build();

            // 분단위 부여 불허 정책
            VacationPolicy policyWithoutMinute = VacationPolicy.createManualGrantPolicy(
                    "연차", "연차 휴가", VacationType.ANNUAL,
                    new BigDecimal("15.0000"), YNType.N, YNType.N,
                    EffectiveType.IMMEDIATELY, ExpirationType.END_OF_YEAR
            );
            ReflectionTestUtils.setField(policyWithoutMinute, "rowId", 1L);

            // 분단위 부여 허용 정책
            VacationPolicy policyWithMinute = VacationPolicy.createManualGrantPolicy(
                    "연차(분단위)", "연차 휴가", VacationType.ANNUAL,
                    new BigDecimal("15.0000"), YNType.N, YNType.Y,
                    EffectiveType.IMMEDIATELY, ExpirationType.END_OF_YEAR
            );
            ReflectionTestUtils.setField(policyWithMinute, "rowId", 2L);

            VacationGrant grant = VacationGrant.createVacationGrant(
                    user, policyWithMinute, "휴가 부여", VacationType.ANNUAL, new BigDecimal("15.0000"),
                    LocalDateTime.of(2025, 1, 1, 0, 0),
                    LocalDateTime.of(2025, 12, 31, 23, 59)
            );
            ReflectionTestUtils.setField(grant, "rowId", 1L);

            given(userService.checkUserExist(userId)).willReturn(user);
            // 분단위 검증이 먼저 실행됨
            given(vacationPolicyRepository.findByVacationType(VacationType.ANNUAL))
                    .willReturn(List.of(policyWithoutMinute, policyWithMinute));
            // 분단위 검증 통과 후 공휴일 조회
            given(holidayRepository.findHolidaysByStartEndDateWithType(any(), any(), any(), any()))
                    .willReturn(List.of());
            given(vacationGrantRepository.findAvailableGrantsByUserIdAndTypeAndDate(eq(userId), eq(VacationType.ANNUAL), any()))
                    .willReturn(List.of(grant));
            willDoNothing().given(vacationUsageRepository).save(any(VacationUsage.class));
            willDoNothing().given(vacationUsageDeductionRepository).saveAll(anyList());

            // when
            Long result = vacationService.useVacation(data);

            // then
            then(vacationUsageRepository).should().save(any(VacationUsage.class));
        }

        @Test
        @DisplayName("성공 - DAYOFF 타입은 분단위 검증을 하지 않는다")
        void useVacationWithDayoffTypeSkipsMinuteCheck() {
            // given
            String userId = "user1";
            User user = createTestUser(userId);

            LocalDateTime startDate = LocalDateTime.of(2025, 6, 2, 9, 0);
            LocalDateTime endDate = LocalDateTime.of(2025, 6, 2, 18, 0);

            VacationServiceDto data = VacationServiceDto.builder()
                    .userId(userId)
                    .type(VacationType.ANNUAL)
                    .timeType(VacationTimeType.DAYOFF)
                    .startDate(startDate)
                    .endDate(endDate)
                    .desc("연차")
                    .build();

            // 분단위 부여 불허 정책 (DAYOFF는 분단위 검증 대상이 아님)
            VacationPolicy policyWithoutMinute = VacationPolicy.createManualGrantPolicy(
                    "연차", "연차 휴가", VacationType.ANNUAL,
                    new BigDecimal("15.0000"), YNType.N, YNType.N,
                    EffectiveType.IMMEDIATELY, ExpirationType.END_OF_YEAR
            );
            ReflectionTestUtils.setField(policyWithoutMinute, "rowId", 1L);

            VacationGrant grant = VacationGrant.createVacationGrant(
                    user, policyWithoutMinute, "휴가 부여", VacationType.ANNUAL, new BigDecimal("15.0000"),
                    LocalDateTime.of(2025, 1, 1, 0, 0),
                    LocalDateTime.of(2025, 12, 31, 23, 59)
            );
            ReflectionTestUtils.setField(grant, "rowId", 1L);

            given(userService.checkUserExist(userId)).willReturn(user);
            given(holidayRepository.findHolidaysByStartEndDateWithType(any(), any(), any(), any()))
                    .willReturn(List.of());
            given(vacationGrantRepository.findAvailableGrantsByUserIdAndTypeAndDate(eq(userId), eq(VacationType.ANNUAL), any()))
                    .willReturn(List.of(grant));
            willDoNothing().given(vacationUsageRepository).save(any(VacationUsage.class));
            willDoNothing().given(vacationUsageDeductionRepository).saveAll(anyList());

            // when
            Long result = vacationService.useVacation(data);

            // then - 분단위 검증 로직이 호출되지 않음 (findByVacationType 호출 안됨)
            then(vacationPolicyRepository).should(never()).findByVacationType(any());
            then(vacationUsageRepository).should().save(any(VacationUsage.class));
        }

        @Test
        @DisplayName("실패 - 해당 VacationType의 정책이 없으면 분단위 사용 불가")
        void useVacationWithMinuteTypeFailNoPolicies() {
            // given
            String userId = "user1";
            User user = createTestUser(userId);

            LocalDateTime startDate = LocalDateTime.of(2025, 6, 2, 9, 0);
            LocalDateTime endDate = LocalDateTime.of(2025, 6, 2, 9, 30);

            VacationServiceDto data = VacationServiceDto.builder()
                    .userId(userId)
                    .type(VacationType.ANNUAL)
                    .timeType(VacationTimeType.HALFTIMEOFF)
                    .startDate(startDate)
                    .endDate(endDate)
                    .desc("30분 휴가")
                    .build();

            given(userService.checkUserExist(userId)).willReturn(user);
            // 분단위 검증이 먼저 실행되어 실패하므로 holidayRepository는 호출되지 않음
            given(vacationPolicyRepository.findByVacationType(VacationType.ANNUAL))
                    .willReturn(List.of());

            // when & then
            assertThatThrownBy(() -> vacationService.useVacation(data))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }
    }

    @Nested
    @DisplayName("휴가 신청 - 승인자 결재선")
    class RequestVacationApprovers {
        private Department deptHeadedBy(String headId, long level) {
            Department dept = mock(Department.class);
            User head = createTestUser(headId);
            lenient().when(dept.getHeadUser()).thenReturn(head);
            lenient().when(dept.getLevel()).thenReturn(level);
            return dept;
        }

        private void givenRequestSetup(String userId, Department... chain) {
            User user = createTestUser(userId);
            VacationPolicy policy = VacationPolicy.createOnRequestPolicy(
                    "연차", "연차 신청", VacationType.ANNUAL, new BigDecimal("1.0000"),
                    YNType.N, YNType.N, 1, EffectiveType.IMMEDIATELY, ExpirationType.END_OF_YEAR);
            ReflectionTestUtils.setField(policy, "rowId", 7L);

            VacationPlan plan = mock(VacationPlan.class);
            given(plan.hasPolicy(7L)).willReturn(true);
            UserVacationPlan userPlan = mock(UserVacationPlan.class);
            given(userPlan.getIsDeleted()).willReturn(YNType.N);
            given(userPlan.getVacationPlan()).willReturn(plan);

            given(userService.checkUserExist(userId)).willReturn(user);
            given(vacationPolicyRepository.findByRowId(7L)).willReturn(Optional.of(policy));
            given(userVacationPlanRepository.findByUserIdWithPlanAndPolicies(userId)).willReturn(List.of(userPlan));
            given(departmentRepository.findApproversByUserId(userId)).willReturn(List.of(chain));
        }

        private VacationServiceDto request(String... approverIds) {
            return VacationServiceDto.builder()
                    .policyId(7L)
                    .desc("연차 신청")
                    .approverIds(new java.util.ArrayList<>(List.of(approverIds)))
                    .grantTime(new BigDecimal("1.0000"))
                    .requestStartTime(LocalDateTime.of(2030, 1, 2, 9, 0))
                    .requestEndTime(LocalDateTime.of(2030, 1, 2, 18, 0))
                    .requestDesc("개인 사유")
                    .build();
        }

        @Test
        @DisplayName("실패 - 신청자의 상위 부서장이 아닌 다른 부서장을 승인자로 고르면 거절한다")
        void requestVacationFailApproverOutsideChain() {
            // given: 신청자의 결재선은 boss 하나, friend 는 다른 부서의 부서장
            Department bossDept = deptHeadedBy("boss", 1L);
            Department friendDept = deptHeadedBy("friend", 1L);
            givenRequestSetup("u1", bossDept);
            given(userService.checkUserExist("friend")).willReturn(createTestUser("friend"));
            given(departmentRepository.findByUserIds(List.of("friend"))).willReturn(List.of(friendDept));

            // when & then
            assertThatThrownBy(() -> vacationService.requestVacation("u1", request("friend")))
                    .isInstanceOf(InvalidValueException.class);
            then(vacationGrantRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("성공 - 결재선 안의 부서장을 승인자로 고르면 신청이 만들어진다")
        void requestVacationSuccessApproverInChain() {
            // given
            Department bossDept = deptHeadedBy("boss", 1L);
            givenRequestSetup("u1", bossDept);
            given(userService.checkUserExist("boss")).willReturn(createTestUser("boss"));
            given(departmentRepository.findByUserIds(List.of("boss"))).willReturn(List.of(bossDept));
            OnRequest onRequest = mock(OnRequest.class);
            given(vacationPolicyStrategyFactory.getStrategy(GrantMethod.ON_REQUEST)).willReturn(onRequest);
            given(onRequest.calculateGrantTime(any(), any())).willReturn(new BigDecimal("1.0000"));

            // when
            vacationService.requestVacation("u1", request("boss"));

            // then
            then(vacationGrantRepository).should().save(any(VacationGrant.class));
        }
    }

    // 테스트 헬퍼 메서드들
    private User createTestUser(String userId) {
        return User.createUser(
                null, userId, "테스트유저", "test@test.com",
                LocalDate.of(1990, 1, 1), "NONE", "9 ~ 18",
                LocalDate.now(), YNType.N, null, null, CountryCode.KR
        );
    }

    private VacationGrant statGrant(User user, GrantStatus status, String grantTime) {
        VacationPolicy policy = createTestPolicy();
        VacationGrant grant = VacationGrant.createPendingVacationGrant(
                user, policy, "신청", VacationType.ANNUAL, new BigDecimal(grantTime),
                LocalDateTime.of(2025, 3, 10, 9, 0), null, "사유");
        ReflectionTestUtils.setField(grant, "status", status);
        return grant;
    }

    private VacationGrant createTestGrant(User user) {
        return createTestGrant(user, VacationType.ANNUAL, new BigDecimal("15.0000"));
    }

    private VacationGrant createTestGrant(User user, VacationType type, BigDecimal remainTime) {
        VacationPolicy policy = createTestPolicy();
        ReflectionTestUtils.setField(policy, "rowId", 1L);

        return VacationGrant.createVacationGrant(
                user, policy, "휴가 부여", type, remainTime,
                LocalDateTime.of(2025, 1, 1, 0, 0),
                LocalDateTime.of(2025, 12, 31, 23, 59)
        );
    }

    private VacationUsage createTestUsage(User user) {
        return VacationUsage.createVacationUsage(
                user, "연차 사용", VacationTimeType.DAYOFF,
                LocalDateTime.of(2025, 6, 1, 9, 0),
                LocalDateTime.of(2025, 6, 1, 18, 0),
                new BigDecimal("1.0000")
        );
    }

    private VacationPolicy createTestPolicy() {
        VacationPolicy policy = VacationPolicy.createManualGrantPolicy(
                "연차", "연차 휴가", VacationType.ANNUAL,
                new BigDecimal("15.0000"), YNType.N, YNType.N,
                EffectiveType.IMMEDIATELY, ExpirationType.END_OF_YEAR
        );
        return policy;
    }
}
