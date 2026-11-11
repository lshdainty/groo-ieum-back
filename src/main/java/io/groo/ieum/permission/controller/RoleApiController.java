package io.groo.ieum.permission.controller;

import io.groo.core.controller.ApiResponse;
import io.groo.ieum.permission.controller.dto.RoleApiDto;
import io.groo.ieum.permission.domain.Permission;
import io.groo.ieum.permission.domain.Role;
import io.groo.ieum.permission.service.RoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Role & Permission API Controller<br>
 * 역할 및 권한 관리 통합 RESTful API
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class RoleApiController implements RoleApi {
    private final RoleService roleService;

    /* ==================== Role API ==================== */

    /**
     * 전체 역할 목록 조회
     * GET /api/v1/roles
     *
     * @return List<RoleResp>
     */
    @Override
    @PreAuthorize("hasAuthority('ROLE:MANAGE')")
    public ApiResponse<List<RoleApiDto.RoleResp>> getAllRoles() {
        List<Role> roles = roleService.getAllRoles();
        List<RoleApiDto.RoleResp> resp = roles.stream()
                .map(role -> new RoleApiDto.RoleResp(
                        role.getCode(),
                        role.getName(),
                        role.getDesc(),
                        role.getPermissions().stream()
                                .map(Permission::getCode)
                                .collect(Collectors.toList())
                ))
                .collect(Collectors.toList());
        return ApiResponse.success(resp);
    }

    /**
     * 특정 역할 조회
     * GET /api/v1/roles/{roleCode}
     *
     * @param roleCode 역할 코드
     * @return RoleResp
     */
    @Override
    @PreAuthorize("hasAuthority('ROLE:MANAGE')")
    public ApiResponse<RoleApiDto.RoleResp> getRole(String roleCode) {
        Role role = roleService.getRole(roleCode);
        return ApiResponse.success(new RoleApiDto.RoleResp(
                role.getCode(),
                role.getName(),
                role.getDesc(),
                role.getPermissions().stream()
                        .map(Permission::getCode)
                        .collect(Collectors.toList())
        ));
    }

    /**
     * 역할 생성
     * POST /api/v1/roles
     *
     * @param req CreateRoleReq
     * @return 생성된 역할 코드
     */
    @Override
    @PreAuthorize("hasAuthority('ROLE:MANAGE')")
    public ApiResponse<String> createRole(RoleApiDto.CreateRoleReq req) {
        Role role;
        if (req.getPermissionCodes() != null && !req.getPermissionCodes().isEmpty()) {
            role = roleService.createRoleWithPermissions(
                    req.getRoleCode(),
                    req.getRoleName(),
                    req.getDesc(),
                    req.getPermissionCodes()
            );
        } else {
            role = roleService.createRole(req.getRoleCode(), req.getRoleName(), req.getDesc());
        }
        return ApiResponse.success(role.getCode());
    }

    /**
     * 역할 수정 (이름 · 설명 및/또는 권한)
     * PUT /api/v1/roles/{roleCode}
     *
     * @param roleCode 역할 코드
     * @param req UpdateRoleReq
     * @return void
     */
    @Override
    @PreAuthorize("hasAuthority('ROLE:MANAGE')")
    public ApiResponse<Void> updateRole(String roleCode, RoleApiDto.UpdateRoleReq req) {
        // 칸이 없으면(null) 그대로 둔다. 설명을 빈 글로 보내면 설명을 지운다
        if (req.getPermissionCodes() != null && !req.getPermissionCodes().isEmpty()) {
            // 이름 · 설명 + 권한 같이 수정
            roleService.updateRoleWithPermissions(
                    roleCode,
                    req.getRoleName(),
                    req.getDesc(),
                    req.getPermissionCodes()
            );
        } else if (req.getRoleName() != null || req.getDesc() != null) {
            // 이름 · 설명만 수정
            roleService.updateRole(roleCode, req.getRoleName(), req.getDesc());
        }
        return ApiResponse.success();
    }

    /**
     * 역할 삭제 (Soft Delete)
     * DELETE /api/v1/roles/{roleCode}
     *
     * @param roleCode 역할 코드
     * @return void
     */
    @Override
    @PreAuthorize("hasAuthority('ROLE:MANAGE')")
    public ApiResponse<Void> deleteRole(String roleCode) {
        roleService.deleteRole(roleCode);
        return ApiResponse.success();
    }

    /* ==================== 역할 권한 관리 ==================== */

    /**
     * 역할 권한 목록 조회
     * GET /api/v1/roles/{roleCode}/permissions
     *
     * @param roleCode 역할 코드
     * @return List<String>
     */
    @Override
    @PreAuthorize("hasAuthority('ROLE:MANAGE')")
    public ApiResponse<List<String>> getRolePermissions(String roleCode) {
        Role role = roleService.getRole(roleCode);
        List<String> permissions = role.getPermissions().stream()
                .map(Permission::getCode)
                .collect(Collectors.toList());
        return ApiResponse.success(permissions);
    }

    /**
     * 역할 권한 설정 (전체 교체)
     * PUT /api/v1/roles/{roleCode}/permissions
     *
     * @param roleCode 역할 코드
     * @param req UpdateRolePermissionsReq
     * @return void
     */
    @Override
    @PreAuthorize("hasAuthority('ROLE:MANAGE')")
    public ApiResponse<Void> updateRolePermissions(String roleCode, RoleApiDto.UpdateRolePermissionsReq req) {
        roleService.updateRolePermissions(roleCode, req.getPermissionCodes());
        return ApiResponse.success();
    }

    /**
     * 역할에 권한 추가
     * POST /api/v1/roles/{roleCode}/permissions
     *
     * @param roleCode 역할 코드
     * @param req RolePermissionReq
     * @return void
     */
    @Override
    @PreAuthorize("hasAuthority('ROLE:MANAGE')")
    public ApiResponse<Void> addPermissionToRole(String roleCode, RoleApiDto.RolePermissionReq req) {
        roleService.addPermissionToRole(roleCode, req.getPermissionCode());
        return ApiResponse.success();
    }

    /**
     * 역할에서 권한 제거
     * DELETE /api/v1/roles/{roleCode}/permissions/{permissionCode}
     *
     * @param roleCode 역할 코드
     * @param permissionCode 권한 코드
     * @return void
     */
    @Override
    @PreAuthorize("hasAuthority('ROLE:MANAGE')")
    public ApiResponse<Void> removePermissionFromRole(String roleCode, String permissionCode) {
        roleService.removePermissionFromRole(roleCode, permissionCode);
        return ApiResponse.success();
    }

    /* ==================== Permission API ==================== */

    /**
     * 현재 사용자의 권한 목록 조회
     * GET /api/v1/permissions/my
     *
     * @return List<String>
     */
    @Override
    public ApiResponse<List<String>> getMyPermissions() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        List<String> permissions = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());
        return ApiResponse.success(permissions);
    }

    /**
     * 전체 권한 목록 조회
     * GET /api/v1/permissions
     *
     * @return List<PermissionResp>
     */
    @Override
    @PreAuthorize("hasAuthority('ROLE:MANAGE')")
    public ApiResponse<List<RoleApiDto.PermissionResp>> getAllPermissions() {
        List<Permission> permissions = roleService.getAllPermissions();
        List<RoleApiDto.PermissionResp> resp = permissions.stream()
                .map(p -> new RoleApiDto.PermissionResp(
                        p.getCode(),
                        p.getName(),
                        p.getDesc(),
                        p.getResource().name(),
                        p.getAction().name()
                ))
                .collect(Collectors.toList());
        return ApiResponse.success(resp);
    }

    /**
     * 특정 권한 조회
     * GET /api/v1/permissions/{permissionCode}
     *
     * @param permissionCode 권한 코드
     * @return PermissionResp
     */
    @Override
    @PreAuthorize("hasAuthority('ROLE:MANAGE')")
    public ApiResponse<RoleApiDto.PermissionResp> getPermission(String permissionCode) {
        Permission permission = roleService.getPermission(permissionCode);
        return ApiResponse.success(new RoleApiDto.PermissionResp(
                permission.getCode(),
                permission.getName(),
                permission.getDesc(),
                permission.getResource().name(),
                permission.getAction().name()
        ));
    }

    /**
     * 리소스별 권한 목록 조회
     * GET /api/v1/permissions/resource/{resource}
     *
     * @param resource 리소스명
     * @return List<PermissionResp>
     */
    @Override
    @PreAuthorize("hasAuthority('ROLE:MANAGE')")
    public ApiResponse<List<RoleApiDto.PermissionResp>> getPermissionsByResource(String resource) {
        List<Permission> permissions = roleService.getPermissionsByResource(resource);
        List<RoleApiDto.PermissionResp> resp = permissions.stream()
                .map(p -> new RoleApiDto.PermissionResp(
                        p.getCode(),
                        p.getName(),
                        p.getDesc(),
                        p.getResource().name(),
                        p.getAction().name()
                ))
                .collect(Collectors.toList());
        return ApiResponse.success(resp);
    }

    /**
     * 권한 생성
     * POST /api/v1/permissions
     *
     * @param req CreatePermissionReq
     * @return 생성된 권한 코드
     */
    @Override
    @PreAuthorize("hasAuthority('ROLE:MANAGE')")
    public ApiResponse<String> createPermission(RoleApiDto.CreatePermissionReq req) {
        Permission permission = roleService.createPermission(
                req.getCode(),
                req.getName(),
                req.getDesc(),
                req.getResource(),
                req.getAction()
        );
        return ApiResponse.success(permission.getCode());
    }

    /**
     * 권한 수정
     * PUT /api/v1/permissions/{permissionCode}
     *
     * @param permissionCode 권한 코드
     * @param req UpdatePermissionReq
     * @return void
     */
    @Override
    @PreAuthorize("hasAuthority('ROLE:MANAGE')")
    public ApiResponse<Void> updatePermission(String permissionCode, RoleApiDto.UpdatePermissionReq req) {
        roleService.updatePermission(
                permissionCode,
                req.getName(),
                req.getDesc(),
                req.getResource(),
                req.getAction()
        );
        return ApiResponse.success();
    }

    /**
     * 권한 삭제 (Soft Delete)
     * DELETE /api/v1/permissions/{permissionCode}
     *
     * @param permissionCode 권한 코드
     * @return void
     */
    @Override
    @PreAuthorize("hasAuthority('ROLE:MANAGE')")
    public ApiResponse<Void> deletePermission(String permissionCode) {
        roleService.deletePermission(permissionCode);
        return ApiResponse.success();
    }
}
