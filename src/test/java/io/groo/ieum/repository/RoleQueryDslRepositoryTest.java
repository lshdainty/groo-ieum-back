package io.groo.ieum.repository;

import java.time.LocalDate;
import io.groo.core.type.YNType;
import io.groo.core.type.CountryCode;
import io.groo.ieum.permission.domain.UserRole;
import io.groo.ieum.user.domain.User;
import io.groo.ieum.permission.domain.Permission;
import io.groo.ieum.permission.domain.Role;
import io.groo.ieum.permission.repository.RoleQueryDslRepository;
import io.groo.ieum.permission.type.ActionType;
import io.groo.ieum.permission.type.ResourceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({RoleQueryDslRepository.class, TestQuerydslConfig.class})
@Transactional
@DisplayName("QueryDSL 역할 레포지토리 테스트")
class RoleQueryDslRepositoryTest {
    @Autowired
    private RoleQueryDslRepository roleRepository;

    @Autowired
    private TestEntityManager em;

    @Test
    @DisplayName("역할 저장 및 코드로 조회")
    void save() {
        // given
        Role role = Role.createRole("ADMIN", "관리자", "시스템 관리자");

        // when
        roleRepository.save(role);
        em.flush();
        em.clear();

        // then
        Optional<Role> findRole = roleRepository.findByCode("ADMIN");
        assertThat(findRole.isPresent()).isTrue();
        assertThat(findRole.get().getCode()).isEqualTo("ADMIN");
        assertThat(findRole.get().getName()).isEqualTo("관리자");
    }

    @Test
    @DisplayName("코드로 역할 조회")
    void findByCode() {
        // given
        Role role = Role.createRole("MANAGER", "매니저", "부서 관리자");
        roleRepository.save(role);
        em.flush();
        em.clear();

        // when
        Optional<Role> findRole = roleRepository.findByCode("MANAGER");

        // then
        assertThat(findRole.isPresent()).isTrue();
        assertThat(findRole.get().getName()).isEqualTo("매니저");
    }

    @Test
    @DisplayName("코드로 조회 시 삭제된 역할 제외")
    void findByCodeExcludesDeleted() {
        // given
        Role role = Role.createRole("ADMIN", "관리자", "시스템 관리자");
        roleRepository.save(role);
        role.deleteRole();
        em.flush();
        em.clear();

        // when
        Optional<Role> findRole = roleRepository.findByCode("ADMIN");

        // then
        assertThat(findRole.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("코드로 조회 시 없으면 빈 Optional 반환")
    void findByCodeEmpty() {
        // when
        Optional<Role> findRole = roleRepository.findByCode("NONEXISTENT");

        // then
        assertThat(findRole.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("코드로 권한과 함께 역할 조회")
    void findByCodeWithPermissions() {
        // given
        Permission permission = Permission.createPermission(
                "VACATION:REQUEST", "휴가 신청", "설명",
                ResourceType.VACATION, ActionType.REQUEST
        );
        em.persist(permission);

        Role role = Role.createRole("USER", "일반 사용자", "기본 사용자");
        role.addPermission(permission);
        roleRepository.save(role);
        em.flush();
        em.clear();

        // when
        Optional<Role> findRole = roleRepository.findByCodeWithPermissions("USER");

        // then
        assertThat(findRole.isPresent()).isTrue();
        assertThat(findRole.get().getPermissions()).hasSize(1);
    }

    @Test
    @DisplayName("코드로 권한과 함께 조회 시 삭제된 역할 제외")
    void findByCodeWithPermissionsExcludesDeleted() {
        // given
        Role role = Role.createRole("ADMIN", "관리자", "시스템 관리자");
        roleRepository.save(role);
        role.deleteRole();
        em.flush();
        em.clear();

        // when
        Optional<Role> findRole = roleRepository.findByCodeWithPermissions("ADMIN");

        // then
        assertThat(findRole.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("전체 역할 권한과 함께 조회")
    void findAllRolesWithPermissions() {
        // given
        Permission permission1 = Permission.createPermission(
                "USER:READ", "사용자 조회", "설명",
                ResourceType.USER, ActionType.READ
        );
        Permission permission2 = Permission.createPermission(
                "VACATION:REQUEST", "휴가 신청", "설명",
                ResourceType.VACATION, ActionType.REQUEST
        );
        em.persist(permission1);
        em.persist(permission2);

        Role adminRole = Role.createRole("ADMIN", "관리자", "시스템 관리자");
        adminRole.addPermission(permission1);
        adminRole.addPermission(permission2);
        roleRepository.save(adminRole);

        Role userRole = Role.createRole("USER", "일반 사용자", "기본 사용자");
        userRole.addPermission(permission2);
        roleRepository.save(userRole);
        em.flush();
        em.clear();

        // when
        List<Role> roles = roleRepository.findAllRolesWithPermissions();

        // then
        assertThat(roles).hasSize(2);
    }

    @Test
    @DisplayName("역할 수정")
    void updateRole() {
        // given
        Role role = Role.createRole("ADMIN", "원래 이름", "원래 설명");
        roleRepository.save(role);
        em.flush();
        em.clear();

        // when
        Role foundRole = roleRepository.findByCode("ADMIN").orElseThrow();
        foundRole.updateRole("수정된 이름", "수정된 설명", null);
        em.flush();
        em.clear();

        // then
        Role updatedRole = roleRepository.findByCode("ADMIN").orElseThrow();
        assertThat(updatedRole.getName()).isEqualTo("수정된 이름");
        assertThat(updatedRole.getDesc()).isEqualTo("수정된 설명");
    }

    @Test
    @DisplayName("역할에 권한 추가")
    void addPermission() {
        // given
        Permission permission = Permission.createPermission(
                "USER:READ", "사용자 조회", "설명",
                ResourceType.USER, ActionType.READ
        );
        em.persist(permission);

        Role role = Role.createRole("ADMIN", "관리자", "시스템 관리자");
        roleRepository.save(role);
        em.flush();
        em.clear();

        // when
        Role foundRole = roleRepository.findByCode("ADMIN").orElseThrow();
        foundRole.addPermission(permission);
        em.flush();
        em.clear();

        // then
        Role updatedRole = roleRepository.findByCodeWithPermissions("ADMIN").orElseThrow();
        assertThat(updatedRole.getPermissions()).hasSize(1);
    }

    @Test
    @DisplayName("역할에서 권한 제거")
    void removePermission() {
        // given
        Permission permission = Permission.createPermission(
                "USER:READ", "사용자 조회", "설명",
                ResourceType.USER, ActionType.READ
        );
        em.persist(permission);

        Role role = Role.createRole("ADMIN", "관리자", "시스템 관리자");
        role.addPermission(permission);
        roleRepository.save(role);
        em.flush();
        em.clear();

        // when
        Role foundRole = roleRepository.findByCodeWithPermissions("ADMIN").orElseThrow();
        foundRole.removePermission(permission);
        em.flush();
        em.clear();

        // then - findByCode로 역할 조회하고 getPermissions()로 권한 확인 (soft delete 필터링)
        Role updatedRole = roleRepository.findByCode("ADMIN").orElseThrow();
        assertThat(updatedRole.getPermissions()).isEmpty();
    }

    @Test
    @DisplayName("역할의 살아 있는 사용자 부여 기록만 조회한다 (IEU-5)")
    void findActiveUserRolesByRole() {
        // given
        Role hr = Role.createRole("HR", "인사", null);
        Role other = Role.createRole("ETC", "기타", null);
        em.persist(hr);
        em.persist(other);
        User active = User.createUser(null, "u1", "직원1", "u1@test.com", LocalDate.of(1990, 1, 1), "NONE", "9 ~ 18",
                LocalDate.now(), YNType.N, null, null, CountryCode.KR);
        User revoked = User.createUser(null, "u2", "직원2", "u2@test.com", LocalDate.of(1990, 1, 1), "NONE", "9 ~ 18",
                LocalDate.now(), YNType.N, null, null, CountryCode.KR);
        active.addRole(hr);
        active.addRole(other);
        revoked.addRole(hr);
        revoked.removeRole(hr);
        em.persist(active);
        em.persist(revoked);
        em.flush();
        em.clear();

        // when
        Role found = roleRepository.findByCode("HR").orElseThrow();
        List<UserRole> grants = roleRepository.findActiveUserRolesByRole(found);

        // then
        assertThat(grants).hasSize(1);
        assertThat(grants.get(0).getUser().getId()).isEqualTo("u1");
    }
}
