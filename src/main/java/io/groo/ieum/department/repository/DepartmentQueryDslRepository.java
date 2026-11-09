package io.groo.ieum.department.repository;

import io.groo.core.type.YNType;
import io.groo.ieum.department.domain.Department;
import io.groo.ieum.department.domain.UserDepartment;
import io.groo.ieum.user.domain.User;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static io.groo.ieum.department.domain.QDepartment.department;
import static io.groo.ieum.department.domain.QUserDepartment.userDepartment;
import static io.groo.ieum.user.domain.QUser.user;

@Repository
@Primary
@RequiredArgsConstructor
public class DepartmentQueryDslRepository implements DepartmentRepository {
    private final EntityManager em;
    private final JPAQueryFactory query;

    @Override
    public void save(Department department) {
        em.persist(department);
    }

    @Override
    public Optional<Department> findById(Long id) {
        return Optional.ofNullable(query
                .selectFrom(department)
                .where(
                        department.rowId.eq(id),
                        department.isDeleted.eq(YNType.N)
                )
                .fetchOne()
        );
    }

    @Override
    public void saveUserDepartment(UserDepartment userDepartment) {
        em.persist(userDepartment);
    }

    @Override
    public Optional<UserDepartment> findMainDepartmentByUserId(String userId) {
        return Optional.ofNullable(query
                .selectFrom(userDepartment)
                .where(
                        userDepartment.user.id.eq(userId),
                        userDepartment.mainYN.eq(YNType.Y),
                        userDepartment.isDeleted.eq(YNType.N)
                )
                .fetchOne()
        );
    }

    @Override
    public Optional<UserDepartment> findUserDepartment(String userId, Long departmentId) {
        return Optional.ofNullable(query
                .selectFrom(userDepartment)
                .where(
                        userDepartment.user.id.eq(userId),
                        userDepartment.department.rowId.eq(departmentId),
                        userDepartment.isDeleted.eq(YNType.N)
                )
                .fetchOne()
        );
    }

    @Override
    public List<UserDepartment> findUserDepartmentsForUpdate(String userId) {
        return query
                .selectFrom(userDepartment)
                .where(
                        userDepartment.user.id.eq(userId),
                        userDepartment.isDeleted.eq(YNType.N)
                )
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .fetch();
    }

    @Override
    public List<User> findUsersNotInDepartment(Long departmentId) {
        return query
                .selectFrom(user)
                .where(
                        user.isDeleted.eq(YNType.N),
                        user.company.ne("SYSTEM"),
                        JPAExpressions
                                .selectFrom(userDepartment)
                                .join(userDepartment.department, department)
                                .where(
                                        userDepartment.user.id.eq(user.id),
                                        userDepartment.department.rowId.eq(departmentId),
                                        userDepartment.isDeleted.eq(YNType.N),
                                        department.isDeleted.eq(YNType.N)
                                )
                                .notExists()
                )
                .fetch();
    }

    @Override
    public List<UserDepartment> findUserDepartmentsInDepartment(Long departmentId) {
        return query
                .selectFrom(userDepartment)
                .join(userDepartment.user, user).fetchJoin()
                .join(userDepartment.department, department)
                .where(
                        userDepartment.department.rowId.eq(departmentId),
                        userDepartment.isDeleted.eq(YNType.N),
                        department.isDeleted.eq(YNType.N),
                        user.isDeleted.eq(YNType.N),
                        user.company.ne("SYSTEM")
                )
                .fetch();
    }

    @Override
    public boolean hasMainDepartment(String userId) {
        Long count = query
                .select(userDepartment.count())
                .from(userDepartment)
                .where(
                        userDepartment.user.id.eq(userId),
                        userDepartment.mainYN.eq(YNType.Y),
                        userDepartment.isDeleted.eq(YNType.N)
                )
                .fetchOne();

        return count != null && count > 0;
    }

    @Override
    public List<Department> findByUserIds(List<String> userIds) {
        return query
                .selectFrom(department)
                .leftJoin(department.headUser, user).fetchJoin()
                .where(
                        department.headUser.id.in(userIds),
                        department.isDeleted.eq(YNType.N)
                )
                .fetch();
    }

    @Override
    public List<Department> findApproversByUserId(String userId) {
        // 1. 사용자의 메인 부서 조회
        UserDepartment userDept = query
                .selectFrom(userDepartment)
                .join(userDepartment.department, department).fetchJoin()
                .where(
                        userDepartment.user.id.eq(userId),
                        userDepartment.mainYN.eq(YNType.Y),
                        userDepartment.isDeleted.eq(YNType.N)
                )
                .fetchOne();

        if (userDept == null) {
            return List.of();
        }

        // 2. 상위 부서들을 재귀적으로 조회
        List<Department> approverDepartments = new ArrayList<>();
        Department currentDept = userDept.getDepartment();

        // 현재 부서의 parent부터 시작하여 최상위 부서까지 조회
        while (currentDept != null && currentDept.getParentId() != null) {
            // parent 부서 조회
            Department parentDept = query
                    .selectFrom(department)
                    .where(
                            department.rowId.eq(currentDept.getParentId()),
                            department.isDeleted.eq(YNType.N)
                    )
                    .fetchOne();

            if (parentDept != null && parentDept.getHeadUser() != null) {
                approverDepartments.add(parentDept);
            }

            currentDept = parentDept;
        }

        return approverDepartments;
    }
}
