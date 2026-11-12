package io.groo.ieum.permission.repository;

import io.groo.core.type.YNType;
import io.groo.ieum.permission.domain.Role;
import io.groo.ieum.permission.domain.UserRole;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository("roleJpaRepository")
@RequiredArgsConstructor
public class RoleJpaRepository implements RoleRepository {
    private final EntityManager em;

    @Override
    public void save(Role role) {
        em.persist(role);
    }

    @Override
    public Optional<Role> findByCode(String code) {
        List<Role> result = em.createQuery(
                "select r from Role r where r.code = :code and r.isDeleted = :isDeleted", Role.class)
                .setParameter("code", code)
                .setParameter("isDeleted", YNType.N)
                .getResultList();
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    @Override
    public Optional<Role> findByCodeWithPermissions(String code) {
        List<Role> result = em.createQuery(
                "select distinct r from Role r " +
                "left join fetch r.rolePermissions rp " +
                "left join fetch rp.permission p " +
                "where r.code = :code and r.isDeleted = :isDeleted " +
                "and (rp.isDeleted = :isDeleted or rp is null)", Role.class)
                .setParameter("code", code)
                .setParameter("isDeleted", YNType.N)
                .getResultList();
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    @Override
    public List<Role> findAllRolesWithPermissions() {
        return em.createQuery(
                "select distinct r from Role r " +
                "left join fetch r.rolePermissions rp " +
                "left join fetch rp.permission p " +
                "where r.isDeleted = :isDeleted " +
                "and (rp.isDeleted = :isDeleted or rp is null) " +
                "order by r.code asc", Role.class)
                .setParameter("isDeleted", YNType.N)
                .getResultList();
    }

    @Override
    public List<UserRole> findActiveUserRolesByRole(Role role) {
        return em.createQuery(
                "select ur from UserRole ur where ur.role = :role and ur.isDeleted = :isDeleted", UserRole.class)
                .setParameter("role", role)
                .setParameter("isDeleted", YNType.N)
                .getResultList();
    }
}
