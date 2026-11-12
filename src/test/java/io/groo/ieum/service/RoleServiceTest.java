package io.groo.ieum.service;

import io.groo.core.exception.DuplicateException;
import io.groo.core.exception.EntityNotFoundException;
import io.groo.core.exception.InvalidValueException;
import io.groo.ieum.permission.domain.Permission;
import io.groo.ieum.permission.domain.Role;
import io.groo.ieum.permission.domain.RolePermission;
import io.groo.ieum.permission.domain.UserRole;
import io.groo.ieum.permission.repository.PermissionRepository;
import io.groo.ieum.permission.repository.RoleRepository;
import io.groo.ieum.permission.service.RoleServiceImpl;
import io.groo.ieum.permission.type.ActionType;
import io.groo.ieum.permission.type.ResourceType;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willDoNothing;

@Slf4j
@ExtendWith(MockitoExtension.class)
@DisplayName("역할/권한 서비스 테스트")
class RoleServiceTest {

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @InjectMocks
    private RoleServiceImpl roleService;

    @Nested
    @DisplayName("전체 역할 조회")
    class GetAllRoles {
        @Test
        @DisplayName("성공 - 전체 역할 목록을 반환한다")
        void getAllRolesSuccess() {
            // given
            Role role1 = Role.createRole("ADMIN", "관리자", "관리자 역할");
            Role role2 = Role.createRole("USER", "사용자", "일반 사용자 역할");
            given(roleRepository.findAllRolesWithPermissions()).willReturn(List.of(role1, role2));

            // when
            List<Role> result = roleService.getAllRoles();

            // then
            assertThat(result).hasSize(2);
            assertThat(result).extracting("code").containsExactly("ADMIN", "USER");
        }

        @Test
        @DisplayName("성공 - 역할이 없으면 빈 리스트를 반환한다")
        void getAllRolesEmpty() {
            // given
            given(roleRepository.findAllRolesWithPermissions()).willReturn(List.of());

            // when
            List<Role> result = roleService.getAllRoles();

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("역할 조회")
    class GetRole {
        @Test
        @DisplayName("성공 - 역할을 반환한다")
        void getRoleSuccess() {
            // given
            String roleCode = "ADMIN";
            Role role = Role.createRole(roleCode, "관리자", "관리자 역할");
            given(roleRepository.findByCodeWithPermissions(roleCode)).willReturn(Optional.of(role));

            // when
            Role result = roleService.getRole(roleCode);

            // then
            assertThat(result.getCode()).isEqualTo(roleCode);
            assertThat(result.getName()).isEqualTo("관리자");
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 역할이면 예외가 발생한다")
        void getRoleFailNotFound() {
            // given
            String roleCode = "NONEXISTENT";
            given(roleRepository.findByCodeWithPermissions(roleCode)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> roleService.getRole(roleCode))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("역할 생성")
    class CreateRole {
        @Test
        @DisplayName("성공 - 역할이 생성된다")
        void createRoleSuccess() {
            // given
            String roleCode = "MANAGER";
            String roleName = "매니저";
            String desc = "매니저 역할";
            given(roleRepository.findByCode(roleCode)).willReturn(Optional.empty());
            willDoNothing().given(roleRepository).save(any(Role.class));

            // when
            Role result = roleService.createRole(roleCode, roleName, desc);

            // then
            assertThat(result.getCode()).isEqualTo(roleCode);
            assertThat(result.getName()).isEqualTo(roleName);
            then(roleRepository).should().save(any(Role.class));
        }

        @Test
        @DisplayName("실패 - 중복된 역할 코드면 예외가 발생한다")
        void createRoleFailDuplicate() {
            // given
            String roleCode = "ADMIN";
            Role existingRole = Role.createRole(roleCode, "관리자", "관리자 역할");
            given(roleRepository.findByCode(roleCode)).willReturn(Optional.of(existingRole));

            // when & then
            assertThatThrownBy(() -> roleService.createRole(roleCode, "새로운 관리자", "설명"))
                    .isInstanceOf(DuplicateException.class);
        }
    }

    @Nested
    @DisplayName("역할 생성 (권한 포함)")
    class CreateRoleWithPermissions {
        @Test
        @DisplayName("성공 - 권한과 함께 역할이 생성된다")
        void createRoleWithPermissionsSuccess() {
            // given
            String roleCode = "MANAGER";
            String roleName = "매니저";
            String desc = "매니저 역할";
            List<String> permissionCodes = List.of("USER:READ", "USER:EDIT");

            Permission permission1 = Permission.createPermission("USER:READ", "사용자 조회", "desc", ResourceType.USER, ActionType.READ);
            Permission permission2 = Permission.createPermission("USER:EDIT", "사용자 수정", "desc", ResourceType.USER, ActionType.EDIT);

            given(roleRepository.findByCode(roleCode)).willReturn(Optional.empty());
            given(permissionRepository.findByCode("USER:READ")).willReturn(Optional.of(permission1));
            given(permissionRepository.findByCode("USER:EDIT")).willReturn(Optional.of(permission2));
            willDoNothing().given(roleRepository).save(any(Role.class));

            // when
            Role result = roleService.createRoleWithPermissions(roleCode, roleName, desc, permissionCodes);

            // then
            assertThat(result.getCode()).isEqualTo(roleCode);
            then(roleRepository).should().save(any(Role.class));
        }

        @Test
        @DisplayName("실패 - 중복된 역할 코드면 예외가 발생한다")
        void createRoleWithPermissionsFailDuplicate() {
            // given
            String roleCode = "ADMIN";
            Role existingRole = Role.createRole(roleCode, "관리자", "관리자 역할");
            given(roleRepository.findByCode(roleCode)).willReturn(Optional.of(existingRole));

            // when & then
            assertThatThrownBy(() -> roleService.createRoleWithPermissions(roleCode, "새로운 관리자", "설명", List.of()))
                    .isInstanceOf(DuplicateException.class);
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 권한이면 예외가 발생한다")
        void createRoleWithPermissionsFailPermissionNotFound() {
            // given
            String roleCode = "MANAGER";
            List<String> permissionCodes = List.of("NONEXISTENT");

            given(roleRepository.findByCode(roleCode)).willReturn(Optional.empty());
            given(permissionRepository.findByCode("NONEXISTENT")).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> roleService.createRoleWithPermissions(roleCode, "매니저", "설명", permissionCodes))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("역할 수정")
    class UpdateRole {
        @Test
        @DisplayName("성공 - 역할이 수정된다")
        void updateRoleSuccess() {
            // given
            String roleCode = "ADMIN";
            String newDesc = "수정된 설명";
            Role role = Role.createRole(roleCode, "관리자", "관리자 역할");
            given(roleRepository.findByCode(roleCode)).willReturn(Optional.of(role));

            // when
            roleService.updateRole(roleCode, null, newDesc);

            // then
            assertThat(role.getDesc()).isEqualTo(newDesc);
        }

        @Test
        @DisplayName("성공 - 이름을 보내면 앞뒤 공백을 걷어 이름이 바뀌고 설명은 그대로다")
        void updateRoleRename() {
            Role role = Role.createRole("ADMIN", "관리자", "관리자 역할");
            given(roleRepository.findByCode("ADMIN")).willReturn(Optional.of(role));

            roleService.updateRole("ADMIN", "  운영 관리자 ", null);

            assertThat(role.getName()).isEqualTo("운영 관리자");
            assertThat(role.getDesc()).isEqualTo("관리자 역할");
        }

        @Test
        @DisplayName("성공 - 설명을 빈 글로 보내면 설명이 지워진다(예전엔 아무 일 없이 성공)")
        void updateRoleClearDesc() {
            Role role = Role.createRole("ADMIN", "관리자", "관리자 역할");
            given(roleRepository.findByCode("ADMIN")).willReturn(Optional.of(role));

            roleService.updateRole("ADMIN", null, "   ");

            assertThat(role.getDesc()).isNull();
            assertThat(role.getName()).isEqualTo("관리자");
        }

        @Test
        @DisplayName("실패 - 이름이 비었으면 InvalidValueException, 값은 그대로다")
        void updateRoleBlankName() {
            Role role = Role.createRole("ADMIN", "관리자", "관리자 역할");
            given(roleRepository.findByCode("ADMIN")).willReturn(Optional.of(role));

            assertThatThrownBy(() -> roleService.updateRole("ADMIN", "  ", "새 설명"))
                    .isInstanceOf(InvalidValueException.class);
            assertThat(role.getName()).isEqualTo("관리자");
            assertThat(role.getDesc()).isEqualTo("관리자 역할");
        }

        @Test
        @DisplayName("실패 - 이름이 20자를 넘으면 InvalidValueException (20자는 된다)")
        void updateRoleNameTooLong() {
            Role role = Role.createRole("ADMIN", "관리자", "관리자 역할");
            given(roleRepository.findByCode("ADMIN")).willReturn(Optional.of(role));

            assertThatThrownBy(() -> roleService.updateRole("ADMIN", "가".repeat(21), null))
                    .isInstanceOf(InvalidValueException.class);

            roleService.updateRole("ADMIN", "가".repeat(20), null);
            assertThat(role.getName()).isEqualTo("가".repeat(20));
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 역할이면 예외가 발생한다")
        void updateRoleFailNotFound() {
            // given
            String roleCode = "NONEXISTENT";
            given(roleRepository.findByCode(roleCode)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> roleService.updateRole(roleCode, null, "설명"))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("역할 수정 (권한 포함)")
    class UpdateRoleWithPermissions {
        @Test
        @DisplayName("성공 - 권한과 함께 역할이 수정된다")
        void updateRoleWithPermissionsSuccess() {
            // given
            String roleCode = "ADMIN";
            String newDesc = "수정된 설명";
            List<String> permissionCodes = List.of("USER:READ");

            Role role = Role.createRole(roleCode, "관리자", "관리자 역할");
            Permission permission = Permission.createPermission("USER:READ", "사용자 조회", "desc", ResourceType.USER, ActionType.READ);

            given(roleRepository.findByCodeWithPermissions(roleCode)).willReturn(Optional.of(role));
            given(permissionRepository.findByCode("USER:READ")).willReturn(Optional.of(permission));

            // when
            roleService.updateRoleWithPermissions(roleCode, null, newDesc, permissionCodes);

            // then
            assertThat(role.getDesc()).isEqualTo(newDesc);
        }

        @Test
        @DisplayName("성공 - 권한과 함께 이름을 바꾸고 빈 설명은 지운다")
        void updateRoleWithPermissionsRenameAndClearDesc() {
            Role role = Role.createRole("ADMIN", "관리자", "관리자 역할");
            Permission permission = Permission.createPermission("USER:READ", "사용자 조회", "desc", ResourceType.USER, ActionType.READ);
            given(roleRepository.findByCodeWithPermissions("ADMIN")).willReturn(Optional.of(role));
            given(permissionRepository.findByCode("USER:READ")).willReturn(Optional.of(permission));

            roleService.updateRoleWithPermissions("ADMIN", "운영자", "", List.of("USER:READ"));

            assertThat(role.getName()).isEqualTo("운영자");
            assertThat(role.getDesc()).isNull();
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 권한이면 예외가 발생한다")
        void updateRoleWithPermissionsFailPermissionNotFound() {
            // given
            String roleCode = "ADMIN";
            Role role = Role.createRole(roleCode, "관리자", "관리자 역할");
            given(roleRepository.findByCodeWithPermissions(roleCode)).willReturn(Optional.of(role));
            given(permissionRepository.findByCode("NONEXISTENT")).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> roleService.updateRoleWithPermissions(roleCode, null, "설명", List.of("NONEXISTENT")))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("역할 권한 수정")
    class UpdateRolePermissions {
        @Test
        @DisplayName("성공 - 역할의 권한이 수정된다")
        void updateRolePermissionsSuccess() {
            // given
            String roleCode = "ADMIN";
            List<String> permissionCodes = List.of("USER:READ", "USER:EDIT");

            Role role = Role.createRole(roleCode, "관리자", "관리자 역할");
            Permission permission1 = Permission.createPermission("USER:READ", "사용자 조회", "desc", ResourceType.USER, ActionType.READ);
            Permission permission2 = Permission.createPermission("USER:EDIT", "사용자 수정", "desc", ResourceType.USER, ActionType.EDIT);

            given(roleRepository.findByCodeWithPermissions(roleCode)).willReturn(Optional.of(role));
            given(permissionRepository.findByCode("USER:READ")).willReturn(Optional.of(permission1));
            given(permissionRepository.findByCode("USER:EDIT")).willReturn(Optional.of(permission2));

            // when
            roleService.updateRolePermissions(roleCode, permissionCodes);

            // then
            then(roleRepository).should().findByCodeWithPermissions(roleCode);
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 권한이면 예외가 발생한다")
        void updateRolePermissionsFailPermissionNotFound() {
            // given
            String roleCode = "ADMIN";
            Role role = Role.createRole(roleCode, "관리자", "관리자 역할");
            given(roleRepository.findByCodeWithPermissions(roleCode)).willReturn(Optional.of(role));
            given(permissionRepository.findByCode("NONEXISTENT")).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> roleService.updateRolePermissions(roleCode, List.of("NONEXISTENT")))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("역할에 권한 추가")
    class AddPermissionToRole {
        @Test
        @DisplayName("성공 - 역할에 권한이 추가된다")
        void addPermissionToRoleSuccess() {
            // given
            String roleCode = "ADMIN";
            String permissionCode = "USER:READ";

            Role role = Role.createRole(roleCode, "관리자", "관리자 역할");
            Permission permission = Permission.createPermission(permissionCode, "사용자 조회", "desc", ResourceType.USER, ActionType.READ);

            given(roleRepository.findByCodeWithPermissions(roleCode)).willReturn(Optional.of(role));
            given(permissionRepository.findByCode(permissionCode)).willReturn(Optional.of(permission));

            // when
            roleService.addPermissionToRole(roleCode, permissionCode);

            // then
            assertThat(role.getPermissions()).contains(permission);
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 권한이면 예외가 발생한다")
        void addPermissionToRoleFailPermissionNotFound() {
            // given
            String roleCode = "ADMIN";
            Role role = Role.createRole(roleCode, "관리자", "관리자 역할");
            given(roleRepository.findByCodeWithPermissions(roleCode)).willReturn(Optional.of(role));
            given(permissionRepository.findByCode("NONEXISTENT")).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> roleService.addPermissionToRole(roleCode, "NONEXISTENT"))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("역할에서 권한 제거")
    class RemovePermissionFromRole {
        @Test
        @DisplayName("성공 - 역할에서 권한이 제거된다")
        void removePermissionFromRoleSuccess() {
            // given
            String roleCode = "ADMIN";
            String permissionCode = "USER:READ";

            Permission permission = Permission.createPermission(permissionCode, "사용자 조회", "desc", ResourceType.USER, ActionType.READ);
            Role role = Role.createRoleWithPermissions(roleCode, "관리자", "관리자 역할", List.of(permission));

            given(roleRepository.findByCodeWithPermissions(roleCode)).willReturn(Optional.of(role));
            given(permissionRepository.findByCode(permissionCode)).willReturn(Optional.of(permission));

            // when
            roleService.removePermissionFromRole(roleCode, permissionCode);

            // then
            then(roleRepository).should().findByCodeWithPermissions(roleCode);
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 권한이면 예외가 발생한다")
        void removePermissionFromRoleFailPermissionNotFound() {
            // given
            String roleCode = "ADMIN";
            Role role = Role.createRole(roleCode, "관리자", "관리자 역할");
            given(roleRepository.findByCodeWithPermissions(roleCode)).willReturn(Optional.of(role));
            given(permissionRepository.findByCode("NONEXISTENT")).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> roleService.removePermissionFromRole(roleCode, "NONEXISTENT"))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("역할 삭제")
    class DeleteRole {
        @Test
        @DisplayName("성공 - 역할이 삭제된다")
        void deleteRoleSuccess() {
            // given
            String roleCode = "ADMIN";
            Role role = Role.createRole(roleCode, "관리자", "관리자 역할");
            given(roleRepository.findByCode(roleCode)).willReturn(Optional.of(role));

            // when
            roleService.deleteRole(roleCode);

            // then
            assertThat(role.getIsDeleted()).isEqualTo(io.groo.core.type.YNType.Y);
        }

        @Test
        @DisplayName("성공 - 역할을 삭제하면 그 역할을 가진 사람의 부여 기록도 함께 지운다 (IEU-5)")
        void deleteRoleRevokesGrants() {
            Role role = Role.createRole("HR", "인사", null);
            given(roleRepository.findByCode("HR")).willReturn(Optional.of(role));
            UserRole grant1 = UserRole.createUserRole(null, role);
            UserRole grant2 = UserRole.createUserRole(null, role);
            given(roleRepository.findActiveUserRolesByRole(role)).willReturn(List.of(grant1, grant2));

            roleService.deleteRole("HR");

            assertThat(grant1.getIsDeleted()).isEqualTo(io.groo.core.type.YNType.Y);
            assertThat(grant2.getIsDeleted()).isEqualTo(io.groo.core.type.YNType.Y);
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 역할이면 예외가 발생한다")
        void deleteRoleFailNotFound() {
            // given
            String roleCode = "NONEXISTENT";
            given(roleRepository.findByCode(roleCode)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> roleService.deleteRole(roleCode))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("전체 권한 조회")
    class GetAllPermissions {
        @Test
        @DisplayName("성공 - 전체 권한 목록을 반환한다")
        void getAllPermissionsSuccess() {
            // given
            Permission permission1 = Permission.createPermission("USER:READ", "사용자 조회", "desc", ResourceType.USER, ActionType.READ);
            Permission permission2 = Permission.createPermission("USER:EDIT", "사용자 수정", "desc", ResourceType.USER, ActionType.EDIT);
            given(permissionRepository.findAllPermissions()).willReturn(List.of(permission1, permission2));

            // when
            List<Permission> result = roleService.getAllPermissions();

            // then
            assertThat(result).hasSize(2);
            assertThat(result).extracting("code").containsExactly("USER:READ", "USER:EDIT");
        }

        @Test
        @DisplayName("성공 - 권한이 없으면 빈 리스트를 반환한다")
        void getAllPermissionsEmpty() {
            // given
            given(permissionRepository.findAllPermissions()).willReturn(List.of());

            // when
            List<Permission> result = roleService.getAllPermissions();

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("권한 조회")
    class GetPermission {
        @Test
        @DisplayName("성공 - 권한을 반환한다")
        void getPermissionSuccess() {
            // given
            String permissionCode = "USER:READ";
            Permission permission = Permission.createPermission(permissionCode, "사용자 조회", "desc", ResourceType.USER, ActionType.READ);
            given(permissionRepository.findByCode(permissionCode)).willReturn(Optional.of(permission));

            // when
            Permission result = roleService.getPermission(permissionCode);

            // then
            assertThat(result.getCode()).isEqualTo(permissionCode);
            assertThat(result.getName()).isEqualTo("사용자 조회");
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 권한이면 예외가 발생한다")
        void getPermissionFailNotFound() {
            // given
            String permissionCode = "NONEXISTENT";
            given(permissionRepository.findByCode(permissionCode)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> roleService.getPermission(permissionCode))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("리소스별 권한 조회")
    class GetPermissionsByResource {
        @Test
        @DisplayName("성공 - 리소스별 권한 목록을 반환한다")
        void getPermissionsByResourceSuccess() {
            // given
            String resource = "USER";
            Permission permission1 = Permission.createPermission("USER:READ", "사용자 조회", "desc", ResourceType.USER, ActionType.READ);
            Permission permission2 = Permission.createPermission("USER:EDIT", "사용자 수정", "desc", ResourceType.USER, ActionType.EDIT);
            given(permissionRepository.findByResource(resource)).willReturn(List.of(permission1, permission2));

            // when
            List<Permission> result = roleService.getPermissionsByResource(resource);

            // then
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("성공 - 해당 리소스의 권한이 없으면 빈 리스트를 반환한다")
        void getPermissionsByResourceEmpty() {
            // given
            String resource = "VACATION";
            given(permissionRepository.findByResource(resource)).willReturn(List.of());

            // when
            List<Permission> result = roleService.getPermissionsByResource(resource);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("권한 생성")
    class CreatePermission {
        @Test
        @DisplayName("성공 - 권한이 생성된다")
        void createPermissionSuccess() {
            // given
            String code = "VACATION:REQUEST";
            String name = "휴가 신청";
            String desc = "휴가 신청 권한";
            String resource = "VACATION";
            String action = "REQUEST";

            given(permissionRepository.findByCode(code)).willReturn(Optional.empty());
            willDoNothing().given(permissionRepository).save(any(Permission.class));

            // when
            Permission result = roleService.createPermission(code, name, desc, resource, action);

            // then
            assertThat(result.getCode()).isEqualTo(code);
            assertThat(result.getName()).isEqualTo(name);
            assertThat(result.getResource()).isEqualTo(ResourceType.VACATION);
            assertThat(result.getAction()).isEqualTo(ActionType.REQUEST);
            then(permissionRepository).should().save(any(Permission.class));
        }

        @Test
        @DisplayName("실패 - 중복된 권한 코드면 예외가 발생한다")
        void createPermissionFailDuplicate() {
            // given
            String code = "USER:READ";
            Permission existingPermission = Permission.createPermission(code, "사용자 조회", "desc", ResourceType.USER, ActionType.READ);
            given(permissionRepository.findByCode(code)).willReturn(Optional.of(existingPermission));

            // when & then
            assertThatThrownBy(() -> roleService.createPermission(code, "새로운 권한", "설명", "USER", "READ"))
                    .isInstanceOf(DuplicateException.class);
        }
    }

    @Nested
    @DisplayName("권한 수정")
    class UpdatePermission {
        @Test
        @DisplayName("성공 - 권한이 수정된다")
        void updatePermissionSuccess() {
            // given
            String code = "USER:READ";
            Permission permission = Permission.createPermission(code, "사용자 조회", "desc", ResourceType.USER, ActionType.READ);
            given(permissionRepository.findByCode(code)).willReturn(Optional.of(permission));

            // when
            roleService.updatePermission(code, "수정된 이름", "수정된 설명", "VACATION", "EDIT");

            // then
            assertThat(permission.getName()).isEqualTo("수정된 이름");
            assertThat(permission.getDesc()).isEqualTo("수정된 설명");
            assertThat(permission.getResource()).isEqualTo(ResourceType.VACATION);
            assertThat(permission.getAction()).isEqualTo(ActionType.EDIT);
        }

        @Test
        @DisplayName("성공 - null 값은 변경하지 않는다")
        void updatePermissionWithNulls() {
            // given
            String code = "USER:READ";
            Permission permission = Permission.createPermission(code, "사용자 조회", "desc", ResourceType.USER, ActionType.READ);
            given(permissionRepository.findByCode(code)).willReturn(Optional.of(permission));

            // when
            roleService.updatePermission(code, null, null, null, null);

            // then
            assertThat(permission.getName()).isEqualTo("사용자 조회");
            assertThat(permission.getDesc()).isEqualTo("desc");
            assertThat(permission.getResource()).isEqualTo(ResourceType.USER);
            assertThat(permission.getAction()).isEqualTo(ActionType.READ);
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 권한이면 예외가 발생한다")
        void updatePermissionFailNotFound() {
            // given
            String code = "NONEXISTENT";
            given(permissionRepository.findByCode(code)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> roleService.updatePermission(code, "이름", "설명", "USER", "READ"))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("권한 삭제")
    class DeletePermission {
        @Test
        @DisplayName("성공 - 권한이 삭제된다")
        void deletePermissionSuccess() {
            // given
            String permissionCode = "USER:READ";
            Permission permission = Permission.createPermission(permissionCode, "사용자 조회", "desc", ResourceType.USER, ActionType.READ);
            given(permissionRepository.findByCode(permissionCode)).willReturn(Optional.of(permission));

            // when
            roleService.deletePermission(permissionCode);

            // then
            assertThat(permission.getIsDeleted()).isEqualTo(io.groo.core.type.YNType.Y);
        }

        @Test
        @DisplayName("성공 - 권한을 삭제하면 그 권한의 역할 매핑도 함께 지운다 (IEU-5)")
        void deletePermissionRevokesMappings() {
            Permission permission = Permission.createPermission("USER:MANAGE", "사용자 관리", null, ResourceType.USER, ActionType.MANAGE);
            given(permissionRepository.findByCode("USER:MANAGE")).willReturn(Optional.of(permission));
            Role role = Role.createRoleWithPermissions("HR", "인사", null, List.of(permission));
            RolePermission mapping = role.getRolePermissions().get(0);
            given(permissionRepository.findActiveRolePermissionsByPermission(permission)).willReturn(List.of(mapping));

            roleService.deletePermission("USER:MANAGE");

            assertThat(mapping.getIsDeleted()).isEqualTo(io.groo.core.type.YNType.Y);
            assertThat(role.hasPermission("USER:MANAGE")).isFalse();
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 권한이면 예외가 발생한다")
        void deletePermissionFailNotFound() {
            // given
            String permissionCode = "NONEXISTENT";
            given(permissionRepository.findByCode(permissionCode)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> roleService.deletePermission(permissionCode))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }
}
