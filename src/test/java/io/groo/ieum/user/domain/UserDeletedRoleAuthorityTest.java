package io.groo.ieum.user.domain;

import io.groo.core.type.CountryCode;
import io.groo.core.type.YNType;
import io.groo.ieum.permission.domain.Permission;
import io.groo.ieum.permission.domain.Role;
import io.groo.ieum.permission.type.ActionType;
import io.groo.ieum.permission.type.ResourceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 보안 점검 IEU-5 — 삭제된 역할 · 권한은 부여 기록이 남아 있어도 권한 계산에서 빠져야 한다.
 * 토큰 발급 · 갱신 · 쓰기 요청 인가가 모두 {@link User#getAllAuthorities()} 와 {@link User#getRoles()} 를 쓴다.
 */
@DisplayName("삭제된 역할 · 권한의 권한 계산 제외")
class UserDeletedRoleAuthorityTest {

    private User userWith(Role... roles) {
        User user = User.createUser(1L, "user1", "이서준", "user1@test.com",
                LocalDate.of(1990, 1, 1), "NONE", "9 ~ 18", LocalDate.now(), YNType.N, null, null, CountryCode.KR);
        for (Role role : roles) {
            user.addRole(role);
        }
        return user;
    }

    @Test
    @DisplayName("삭제된 역할은 역할 목록 · 권한 목록 · 역할 보유 확인에서 빠진다")
    void deletedRoleExcluded() {
        Permission manage = Permission.createPermission("USER:MANAGE", "사용자 관리", null, ResourceType.USER, ActionType.MANAGE);
        Permission read = Permission.createPermission("USER:READ", "사용자 조회", null, ResourceType.USER, ActionType.READ);
        Role hr = Role.createRoleWithPermissions("HR", "인사", null, List.of(manage));
        Role basic = Role.createRoleWithPermissions("USER", "직원", null, List.of(read));
        User user = userWith(hr, basic);

        hr.deleteRole();

        assertThat(user.getRoles()).extracting(Role::getCode).containsExactly("USER");
        assertThat(user.getAllAuthorities()).containsExactlyInAnyOrder("USER", "USER:READ");
        assertThat(user.hasRole("HR")).isFalse();
    }

    @Test
    @DisplayName("삭제된 권한은 역할의 권한 목록과 사용자 권한 목록에서 빠진다")
    void deletedPermissionExcluded() {
        Permission manage = Permission.createPermission("USER:MANAGE", "사용자 관리", null, ResourceType.USER, ActionType.MANAGE);
        Permission read = Permission.createPermission("USER:READ", "사용자 조회", null, ResourceType.USER, ActionType.READ);
        Role hr = Role.createRoleWithPermissions("HR", "인사", null, List.of(manage, read));
        User user = userWith(hr);

        manage.deletePermission();

        assertThat(hr.getPermissions()).extracting(Permission::getCode).containsExactly("USER:READ");
        assertThat(user.getAllAuthorities()).containsExactlyInAnyOrder("HR", "USER:READ");
    }
}
