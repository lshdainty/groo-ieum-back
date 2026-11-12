package io.groo.ieum.permission.repository;

import io.groo.ieum.permission.domain.Permission;
import io.groo.ieum.permission.domain.RolePermission;

import java.util.List;
import java.util.Optional;

/**
 * Permission Repository Interface<br>
 * QueryDSL을 활용한 권한 조회 인터페이스
 */
public interface PermissionRepository {
    /**
     * 신규 권한 저장
     *
     * @param permission 저장할 권한
     */
    void save(Permission permission);

    /**
     * 권한 코드로 권한 조회
     *
     * @param code 권한 코드
     * @return Optional<Permission>
     */
    Optional<Permission> findByCode(String code);

    /**
     * 전체 권한 목록 조회 (삭제되지 않은 것만)
     *
     * @return List<Permission>
     */
    List<Permission> findAllPermissions();

    /**
     * 리소스별 권한 목록 조회
     *
     * @param resource 리소스명
     * @return List<Permission>
     */
    List<Permission> findByResource(String resource);

    /**
     * 권한의 살아 있는 역할 매핑 조회<br>
     * 권한을 삭제할 때 그 권한을 가진 역할에서 함께 회수하기 위해 쓴다
     *
     * @param permission 대상 권한
     * @return 삭제되지 않은 RolePermission 목록
     */
    List<RolePermission> findActiveRolePermissionsByPermission(Permission permission);
}
