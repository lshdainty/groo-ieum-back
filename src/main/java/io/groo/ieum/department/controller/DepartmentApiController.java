package io.groo.ieum.department.controller;

import io.groo.core.controller.ApiResponse;
import io.groo.ieum.department.controller.dto.DepartmentApiDto;
import io.groo.ieum.department.service.DepartmentService;
import io.groo.ieum.department.service.dto.DepartmentServiceDto;
import io.groo.ieum.department.service.dto.UserDepartmentServiceDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
public class DepartmentApiController implements DepartmentApi {
    private final DepartmentService departmentService;

    @Override
    @PreAuthorize("hasAuthority('COMPANY:MANAGE')")
    public ApiResponse registDepartment(DepartmentApiDto.RegistDepartmentReq data) {
        Long departmentId = departmentService.regist(DepartmentServiceDto.builder()
                .name(data.getDepartmentName())
                .nameKR(data.getDepartmentNameKr())
                .parentId(data.getParentId())
                .headUserId(data.getHeadUserId())
                .level(data.getTreeLevel())
                .desc(data.getDepartmentDesc())
                .color(data.getColorCode())
                .companyId(data.getCompanyId())
                .build()
        );
        return ApiResponse.success(new DepartmentApiDto.RegistDepartmentResp(departmentId));
    }

    @Override
    @PreAuthorize("hasAuthority('COMPANY:MANAGE')")
    public ApiResponse editDepartment(Long departmentId, DepartmentApiDto.EditDepartmentReq data) {
        departmentService.edit(DepartmentServiceDto.builder()
                .id(departmentId)
                .name(data.getDepartmentName())
                .nameKR(data.getDepartmentNameKr())
                .parentId(data.getParentId())
                .headUserId(data.getHeadUserId())
                .level(data.getTreeLevel())
                .desc(data.getDepartmentDesc())
                .color(data.getColorCode())
                .build()
        );
        return ApiResponse.success();
    }

    @Override
    @PreAuthorize("hasAuthority('COMPANY:MANAGE')")
    public ApiResponse deleteDepartment(Long departmentId) {
        departmentService.delete(departmentId);
        return ApiResponse.success();
    }

    @Override
    @PreAuthorize("hasAuthority('COMPANY:READ')")
    public ApiResponse searchDepartmentById(Long departmentId) {
        DepartmentServiceDto serviceDto = departmentService.searchDepartmentById(departmentId);

        return ApiResponse.success(new DepartmentApiDto.SearchDepartmentResp(
                serviceDto.getId(),
                serviceDto.getName(),
                serviceDto.getNameKR(),
                serviceDto.getParentId(),
                serviceDto.getHeadUserId(),
                serviceDto.getLevel(),
                serviceDto.getDesc(),
                serviceDto.getColor(),
                serviceDto.getCompanyId()
        ));
    }

    @Override
    @PreAuthorize("hasAuthority('COMPANY:READ')")
    public ApiResponse searchDepartmentByIdWithChildren(Long departmentId) {
        DepartmentServiceDto serviceDto = departmentService.searchDepartmentByIdWithChildren(departmentId);
        DepartmentApiDto.SearchDepartmentWithChildrenResp responseDto =
                DepartmentApiDto.SearchDepartmentWithChildrenResp.fromServiceDto(serviceDto);
        return ApiResponse.success(responseDto);
    }

    @Override
    @PreAuthorize("hasAuthority('COMPANY:MANAGE')")
    public ApiResponse registDepartmentUsers(Long departmentId, DepartmentApiDto.RegistDepartmentUserReq data) {
        // DTO 변환
        List<UserDepartmentServiceDto> userDepartmentServiceDtos = data.getUsers().stream()
                .map(userInfo -> UserDepartmentServiceDto.builder()
                        .userId(userInfo.getUserId())
                        .mainYN(userInfo.getMainYn())
                        .build())
                .toList();

        List<Long> userDepartmentIds = departmentService.registUserDepartments(userDepartmentServiceDtos, departmentId);
        return ApiResponse.success(new DepartmentApiDto.RegistDepartmentUserResp(userDepartmentIds));
    }

    @Override
    @PreAuthorize("hasAuthority('COMPANY:MANAGE')")
    public ApiResponse deleteDepartmentUsers(Long departmentId, DepartmentApiDto.DeleteDepartmentUserReq data) {
        departmentService.deleteUserDepartments(data.getUserIds(), departmentId);
        return ApiResponse.success();
    }

    @Override
    @PreAuthorize("hasAuthority('COMPANY:MANAGE')")
    public ApiResponse changeMainDepartment(Long departmentId, String userId) {
        departmentService.changeMainDepartment(departmentId, userId);
        return ApiResponse.success();
    }

    @Override
    @PreAuthorize("hasAuthority('COMPANY:READ')")
    public ApiResponse getDepartmentUsers(Long departmentId) {
        DepartmentServiceDto serviceDto = departmentService.getUsersInAndNotInDepartment(departmentId);

        DepartmentApiDto.GetDepartmentUsersResp responseDto = new DepartmentApiDto.GetDepartmentUsersResp(
                serviceDto.getId(),
                serviceDto.getName(),
                serviceDto.getNameKR(),
                serviceDto.getParentId(),
                serviceDto.getHeadUserId(),
                serviceDto.getLevel(),
                serviceDto.getDesc(),
                serviceDto.getColor(),
                serviceDto.getCompanyId(),
                serviceDto.getUsersInDepartment().stream()
                        .map(userDepartment -> new DepartmentApiDto.UserInfo(
                                userDepartment.getUser().getId(),
                                userDepartment.getUser().getName(),
                                userDepartment.getMainYN()
                        ))
                        .toList(),
                serviceDto.getUsersNotInDepartment().stream()
                        .map(userDepartment -> new DepartmentApiDto.UserInfo(
                                userDepartment.getUser().getId(),
                                userDepartment.getUser().getName(),
                                userDepartment.getMainYN()
                        ))
                        .toList()
        );

        return ApiResponse.success(responseDto);
    }
}
