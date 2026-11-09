package io.groo.ieum.department.service;

import io.groo.ieum.department.domain.Department;
import io.groo.ieum.department.service.dto.DepartmentServiceDto;
import io.groo.ieum.department.service.dto.UserDepartmentServiceDto;

import java.util.List;

/**
 * 부서 관리를 위한 서비스 인터페이스
 */
public interface DepartmentService {

    /**
     * 부서를 생성합니다.
     *
     * @param data 부서 생성 정보
     * @return 생성된 부서 ID
     */
    Long regist(DepartmentServiceDto data);

    /**
     * 부서 정보를 수정합니다.
     *
     * @param data 부서 수정 정보
     */
    void edit(DepartmentServiceDto data);

    /**
     * 부서를 삭제합니다 (논리 삭제).
     *
     * @param departmentId 삭제할 부서 ID
     */
    void delete(Long departmentId);

    /**
     * ID로 부서를 조회합니다.
     *
     * @param id 부서 ID
     * @return 부서 정보
     */
    DepartmentServiceDto searchDepartmentById(Long id);

    /**
     * ID로 부서를 조회합니다 (하위 부서 포함).
     *
     * @param id 부서 ID
     * @return 부서 정보 (하위 부서 포함)
     */
    DepartmentServiceDto searchDepartmentByIdWithChildren(Long id);

    /**
     * 부서에 사용자들을 등록합니다.
     *
     * @param userDataList 등록할 사용자 정보 목록
     * @param departmentId 부서 ID
     * @return 생성된 UserDepartment ID 목록
     */
    List<Long> registUserDepartments(List<UserDepartmentServiceDto> userDataList, Long departmentId);

    /**
     * 부서에서 사용자들을 삭제합니다 (논리 삭제).
     *
     * @param userIds 삭제할 사용자 ID 목록
     * @param departmentId 부서 ID
     */
    void deleteUserDepartments(List<String> userIds, Long departmentId);

    /**
     * 사용자의 주 부서를 이 부서로 옮깁니다.
     * 이 부서가 이미 주 부서면 그대로 성공하고, 사용자의 다른 소속은 모두 주 부서가 아니게 됩니다.
     *
     * @param departmentId 주 부서로 정할 부서 ID
     * @param userId 사용자 ID
     * @throws io.groo.core.exception.EntityNotFoundException 부서가 없거나, 사용자가 이 부서 구성원이 아닌 경우
     */
    void changeMainDepartment(Long departmentId, String userId);

    /**
     * 부서에 속한 사용자와 속하지 않은 사용자를 조회합니다.
     *
     * @param departmentId 부서 ID
     * @return 부서 정보 (부서에 속한/속하지 않은 사용자 목록 포함)
     */
    DepartmentServiceDto getUsersInAndNotInDepartment(Long departmentId);

    /**
     * 부서 존재 여부를 확인하고 부서 엔티티를 반환합니다.
     *
     * @param departmentId 부서 ID
     * @return 부서 엔티티
     * @throws io.groo.core.exception.EntityNotFoundException 부서가 존재하지 않거나 삭제된 경우
     */
    Department checkDepartmentExists(Long departmentId);
}
