package io.groo.ieum.user.controller;

import io.groo.core.audit.AccessAction;
import io.groo.core.audit.AuditAccess;
import io.groo.core.exception.ForbiddenException;
import io.groo.core.type.YNType;
import io.groo.ieum.common.exception.HrErrorCode;
import io.groo.ieum.security.annotation.LoginUser;
import io.groo.ieum.user.controller.dto.UserApiDto;
import io.groo.core.controller.ApiResponse;
import io.groo.ieum.user.domain.User;
import io.groo.ieum.user.service.UserService;
import io.groo.ieum.user.service.dto.UserServiceDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@Slf4j
public class UserApiController implements UserApi {
    private final UserService userService;
    private final MessageSource messageSource;

    @Override
    public ApiResponse joinUser(UserApiDto.JoinUserReq data) {
        String userId = userService.joinUser(UserServiceDto.builder()
                .id(data.getUserId())
                .name(data.getUserName())
                .email(data.getUserEmail())
                .birth(data.getUserBirth())
                .company(data.getUserCompanyType())
                .workTime(data.getUserWorkTime())
                .joinDate(data.getJoinDate())
                .lunarYN(data.getLunarYn())
                .profileUrl(data.getProfileUrl())
                .profileUUID(data.getProfileUuid())
                .countryCode(data.getCountryCode())
                .build()
        );

        return ApiResponse.success(new UserApiDto.JoinUserResp(userId));
    }

    @Override
    @PreAuthorize("hasAuthority('USER:READ')")
    @AuditAccess(action = AccessAction.READ, targetType = "USER", targetId = "#userId")
    public ApiResponse searchUser(String userId, @LoginUser User loginUser) {
        // 본인 정보이거나 사용자 관리(USER:MANAGE) · 권한 관리(ROLE:MANAGE) 권한이 있을 때만 다른 사람의 상세 정보를 읽는다
        if (!loginUser.getId().equals(userId) && !canReadAnyUser()) {
            log.warn("사용자 조회 거부 - 로그인 유저: {}, 대상 유저: {}", loginUser.getId(), userId);
            throw new ForbiddenException(HrErrorCode.PERMISSION_DENIED);
        }
        UserServiceDto user = userService.searchUser(userId);

        // 역할 상세 정보 변환 (null 체크)
        List<UserApiDto.RoleDetailResp> roleDetails = user.getRoles() != null
                ? user.getRoles().stream()
                    .map(role -> new UserApiDto.RoleDetailResp(
                            role.getRoleCode(),
                            role.getRoleName(),
                            role.getPermissions() != null
                                ? role.getPermissions().stream()
                                    .map(perm -> new UserApiDto.PermissionDetailResp(
                                            perm.getPermissionCode(),
                                            perm.getPermissionName()
                                    ))
                                    .collect(Collectors.toList())
                                : List.of()
                    ))
                    .collect(Collectors.toList())
                : List.of();

        // roleNames null 체크
        List<String> roleNames = user.getRoleNames() != null ? user.getRoleNames() : List.of();
        List<String> allPermissions = user.getAllPermissions() != null ? user.getAllPermissions() : List.of();

        return ApiResponse.success(new UserApiDto.SearchUserResp(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getBirth(),
                user.getWorkTime(),
                user.getJoinDate(),
                roleDetails,
                roleNames,
                roleNames.isEmpty() ? null : roleNames.get(0),
                allPermissions,
                user.getCompany(),
                getTranslatedName(user.getCompany()),
                user.getLunarYN(),
                user.getProfileUrl(),
                user.getMainDepartmentNameKR(),
                user.getDashboard(),
                user.getCountryCode(),
                user.getInvitationSentAt(),
                user.getInvitationExpiresAt(),
                user.getInvitationStatus(),
                user.getRegisteredAt()
        ));
    }

    private boolean hasAuthority(String authority) {
        org.springframework.security.core.Authentication authentication =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.getAuthorities().stream().anyMatch(auth -> authority.equals(auth.getAuthority()));
    }

    private boolean canReadAnyUser() {
        org.springframework.security.core.Authentication authentication =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(auth -> "USER:MANAGE".equals(auth.getAuthority())
                        || "ROLE:MANAGE".equals(auth.getAuthority()));
    }

    @Override
    public ApiResponse checkUserIdDuplicate(String userId) {
        boolean isDuplicate = userService.checkUserIdDuplicate(userId);
        return ApiResponse.success(new UserApiDto.CheckUserIdDuplicateResp(isDuplicate));
    }

    @Override
    @PreAuthorize("hasAuthority('USER:READ')")
    @AuditAccess(action = AccessAction.LIST, targetType = "USER", detail = "전체 사용자 목록")
    public ApiResponse searchUsers() {
        List<UserServiceDto> users = userService.searchUsers();

        // 사용자/권한 관리 권한자는 전체 응답, 그 외에는 화면(달력 · 일정 · 생일 회비 · 회사 통계)이 쓰는 칸만 내린다
        boolean fullView = canReadAnyUser();

        List<UserApiDto.SearchUserResp> resps = users.stream()
                .map(u -> {
                    // 역할 상세 정보 변환 (null 체크)
                    List<UserApiDto.RoleDetailResp> roleDetails = u.getRoles() != null
                            ? u.getRoles().stream()
                                .map(role -> new UserApiDto.RoleDetailResp(
                                        role.getRoleCode(),
                                        role.getRoleName(),
                                        role.getPermissions() != null
                                            ? role.getPermissions().stream()
                                                .map(perm -> new UserApiDto.PermissionDetailResp(
                                                        perm.getPermissionCode(),
                                                        perm.getPermissionName()
                                                ))
                                                .collect(Collectors.toList())
                                            : List.of()
                                ))
                                .collect(Collectors.toList())
                            : List.of();

                    // roleNames null 체크
                    List<String> roleNames = u.getRoleNames() != null ? u.getRoleNames() : List.of();
                    List<String> allPermissions = u.getAllPermissions() != null ? u.getAllPermissions() : List.of();

                    return new UserApiDto.SearchUserResp(
                            u.getId(),
                            u.getName(),
                            fullView ? u.getEmail() : null,
                            u.getBirth(),
                            u.getWorkTime(),
                            fullView ? u.getJoinDate() : null,
                            fullView ? roleDetails : List.of(),
                            fullView ? roleNames : List.of(),
                            roleNames.isEmpty() ? null : roleNames.get(0),
                            fullView ? allPermissions : List.of(),
                            u.getCompany(),
                            getTranslatedName(u.getCompany()),
                            fullView ? u.getLunarYN() : null,
                            u.getProfileUrl(),
                            u.getMainDepartmentNameKR(),
                            fullView ? u.getDashboard() : null,
                            fullView ? u.getCountryCode() : null,
                            fullView ? u.getInvitationSentAt() : null,
                            fullView ? u.getInvitationExpiresAt() : null,
                            fullView ? u.getInvitationStatus() : null,
                            fullView ? u.getRegisteredAt() : null
                    );
                })
                .collect(Collectors.toList());

        return ApiResponse.success(resps);
    }

    @Override
    @PreAuthorize("hasAuthority('USER:EDIT')")
    @AuditAccess(action = AccessAction.UPDATE, targetType = "USER", targetId = "#userId")
    public ApiResponse editUser(String userId, UserApiDto.EditUserReq data, User loginUser) {
        userService.editUser(UserServiceDto.builder()
                .id(userId)
                .name(data.getUserName())
                .email(data.getUserEmail())
                .birth(data.getUserBirth())
                .roleNames(data.getUserRoles())
                .company(data.getUserCompanyType())
                .workTime(data.getUserWorkTime())
                .lunarYN(data.getLunarYn())
                .profileUrl(data.getProfileUrl())
                .profileUUID(data.getProfileUuid())
                .dashboard(data.getDashboard())
                .countryCode(data.getCountryCode())
                .build(),
                loginUser.getId(),
                hasUserManageAuthority()
        );

        UserServiceDto findUser = userService.searchUser(userId);

        // 역할 상세 정보 변환 (null 체크)
        List<UserApiDto.RoleDetailResp> roleDetails = findUser.getRoles() != null
                ? findUser.getRoles().stream()
                    .map(role -> new UserApiDto.RoleDetailResp(
                            role.getRoleCode(),
                            role.getRoleName(),
                            role.getPermissions() != null
                                ? role.getPermissions().stream()
                                    .map(perm -> new UserApiDto.PermissionDetailResp(
                                            perm.getPermissionCode(),
                                            perm.getPermissionName()
                                    ))
                                    .collect(Collectors.toList())
                                : List.of()
                    ))
                    .collect(Collectors.toList())
                : List.of();

        // roleNames null 체크
        List<String> roleNames = findUser.getRoleNames() != null ? findUser.getRoleNames() : List.of();
        List<String> allPermissions = findUser.getAllPermissions() != null ? findUser.getAllPermissions() : List.of();

        return ApiResponse.success(new UserApiDto.EditUserResp(
                findUser.getId(),
                findUser.getName(),
                findUser.getEmail(),
                findUser.getBirth(),
                findUser.getWorkTime(),
                roleDetails,
                roleNames,
                roleNames.isEmpty() ? null : roleNames.get(0),
                allPermissions,
                findUser.getCompany(),
                getTranslatedName(findUser.getCompany()),
                findUser.getLunarYN(),
                findUser.getProfileUrl(),
                findUser.getDashboard(),
                findUser.getCountryCode()
        ));
    }

    @Override
    @PreAuthorize("hasAuthority('USER:MANAGE')")
    @AuditAccess(action = AccessAction.DELETE, targetType = "USER", targetId = "#userId")
    public ApiResponse deleteUser(String userId) {
        userService.deleteUser(userId);
        return ApiResponse.success();
    }

    @Override
    public ApiResponse uploadProfile(UserApiDto.UploadProfileReq data) {
        UserServiceDto dto = userService.saveProfileImgInTempFolder(data.getProfile());
        return ApiResponse.success(new UserApiDto.UploadProfileResp(
                dto.getProfileUrl(),
                dto.getProfileUUID()
        ));
    }

    /**
     * 사용자의 메인 부서 존재 여부 확인
     */
    @Override
    public ApiResponse checkUserMainDepartmentExistence(String userId) {
        YNType hasMainDepartment = userService.checkUserHasMainDepartment(userId);
        return ApiResponse.success(new UserApiDto.CheckMainDepartmentExistenceResp(hasMainDepartment));
    }

    /**
     * 유저 대시보드 수정
     * PATCH /api/v1/users/{userId}/dashboard
     */
    @Override
    @PreAuthorize("hasAuthority('USER:EDIT')")
    public ApiResponse updateDashboard(String userId, UserApiDto.UpdateDashboardReq data, User loginUser) {
        UserServiceDto result = userService.updateDashboard(loginUser.getId(), userId, data.getDashboard());

        return ApiResponse.success(new UserApiDto.UpdateDashboardResp(
                result.getId(),
                result.getDashboard()
        ));
    }

    /**
     * 특정 유저의 승인권자 목록 조회
     * GET /api/v1/users/{userId}/approvers
     */
    @Override
    public ApiResponse getUserApprovers(String userId, @LoginUser User loginUser) {
        // 본인의 승인권자이거나 사용자/휴가 관리 권한이 있을 때만 본다
        if (!loginUser.getId().equals(userId) && !canReadAnyUser() && !hasAuthority("VACATION:MANAGE")) {
            log.warn("승인권자 조회 거부 - 로그인 유저: {}, 대상 유저: {}", loginUser.getId(), userId);
            throw new ForbiddenException(HrErrorCode.PERMISSION_DENIED);
        }
        List<UserServiceDto> approvers = userService.getUserApprovers(userId);

        List<UserApiDto.ApproverDetailResp> approverDetails = approvers.stream()
                .map(approver -> {
                    // 역할 상세 정보 변환 (null 체크)
                    List<UserApiDto.RoleDetailResp> roleDetails = approver.getRoles() != null
                            ? approver.getRoles().stream()
                                .map(role -> new UserApiDto.RoleDetailResp(
                                        role.getRoleCode(),
                                        role.getRoleName(),
                                        role.getPermissions() != null
                                            ? role.getPermissions().stream()
                                                .map(perm -> new UserApiDto.PermissionDetailResp(
                                                        perm.getPermissionCode(),
                                                        perm.getPermissionName()
                                                ))
                                                .collect(Collectors.toList())
                                            : List.of()
                                ))
                                .collect(Collectors.toList())
                            : List.of();

                    // roleNames null 체크
                    List<String> roleNames = approver.getRoleNames() != null ? approver.getRoleNames() : List.of();
                    List<String> allPermissions = approver.getAllPermissions() != null ? approver.getAllPermissions() : List.of();

                    return new UserApiDto.ApproverDetailResp(
                            approver.getId(),
                            approver.getName(),
                            approver.getEmail(),
                            roleDetails,
                            roleNames,
                            roleNames.isEmpty() ? null : roleNames.get(0),
                            allPermissions,
                            approver.getDepartmentId(),
                            approver.getDepartmentName(),
                            approver.getDepartmentNameKR(),
                            approver.getDepartmentLevel()
                    );
                })
                .collect(Collectors.toList());

        int maxAvailableCount = approverDetails.size();
        boolean isAutoApproval = maxAvailableCount == 0;

        return ApiResponse.success(new UserApiDto.GetApproversResp(
                approverDetails,
                maxAvailableCount,
                isAutoApproval
        ));
    }

    /**
     * 현재 사용자가 사용자 관리 권한(USER:MANAGE 또는 ROLE:MANAGE)을 가지고 있는지 확인
     */
    private boolean hasUserManageAuthority() {
        org.springframework.security.core.Authentication authentication =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(auth -> "USER:MANAGE".equals(auth.getAuthority())
                        || "ROLE:MANAGE".equals(auth.getAuthority()));
    }

    private String getTranslatedName(String companyCode) {
        if (companyCode == null) return null;
        return companyCode;
    }

    // ==================== 초대 관련 API ====================

    @Override
    @PreAuthorize("hasAuthority('USER:MANAGE')")
    @AuditAccess(action = AccessAction.CREATE, targetType = "USER", targetId = "#data.userId",
            detail = "사용자 초대")
    public ApiResponse inviteUser(UserApiDto.InviteUserReq data) {
        log.debug("REST request to invite user: {}", data.getUserId());

        UserServiceDto.InviteResult result = userService.inviteUser(
                UserServiceDto.builder()
                        .id(data.getUserId())
                        .name(data.getUserName())
                        .email(data.getUserEmail())
                        .joinDate(data.getJoinDate())
                        .company(data.getUserCompanyType())
                        .workTime(data.getUserWorkTime())
                        .countryCode(data.getCountryCode())
                        .build()
        );

        return ApiResponse.success(new UserApiDto.InviteUserResp(
                result.isAlreadyExists(),
                result.getSsoUserRowId(),
                result.getUserId(),
                result.getName(),
                result.getEmail(),
                result.getMessage() != null ? result.getMessage() :
                        (result.isAlreadyExists() ? "기존 SSO 계정과 연결되었습니다." : "초대가 발송되었습니다."),
                result.getInvitationSentAt(),
                result.getInvitationExpiresAt(),
                result.getInvitationStatus()
        ));
    }

    @Override
    @PreAuthorize("hasAuthority('USER:MANAGE')")
    @AuditAccess(action = AccessAction.UPDATE, targetType = "USER", targetId = "#userId",
            detail = "초대 정보 수정")
    public ApiResponse editInvitation(String userId, UserApiDto.EditInvitationReq data) {
        log.debug("REST request to edit invitation: {}", userId);

        UserServiceDto result = userService.editInvitation(
                userId,
                UserServiceDto.builder()
                        .name(data.getUserName())
                        .email(data.getUserEmail())
                        .company(data.getUserCompanyType())
                        .workTime(data.getUserWorkTime())
                        .countryCode(data.getCountryCode())
                        .build()
        );

        return ApiResponse.success(new UserApiDto.EditInvitationResp(
                result.getSsoUserRowId(),
                result.getId(),
                result.getName(),
                result.getEmail(),
                result.getCompany(),
                result.getWorkTime(),
                result.getCountryCode()
        ));
    }

    @Override
    @PreAuthorize("hasAuthority('USER:MANAGE')")
    @AuditAccess(action = AccessAction.UPDATE, targetType = "USER", targetId = "#userId",
            detail = "초대 재발송")
    public ApiResponse resendInvitation(String userId) {
        log.debug("REST request to resend invitation: {}", userId);

        userService.resendInvitation(userId);

        return ApiResponse.success();
    }

    // ==================== 비밀번호 관련 API ====================

    @Override
    public ApiResponse changePassword(User loginUser, UserApiDto.ChangePasswordReq data) {
        log.debug("REST request to change password: {}", loginUser.getId());

        userService.changePassword(
                loginUser.getId(),
                data.getCurrentPassword(),
                data.getNewPassword(),
                data.getNewPasswordConfirm()
        );

        return ApiResponse.success();
    }

    @Override
    @PreAuthorize("hasAuthority('USER:MANAGE')")
    @AuditAccess(action = AccessAction.UPDATE, targetType = "USER", targetId = "#userId",
            detail = "비밀번호 초기화")
    public ApiResponse resetPassword(String userId) {
        log.debug("REST request to reset password: {}", userId);

        userService.resetPassword(userId);

        return ApiResponse.success();
    }
}
