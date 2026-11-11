package io.groo.ieum.user.service.dto;

import io.groo.core.type.CountryCode;
import io.groo.core.type.YNType;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import lombok.AllArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Getter @Setter
@Builder
public class UserServiceDto {
    private Long ssoUserRowId; // SSO에서 발급한 사용자 순번 (HR PK로 사용)
    private String id;
    private String name;
    private String email;
    private List<String> roleNames;
    private LocalDate birth;
    private String workTime;
    private LocalDate joinDate; // 입사일
    private String company;
    private YNType lunarYN;
    private CountryCode countryCode; // 국가 코드

    private String profileName;
    private String profileUrl;
    private String profileUUID;
    /** 프로필 사진 지우기 (수정 요청 전용 — Y 면 사진을 지운다) */
    private YNType profileDeleteYN;
    private String dashboard;

    // 초대 상태 정보 (SSO에서 조회)
    private java.time.LocalDateTime invitationSentAt;
    private java.time.LocalDateTime invitationExpiresAt;
    private String invitationStatus;
    private java.time.LocalDateTime registeredAt;

    // 메인 부서 정보
    private String mainDepartmentNameKR; // 메인 부서의 한글명

    // 승인권자 정보 (부서 정보 포함)
    private Long departmentId; // 부서 ID
    private String departmentName; // 부서명
    private String departmentNameKR; // 부서 한글명
    private Long departmentLevel; // 부서 레벨

    // 역할 및 권한 상세 정보
    private List<RoleDetailDto> roles; // 역할 상세 정보 (역할 코드, 이름, 권한 목록)
    private List<String> allPermissions; // 모든 권한 코드 목록

    /**
     * 역할 상세 정보 DTO
     */
    @Getter
    @Builder
    public static class RoleDetailDto {
        private String roleCode;
        private String roleName;
        private List<PermissionDetailDto> permissions;
    }

    /**
     * 권한 상세 정보 DTO
     */
    @Getter
    @Builder
    public static class PermissionDetailDto {
        private String permissionCode;
        private String permissionName;
    }

    /**
     * 사용자 초대 결과 DTO
     */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class InviteResult {
        private boolean alreadyExists;
        private Long ssoUserRowId;
        private String userId;
        private String name;
        private String email;
        private String message;
        private java.time.LocalDateTime invitationSentAt;
        private java.time.LocalDateTime invitationExpiresAt;
        private String invitationStatus;
    }
}
