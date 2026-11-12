package io.groo.ieum.repository;

import io.groo.ieum.permission.domain.RolePermission;
import io.groo.ieum.permission.domain.Role;
import io.groo.ieum.permission.domain.Permission;
import io.groo.ieum.permission.repository.PermissionJpaRepository;
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
@Import({PermissionJpaRepository.class, TestQuerydslConfig.class})
@Transactional
@DisplayName("JPA 권한 레포지토리 테스트")
class PermissionJpaRepositoryTest {
    @Autowired
    private PermissionJpaRepository permissionRepository;

    @Autowired
    private TestEntityManager em;

    @Test
    @DisplayName("권한 저장 및 코드로 조회")
    void save() {
        // given
        Permission permission = Permission.createPermission(
                "USER:READ", "사용자 조회", "사용자 정보 조회 권한",
                ResourceType.USER, ActionType.READ
        );

        // when
        permissionRepository.save(permission);
        em.flush();
        em.clear();

        // then
        Optional<Permission> findPermission = permissionRepository.findByCode("USER:READ");
        assertThat(findPermission.isPresent()).isTrue();
        assertThat(findPermission.get().getCode()).isEqualTo("USER:READ");
        assertThat(findPermission.get().getName()).isEqualTo("사용자 조회");
    }

    @Test
    @DisplayName("코드로 권한 조회")
    void findByCode() {
        // given
        Permission permission = Permission.createPermission(
                "VACATION:REQUEST", "휴가 신청", "휴가 신청 권한",
                ResourceType.VACATION, ActionType.REQUEST
        );
        permissionRepository.save(permission);
        em.flush();
        em.clear();

        // when
        Optional<Permission> findPermission = permissionRepository.findByCode("VACATION:REQUEST");

        // then
        assertThat(findPermission.isPresent()).isTrue();
        assertThat(findPermission.get().getResource()).isEqualTo(ResourceType.VACATION);
        assertThat(findPermission.get().getAction()).isEqualTo(ActionType.REQUEST);
    }

    @Test
    @DisplayName("코드로 조회 시 삭제된 권한 제외")
    void findByCodeExcludesDeleted() {
        // given
        Permission permission = Permission.createPermission(
                "USER:WRITE", "사용자 수정", "사용자 정보 수정 권한",
                ResourceType.USER, ActionType.WRITE
        );
        permissionRepository.save(permission);
        permission.deletePermission();
        em.flush();
        em.clear();

        // when
        Optional<Permission> findPermission = permissionRepository.findByCode("USER:WRITE");

        // then
        assertThat(findPermission.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("코드로 조회 시 없으면 빈 Optional 반환")
    void findByCodeEmpty() {
        // when
        Optional<Permission> findPermission = permissionRepository.findByCode("NONEXISTENT");

        // then
        assertThat(findPermission.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("전체 권한 조회")
    void findAllPermissions() {
        // given
        permissionRepository.save(Permission.createPermission(
                "USER:READ", "사용자 조회", "설명",
                ResourceType.USER, ActionType.READ
        ));
        permissionRepository.save(Permission.createPermission(
                "USER:WRITE", "사용자 수정", "설명",
                ResourceType.USER, ActionType.WRITE
        ));
        permissionRepository.save(Permission.createPermission(
                "VACATION:READ", "휴가 조회", "설명",
                ResourceType.VACATION, ActionType.READ
        ));
        em.flush();
        em.clear();

        // when
        List<Permission> permissions = permissionRepository.findAllPermissions();

        // then
        assertThat(permissions).hasSize(3);
    }

    @Test
    @DisplayName("전체 권한 조회 시 삭제된 권한 제외")
    void findAllPermissionsExcludesDeleted() {
        // given
        Permission activePermission = Permission.createPermission(
                "USER:READ", "사용자 조회", "설명",
                ResourceType.USER, ActionType.READ
        );
        Permission deletedPermission = Permission.createPermission(
                "USER:WRITE", "사용자 수정", "설명",
                ResourceType.USER, ActionType.WRITE
        );
        permissionRepository.save(activePermission);
        permissionRepository.save(deletedPermission);
        deletedPermission.deletePermission();
        em.flush();
        em.clear();

        // when
        List<Permission> permissions = permissionRepository.findAllPermissions();

        // then
        assertThat(permissions).hasSize(1);
        assertThat(permissions.get(0).getCode()).isEqualTo("USER:READ");
    }

    @Test
    @DisplayName("전체 권한 조회 시 정렬 확인 (리소스, 액션순)")
    void findAllPermissionsOrdered() {
        // given
        permissionRepository.save(Permission.createPermission(
                "VACATION:WRITE", "휴가 수정", "설명",
                ResourceType.VACATION, ActionType.WRITE
        ));
        permissionRepository.save(Permission.createPermission(
                "USER:READ", "사용자 조회", "설명",
                ResourceType.USER, ActionType.READ
        ));
        permissionRepository.save(Permission.createPermission(
                "USER:WRITE", "사용자 수정", "설명",
                ResourceType.USER, ActionType.WRITE
        ));
        em.flush();
        em.clear();

        // when
        List<Permission> permissions = permissionRepository.findAllPermissions();

        // then
        assertThat(permissions).hasSize(3);
        // USER가 VACATION보다 먼저, 같은 리소스는 액션순
        assertThat(permissions.get(0).getResource()).isEqualTo(ResourceType.USER);
    }

    @Test
    @DisplayName("전체 권한이 없으면 빈 리스트 반환")
    void findAllPermissionsEmpty() {
        // when
        List<Permission> permissions = permissionRepository.findAllPermissions();

        // then
        assertThat(permissions).isEmpty();
    }

    @Test
    @DisplayName("리소스로 권한 조회")
    void findByResource() {
        // given
        permissionRepository.save(Permission.createPermission(
                "USER:READ", "사용자 조회", "설명",
                ResourceType.USER, ActionType.READ
        ));
        permissionRepository.save(Permission.createPermission(
                "USER:WRITE", "사용자 수정", "설명",
                ResourceType.USER, ActionType.WRITE
        ));
        permissionRepository.save(Permission.createPermission(
                "VACATION:READ", "휴가 조회", "설명",
                ResourceType.VACATION, ActionType.READ
        ));
        em.flush();
        em.clear();

        // when
        List<Permission> permissions = permissionRepository.findByResource("USER");

        // then
        assertThat(permissions).hasSize(2);
        assertThat(permissions).extracting("resource").containsOnly(ResourceType.USER);
    }

    @Test
    @DisplayName("리소스로 조회 시 삭제된 권한 제외")
    void findByResourceExcludesDeleted() {
        // given
        Permission activePermission = Permission.createPermission(
                "USER:READ", "사용자 조회", "설명",
                ResourceType.USER, ActionType.READ
        );
        Permission deletedPermission = Permission.createPermission(
                "USER:WRITE", "사용자 수정", "설명",
                ResourceType.USER, ActionType.WRITE
        );
        permissionRepository.save(activePermission);
        permissionRepository.save(deletedPermission);
        deletedPermission.deletePermission();
        em.flush();
        em.clear();

        // when
        List<Permission> permissions = permissionRepository.findByResource("USER");

        // then
        assertThat(permissions).hasSize(1);
    }

    @Test
    @DisplayName("권한 수정")
    void updatePermission() {
        // given
        Permission permission = Permission.createPermission(
                "USER:READ", "원래 이름", "원래 설명",
                ResourceType.USER, ActionType.READ
        );
        permissionRepository.save(permission);
        em.flush();
        em.clear();

        // when
        Permission foundPermission = permissionRepository.findByCode("USER:READ").orElseThrow();
        foundPermission.updatePermission("수정된 이름", "수정된 설명", ResourceType.VACATION, ActionType.WRITE);
        em.flush();
        em.clear();

        // then
        Permission updatedPermission = permissionRepository.findByCode("USER:READ").orElseThrow();
        assertThat(updatedPermission.getName()).isEqualTo("수정된 이름");
        assertThat(updatedPermission.getDesc()).isEqualTo("수정된 설명");
        assertThat(updatedPermission.getResource()).isEqualTo(ResourceType.VACATION);
        assertThat(updatedPermission.getAction()).isEqualTo(ActionType.WRITE);
    }

    @Test
    @DisplayName("권한의 살아 있는 역할 매핑만 조회한다 (IEU-5)")
    void findActiveRolePermissionsByPermission() {
        // given
        Permission manage = Permission.createPermission("USER:MANAGE", "사용자 관리", null, ResourceType.USER, ActionType.MANAGE);
        Permission read = Permission.createPermission("USER:READ", "사용자 조회", null, ResourceType.USER, ActionType.READ);
        em.persist(manage);
        em.persist(read);
        Role hr = Role.createRoleWithPermissions("HR", "인사", null, List.of(manage, read));
        Role old = Role.createRoleWithPermissions("OLD", "옛역할", null, List.of(manage));
        old.removePermission(manage);
        em.persist(hr);
        em.persist(old);
        em.flush();
        em.clear();

        // when
        Permission found = permissionRepository.findByCode("USER:MANAGE").orElseThrow();
        List<RolePermission> mappings = permissionRepository.findActiveRolePermissionsByPermission(found);

        // then
        assertThat(mappings).hasSize(1);
        assertThat(mappings.get(0).getRole().getCode()).isEqualTo("HR");
    }
}
