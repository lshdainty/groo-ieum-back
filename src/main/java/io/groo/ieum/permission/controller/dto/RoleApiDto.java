package io.groo.ieum.permission.controller.dto;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Role & Permission API DTO
 */
public class RoleApiDto {

    /* ==================== Role Response DTO ==================== */

    /**
     * 역할 응답 DTO
     */
    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "역할 응답")
    public static class RoleResp {
        @Schema(description = "역할 코드", example = "ADMIN")
        private String roleCode;

        @Schema(description = "역할 이름", example = "관리자")
        private String roleName;

        @Schema(description = "설명", example = "시스템 관리자 역할")
        private String desc;

        @Schema(description = "권한 목록", example = "[\"USER_READ\", \"USER_MANAGE\"]")
        private List<String> permissions;
    }

    /**
     * 역할 간단 응답 DTO (권한 제외)
     */
    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class RoleSimpleResp {
        private String roleCode;
        private String roleName;
        private String desc;
    }

    /* ==================== Role Request DTO ==================== */

    /**
     * 역할 생성 요청 DTO
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "역할 생성 요청")
    public static class CreateRoleReq {
        @Schema(description = "역할 코드", example = "ADMIN", required = true)
        private String roleCode;

        @Schema(description = "역할 이름", example = "관리자", required = true)
        private String roleName;

        @Schema(description = "설명", example = "시스템 관리자 역할")
        private String desc;

        @Schema(description = "권한 코드 목록 (선택)", example = "[\"USER_READ\", \"USER_MANAGE\"]")
        private List<String> permissionCodes;
    }

    /**
     * 역할 수정 요청 DTO
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "역할 수정 요청")
    public static class UpdateRoleReq {
        @Schema(description = "역할 이름 (선택 — 없으면 그대로, 빈 글이거나 20자를 넘으면 400)", example = "관리자")
        private String roleName;

        @Schema(description = "설명 (선택 — 없으면 그대로, 빈 글이면 설명을 지움)", example = "시스템 관리자 역할")
        private String desc;

        @Schema(description = "권한 코드 목록 (선택)", example = "[\"USER_READ\", \"USER_MANAGE\"]")
        private List<String> permissionCodes;
    }

    /**
     * 역할 권한 수정 요청 DTO
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class UpdateRolePermissionsReq {
        private List<String> permissionCodes;
    }

    /**
     * 역할에 권한 추가/제거 요청 DTO
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class RolePermissionReq {
        private String permissionCode;
    }

    /* ==================== Permission Response DTO ==================== */

    /**
     * 권한 응답 DTO
     */
    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "권한 응답")
    public static class PermissionResp {
        @Schema(description = "권한 코드", example = "USER_READ")
        private String code;

        @Schema(description = "권한 이름", example = "사용자 조회")
        private String name;

        @Schema(description = "설명", example = "사용자 정보를 조회할 수 있는 권한")
        private String desc;

        @Schema(description = "리소스", example = "USER")
        private String resource;

        @Schema(description = "액션", example = "READ")
        private String action;
    }

    /* ==================== Permission Request DTO ==================== */

    /**
     * 권한 생성 요청 DTO
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "권한 생성 요청")
    public static class CreatePermissionReq {
        @Schema(description = "권한 코드", example = "USER_READ", required = true)
        private String code;

        @Schema(description = "권한 이름", example = "사용자 조회", required = true)
        private String name;

        @Schema(description = "설명", example = "사용자 정보를 조회할 수 있는 권한")
        private String desc;

        @Schema(description = "리소스", example = "USER", required = true)
        private String resource;

        @Schema(description = "액션", example = "READ", required = true)
        private String action;
    }

    /**
     * 권한 수정 요청 DTO
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class UpdatePermissionReq {
        private String name;
        private String desc;
        private String resource;
        private String action;
    }
}
