package io.groo.ieum.service;

import io.groo.core.exception.BusinessRuleViolationException;
import io.groo.core.exception.EntityNotFoundException;
import io.groo.core.exception.InvalidValueException;
import io.groo.core.type.CountryCode;
import io.groo.core.type.YNType;
import io.groo.ieum.company.domain.Company;
import io.groo.ieum.company.service.CompanyService;
import io.groo.ieum.department.domain.Department;
import io.groo.ieum.department.domain.UserDepartment;
import io.groo.ieum.department.repository.DepartmentRepository;
import io.groo.ieum.department.service.DepartmentService;
import io.groo.ieum.department.service.DepartmentServiceImpl;
import io.groo.ieum.department.service.dto.DepartmentServiceDto;
import io.groo.ieum.department.service.dto.UserDepartmentServiceDto;
import io.groo.ieum.user.domain.User;
import io.groo.ieum.user.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@Slf4j
@ExtendWith(MockitoExtension.class)
@DisplayName("부서 서비스 테스트")
class DepartmentServiceTest {
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private CompanyService companyService;
    @Mock
    private UserService userService;

    @InjectMocks
    private DepartmentServiceImpl departmentService;

    // 테스트용 User 생성 헬퍼 메소드
    private User createTestUser(String id) {
        return User.createUser(
                null, id, "테스트유저", "test@test.com",
                LocalDate.of(1990, 1, 1), "NONE", "9 ~ 18",
                LocalDate.now(), YNType.N, null, null, CountryCode.KR
        );
    }

    @Nested
    @DisplayName("부서 등록")
    class Regist {
        @Test
        @DisplayName("성공 - 최상위 부서가 정상적으로 등록된다")
        void registRootDepartmentSuccess() {
            // given
            Company company = Company.createCompany("COMPANY001", "테스트 회사", "설명");
            setCompanyId(company, 1L);
            DepartmentServiceDto data = DepartmentServiceDto.builder()
                    .companyId("COMPANY001")
                    .name("Development")
                    .nameKR("개발팀")
                    .headUserId("user1")
                    .level(1L)
                    .desc("개발팀 설명")
                    .color("#FF0000")
                    .build();

            given(companyService.checkCompanyExists("COMPANY001")).willReturn(company);
            willDoNothing().given(departmentRepository).save(any(Department.class));

            // when
            departmentService.regist(data);

            // then
            then(companyService).should().checkCompanyExists("COMPANY001");
            then(departmentRepository).should().save(any(Department.class));
        }

        @Test
        @DisplayName("성공 - 하위 부서가 정상적으로 등록된다")
        void registChildDepartmentSuccess() {
            // given
            Company company = Company.createCompany("COMPANY001", "테스트 회사", "설명");
            setCompanyId(company, 1L);
            Department parent = Department.createDepartment("Development", "개발팀", null, null, 1L, "설명", "#FF0000", company);
            setDepartmentId(parent, 1L);

            DepartmentServiceDto data = DepartmentServiceDto.builder()
                    .companyId("COMPANY001")
                    .parentId(1L)
                    .name("Backend")
                    .nameKR("백엔드팀")
                    .headUserId("user2")
                    .level(2L)
                    .build();

            given(companyService.checkCompanyExists("COMPANY001")).willReturn(company);
            given(departmentRepository.findById(1L)).willReturn(Optional.of(parent));
            willDoNothing().given(departmentRepository).save(any(Department.class));

            // when
            departmentService.regist(data);

            // then
            then(companyService).should().checkCompanyExists("COMPANY001");
            then(departmentRepository).should().findById(1L);
            then(departmentRepository).should().save(any(Department.class));
        }

        @Test
        @DisplayName("실패 - 부모 부서가 다른 회사면 예외가 발생한다")
        void registFailDifferentCompany() {
            // given
            Company company1 = Company.createCompany("COMPANY001", "회사1", "설명");
            setCompanyId(company1, 1L);
            Company company2 = Company.createCompany("COMPANY002", "회사2", "설명");
            setCompanyId(company2, 2L);
            Department parent = Department.createDepartment("Development", "개발팀", null, null, 1L, "설명", "#FF0000", company2);
            setDepartmentId(parent, 1L);

            DepartmentServiceDto data = DepartmentServiceDto.builder()
                    .companyId("COMPANY001")
                    .parentId(1L)
                    .name("Backend")
                    .nameKR("백엔드팀")
                    .build();

            given(companyService.checkCompanyExists("COMPANY001")).willReturn(company1);
            given(departmentRepository.findById(1L)).willReturn(Optional.of(parent));

            // when & then
            assertThatThrownBy(() -> departmentService.regist(data))
                    .isInstanceOf(InvalidValueException.class);
        }
    }

    @Nested
    @DisplayName("부서 수정")
    class Edit {
        @Test
        @DisplayName("성공 - 부서 정보가 수정된다")
        void editSuccess() {
            // given
            Company company = Company.createCompany("COMPANY001", "회사", "설명");
            setCompanyId(company, 1L);
            Department department = Department.createDepartment("Development", "개발팀", null, null, 1L, "설명", "#FF0000", company);
            setDepartmentId(department, 1L);

            given(departmentRepository.findById(1L)).willReturn(Optional.of(department));

            DepartmentServiceDto data = DepartmentServiceDto.builder()
                    .id(1L)
                    .name("Dev Team")
                    .nameKR("개발팀 수정")
                    .headUserId("user2")
                    .build();

            // when
            departmentService.edit(data);

            // then
            then(departmentRepository).should().findById(1L);
            assertThat(department.getName()).isEqualTo("Dev Team");
            assertThat(department.getNameKR()).isEqualTo("개발팀 수정");
        }

        @Test
        @DisplayName("실패 - 자기 자신을 부모로 설정하면 예외가 발생한다")
        void editFailSelfParent() {
            // given
            Company company = Company.createCompany("COMPANY001", "회사", "설명");
            setCompanyId(company, 1L);
            Department department = Department.createDepartment("Development", "개발팀", null, null, 1L, "설명", "#FF0000", company);
            setDepartmentId(department, 1L);

            // 자기 자신을 부모로 설정: findById가 두 번 호출됨 (department, newParent)
            given(departmentRepository.findById(1L)).willReturn(Optional.of(department));

            DepartmentServiceDto data = DepartmentServiceDto.builder()
                    .id(1L)
                    .parentId(1L)
                    .name("Development")
                    .build();

            // when & then
            assertThatThrownBy(() -> departmentService.edit(data))
                    .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("실패 - 순환 참조가 발생하면 예외가 발생한다")
        void editFailCircularReference() {
            // given
            Company company = Company.createCompany("COMPANY001", "회사", "설명");
            setCompanyId(company, 1L);
            Department parent = Department.createDepartment("Development", "개발팀", null, null, 1L, "설명", "#FF0000", company);
            setDepartmentId(parent, 1L);
            Department child = Department.createDepartment("Backend", "백엔드팀", parent, null, 2L, "설명", "#00FF00", company);
            setDepartmentId(child, 2L);

            // 부모(1L)를 수정할 때 자식(2L)을 부모로 설정하려고 함
            given(departmentRepository.findById(1L)).willReturn(Optional.of(parent));
            given(departmentRepository.findById(2L)).willReturn(Optional.of(child));

            DepartmentServiceDto data = DepartmentServiceDto.builder()
                    .id(1L)
                    .parentId(2L)
                    .name("Development")
                    .build();

            // when & then
            assertThatThrownBy(() -> departmentService.edit(data))
                    .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("실패 - 부모 부서가 다른 회사면 예외가 발생한다")
        void editFailDifferentCompany() {
            // given
            Company company1 = Company.createCompany("COMPANY001", "회사1", "설명");
            setCompanyId(company1, 1L);
            Company company2 = Company.createCompany("COMPANY002", "회사2", "설명");
            setCompanyId(company2, 2L);

            Department department = Department.createDepartment("Development", "개발팀", null, null, 1L, "설명", "#FF0000", company1);
            setDepartmentId(department, 1L);
            Department otherDept = Department.createDepartment("Other", "다른팀", null, null, 1L, "설명", "#00FF00", company2);
            setDepartmentId(otherDept, 2L);

            given(departmentRepository.findById(1L)).willReturn(Optional.of(department));
            given(departmentRepository.findById(2L)).willReturn(Optional.of(otherDept));

            DepartmentServiceDto data = DepartmentServiceDto.builder()
                    .id(1L)
                    .parentId(2L)
                    .name("Development")
                    .build();

            // when & then
            assertThatThrownBy(() -> departmentService.edit(data))
                    .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("성공 - 부모 부서를 변경한다")
        void editSuccessWithParentChange() {
            // given
            Company company = Company.createCompany("COMPANY001", "회사", "설명");
            setCompanyId(company, 1L);

            Department department = Department.createDepartment("Backend", "백엔드팀", null, null, 2L, "설명", "#FF0000", company);
            setDepartmentId(department, 1L);
            Department newParent = Department.createDepartment("Development", "개발팀", null, null, 1L, "설명", "#00FF00", company);
            setDepartmentId(newParent, 2L);

            given(departmentRepository.findById(1L)).willReturn(Optional.of(department));
            given(departmentRepository.findById(2L)).willReturn(Optional.of(newParent));

            DepartmentServiceDto data = DepartmentServiceDto.builder()
                    .id(1L)
                    .parentId(2L)
                    .name("Backend Team")
                    .build();

            // when
            departmentService.edit(data);

            // then
            assertThat(department.getName()).isEqualTo("Backend Team");
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 부서를 수정하려 하면 예외가 발생한다")
        void editFailNotFound() {
            // given
            given(departmentRepository.findById(999L)).willReturn(Optional.empty());

            DepartmentServiceDto data = DepartmentServiceDto.builder()
                    .id(999L)
                    .name("Department")
                    .build();

            // when & then
            assertThatThrownBy(() -> departmentService.edit(data))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("부서 삭제")
    class Delete {
        @Test
        @DisplayName("성공 - 하위 부서가 없는 부서가 삭제된다")
        void deleteSuccess() {
            // given
            Company company = Company.createCompany("COMPANY001", "회사", "설명");
            setCompanyId(company, 1L);
            Department department = Department.createDepartment("Development", "개발팀", null, null, 1L, "설명", "#FF0000", company);
            setDepartmentId(department, 1L);

            given(departmentRepository.findById(1L)).willReturn(Optional.of(department));

            // when
            departmentService.delete(1L);

            // then
            then(departmentRepository).should().findById(1L);
            assertThat(department.getIsDeleted()).isEqualTo(YNType.Y);
        }

        @Test
        @DisplayName("실패 - 하위 부서가 있으면 예외가 발생한다")
        void deleteFailHasChildren() {
            // given
            Company company = Company.createCompany("COMPANY001", "회사", "설명");
            setCompanyId(company, 1L);
            Department parent = Department.createDepartment("Development", "개발팀", null, null, 1L, "설명", "#FF0000", company);
            setDepartmentId(parent, 1L);
            Department child = Department.createDepartment("Backend", "백엔드팀", parent, null, 2L, "설명", "#00FF00", company);

            given(departmentRepository.findById(1L)).willReturn(Optional.of(parent));

            // when & then
            assertThatThrownBy(() -> departmentService.delete(1L))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }

        @Test
        @DisplayName("성공 - 삭제된 하위 부서만 있으면 삭제된다")
        void deleteSuccessWithDeletedChildren() {
            // given
            Company company = Company.createCompany("COMPANY001", "회사", "설명");
            setCompanyId(company, 1L);
            Department parent = Department.createDepartment("Development", "개발팀", null, null, 1L, "설명", "#FF0000", company);
            setDepartmentId(parent, 1L);
            Department child = Department.createDepartment("Backend", "백엔드팀", parent, null, 2L, "설명", "#00FF00", company);
            child.deleteDepartment(); // 자식 부서 삭제 처리

            given(departmentRepository.findById(1L)).willReturn(Optional.of(parent));

            // when
            departmentService.delete(1L);

            // then
            then(departmentRepository).should().findById(1L);
            assertThat(parent.getIsDeleted()).isEqualTo(YNType.Y);
        }
    }

    @Nested
    @DisplayName("부서 조회")
    class SearchDepartment {
        @Test
        @DisplayName("성공 - 부서 정보를 반환한다")
        void searchDepartmentByIdSuccess() {
            // given
            Company company = Company.createCompany("COMPANY001", "회사", "설명");
            setCompanyId(company, 1L);
            Department department = Department.createDepartment("Development", "개발팀", null, null, 1L, "설명", "#FF0000", company);
            setDepartmentId(department, 1L);

            given(departmentRepository.findById(1L)).willReturn(Optional.of(department));

            // when
            DepartmentServiceDto result = departmentService.searchDepartmentById(1L);

            // then
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getName()).isEqualTo("Development");
            assertThat(result.getNameKR()).isEqualTo("개발팀");
        }

        @Test
        @DisplayName("성공 - 하위 부서 포함하여 부서 정보를 반환한다")
        void searchDepartmentByIdWithChildrenSuccess() {
            // given
            Company company = Company.createCompany("COMPANY001", "회사", "설명");
            setCompanyId(company, 1L);
            Department department = Department.createDepartment("Development", "개발팀", null, null, 1L, "설명", "#FF0000", company);
            setDepartmentId(department, 1L);
            Department child = Department.createDepartment("Backend", "백엔드팀", department, null, 2L, "설명", "#00FF00", company);
            setDepartmentId(child, 2L);

            given(departmentRepository.findById(1L)).willReturn(Optional.of(department));

            // when
            DepartmentServiceDto result = departmentService.searchDepartmentByIdWithChildren(1L);

            // then
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getName()).isEqualTo("Development");
            assertThat(result.getChildren()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("사용자 부서 등록")
    class RegistUserDepartments {
        @Test
        @DisplayName("성공 - 사용자가 부서에 등록된다")
        void registUserDepartmentsSuccess() {
            // given
            Company company = Company.createCompany("COMPANY001", "회사", "설명");
            setCompanyId(company, 1L);
            Department department = Department.createDepartment("Development", "개발팀", null, null, 1L, "설명", "#FF0000", company);
            setDepartmentId(department, 1L);
            User user = createTestUser("user1");

            List<UserDepartmentServiceDto> userDataList = List.of(
                    UserDepartmentServiceDto.builder()
                            .userId("user1")
                            .mainYN(YNType.Y)
                            .build()
            );

            given(departmentRepository.findById(1L)).willReturn(Optional.of(department));
            given(userService.checkUserExist("user1")).willReturn(user);
            given(departmentRepository.findMainDepartmentByUserId("user1")).willReturn(Optional.empty());
            willDoNothing().given(departmentRepository).saveUserDepartment(any(UserDepartment.class));

            // when
            List<Long> result = departmentService.registUserDepartments(userDataList, 1L);

            // then
            then(departmentRepository).should().findById(1L);
            then(userService).should().checkUserExist("user1");
            then(departmentRepository).should().saveUserDepartment(any(UserDepartment.class));
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("실패 - 이미 메인 부서가 있는 사용자에게 메인 부서를 설정하면 예외가 발생한다")
        void registUserDepartmentsFailAlreadyHasMain() {
            // given
            Company company = Company.createCompany("COMPANY001", "회사", "설명");
            setCompanyId(company, 1L);
            Department department = Department.createDepartment("Development", "개발팀", null, null, 1L, "설명", "#FF0000", company);
            setDepartmentId(department, 1L);
            User user = createTestUser("user1");
            UserDepartment existingMain = UserDepartment.createUserDepartment(user, department, YNType.Y);

            List<UserDepartmentServiceDto> userDataList = List.of(
                    UserDepartmentServiceDto.builder()
                            .userId("user1")
                            .mainYN(YNType.Y)
                            .build()
            );

            given(departmentRepository.findById(1L)).willReturn(Optional.of(department));
            given(userService.checkUserExist("user1")).willReturn(user);
            given(departmentRepository.findMainDepartmentByUserId("user1")).willReturn(Optional.of(existingMain));

            // when & then
            assertThatThrownBy(() -> departmentService.registUserDepartments(userDataList, 1L))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }

        @Test
        @DisplayName("성공 - 메인이 아닌 부서로 사용자가 등록된다")
        void registUserDepartmentsSuccessNotMain() {
            // given
            Company company = Company.createCompany("COMPANY001", "회사", "설명");
            setCompanyId(company, 1L);
            Department department = Department.createDepartment("Development", "개발팀", null, null, 1L, "설명", "#FF0000", company);
            setDepartmentId(department, 1L);
            User user = createTestUser("user1");

            List<UserDepartmentServiceDto> userDataList = List.of(
                    UserDepartmentServiceDto.builder()
                            .userId("user1")
                            .mainYN(YNType.N)
                            .build()
            );

            given(departmentRepository.findById(1L)).willReturn(Optional.of(department));
            given(userService.checkUserExist("user1")).willReturn(user);
            willDoNothing().given(departmentRepository).saveUserDepartment(any(UserDepartment.class));

            // when
            List<Long> result = departmentService.registUserDepartments(userDataList, 1L);

            // then
            then(departmentRepository).should().findById(1L);
            then(userService).should().checkUserExist("user1");
            then(departmentRepository).should().saveUserDepartment(any(UserDepartment.class));
            // mainYN이 N이므로 findMainDepartmentByUserId는 호출되지 않음
            then(departmentRepository).should(never()).findMainDepartmentByUserId(any());
            assertThat(result).hasSize(1);
        }
    }

    @Nested
    @DisplayName("사용자 부서 삭제")
    class DeleteUserDepartments {
        @Test
        @DisplayName("성공 - 사용자가 부서에서 제거된다")
        void deleteUserDepartmentsSuccess() {
            // given
            Company company = Company.createCompany("COMPANY001", "회사", "설명");
            setCompanyId(company, 1L);
            Department department = Department.createDepartment("Development", "개발팀", null, null, 1L, "설명", "#FF0000", company);
            setDepartmentId(department, 1L);
            User user = createTestUser("user1");
            UserDepartment userDepartment = UserDepartment.createUserDepartment(user, department, YNType.N);

            given(departmentRepository.findUserDepartment("user1", 1L)).willReturn(Optional.of(userDepartment));

            // when
            departmentService.deleteUserDepartments(List.of("user1"), 1L);

            // then
            then(departmentRepository).should().findUserDepartment("user1", 1L);
            assertThat(userDepartment.getIsDeleted()).isEqualTo(YNType.Y);
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 사용자 부서면 예외가 발생한다")
        void deleteUserDepartmentsFailNotFound() {
            // given
            given(departmentRepository.findUserDepartment("user1", 1L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> departmentService.deleteUserDepartments(List.of("user1"), 1L))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("주 부서 옮기기")
    class ChangeMainDepartment {
        private Company company() {
            Company company = Company.createCompany("COMPANY001", "회사", "설명");
            setCompanyId(company, 1L);
            return company;
        }

        private Department dept(String name, Long id, Company company) {
            Department d = Department.createDepartment(name, name, null, null, 1L, "설명", "#FF0000", company);
            setDepartmentId(d, id);
            return d;
        }

        @Test
        @DisplayName("성공 - 다른 부서가 주 부서인 사람을 이 부서로 옮긴다 (앞 N · 이 Y · 한 사람에 Y 하나)")
        void movesMainFromOtherDepartment() {
            Company company = company();
            Department a = dept("A", 1L, company);
            Department b = dept("B", 2L, company);
            Department c = dept("C", 3L, company);
            User user = createTestUser("user1");
            UserDepartment inA = UserDepartment.createUserDepartment(user, a, YNType.Y);
            UserDepartment inB = UserDepartment.createUserDepartment(user, b, YNType.N);
            UserDepartment inC = UserDepartment.createUserDepartment(user, c, YNType.N);
            given(departmentRepository.findById(2L)).willReturn(Optional.of(b));
            given(departmentRepository.findUserDepartment("user1", 2L)).willReturn(Optional.of(inB));

            departmentService.changeMainDepartment(2L, "user1");

            assertThat(inA.getMainYN()).isEqualTo(YNType.N);
            assertThat(inB.getMainYN()).isEqualTo(YNType.Y);
            assertThat(inC.getMainYN()).isEqualTo(YNType.N);
            assertThat(user.getUserDepartments().stream().filter(ud -> ud.getMainYN() == YNType.Y)).hasSize(1);
        }

        @Test
        @DisplayName("성공 - 주 부서가 없던 사람은 이 부서가 주 부서가 된다")
        void setsMainWhenNone() {
            Company company = company();
            Department b = dept("B", 2L, company);
            User user = createTestUser("user1");
            UserDepartment inB = UserDepartment.createUserDepartment(user, b, YNType.N);
            given(departmentRepository.findById(2L)).willReturn(Optional.of(b));
            given(departmentRepository.findUserDepartment("user1", 2L)).willReturn(Optional.of(inB));

            departmentService.changeMainDepartment(2L, "user1");

            assertThat(inB.getMainYN()).isEqualTo(YNType.Y);
        }

        @Test
        @DisplayName("성공 - 이미 이 부서가 주 부서면 그대로 성공한다")
        void alreadyMainIsNoop() {
            Company company = company();
            Department a = dept("A", 1L, company);
            Department b = dept("B", 2L, company);
            User user = createTestUser("user1");
            UserDepartment inA = UserDepartment.createUserDepartment(user, a, YNType.Y);
            UserDepartment inB = UserDepartment.createUserDepartment(user, b, YNType.N);
            given(departmentRepository.findById(1L)).willReturn(Optional.of(a));
            given(departmentRepository.findUserDepartment("user1", 1L)).willReturn(Optional.of(inA));

            departmentService.changeMainDepartment(1L, "user1");

            assertThat(inA.getMainYN()).isEqualTo(YNType.Y);
            assertThat(inB.getMainYN()).isEqualTo(YNType.N);
        }

        @Test
        @DisplayName("성공 - 지운 소속은 건드리지 않는다")
        void leavesDeletedMembershipAlone() {
            Company company = company();
            Department a = dept("A", 1L, company);
            Department b = dept("B", 2L, company);
            User user = createTestUser("user1");
            UserDepartment oldA = UserDepartment.createUserDepartment(user, a, YNType.Y);
            oldA.deleteUserDepartment(); // main 도 N 이 되고 isDeleted = Y
            UserDepartment inB = UserDepartment.createUserDepartment(user, b, YNType.N);
            given(departmentRepository.findById(2L)).willReturn(Optional.of(b));
            given(departmentRepository.findUserDepartment("user1", 2L)).willReturn(Optional.of(inB));

            departmentService.changeMainDepartment(2L, "user1");

            assertThat(oldA.getIsDeleted()).isEqualTo(YNType.Y);
            assertThat(oldA.getMainYN()).isEqualTo(YNType.N);
            assertThat(inB.getMainYN()).isEqualTo(YNType.Y);
        }

        @Test
        @DisplayName("실패 - 이 부서 구성원이 아니면 DEPARTMENT_NOT_FOUND 이고 다른 소속은 그대로다")
        void notMemberFails() {
            Company company = company();
            Department a = dept("A", 1L, company);
            Department b = dept("B", 2L, company);
            User user = createTestUser("user1");
            UserDepartment inA = UserDepartment.createUserDepartment(user, a, YNType.Y);
            given(departmentRepository.findById(2L)).willReturn(Optional.of(b));
            given(departmentRepository.findUserDepartment("user1", 2L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> departmentService.changeMainDepartment(2L, "user1"))
                    .isInstanceOf(EntityNotFoundException.class)
                    .extracting("errorCode").isEqualTo(io.groo.ieum.common.exception.HrErrorCode.DEPARTMENT_NOT_FOUND);
            assertThat(inA.getMainYN()).isEqualTo(YNType.Y);
        }

        @Test
        @DisplayName("실패 - 없는 부서면 DEPARTMENT_NOT_FOUND")
        void departmentNotFoundFails() {
            given(departmentRepository.findById(9L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> departmentService.changeMainDepartment(9L, "user1"))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("지금 규칙 그대로 - 주 부서가 이미 있는 사람을 주 부서로 새로 넣으면 거절한다")
        void registMainStillRejectedWhenMainExists() {
            Company company = company();
            Department b = dept("B", 2L, company);
            User user = createTestUser("user1");
            given(departmentRepository.findById(2L)).willReturn(Optional.of(b));
            given(userService.checkUserExist("user1")).willReturn(user);
            given(departmentRepository.findMainDepartmentByUserId(user.getId()))
                    .willReturn(Optional.of(UserDepartment.createUserDepartment(user, dept("A", 1L, company), YNType.Y)));

            assertThatThrownBy(() -> departmentService.registUserDepartments(
                    List.of(UserDepartmentServiceDto.builder().userId("user1").mainYN(YNType.Y).build()), 2L))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }
    }

    @Nested
    @DisplayName("부서 내 사용자 조회")
    class GetUsersInAndNotInDepartment {
        @Test
        @DisplayName("성공 - 부서에 속한 사용자와 속하지 않은 사용자를 조회한다")
        void getUsersInAndNotInDepartmentSuccess() {
            // given
            Company company = Company.createCompany("COMPANY001", "회사", "설명");
            setCompanyId(company, 1L);
            Department department = Department.createDepartment("Development", "개발팀", null, null, 1L, "설명", "#FF0000", company);
            setDepartmentId(department, 1L);

            User userIn = createTestUser("user1");
            User userNotIn = createTestUser("user2");
            UserDepartment userDepartment = UserDepartment.createUserDepartment(userIn, department, YNType.Y);

            given(departmentRepository.findById(1L)).willReturn(Optional.of(department));
            given(departmentRepository.findUserDepartmentsInDepartment(1L)).willReturn(List.of(userDepartment));
            given(departmentRepository.findUsersNotInDepartment(1L)).willReturn(List.of(userNotIn));

            // when
            DepartmentServiceDto result = departmentService.getUsersInAndNotInDepartment(1L);

            // then
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getName()).isEqualTo("Development");
            assertThat(result.getUsersInDepartment()).hasSize(1);
            assertThat(result.getUsersNotInDepartment()).hasSize(1);
            assertThat(result.getCompanyId()).isEqualTo("COMPANY001");
        }

        @Test
        @DisplayName("성공 - 부서에 속한 사용자가 없는 경우")
        void getUsersInAndNotInDepartmentEmptyUsersIn() {
            // given
            Company company = Company.createCompany("COMPANY001", "회사", "설명");
            setCompanyId(company, 1L);
            Department department = Department.createDepartment("Development", "개발팀", null, null, 1L, "설명", "#FF0000", company);
            setDepartmentId(department, 1L);

            User userNotIn = createTestUser("user1");

            given(departmentRepository.findById(1L)).willReturn(Optional.of(department));
            given(departmentRepository.findUserDepartmentsInDepartment(1L)).willReturn(List.of());
            given(departmentRepository.findUsersNotInDepartment(1L)).willReturn(List.of(userNotIn));

            // when
            DepartmentServiceDto result = departmentService.getUsersInAndNotInDepartment(1L);

            // then
            assertThat(result.getUsersInDepartment()).isEmpty();
            assertThat(result.getUsersNotInDepartment()).hasSize(1);
        }

    }

    @Nested
    @DisplayName("부서 존재 확인")
    class CheckDepartmentExists {
        @Test
        @DisplayName("성공 - 존재하는 부서를 반환한다")
        void checkDepartmentExistsSuccess() {
            // given
            Company company = Company.createCompany("COMPANY001", "회사", "설명");
            setCompanyId(company, 1L);
            Department department = Department.createDepartment("Development", "개발팀", null, null, 1L, "설명", "#FF0000", company);
            setDepartmentId(department, 1L);

            given(departmentRepository.findById(1L)).willReturn(Optional.of(department));

            // when
            Department result = departmentService.checkDepartmentExists(1L);

            // then
            assertThat(result).isEqualTo(department);
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 부서면 예외가 발생한다")
        void checkDepartmentExistsFailNotFound() {
            // given
            given(departmentRepository.findById(999L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> departmentService.checkDepartmentExists(999L))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("실패 - 삭제된 부서면 예외가 발생한다")
        void checkDepartmentExistsFailDeleted() {
            // given
            Company company = Company.createCompany("COMPANY001", "회사", "설명");
            setCompanyId(company, 1L);
            Department department = Department.createDepartment("Development", "개발팀", null, null, 1L, "설명", "#FF0000", company);
            department.deleteDepartment();

            given(departmentRepository.findById(1L)).willReturn(Optional.of(department));

            // when & then
            assertThatThrownBy(() -> departmentService.checkDepartmentExists(1L))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    // 테스트 헬퍼 메서드
    /** 영속 엔티티를 흉내 낸다 — rowId 가 null 이면 회사 비교(getRowId().equals)가 NPE 로 죽는다. */
    private void setCompanyId(Company company, Long id) {
        try {
            java.lang.reflect.Field field = Company.class.getDeclaredField("rowId");
            field.setAccessible(true);
            field.set(company, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setDepartmentId(Department department, Long id) {
        try {
            java.lang.reflect.Field field = Department.class.getDeclaredField("rowId");
            field.setAccessible(true);
            field.set(department, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
