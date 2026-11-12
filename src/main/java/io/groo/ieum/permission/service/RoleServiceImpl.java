package io.groo.ieum.permission.service;

import io.groo.core.exception.BusinessRuleViolationException;
import io.groo.core.exception.DuplicateException;
import io.groo.core.exception.EntityNotFoundException;
import io.groo.core.exception.ErrorCode;
import io.groo.core.exception.InvalidValueException;
import io.groo.ieum.common.exception.HrErrorCode;
import io.groo.ieum.permission.domain.Permission;
import io.groo.ieum.permission.domain.Role;
import io.groo.ieum.permission.domain.RolePermission;
import io.groo.ieum.permission.domain.UserRole;
import io.groo.ieum.permission.repository.PermissionRepository;
import io.groo.ieum.permission.repository.RoleRepository;
import io.groo.ieum.permission.type.ActionType;
import io.groo.ieum.permission.type.ResourceType;
import io.groo.ieum.security.service.TokenRevocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class RoleServiceImpl implements RoleService {
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final TokenRevocationService tokenRevocationService;

    /**
     * 시스템 필수 역할 · 권한 — 지우면 역할 · 권한을 고칠 수 있는 사람이 남지 않아 SQL 로만 복구된다
     * (#78 이후 삭제가 실제로 가진 사람에게서 회수되므로). ADMIN 역할은 ROLE:MANAGE 를 늘 가져야 한다(보안 점검 IEU-5)
     */
    static final String SYSTEM_ADMIN_ROLE = "ADMIN";
    static final String ROLE_MANAGE_PERMISSION = "ROLE:MANAGE";

    /** 역할 이름 최대 길이 — roles.role_name 칸(length 20) */
    static final int ROLE_NAME_MAX = 20;

    /* ==================== Role 관련 메서드 ==================== */

    @Override
    public List<Role> getAllRoles() {
        log.debug("전체 역할 목록 조회");
        return roleRepository.findAllRolesWithPermissions();
    }

    @Override
    public Role getRole(String roleCode) {
        log.debug("역할 조회: roleCode={}", roleCode);
        return roleRepository.findByCodeWithPermissions(roleCode)
                .orElseThrow(() -> {
                    log.warn("역할 조회 실패 - 존재하지 않는 역할: roleCode={}", roleCode);
                    return new EntityNotFoundException(HrErrorCode.ROLE_NOT_FOUND);
                });
    }

    @Override
    @Transactional
    public Role createRole(String roleCode, String roleName, String desc) {
        log.debug("역할 생성 시작: roleCode={}, roleName={}", roleCode, roleName);
        if (roleRepository.findByCode(roleCode).isPresent()) {
            log.warn("역할 생성 실패 - 중복 코드: roleCode={}", roleCode);
            throw new DuplicateException(HrErrorCode.ROLE_ALREADY_EXISTS);
        }
        Role role = Role.createRole(roleCode, roleName, desc);
        roleRepository.save(role);
        log.info("역할 생성 완료: roleCode={}", roleCode);
        return role;
    }

    @Override
    @Transactional
    public Role createRoleWithPermissions(String roleCode, String roleName, String desc, List<String> permissionCodes) {
        log.debug("역할 생성 (권한 포함) 시작: roleCode={}, roleName={}, permissionCount={}", roleCode, roleName, permissionCodes.size());
        if (roleRepository.findByCode(roleCode).isPresent()) {
            log.warn("역할 생성 실패 - 중복 코드: roleCode={}", roleCode);
            throw new DuplicateException(HrErrorCode.ROLE_ALREADY_EXISTS);
        }

        List<Permission> permissions = permissionCodes.stream()
                .map(code -> permissionRepository.findByCode(code)
                        .orElseThrow(() -> {
                            log.warn("역할 생성 실패 - 존재하지 않는 권한: permissionCode={}", code);
                            return new EntityNotFoundException(HrErrorCode.PERMISSION_NOT_FOUND);
                        }))
                .collect(Collectors.toList());

        Role role = Role.createRoleWithPermissions(roleCode, roleName, desc, permissions);
        roleRepository.save(role);
        log.info("역할 생성 (권한 포함) 완료: roleCode={}, permissionCount={}", roleCode, permissions.size());
        return role;
    }

    @Override
    @Transactional
    public void updateRole(String roleCode, String name, String desc) {
        log.debug("역할 수정 시작: roleCode={}", roleCode);
        Role role = roleRepository.findByCode(roleCode)
                .orElseThrow(() -> {
                    log.warn("역할 수정 실패 - 존재하지 않는 역할: roleCode={}", roleCode);
                    return new EntityNotFoundException(HrErrorCode.ROLE_NOT_FOUND);
                });
        applyNameAndDesc(role, name, desc, null);
        log.info("역할 수정 완료: roleCode={}", roleCode);
    }

    @Override
    @Transactional
    public void updateRoleWithPermissions(String roleCode, String name, String desc, List<String> permissionCodes) {
        log.debug("역할 수정 (권한 포함) 시작: roleCode={}", roleCode);
        Role role = getRole(roleCode);

        List<Permission> permissions = permissionCodes.stream()
                .map(code -> permissionRepository.findByCode(code)
                        .orElseThrow(() -> {
                            log.warn("역할 수정 실패 - 존재하지 않는 권한: permissionCode={}", code);
                            return new EntityNotFoundException(HrErrorCode.PERMISSION_NOT_FOUND);
                        }))
                .collect(Collectors.toList());
        requireAdminKeepsRoleManage(role, permissions);

        Set<String> before = permissionCodesOf(role);
        applyNameAndDesc(role, name, desc, permissions);
        revokeHoldersIfPermissionsShrank(role, before);
        log.info("역할 수정 (권한 포함) 완료: roleCode={}", roleCode);
    }

    /**
     * 이름 · 설명 · 권한을 반영한다. null 은 "바꾸지 않음"이다.<br>
     * 이름은 앞뒤 공백을 걷고, 비었거나 {@value #ROLE_NAME_MAX} 자를 넘으면 거절한다(DB 칸 role_name 20).<br>
     * 설명이 빈 글(공백뿐 포함)이면 설명을 지운다 — 예전에는 빈 설명을 받아도 아무 일 없이 성공했다.
     */
    private void applyNameAndDesc(Role role, String name, String desc, List<Permission> permissions) {
        String trimmedName = null;
        if (name != null) {
            trimmedName = name.trim();
            if (trimmedName.isEmpty() || trimmedName.length() > ROLE_NAME_MAX) {
                log.warn("역할 수정 실패 - 잘못된 이름: roleCode={}, length={}", role.getCode(), trimmedName.length());
                throw new InvalidValueException(ErrorCode.INVALID_PARAMETER);
            }
        }
        boolean clearDesc = desc != null && !StringUtils.hasText(desc);
        role.updateRole(trimmedName, clearDesc ? null : desc, permissions);
        if (clearDesc) {
            role.clearDesc();
        }
    }

    @Override
    @Transactional
    public void updateRolePermissions(String roleCode, List<String> permissionCodes) {
        log.debug("역할 권한 수정 시작: roleCode={}", roleCode);
        Role role = getRole(roleCode);

        List<Permission> permissions = permissionCodes.stream()
                .map(code -> permissionRepository.findByCode(code)
                        .orElseThrow(() -> {
                            log.warn("역할 권한 수정 실패 - 존재하지 않는 권한: permissionCode={}", code);
                            return new EntityNotFoundException(HrErrorCode.PERMISSION_NOT_FOUND);
                        }))
                .collect(Collectors.toList());
        requireAdminKeepsRoleManage(role, permissions);

        Set<String> before = permissionCodesOf(role);
        role.clearPermissions();
        permissions.forEach(role::addPermission);
        revokeHoldersIfPermissionsShrank(role, before);
        log.info("역할 권한 수정 완료: roleCode={}, permissionCount={}", roleCode, permissions.size());
    }

    @Override
    @Transactional
    public void addPermissionToRole(String roleCode, String permissionCode) {
        log.debug("역할에 권한 추가 시작: roleCode={}, permissionCode={}", roleCode, permissionCode);
        Role role = getRole(roleCode);
        Permission permission = permissionRepository.findByCode(permissionCode)
                .orElseThrow(() -> {
                    log.warn("권한 추가 실패 - 존재하지 않는 권한: permissionCode={}", permissionCode);
                    return new EntityNotFoundException(HrErrorCode.PERMISSION_NOT_FOUND);
                });
        role.addPermission(permission);
        log.info("역할에 권한 추가 완료: roleCode={}, permissionCode={}", roleCode, permissionCode);
    }

    @Override
    @Transactional
    public void removePermissionFromRole(String roleCode, String permissionCode) {
        log.debug("역할에서 권한 제거 시작: roleCode={}, permissionCode={}", roleCode, permissionCode);
        Role role = getRole(roleCode);
        Permission permission = permissionRepository.findByCode(permissionCode)
                .orElseThrow(() -> {
                    log.warn("권한 제거 실패 - 존재하지 않는 권한: permissionCode={}", permissionCode);
                    return new EntityNotFoundException(HrErrorCode.PERMISSION_NOT_FOUND);
                });
        if (isSystemAdminRole(role) && isRoleManagePermission(permission)) {
            log.warn("권한 제거 거부 - 관리자 역할의 역할 관리 권한");
            throw new BusinessRuleViolationException(HrErrorCode.SYSTEM_ROLE_PROTECTED);
        }
        Set<String> before = permissionCodesOf(role);
        role.removePermission(permission);
        revokeHoldersIfPermissionsShrank(role, before);
        log.info("역할에서 권한 제거 완료: roleCode={}, permissionCode={}", roleCode, permissionCode);
    }

    @Override
    @Transactional
    public void deleteRole(String roleCode) {
        log.debug("역할 삭제 시작: roleCode={}", roleCode);
        Role role = roleRepository.findByCode(roleCode)
                .orElseThrow(() -> {
                    log.warn("역할 삭제 실패 - 존재하지 않는 역할: roleCode={}", roleCode);
                    return new EntityNotFoundException(HrErrorCode.ROLE_NOT_FOUND);
                });
        if (isSystemAdminRole(role)) {
            log.warn("역할 삭제 거부 - 시스템 필수 역할: roleCode={}", role.getCode());
            throw new BusinessRuleViolationException(HrErrorCode.SYSTEM_ROLE_PROTECTED);
        }
        role.deleteRole();
        // 부여 기록도 함께 지운다 — 목록에서만 사라지고 가진 사람의 권한에는 남던 것을 막는다(보안 점검 IEU-5)
        List<UserRole> grants = roleRepository.findActiveUserRolesByRole(role);
        grants.forEach(UserRole::deleteUserRole);
        // 그 역할을 가졌던 사람의 이음 토큰을 끊는다 — 토큰에 실린 역할로 남은 수명 동안 읽지 못하게(보안 점검 IEU-2)
        grants.forEach(grant -> tokenRevocationService.revokeAllForUser(grant.getUser().getId()));
        log.info("역할 삭제 완료: roleCode={}, revokedUserCount={}", roleCode, grants.size());
    }

    /* ==================== Permission 관련 메서드 ==================== */

    @Override
    public List<Permission> getAllPermissions() {
        log.debug("전체 권한 목록 조회");
        return permissionRepository.findAllPermissions();
    }

    @Override
    public Permission getPermission(String permissionCode) {
        log.debug("권한 조회: permissionCode={}", permissionCode);
        return permissionRepository.findByCode(permissionCode)
                .orElseThrow(() -> {
                    log.warn("권한 조회 실패 - 존재하지 않는 권한: permissionCode={}", permissionCode);
                    return new EntityNotFoundException(HrErrorCode.PERMISSION_NOT_FOUND);
                });
    }

    @Override
    public List<Permission> getPermissionsByResource(String resource) {
        log.debug("리소스별 권한 목록 조회: resource={}", resource);
        return permissionRepository.findByResource(resource);
    }

    @Override
    @Transactional
    public Permission createPermission(String code, String name, String desc, String resource, String action) {
        log.debug("권한 생성 시작: code={}, name={}", code, name);
        if (permissionRepository.findByCode(code).isPresent()) {
            log.warn("권한 생성 실패 - 중복 코드: code={}", code);
            throw new DuplicateException(HrErrorCode.PERMISSION_ALREADY_EXISTS);
        }
        ResourceType resourceType = ResourceType.valueOf(resource);
        ActionType actionType = ActionType.valueOf(action);
        Permission permission = Permission.createPermission(code, name, desc, resourceType, actionType);
        permissionRepository.save(permission);
        log.info("권한 생성 완료: code={}", code);
        return permission;
    }

    @Override
    @Transactional
    public void updatePermission(String code, String name, String desc, String resource, String action) {
        log.debug("권한 수정 시작: code={}", code);
        Permission permission = getPermission(code);
        ResourceType resourceType = resource != null ? ResourceType.valueOf(resource) : null;
        ActionType actionType = action != null ? ActionType.valueOf(action) : null;
        permission.updatePermission(name, desc, resourceType, actionType);
        log.info("권한 수정 완료: code={}", code);
    }

    @Override
    @Transactional
    public void deletePermission(String permissionCode) {
        log.debug("권한 삭제 시작: permissionCode={}", permissionCode);
        Permission permission = permissionRepository.findByCode(permissionCode)
                .orElseThrow(() -> {
                    log.warn("권한 삭제 실패 - 존재하지 않는 권한: permissionCode={}", permissionCode);
                    return new EntityNotFoundException(HrErrorCode.PERMISSION_NOT_FOUND);
                });
        if (isRoleManagePermission(permission)) {
            log.warn("권한 삭제 거부 - 시스템 필수 권한: permissionCode={}", permission.getCode());
            throw new BusinessRuleViolationException(HrErrorCode.SYSTEM_ROLE_PROTECTED);
        }
        permission.deletePermission();
        // 역할 매핑도 함께 지운다 — 삭제한 권한이 역할을 통해 계속 실리지 않게(보안 점검 IEU-5)
        List<RolePermission> mappings = permissionRepository.findActiveRolePermissionsByPermission(permission);
        mappings.forEach(RolePermission::deleteRolePermission);
        // 그 권한을 가졌던 역할의 보유자 토큰을 끊는다(보안 점검 IEU-2)
        mappings.stream()
                .map(RolePermission::getRole)
                .distinct()
                .forEach(this::revokeHolders);
        log.info("권한 삭제 완료: permissionCode={}, revokedRoleCount={}", permissionCode, mappings.size());
    }

    private static Set<String> permissionCodesOf(Role role) {
        return role.getPermissions().stream().map(Permission::getCode).collect(Collectors.toSet());
    }

    /**
     * 역할에서 권한이 하나라도 빠졌으면 그 역할 보유자의 이음 토큰을 끊는다 — 늘어나기만 했으면 끊지 않는다(보안 점검 IEU-2)
     */
    private void revokeHoldersIfPermissionsShrank(Role role, Set<String> before) {
        if (!permissionCodesOf(role).containsAll(before)) {
            revokeHolders(role);
        }
    }

    private void revokeHolders(Role role) {
        roleRepository.findActiveUserRolesByRole(role)
                .forEach(grant -> tokenRevocationService.revokeAllForUser(grant.getUser().getId()));
    }

    /*
     * 필수 역할 · 권한 판별은 입력 문자열이 아니라 찾아온 엔티티의 코드로 한다.
     * 조회(findByCode)는 DB 콜레이션(utf8mb4_unicode_ci)을 따라 대소문자 · 끝 공백을 가리지 않아
     * "admin" · "ADMIN " · "role:manage" 로도 실제 ADMIN · ROLE:MANAGE 행이 찾아진다 — 입력값을 글자 그대로
     * 비교하면 이 표기로 보호를 건너뛴다(보안 점검 IEU-5 3차, groo-sso-back#85 와 같은 원리)
     */
    private static boolean isSystemAdminRole(Role role) {
        return SYSTEM_ADMIN_ROLE.equals(role.getCode());
    }

    private static boolean isRoleManagePermission(Permission permission) {
        return ROLE_MANAGE_PERMISSION.equals(permission.getCode());
    }

    /** 관리자 역할의 권한 목록을 통째로 바꿀 때 역할 관리 권한이 빠지면 거절한다 — 둘 다 찾아온 엔티티 코드로 본다 */
    private static void requireAdminKeepsRoleManage(Role role, List<Permission> permissions) {
        if (isSystemAdminRole(role) && permissions.stream().noneMatch(RoleServiceImpl::isRoleManagePermission)) {
            log.warn("역할 권한 수정 거부 - 관리자 역할에서 역할 관리 권한을 뺌");
            throw new BusinessRuleViolationException(HrErrorCode.SYSTEM_ROLE_PROTECTED);
        }
    }
}
