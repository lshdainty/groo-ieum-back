package io.groo.ieum.permission.service;

import io.groo.ieum.permission.domain.Permission;
import io.groo.ieum.permission.domain.Role;

import java.util.List;

/**
 * Role & Permission Service<br>
 * 역할 및 권한 관리 서비스
 */
public interface RoleService {

    /* ==================== Role 관련 메서드 ==================== */

    /**
     * 전체 역할 목록 조회 (권한 포함)
     *
     * @return List<Role>
     */
    List<Role> getAllRoles();

    /**
     * 특정 역할 조회 (권한 포함)
     *
     * @param roleCode 역할 코드
     * @return Role
     */
    Role getRole(String roleCode);

    /**
     * 역할 생성
     *
     * @param roleCode 역할 코드
     * @param roleName 역할 이름
     * @param desc 역할 설명
     * @return Role
     */
    Role createRole(String roleCode, String roleName, String desc);

    /**
     * 역할 생성 (권한 포함)
     *
     * @param roleCode 역할 코드
     * @param roleName 역할 이름
     * @param desc 역할 설명
     * @param permissionCodes 권한 코드 리스트
     * @return Role
     */
    Role createRoleWithPermissions(String roleCode, String roleName, String desc, List<String> permissionCodes);

    /**
     * 역할 정보 수정 (이름 · 설명). null 인 값은 바꾸지 않는다.
     *
     * @param roleCode 역할 코드
     * @param name 역할 이름 (null = 그대로, 빈 글 · 20자 초과 = InvalidValueException)
     * @param desc 역할 설명 (null = 그대로, 빈 글 = 설명 지움)
     */
    void updateRole(String roleCode, String name, String desc);

    /**
     * 역할 정보 수정 (이름 · 설명 + 권한). 이름 · 설명 규칙은 {@link #updateRole(String, String, String)} 와 같다.
     *
     * @param roleCode 역할 코드
     * @param name 역할 이름
     * @param desc 역할 설명
     * @param permissionCodes 권한 코드 리스트
     */
    void updateRoleWithPermissions(String roleCode, String name, String desc, List<String> permissionCodes);

    /**
     * 역할의 권한만 수정
     *
     * @param roleCode 역할 코드
     * @param permissionCodes 권한 코드 리스트
     */
    void updateRolePermissions(String roleCode, List<String> permissionCodes);

    /**
     * 역할에 권한 추가
     *
     * @param roleCode 역할 코드
     * @param permissionCode 권한 코드
     */
    void addPermissionToRole(String roleCode, String permissionCode);

    /**
     * 역할에서 권한 제거
     *
     * @param roleCode 역할 코드
     * @param permissionCode 권한 코드
     */
    void removePermissionFromRole(String roleCode, String permissionCode);

    /**
     * 역할 삭제 (Soft Delete)
     *
     * @param roleCode 역할 코드
     */
    void deleteRole(String roleCode);

    /* ==================== Permission 관련 메서드 ==================== */

    /**
     * 전체 권한 목록 조회
     *
     * @return List<Permission>
     */
    List<Permission> getAllPermissions();

    /**
     * 특정 권한 조회
     *
     * @param permissionCode 권한 코드
     * @return Permission
     */
    Permission getPermission(String permissionCode);

    /**
     * 리소스별 권한 목록 조회
     *
     * @param resource 리소스명
     * @return List<Permission>
     */
    List<Permission> getPermissionsByResource(String resource);

    /**
     * 권한 생성
     *
     * @param code 권한 코드
     * @param name 권한 이름 (한글명)
     * @param desc 권한 설명
     * @param resource 리소스
     * @param action 액션
     * @return Permission
     */
    Permission createPermission(String code, String name, String desc, String resource, String action);

    /**
     * 권한 수정
     *
     * @param code 권한 코드
     * @param name 권한 이름 (한글명)
     * @param desc 권한 설명
     * @param resource 리소스
     * @param action 액션
     */
    void updatePermission(String code, String name, String desc, String resource, String action);

    /**
     * 권한 삭제 (Soft Delete)
     *
     * @param permissionCode 권한 코드
     */
    void deletePermission(String permissionCode);
}
