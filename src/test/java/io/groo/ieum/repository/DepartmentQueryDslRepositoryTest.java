package io.groo.ieum.repository;

import io.groo.core.type.CountryCode;
import io.groo.ieum.company.domain.Company;
import io.groo.ieum.department.domain.Department;
import io.groo.ieum.department.domain.UserDepartment;
import io.groo.ieum.department.repository.DepartmentQueryDslRepository;
import io.groo.ieum.user.domain.User;
import io.groo.core.type.YNType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Import({DepartmentQueryDslRepository.class, TestQuerydslConfig.class})
@Transactional
@DisplayName("QueryDSL 부서 레포지토리 테스트")
class DepartmentQueryDslRepositoryTest {
    @Autowired
    private DepartmentQueryDslRepository departmentRepository;

    @Autowired
    private TestEntityManager em;

    private Company company;

    // 테스트용 User 생성 헬퍼 메소드
    private User createTestUser(String id, String name, String email) {
        return User.createUser(
                null, id, name, email,
                LocalDate.of(1990, 1, 1), "NONE", "9 ~ 18",
                LocalDate.now(), YNType.N, null, null, CountryCode.KR
        );
    }

    // 테스트용 User 생성 헬퍼 메소드 (생년월일 지정)
    private User createTestUser(String id, String name, String email, LocalDate birth) {
        return User.createUser(
                null, id, name, email,
                birth, "NONE", "9 ~ 18",
                LocalDate.now(), YNType.N, null, null, CountryCode.KR
        );
    }

    // 테스트용 User 생성 헬퍼 메소드 (회사 타입 지정)
    private User createTestUserWithCompany(String id, String name, String email, String companyType) {
        return User.createUser(
                null, id, name, email,
                LocalDate.of(1990, 1, 1), companyType, "9 ~ 18",
                LocalDate.now(), YNType.N, null, null, CountryCode.KR
        );
    }

    @BeforeEach
    void setUp() {
        company = Company.createCompany("company1", "테스트 회사", "설명");
        em.persist(company);
    }

    @Test
    @DisplayName("부서 저장 및 단건 조회")
    void save() {
        // given
        Department department = Department.createDepartment("개발팀", "개발팀", null, null, 1L, "개발 부서", "#FF0000", company);

        // when
        departmentRepository.save(department);
        em.flush();
        em.clear();

        // then
        Optional<Department> findDepartment = departmentRepository.findById(department.getRowId());
        assertThat(findDepartment.isPresent()).isTrue();
        assertThat(findDepartment.get().getName()).isEqualTo("개발팀");
        assertThat(findDepartment.get().getLevel()).isEqualTo(1L);
    }

    @Test
    @DisplayName("단건 조회 시 부서가 없어도 Null이 반환되면 안된다.")
    void findByIdEmpty() {
        // given & when
        Optional<Department> findDepartment = departmentRepository.findById(999L);

        // then
        assertThat(findDepartment.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("유저-부서 연결 저장")
    void saveUserDepartment() {
        // given
        User user = createTestUser("user1", "테스트유저1", "user1@test.com");
        em.persist(user);

        Department department = Department.createDepartment("개발팀", "개발팀", null, null, 1L, "개발 부서", "#FF0000", company);
        departmentRepository.save(department);

        // when
        departmentRepository.saveUserDepartment(UserDepartment.createUserDepartment(user, department, YNType.Y));
        em.flush();
        em.clear();

        // then
        List<UserDepartment> userDepartments = departmentRepository.findUserDepartmentsInDepartment(department.getRowId());
        assertThat(userDepartments).hasSize(1);
        assertThat(userDepartments.get(0).getUser().getId()).isEqualTo("user1");
    }

    @Test
    @DisplayName("특정 유저의 메인 부서 조회")
    void findMainDepartmentByUserId() {
        // given
        User user = createTestUser("user1", "테스트유저1", "user1@test.com");
        em.persist(user);

        Department department = Department.createDepartment("개발팀", "개발팀", null, null, 1L, "개발 부서", "#FF0000", company);
        departmentRepository.save(department);

        departmentRepository.saveUserDepartment(UserDepartment.createUserDepartment(user, department, YNType.Y));
        em.flush();
        em.clear();

        // when
        Optional<UserDepartment> mainDepartment = departmentRepository.findMainDepartmentByUserId("user1");

        // then
        assertThat(mainDepartment.isPresent()).isTrue();
        assertThat(mainDepartment.get().getDepartment().getName()).isEqualTo("개발팀");
    }

    @Test
    @DisplayName("특정 유저와 부서의 연결 조회")
    void findUserDepartment() {
        // given
        User user = createTestUser("user1", "테스트유저1", "user1@test.com");
        em.persist(user);

        Department department = Department.createDepartment("개발팀", "개발팀", null, null, 1L, "개발 부서", "#FF0000", company);
        departmentRepository.save(department);

        departmentRepository.saveUserDepartment(UserDepartment.createUserDepartment(user, department, YNType.Y));
        em.flush();
        em.clear();

        // when
        Optional<UserDepartment> found = departmentRepository.findUserDepartment("user1", department.getRowId());

        // then
        assertThat(found.isPresent()).isTrue();
    }

    @Test
    @DisplayName("특정 유저의 지우지 않은 소속 전부를 잠금 조회한다 (지운 소속 · 다른 유저 제외)")
    void findUserDepartmentsForUpdate() {
        // given
        User user = createTestUser("user1", "테스트유저1", "user1@test.com");
        User other = createTestUser("user2", "테스트유저2", "user2@test.com");
        em.persist(user);
        em.persist(other);
        Department d1 = Department.createDepartment("개발팀", "개발팀", null, null, 1L, "개발 부서", "#FF0000", company);
        Department d2 = Department.createDepartment("기획팀", "기획팀", null, null, 1L, "기획 부서", "#FF0000", company);
        Department d3 = Department.createDepartment("영업팀", "영업팀", null, null, 1L, "영업 부서", "#FF0000", company);
        departmentRepository.save(d1);
        departmentRepository.save(d2);
        departmentRepository.save(d3);
        departmentRepository.saveUserDepartment(UserDepartment.createUserDepartment(user, d1, YNType.Y));
        departmentRepository.saveUserDepartment(UserDepartment.createUserDepartment(user, d2, YNType.N));
        UserDepartment removed = UserDepartment.createUserDepartment(user, d3, YNType.N);
        departmentRepository.saveUserDepartment(removed);
        removed.deleteUserDepartment();
        departmentRepository.saveUserDepartment(UserDepartment.createUserDepartment(other, d1, YNType.Y));
        em.flush();
        em.clear();

        // when
        List<UserDepartment> found = departmentRepository.findUserDepartmentsForUpdate("user1");

        // then
        assertThat(found).hasSize(2);
        assertThat(found).extracting(ud -> ud.getDepartment().getName()).containsExactlyInAnyOrder("개발팀", "기획팀");
    }

    @Test
    @DisplayName("특정 부서에 속하지 않은 유저 조회")
    void findUsersNotInDepartment() {
        // given
        User user1 = createTestUser("user1", "테스트유저1", "user1@test.com");
        User user2 = createTestUser("user2", "테스트유저2", "user2@test.com", LocalDate.of(1991, 2, 2));
        User user3 = createTestUser("user3", "테스트유저3", "user3@test.com", LocalDate.of(1992, 3, 3));
        em.persist(user1);
        em.persist(user2);
        em.persist(user3);

        Department department = Department.createDepartment("개발팀", "개발팀", null, null, 1L, "개발 부서", "#FF0000", company);
        departmentRepository.save(department);

        departmentRepository.saveUserDepartment(UserDepartment.createUserDepartment(user1, department, YNType.Y));
        em.flush();
        em.clear();

        // when
        List<User> usersNotIn = departmentRepository.findUsersNotInDepartment(department.getRowId());

        // then
        assertThat(usersNotIn).hasSize(2);
    }

    @Test
    @DisplayName("특정 유저의 메인 부서 존재 여부 확인")
    void hasMainDepartment() {
        // given
        User user = createTestUser("user1", "테스트유저1", "user1@test.com");
        em.persist(user);

        Department department = Department.createDepartment("개발팀", "개발팀", null, null, 1L, "개발 부서", "#FF0000", company);
        departmentRepository.save(department);

        departmentRepository.saveUserDepartment(UserDepartment.createUserDepartment(user, department, YNType.Y));
        em.flush();
        em.clear();

        // when
        boolean hasMain = departmentRepository.hasMainDepartment("user1");
        boolean hasNoMain = departmentRepository.hasMainDepartment("user2");

        // then
        assertThat(hasMain).isTrue();
        assertThat(hasNoMain).isFalse();
    }

    @Test
    @DisplayName("삭제된 부서는 조회되지 않는다.")
    void deletedDepartmentNotFound() {
        // given
        Department department = Department.createDepartment("개발팀", "개발팀", null, null, 1L, "개발 부서", "#FF0000", company);
        departmentRepository.save(department);
        Long departmentId = department.getRowId();

        department.deleteDepartment();
        em.flush();
        em.clear();

        // when
        Optional<Department> findDepartment = departmentRepository.findById(departmentId);

        // then
        assertThat(findDepartment.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("특정 부서에 속한 UserDepartment 목록 조회")
    void findUserDepartmentsInDepartment() {
        // given
        User user1 = createTestUser("user1", "테스트유저1", "user1@test.com");
        User user2 = createTestUser("user2", "테스트유저2", "user2@test.com", LocalDate.of(1991, 2, 2));
        em.persist(user1);
        em.persist(user2);

        Department department = Department.createDepartment("개발팀", "개발팀", null, null, 1L, "개발 부서", "#FF0000", company);
        departmentRepository.save(department);

        departmentRepository.saveUserDepartment(UserDepartment.createUserDepartment(user1, department, YNType.Y));
        departmentRepository.saveUserDepartment(UserDepartment.createUserDepartment(user2, department, YNType.N));
        em.flush();
        em.clear();

        // when
        List<UserDepartment> userDepartments = departmentRepository.findUserDepartmentsInDepartment(department.getRowId());

        // then
        assertThat(userDepartments).hasSize(2);
    }

    @Test
    @DisplayName("headUserId로 부서 목록 조회")
    void findByUserIds() {
        // given
        User headUser1 = createTestUser("user1", "테스트유저1", "user1@test.com");
        User headUser2 = createTestUser("user2", "테스트유저2", "user2@test.com");
        User headUser3 = createTestUser("user3", "테스트유저3", "user3@test.com");
        em.persist(headUser1);
        em.persist(headUser2);
        em.persist(headUser3);

        Department dept1 = Department.createDepartment("개발팀", "개발팀", null, headUser1, 1L, "개발 부서", "#FF0000", company);
        Department dept2 = Department.createDepartment("디자인팀", "디자인팀", null, headUser2, 1L, "디자인 부서", "#00FF00", company);
        Department dept3 = Department.createDepartment("기획팀", "기획팀", null, headUser3, 1L, "기획 부서", "#0000FF", company);
        departmentRepository.save(dept1);
        departmentRepository.save(dept2);
        departmentRepository.save(dept3);
        em.flush();
        em.clear();

        // when
        List<Department> departments = departmentRepository.findByUserIds(List.of("user1", "user2"));

        // then
        assertThat(departments).hasSize(2);
    }

    @Test
    @DisplayName("사용자의 상위 결재자 부서 목록 조회")
    void findApproversByUserId() {
        // given
        User user = createTestUser("user1", "테스트유저1", "user1@test.com");
        User head1 = createTestUser("head1", "부서장1", "head1@test.com", LocalDate.of(1985, 1, 1));
        User head2 = createTestUser("head2", "부서장2", "head2@test.com", LocalDate.of(1985, 1, 1));
        User head3 = createTestUser("head3", "부서장3", "head3@test.com", LocalDate.of(1985, 1, 1));
        em.persist(user);
        em.persist(head1);
        em.persist(head2);
        em.persist(head3);

        // 부서 계층 생성: 본부 -> 팀 -> 파트
        Department headquarters = Department.createDepartment("본부", "본부", null, head1, 1L, "본부", "#FF0000", company);
        departmentRepository.save(headquarters);

        Department team = Department.createDepartment("팀", "팀", headquarters, head2, 2L, "팀", "#00FF00", company);
        departmentRepository.save(team);

        Department part = Department.createDepartment("파트", "파트", team, head3, 3L, "파트", "#0000FF", company);
        departmentRepository.save(part);

        // user1을 파트에 메인 부서로 등록
        departmentRepository.saveUserDepartment(UserDepartment.createUserDepartment(user, part, YNType.Y));
        em.flush();
        em.clear();

        // when
        List<Department> approvers = departmentRepository.findApproversByUserId("user1");

        // then — 자기 부서장(파트)부터 위로 (보안 점검 IEU-8: 자기 부서장도 결재선에 든다)
        assertThat(approvers).hasSize(3); // 파트, 팀, 본부
        assertThat(approvers.get(0).getName()).isEqualTo("파트");
        assertThat(approvers.get(1).getName()).isEqualTo("팀");
        assertThat(approvers.get(2).getName()).isEqualTo("본부");
    }

    @Test
    @DisplayName("결재선 - 본인이 부서장인 부서는 빼고, 같은 사람이 여러 부서장이면 한 번만 넣는다 (IEU-8)")
    void findApproversByUserIdExcludesSelfAndDuplicateHeads() {
        // given
        User partHead = createTestUser("head3", "파트장", "head3@test.com", LocalDate.of(1985, 1, 1));
        User upperHead = createTestUser("head2", "팀장겸본부장", "head2@test.com", LocalDate.of(1985, 1, 1));
        em.persist(partHead);
        em.persist(upperHead);

        Department headquarters = Department.createDepartment("본부", "본부", null, upperHead, 1L, "본부", "#FF0000", company);
        departmentRepository.save(headquarters);
        Department team = Department.createDepartment("팀", "팀", headquarters, upperHead, 2L, "팀", "#00FF00", company);
        departmentRepository.save(team);
        Department part = Department.createDepartment("파트", "파트", team, partHead, 3L, "파트", "#0000FF", company);
        departmentRepository.save(part);

        departmentRepository.saveUserDepartment(UserDepartment.createUserDepartment(partHead, part, YNType.Y));
        em.flush();
        em.clear();

        // when
        List<Department> approvers = departmentRepository.findApproversByUserId("head3");

        // then — 본인(파트장)은 빠지고, 팀장 겸 본부장은 한 번만
        assertThat(approvers).hasSize(1);
        assertThat(approvers.get(0).getHeadUser().getId()).isEqualTo("head2");
    }

    @Test
    @DisplayName("메인 부서가 없는 사용자의 결재자 조회 시 빈 목록 반환")
    void findApproversByUserIdNoMainDepartment() {
        // given & when
        List<Department> approvers = departmentRepository.findApproversByUserId("nonexistent");

        // then
        assertThat(approvers).isEmpty();
    }

    @Test
    @DisplayName("최상위 부서의 일반 구성원은 자기 부서장이 결재자다 (IEU-8)")
    void findApproversByUserIdTopLevel() {
        // given
        User user = createTestUser("user1", "테스트유저1", "user1@test.com");
        User head1 = createTestUser("head1", "부서장1", "head1@test.com", LocalDate.of(1985, 1, 1));
        em.persist(user);
        em.persist(head1);

        Department topDept = Department.createDepartment("최상위", "최상위", null, head1, 1L, "최상위", "#FF0000", company);
        departmentRepository.save(topDept);

        departmentRepository.saveUserDepartment(UserDepartment.createUserDepartment(user, topDept, YNType.Y));
        em.flush();
        em.clear();

        // when
        List<Department> approvers = departmentRepository.findApproversByUserId("user1");

        // then
        assertThat(approvers).hasSize(1);
        assertThat(approvers.get(0).getName()).isEqualTo("최상위");
    }

    @Test
    @DisplayName("최상위 부서장 본인은 결재자가 없다")
    void findApproversByUserIdTopLevelHead() {
        // given
        User head1 = createTestUser("head1", "부서장1", "head1@test.com", LocalDate.of(1985, 1, 1));
        em.persist(head1);
        Department topDept = Department.createDepartment("최상위", "최상위", null, head1, 1L, "최상위", "#FF0000", company);
        departmentRepository.save(topDept);
        departmentRepository.saveUserDepartment(UserDepartment.createUserDepartment(head1, topDept, YNType.Y));
        em.flush();
        em.clear();

        // when & then
        assertThat(departmentRepository.findApproversByUserId("head1")).isEmpty();
    }

    @Test
    @DisplayName("부서 미배치 유저 조회 시 SYSTEM 계정 제외")
    void findUsersNotInDepartmentExcludesSystemAccount() {
        // given
        User normalUser = createTestUser("normalUser", "일반유저", "normal@test.com");
        User systemUser = createTestUserWithCompany("systemUser", "시스템유저", "system@test.com", "SYSTEM");
        em.persist(normalUser);
        em.persist(systemUser);

        Department department = Department.createDepartment("개발팀", "개발팀", null, null, 1L, "개발 부서", "#FF0000", company);
        departmentRepository.save(department);
        em.flush();
        em.clear();

        // when
        List<User> users = departmentRepository.findUsersNotInDepartment(department.getRowId());

        // then
        assertThat(users).hasSize(1);
        assertThat(users.get(0).getId()).isEqualTo("normalUser");
        assertThat(users.get(0).getCompany()).isNotEqualTo("SYSTEM");
    }

    @Test
    @DisplayName("부서별 유저-부서 관계 조회 시 SYSTEM 계정 제외")
    void findUserDepartmentsInDepartmentExcludesSystemAccount() {
        // given
        User normalUser = createTestUser("normalUser", "일반유저", "normal@test.com");
        User systemUser = createTestUserWithCompany("systemUser", "시스템유저", "system@test.com", "SYSTEM");
        em.persist(normalUser);
        em.persist(systemUser);

        Department department = Department.createDepartment("개발팀", "개발팀", null, null, 1L, "개발 부서", "#FF0000", company);
        departmentRepository.save(department);

        departmentRepository.saveUserDepartment(UserDepartment.createUserDepartment(normalUser, department, YNType.Y));
        departmentRepository.saveUserDepartment(UserDepartment.createUserDepartment(systemUser, department, YNType.N));
        em.flush();
        em.clear();

        // when
        List<UserDepartment> userDepartments = departmentRepository.findUserDepartmentsInDepartment(department.getRowId());

        // then
        assertThat(userDepartments).hasSize(1);
        assertThat(userDepartments.get(0).getUser().getId()).isEqualTo("normalUser");
        assertThat(userDepartments.get(0).getUser().getCompany()).isNotEqualTo("SYSTEM");
    }
}
