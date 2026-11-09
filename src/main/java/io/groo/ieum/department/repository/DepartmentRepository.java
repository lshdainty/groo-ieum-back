package io.groo.ieum.department.repository;

import io.groo.ieum.department.domain.Department;
import io.groo.ieum.department.domain.UserDepartment;
import io.groo.ieum.user.domain.User;

import java.util.List;
import java.util.Optional;

/**
 * Department Repository Interface
 */
public interface DepartmentRepository {
    /**
     * 신규 부서 저장
     *
     * @param department 저장할 부서
     */
    void save(Department department);

    /**
     * 단건 부서 조회
     *
     * @param id 부서 ID
     * @return Optional&lt;Department&gt;
     */
    Optional<Department> findById(Long id);

    /**
     * 유저-부서 연결 저장
     *
     * @param userDepartment 저장할 유저-부서 연결
     */
    void saveUserDepartment(UserDepartment userDepartment);

    /**
     * 특정 유저의 메인 부서 조회
     *
     * @param userId 유저 ID
     * @return Optional&lt;UserDepartment&gt;
     */
    Optional<UserDepartment> findMainDepartmentByUserId(String userId);

    /**
     * 특정 유저와 부서의 연결 조회
     *
     * @param userId 유저 ID
     * @param departmentId 부서 ID
     * @return Optional&lt;UserDepartment&gt;
     */
    Optional<UserDepartment> findUserDepartment(String userId, Long departmentId);

    /**
     * 특정 유저의 지우지 않은 소속 전부를 행 잠금(PESSIMISTIC_WRITE)으로 조회
     * 주 부서 옮기기처럼 "한 사람에 주 부서 하나" 를 지켜야 하는 쓰기가 겹치지 않게 직렬화한다.
     *
     * @param userId 유저 ID
     * @return 잠근 UserDepartment 목록
     */
    List<UserDepartment> findUserDepartmentsForUpdate(String userId);

    /**
     * 특정 부서에 속하지 않은 유저 조회
     *
     * @param departmentId 부서 ID
     * @return List&lt;User&gt;
     */
    List<User> findUsersNotInDepartment(Long departmentId);

    /**
     * 특정 부서에 속한 UserDepartment 조회 (mainYN 포함)
     *
     * @param departmentId 부서 ID
     * @return List&lt;UserDepartment&gt;
     */
    List<UserDepartment> findUserDepartmentsInDepartment(Long departmentId);

    /**
     * 특정 유저의 메인 부서 존재 여부 확인
     *
     * @param userId 유저 ID
     * @return 메인 부서 존재 여부
     */
    boolean hasMainDepartment(String userId);

    /**
     * 여러 유저 ID로 Department 조회 (부서장 확인용)
     *
     * @param userIds 유저 ID 리스트
     * @return List&lt;Department&gt;
     */
    List<Department> findByUserIds(List<String> userIds);

    /**
     * 특정 유저의 승인권자 목록 조회 (상위 부서장들)
     *
     * @param userId 유저 ID
     * @return List&lt;Department&gt;
     */
    List<Department> findApproversByUserId(String userId);
}
