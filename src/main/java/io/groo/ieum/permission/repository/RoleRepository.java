package io.groo.ieum.permission.repository;

import io.groo.ieum.permission.domain.Role;
import io.groo.ieum.permission.domain.UserRole;

import java.util.List;
import java.util.Optional;

/**
 * Role Repository Interface<br>
 * QueryDSL을 활용한 역할 조회 인터페이스
 */
public interface RoleRepository {
    /**
     * 신규 역할 저장
     *
     * @param role 저장할 역할
     */
    void save(Role role);

    /**
     * 역할 코드로 역할 조회
     *
     * @param code 역할 코드
     * @return Optional<Role>
     */
    Optional<Role> findByCode(String code);

    /**
     * 역할 코드로 역할 조회 (권한 포함 - fetch join)
     *
     * @param code 역할 코드
     * @return Optional<Role>
     */
    Optional<Role> findByCodeWithPermissions(String code);

    /**
     * 전체 역할 목록 조회 (권한 포함 - fetch join)
     *
     * @return List<Role>
     */
    List<Role> findAllRolesWithPermissions();

    /**
     * 역할의 살아 있는 사용자 부여 기록 조회<br>
     * 역할을 삭제할 때 가진 사람에게서 함께 회수하기 위해 쓴다
     *
     * @param role 대상 역할
     * @return 삭제되지 않은 UserRole 목록
     */
    List<UserRole> findActiveUserRolesByRole(Role role);
}
