package io.groo.ieum.department.repository;

import io.groo.core.type.YNType;
import io.groo.ieum.department.domain.Department;
import io.groo.ieum.department.domain.UserDepartment;
import io.groo.ieum.user.domain.User;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Optional;

@Repository("departmentJpaRepository")
@RequiredArgsConstructor
public class DepartmentJpaRepository implements DepartmentRepository {
    private final EntityManager em;

    @Override
    public void save(Department department) {
        em.persist(department);
    }

    @Override
    public Optional<Department> findById(Long id) {
        List<Department> result = em.createQuery(
                "select d from Department d where d.id = :id and d.isDeleted = :isDeleted", Department.class)
                .setParameter("id", id)
                .setParameter("isDeleted", YNType.N)
                .getResultList();
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    @Override
    public void saveUserDepartment(UserDepartment userDepartment) {
        em.persist(userDepartment);
    }

    @Override
    public Optional<UserDepartment> findMainDepartmentByUserId(String userId) {
        List<UserDepartment> result = em.createQuery(
                "select ud from UserDepartment ud where ud.user.id = :userId and ud.mainYN = :mainYN and ud.isDeleted = :isDeleted", UserDepartment.class)
                .setParameter("userId", userId)
                .setParameter("mainYN", YNType.Y)
                .setParameter("isDeleted", YNType.N)
                .getResultList();
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    @Override
    public Optional<UserDepartment> findUserDepartment(String userId, Long departmentId) {
        List<UserDepartment> result = em.createQuery(
                "select ud from UserDepartment ud where ud.user.id = :userId and ud.department.id = :departmentId and ud.isDeleted = :isDeleted", UserDepartment.class)
                .setParameter("userId", userId)
                .setParameter("departmentId", departmentId)
                .setParameter("isDeleted", YNType.N)
                .getResultList();
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    @Override
    public List<UserDepartment> findUserDepartmentsForUpdate(String userId) {
        return em.createQuery(
                "select ud from UserDepartment ud where ud.user.id = :userId and ud.isDeleted = :isDeleted", UserDepartment.class)
                .setParameter("userId", userId)
                .setParameter("isDeleted", YNType.N)
                .setLockMode(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
                .getResultList();
    }

    @Override
    public List<User> findUsersNotInDepartment(Long departmentId) {
        return em.createQuery(
                "select u from User u where u.isDeleted = :isDeleted and u.company != :systemCompany and not exists (" +
                "select 1 from UserDepartment ud join ud.department d " +
                "where ud.user.id = u.id and ud.department.id = :departmentId and ud.isDeleted = :isDeleted and d.isDeleted = :isDeleted)", User.class)
                .setParameter("departmentId", departmentId)
                .setParameter("isDeleted", YNType.N)
                .setParameter("systemCompany", "SYSTEM")
                .getResultList();
    }

    @Override
    public List<UserDepartment> findUserDepartmentsInDepartment(Long departmentId) {
        return em.createQuery(
                "select ud from UserDepartment ud " +
                "join fetch ud.user u join ud.department d " +
                "where ud.department.id = :departmentId and ud.isDeleted = :isDeleted and d.isDeleted = :isDeleted and u.isDeleted = :isDeleted and u.company != :systemCompany", UserDepartment.class)
                .setParameter("departmentId", departmentId)
                .setParameter("isDeleted", YNType.N)
                .setParameter("systemCompany", "SYSTEM")
                .getResultList();
    }

    @Override
    public boolean hasMainDepartment(String userId) {
        Long count = em.createQuery(
                "select count(ud) from UserDepartment ud where ud.user.id = :userId and ud.mainYN = :mainYN and ud.isDeleted = :isDeleted", Long.class)
                .setParameter("userId", userId)
                .setParameter("mainYN", YNType.Y)
                .setParameter("isDeleted", YNType.N)
                .getSingleResult();
        return count != null && count > 0;
    }

    @Override
    public List<Department> findByUserIds(List<String> userIds) {
        return em.createQuery(
                "select d from Department d left join fetch d.headUser where d.headUser.id in :userIds and d.isDeleted = :isDeleted", Department.class)
                .setParameter("userIds", userIds)
                .setParameter("isDeleted", YNType.N)
                .getResultList();
    }

    @Override
    public List<Department> findApproversByUserId(String userId) {
        List<UserDepartment> userDeptResult = em.createQuery(
                "select ud from UserDepartment ud " +
                "join fetch ud.department d " +
                "where ud.user.id = :userId and ud.mainYN = :mainYN and ud.isDeleted = :isDeleted", UserDepartment.class)
                .setParameter("userId", userId)
                .setParameter("mainYN", YNType.Y)
                .setParameter("isDeleted", YNType.N)
                .getResultList();

        if (userDeptResult.isEmpty()) {
            return List.of();
        }

        List<Department> approverDepartments = new ArrayList<>();
        Set<String> headIds = new HashSet<>();
        Department currentDept = userDeptResult.get(0).getDepartment();
        addApproverDepartment(approverDepartments, headIds, currentDept, userId);

        while (currentDept != null && currentDept.getParentId() != null) {
            List<Department> parentResult = em.createQuery(
                    "select d from Department d where d.id = :id and d.isDeleted = :isDeleted", Department.class)
                    .setParameter("id", currentDept.getParentId())
                    .setParameter("isDeleted", YNType.N)
                    .getResultList();

            if (parentResult.isEmpty()) {
                break;
            }

            Department parentDept = parentResult.get(0);
            addApproverDepartment(approverDepartments, headIds, parentDept, userId);

            currentDept = parentDept;
        }

        return approverDepartments;
    }

    private static void addApproverDepartment(List<Department> approverDepartments, Set<String> headIds,
                                              Department dept, String userId) {
        if (dept == null || dept.getHeadUser() == null) {
            return;
        }
        String headId = dept.getHeadUser().getId();
        if (headId.equals(userId) || !headIds.add(headId)) {
            return;
        }
        approverDepartments.add(dept);
    }
}
