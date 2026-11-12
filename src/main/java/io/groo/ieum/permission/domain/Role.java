package io.groo.ieum.permission.domain;

import io.groo.ieum.common.domain.AuditingFieldsWithIp;
import io.groo.core.type.YNType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Role Entity<br>
 * 역할 정보를 관리하는 엔티티<br>
 * RBAC(Role-Based Access Control) 기반의 역할 관리<br>
 * 각 역할은 여러 개의 Permission을 가질 수 있음
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "roles")
public class Role extends AuditingFieldsWithIp {
    /**
     * 행 아이디<br>
     * 자동 생성되는 고유 식별자
     */
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "row_id")
    private Long rowId;

    /**
     * 역할 코드<br>
     * 예: ADMIN, MANAGER, USER
     */
    @Column(name = "role_code", unique = true, nullable = false, length = 20)
    private String code;

    /**
     * 역할 이름<br>
     * 예: 관리자, 매니저, 일반 사용자
     */
    @Column(name = "role_name", nullable = false, length = 20)
    private String name;

    /**
     * 역할 설명<br>
     * 역할에 대한 상세 설명
     */
    @Column(name = "role_desc", length = 1000)
    private String desc;

    /**
     * 역할-권한 매핑 목록<br>
     * 해당 역할이 가진 권한 매핑 리스트<br>
     * 중간 엔티티를 통해 생성/수정 이력 추적 가능
     */
    @BatchSize(size = 100)
    @OneToMany(mappedBy = "role", cascade = CascadeType.ALL)
    private List<RolePermission> rolePermissions = new ArrayList<>();

    /**
     * 삭제 여부<br>
     * Soft delete를 위한 플래그
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "is_deleted", nullable = false, length = 1)
    private YNType isDeleted;

    /**
     * 역할 생성 함수<br>
     * Entity의 경우 Setter 없이 Getter만 사용<br>
     * 해당 메소드를 통해 역할 생성할 것
     *
     * @param code 역할 코드
     * @param name 역할 이름 (한글명)
     * @param desc 역할 설명
     * @return Role
     */
    public static Role createRole(String code, String name, String desc) {
        Role role = new Role();
        role.code = code;
        role.name = name;
        role.desc = desc;
        role.rolePermissions = new ArrayList<>();
        role.isDeleted = YNType.N;
        return role;
    }

    /**
     * 역할 생성 함수 (권한 포함)<br>
     * 권한 리스트와 함께 역할을 생성
     *
     * @param code 역할 코드
     * @param name 역할 이름 (한글명)
     * @param desc 역할 설명
     * @param permissions 권한 리스트
     * @return Role
     */
    public static Role createRoleWithPermissions(String code, String name, String desc, List<Permission> permissions) {
        Role role = new Role();
        role.code = code;
        role.name = name;
        role.desc = desc;
        role.rolePermissions = new ArrayList<>();
        role.isDeleted = YNType.N;

        if (permissions != null) {
            for (Permission permission : permissions) {
                RolePermission rolePermission = RolePermission.createRolePermission(role, permission);
                role.rolePermissions.add(rolePermission);
            }
        }
        return role;
    }

    /**
     * 역할 수정 함수<br>
     * Entity의 경우 Setter 없이 Getter만 사용<br>
     * 해당 메소드를 통해 역할 수정할 것
     *
     * @param name 역할 이름 (한글명)
     * @param desc 역할 설명
     * @param permissions 권한 리스트
     */
    public void updateRole(String name, String desc, List<Permission> permissions) {
        if (!Objects.isNull(name)) { this.name = name; }
        if (!Objects.isNull(desc)) { this.desc = desc; }
        if (!Objects.isNull(permissions)) {
            clearPermissions();
            for (Permission permission : permissions) {
                addPermission(permission);
            }
        }
    }

    /**
     * 역할 설명 지우기<br>
     * updateRole 은 null 을 "바꾸지 않음"으로 보므로 설명을 비울 때는 이 메소드를 쓴다
     */
    public void clearDesc() {
        this.desc = null;
    }

    /**
     * 역할 삭제 함수<br>
     * Entity의 경우 Setter 없이 Getter만 사용<br>
     * 해당 메소드를 통해 역할 삭제할 것 (Soft Delete)
     */
    public void deleteRole() {
        this.isDeleted = YNType.Y;
    }

    /* 비즈니스 편의 메소드 */

    /**
     * 권한 목록 조회<br>
     * RolePermission에서 Permission만 추출하여 반환
     *
     * @return 권한 리스트
     */
    public List<Permission> getPermissions() {
        // 매핑이 살아 있어도 권한 자체가 삭제됐으면 뺀다(보안 점검 IEU-5)
        return this.rolePermissions.stream()
                .filter(rp -> YNType.isN(rp.getIsDeleted()))
                .map(RolePermission::getPermission)
                .filter(permission -> !YNType.isY(permission.getIsDeleted()))
                .toList();
    }

    /**
     * 권한 추가<br>
     * 역할에 새로운 권한을 추가
     *
     * @param permission 추가할 권한
     */
    public void addPermission(Permission permission) {
        boolean exists = this.rolePermissions.stream()
                .anyMatch(rp -> rp.getPermission().getCode().equals(permission.getCode())
                        && YNType.isN(rp.getIsDeleted()));

        if (!exists) {
            RolePermission rolePermission = RolePermission.createRolePermission(this, permission);
            this.rolePermissions.add(rolePermission);
        }
    }

    /**
     * 권한 제거<br>
     * 역할에서 특정 권한을 제거 (Soft Delete)
     *
     * @param permission 제거할 권한
     */
    public void removePermission(Permission permission) {
        this.rolePermissions.stream()
                .filter(rp -> rp.getPermission().getCode().equals(permission.getCode())
                        && YNType.isN(rp.getIsDeleted()))
                .forEach(RolePermission::deleteRolePermission);
    }

    /**
     * 모든 권한 제거<br>
     * 역할의 모든 권한을 제거 (Soft Delete)
     */
    public void clearPermissions() {
        this.rolePermissions.stream()
                .filter(rp -> YNType.isN(rp.getIsDeleted()))
                .forEach(RolePermission::deleteRolePermission);
    }

    /**
     * 특정 권한 보유 여부 확인<br>
     * 역할이 특정 권한을 가지고 있는지 확인
     *
     * @param permissionCode 확인할 권한 코드
     * @return 권한 보유 여부
     */
    public boolean hasPermission(String permissionCode) {
        return this.rolePermissions.stream()
                .filter(rp -> YNType.isN(rp.getIsDeleted()))
                .filter(rp -> !YNType.isY(rp.getPermission().getIsDeleted()))
                .anyMatch(rp -> rp.getPermission().getCode().equals(permissionCode));
    }
}
