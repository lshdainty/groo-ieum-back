package io.groo.ieum.user.controller.dto;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;
import io.groo.core.type.CountryCode;
import io.groo.core.type.YNType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

public class UserApiDto {
    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "회원가입 요청")
    public static class JoinUserReq {
        @Schema(description = "사용자 ID", example = "user123")
        private String userId;

        @Schema(description = "사용자 이름", example = "홍길동")
        private String userName;

        @Schema(description = "사용자 이메일", example = "user@example.com")
        private String userEmail;

        @Schema(description = "생년월일", example = "1990-01-01")
        private LocalDate userBirth;

        @Schema(description = "소속 회사", example = "KAKAO")
        private String userCompanyType;

        @Schema(description = "근무 시간", example = "09:00-18:00")
        private String userWorkTime;

        @Schema(description = "입사일", example = "2024-01-01")
        private LocalDate joinDate;

        @Schema(description = "음력 여부", example = "N")
        private YNType lunarYn;

        @Schema(description = "프로필 이미지 URL")
        private String profileUrl;

        @Schema(description = "프로필 이미지 UUID")
        private String profileUuid;

        @Schema(description = "국가 코드", example = "KR")
        private CountryCode countryCode;
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "회원가입 응답")
    public static class JoinUserResp {
        @Schema(description = "등록된 사용자 ID", example = "user123")
        private String userId;
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "사용자 조회 응답")
    public static class SearchUserResp {
        @Schema(description = "사용자 ID", example = "user123")
        private String userId;

        @Schema(description = "사용자 이름", example = "홍길동")
        private String userName;

        @Schema(description = "사용자 이메일", example = "user@example.com")
        private String userEmail;

        @Schema(description = "생년월일", example = "1990-01-01")
        private LocalDate userBirth;

        @Schema(description = "근무 시간", example = "09:00-18:00")
        private String userWorkTime;

        @Schema(description = "입사일", example = "2024-01-01")
        private LocalDate joinDate;

        @Schema(description = "역할 상세 정보 (역할 코드, 이름, 권한 목록)")
        private List<RoleDetailResp> roles;

        @Schema(description = "역할 이름 목록 (기존 호환성)", example = "[\"ADMIN\", \"MANAGER\"]")
        private List<String> userRoles;

        @Schema(description = "첫 번째 역할 이름 (기존 호환성)", example = "ADMIN")
        private String userRoleName;

        @Schema(description = "모든 권한 코드 목록", example = "[\"USER_READ\", \"USER_UPDATE\"]")
        private List<String> permissions;

        @Schema(description = "소속 회사", example = "KAKAO")
        private String userCompanyType;

        @Schema(description = "소속 회사명", example = "카카오")
        private String userOriginCompanyName;

        @Schema(description = "음력 여부", example = "N")
        private YNType lunarYn;

        @Schema(description = "프로필 이미지 URL")
        private String profileUrl;

        @Schema(description = "메인 부서명 (한글)", example = "개발팀")
        private String mainDepartmentNameKr;

        @Schema(description = "대시보드 설정")
        private String dashboard;

        @Schema(description = "국가 코드", example = "KR")
        private CountryCode countryCode;

        @Schema(description = "초대 발송 시간")
        private java.time.LocalDateTime invitationSentAt;

        @Schema(description = "초대 만료 시간")
        private java.time.LocalDateTime invitationExpiresAt;

        @Schema(description = "초대 상태", example = "PENDING")
        private String invitationStatus;

        @Schema(description = "회원가입 완료 시간")
        private java.time.LocalDateTime registeredAt;
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "사용자 수정 요청")
    public static class EditUserReq {
        @Schema(description = "사용자 이름", example = "홍길동")
        private String userName;

        @Schema(description = "사용자 이메일", example = "user@example.com")
        private String userEmail;

        @Schema(description = "생년월일", example = "1990-01-01")
        private LocalDate userBirth;

        @Schema(description = "역할 목록", example = "[\"ADMIN\", \"MANAGER\"]")
        private List<String> userRoles;

        @Schema(description = "소속 회사", example = "KAKAO")
        private String userCompanyType;

        @Schema(description = "근무 시간", example = "09:00-18:00")
        private String userWorkTime;

        @Schema(description = "음력 여부", example = "N")
        private YNType lunarYn;

        @Schema(description = "프로필 이미지 URL")
        private String profileUrl;

        @Schema(description = "프로필 이미지 UUID")
        private String profileUuid;

        @Schema(description = "대시보드 설정")
        private String dashboard;

        @Schema(description = "국가 코드", example = "KR")
        private CountryCode countryCode;
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "사용자 수정 응답")
    public static class EditUserResp {
        @Schema(description = "사용자 ID", example = "user123")
        private String userId;

        @Schema(description = "사용자 이름", example = "홍길동")
        private String userName;

        @Schema(description = "사용자 이메일", example = "user@example.com")
        private String userEmail;

        @Schema(description = "생년월일", example = "1990-01-01")
        private LocalDate userBirth;

        @Schema(description = "근무 시간", example = "09:00-18:00")
        private String userWorkTime;

        @Schema(description = "역할 상세 정보 (역할 코드, 이름, 권한 목록)")
        private List<RoleDetailResp> roles;

        @Schema(description = "역할 이름 목록 (기존 호환성)", example = "[\"ADMIN\", \"MANAGER\"]")
        private List<String> userRoles;

        @Schema(description = "첫 번째 역할 이름 (기존 호환성)", example = "ADMIN")
        private String userRoleName;

        @Schema(description = "모든 권한 코드 목록", example = "[\"USER_READ\", \"USER_UPDATE\"]")
        private List<String> permissions;

        @Schema(description = "소속 회사", example = "KAKAO")
        private String userCompanyType;

        @Schema(description = "소속 회사명", example = "카카오")
        private String userOriginCompanyName;

        @Schema(description = "음력 여부", example = "N")
        private YNType lunarYn;

        @Schema(description = "프로필 이미지 URL")
        private String profileUrl;

        @Schema(description = "대시보드 설정")
        private String dashboard;

        @Schema(description = "국가 코드", example = "KR")
        private CountryCode countryCode;
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "프로필 이미지 업로드 요청")
    public static class UploadProfileReq {
        @Schema(description = "프로필 이미지 파일")
        private MultipartFile profile;
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "프로필 이미지 업로드 응답")
    public static class UploadProfileResp {
        @Schema(description = "프로필 이미지 URL")
        private String profileUrl;

        @Schema(description = "프로필 이미지 UUID")
        private String profileUuid;
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "사용자 ID 중복 확인 응답")
    public static class CheckUserIdDuplicateResp {
        @Schema(description = "중복 여부", example = "false")
        private boolean duplicate;
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "메인 부서 존재 여부 확인 응답")
    public static class CheckMainDepartmentExistenceResp {
        @Schema(description = "메인 부서 보유 여부", example = "Y")
        private YNType hasMainDepartment;
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "승인권자 상세 정보")
    public static class ApproverDetailResp {
        @Schema(description = "사용자 ID", example = "user123")
        private String userId;

        @Schema(description = "사용자 이름", example = "홍길동")
        private String userName;

        @Schema(description = "사용자 이메일", example = "user@example.com")
        private String userEmail;

        @Schema(description = "역할 상세 정보 (역할 코드, 이름, 권한 목록)")
        private List<RoleDetailResp> roles;

        @Schema(description = "역할 이름 목록 (기존 호환성)", example = "[\"ADMIN\", \"MANAGER\"]")
        private List<String> userRoles;

        @Schema(description = "첫 번째 역할 이름 (기존 호환성)", example = "ADMIN")
        private String userRoleName;

        @Schema(description = "모든 권한 코드 목록", example = "[\"USER_READ\", \"USER_UPDATE\"]")
        private List<String> permissions;

        @Schema(description = "부서 ID", example = "1")
        private Long departmentId;

        @Schema(description = "부서명 (영문)", example = "Development")
        private String departmentName;

        @Schema(description = "부서명 (한글)", example = "개발팀")
        private String departmentNameKr;

        @Schema(description = "부서 레벨", example = "2")
        private Long departmentLevel;
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "승인권자 목록 조회 응답")
    public static class GetApproversResp {
        @Schema(description = "승인권자 목록")
        private List<ApproverDetailResp> approvers;

        @Schema(description = "가용 승인자 수 (최대 선택 가능 인원)", example = "2")
        private int maxAvailableCount;

        @Schema(description = "자동 승인 여부 (가용 승인자가 없는 경우 true)", example = "false")
        private boolean isAutoApproval;
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "대시보드 수정 요청")
    public static class UpdateDashboardReq {
        @Schema(description = "대시보드 설정")
        @Size(max = 20000)
        private String dashboard;
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "대시보드 수정 응답")
    public static class UpdateDashboardResp {
        @Schema(description = "사용자 ID", example = "user123")
        private String userId;

        @Schema(description = "대시보드 설정")
        private String dashboard;
    }

    /**
     * 역할 상세 정보 DTO
     */
    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "역할 상세 정보")
    public static class RoleDetailResp {
        @Schema(description = "역할 코드", example = "ADMIN")
        private String roleCode;

        @Schema(description = "역할 이름", example = "관리자")
        private String roleName;

        @Schema(description = "해당 역할의 권한 목록")
        private List<PermissionDetailResp> permissions;
    }

    /**
     * 권한 상세 정보 DTO
     */
    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "권한 상세 정보")
    public static class PermissionDetailResp {
        @Schema(description = "권한 코드", example = "USER_READ")
        private String permissionCode;

        @Schema(description = "권한 이름", example = "사용자 조회")
        private String permissionName;
    }

    // ==================== 초대 관련 DTO ====================

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "사용자 초대 요청")
    public static class InviteUserReq {
        @Schema(description = "사용자 ID", example = "hong")
        private String userId;

        @Schema(description = "사용자 이름", example = "홍길동")
        private String userName;

        @Schema(description = "사용자 이메일", example = "hong@example.com")
        private String userEmail;

        @Schema(description = "입사일", example = "2024-01-01")
        private LocalDate joinDate;

        @Schema(description = "소속 회사", example = "KAKAO")
        private String userCompanyType;

        @Schema(description = "근무시간", example = "9 ~ 18")
        private String userWorkTime;

        @Schema(description = "국가코드", example = "KR")
        private CountryCode countryCode;
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "사용자 초대 응답")
    public static class InviteUserResp {
        @Schema(description = "기존 SSO 사용자 여부", example = "false")
        private boolean alreadyExists;

        @Schema(description = "SSO 사용자 번호", example = "1")
        private Long ssoUserRowId;

        @Schema(description = "사용자 ID", example = "hong")
        private String userId;

        @Schema(description = "사용자 이름", example = "홍길동")
        private String userName;

        @Schema(description = "사용자 이메일", example = "hong@example.com")
        private String userEmail;

        @Schema(description = "메시지", example = "초대가 발송되었습니다.")
        private String message;

        @Schema(description = "초대 발송 시간")
        private java.time.LocalDateTime invitationSentAt;

        @Schema(description = "초대 만료 시간")
        private java.time.LocalDateTime invitationExpiresAt;

        @Schema(description = "초대 상태", example = "PENDING")
        private String invitationStatus;
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "초대 정보 수정 요청")
    public static class EditInvitationReq {
        @Schema(description = "사용자 이름", example = "홍길동")
        private String userName;

        @Schema(description = "사용자 이메일", example = "hong@example.com")
        private String userEmail;

        @Schema(description = "소속 회사", example = "KAKAO")
        private String userCompanyType;

        @Schema(description = "근무시간", example = "9 ~ 18")
        private String userWorkTime;

        @Schema(description = "국가코드", example = "KR")
        private CountryCode countryCode;
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "초대 정보 수정 응답")
    public static class EditInvitationResp {
        @Schema(description = "SSO 사용자 번호", example = "1")
        private Long ssoUserRowId;

        @Schema(description = "사용자 ID", example = "hong")
        private String userId;

        @Schema(description = "사용자 이름", example = "홍길동")
        private String userName;

        @Schema(description = "사용자 이메일", example = "hong@example.com")
        private String userEmail;

        @Schema(description = "소속 회사", example = "KAKAO")
        private String userCompanyType;

        @Schema(description = "근무시간", example = "9 ~ 18")
        private String userWorkTime;

        @Schema(description = "국가코드", example = "KR")
        private CountryCode countryCode;
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "비밀번호 변경 요청")
    public static class ChangePasswordReq {
        @NotBlank(message = "현재 비밀번호는 필수입니다")
        @Schema(description = "현재 비밀번호", example = "OldPass123!")
        private String currentPassword;

        @NotBlank(message = "새 비밀번호는 필수입니다")
        @Schema(description = "새 비밀번호", example = "NewPass123!")
        private String newPassword;

        @NotBlank(message = "새 비밀번호 확인은 필수입니다")
        @Schema(description = "새 비밀번호 확인", example = "NewPass123!")
        private String newPasswordConfirm;
    }

}