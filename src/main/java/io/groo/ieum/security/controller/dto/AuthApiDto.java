package io.groo.ieum.security.controller.dto;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;
import io.groo.core.type.YNType;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

public class AuthApiDto {
    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class LoginUserInfo {
        private String userId;
        private String userName;
        private String userEmail;
        private List<RoleInfo> roles;          // 역할 상세 정보 (역할 코드, 이름, 권한 목록)
        private List<String> userRoles;        // 역할 이름 목록 (기존 호환성)
        private String userRoleName;           // 첫 번째 역할 이름 (기존 호환성)
        private List<String> permissions;      // 모든 권한 코드 목록
        private YNType isLogin;
        private String profileUrl;
        private YNType passwordChangeRequired; // 비밀번호 변경 필요 여부 (토큰 pwdChange 클레임)
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class RoleInfo {
        private String roleCode;               // 역할 코드 (예: ADMIN, MANAGER)
        private String roleName;               // 역할 이름 (예: 관리자, 매니저)
        private List<PermissionInfo> permissions; // 해당 역할의 권한 목록
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class PermissionInfo {
        private String permissionCode;         // 권한 코드 (예: USER:READ, VACATION:APPROVE)
        private String permissionName;         // 권한 이름 (예: 사용자 조회, 휴가 승인)
    }
}
