package io.groo.ieum.user.service;

import io.groo.core.type.YNType;
import io.groo.ieum.user.domain.User;
import io.groo.ieum.user.service.dto.UserServiceDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

/**
 * 사용자 관리 서비스 인터페이스
 */
public interface UserService {

    /**
     * 사용자 생성
     *
     * @param data 사용자 생성 정보
     * @return 생성된 사용자 ID
     */
    String joinUser(UserServiceDto data);

    /**
     * 사용자 조회
     *
     * @param userId 사용자 ID
     * @return 사용자 정보 DTO
     */
    UserServiceDto searchUser(String userId);

    /**
     * 전체 사용자 목록 조회
     *
     * @return 사용자 목록
     */
    List<UserServiceDto> searchUsers();

    /**
     * 사용자 정보 수정
     *
     * 대상이 로그인 유저가 아니거나 소속이 현재와 달라지는 요청은 canManage 일 때만,
     * 역할(roleNames)이 현재와 달라지는 요청은 canManageRoles 일 때만 허용한다.
     * 아니면 PERMISSION_DENIED 로 거절하고 아무것도 바꾸지 않는다.
     * 역할 부여는 사용자 관리 권한과 분리한다 — 사용자 관리만 가진 역할(예: 인사 담당)이
     * 자신이나 남에게 관리자 역할을 붙이지 못하게(보안 점검 IEU-7)
     *
     * @param data 수정할 사용자 정보
     * @param loginUserId 로그인한 사용자 ID
     * @param canManage 사용자 관리 권한(USER:MANAGE 또는 ROLE:MANAGE) 여부
     * @param canManageRoles 역할 관리 권한(ROLE:MANAGE) 여부
     */
    void editUser(UserServiceDto data, String loginUserId, boolean canManage, boolean canManageRoles);

    /**
     * 사용자 삭제 (소프트 삭제)
     *
     * @param userId 삭제할 사용자 ID
     */
    void deleteUser(String userId);

    /**
     * 프로필 이미지를 임시 폴더에 저장
     *
     * @param file 프로필 이미지 파일
     * @return 저장된 파일 정보 (URL, UUID)
     */
    UserServiceDto saveProfileImgInTempFolder(MultipartFile file);

    /**
     * 사용자 존재 여부 확인 및 조회
     *
     * @param userId 사용자 ID
     * @return User 엔티티
     * @throws io.groo.core.exception.EntityNotFoundException 사용자가 존재하지 않는 경우
     */
    User checkUserExist(String userId);

    /**
     * 사용자 조회 (역할 및 권한 정보 포함)
     * 로그인 체크 시 최신 사용자 정보를 조회하기 위한 메서드
     *
     * @param userId 사용자 ID
     * @return User 엔티티 (역할 및 권한 정보 포함)
     * @throws io.groo.core.exception.EntityNotFoundException 사용자가 존재하지 않는 경우
     */
    User findUserById(String userId);

    /**
     * 프로필 URL에서 물리적 파일명을 추출
     *
     * @param profileUrl 프로필 URL
     * @return 물리적 파일명
     */
    String extractPhysicalFileNameFromUrl(String profileUrl);

    /**
     * 원본 파일명과 UUID로 프로필 URL 생성
     *
     * @param originalFilename 원본 파일명
     * @param uuid UUID
     * @return 프로필 URL
     */
    String generateProfileUrl(String originalFilename, String uuid);

    /**
     * 임시 폴더에 저장된 프로필 이미지를 관리용 폴더로 복사
     *
     * @param data 프로필 정보
     * @return 복사된 프로필 정보
     */
    UserServiceDto copyTempProfileToOrigin(UserServiceDto data);

    /**
     * 아이디 중복 체크
     *
     * @param userId 확인할 사용자 ID
     * @return 중복 여부 (true: 중복, false: 사용 가능)
     */
    boolean checkUserIdDuplicate(String userId);

    /**
     * 사용자의 메인 부서 존재 여부 확인
     *
     * @param userId 사용자 ID
     * @return 메인 부서 존재 여부 (Y/N)
     */
    YNType checkUserHasMainDepartment(String userId);

    /**
     * 대시보드 데이터 수정
     *
     * 본인 대시보드만 수정할 수 있다 (관리자도 예외 없음).
     *
     * @param loginUserId 로그인한 사용자 ID
     * @param userId 대상 사용자 ID
     * @param dashboard 대시보드 JSON 데이터
     * @return 수정된 사용자 정보
     */
    UserServiceDto updateDashboard(String loginUserId, String userId, String dashboard);

    /**
     * 특정 유저의 승인권자 목록 조회
     * 사용자의 메인 부서 기준으로 상위 부서장들을 승인권자로 반환합니다.
     *
     * @param userId 유저 ID
     * @return 승인권자 목록 (상위 부서장들)
     */
    List<UserServiceDto> getUserApprovers(String userId);

    /**
     * Spring Security용: 사용자 ID로 User 조회 (역할 및 권한 포함)
     * 2단계 쿼리로 MultipleBagFetchException 방지
     *
     * @param userId 사용자 ID
     * @return User Optional
     */
    Optional<User> getUserWithRolesById(String userId);

    /**
     * 전체 사용자 엔티티 목록 조회
     * N+1 문제 방지를 위해 DTO 변환 없이 User 엔티티 직접 반환
     *
     * @return 모든 User 엔티티 리스트
     */
    List<User> findAllUsers();

    /**
     * 사용자 초대<br>
     * SSO에 사용자 초대 요청 후 HR에 사용자 정보 저장
     *
     * @param data 초대할 사용자 정보
     * @return 초대 결과 (SSO 응답 + HR 저장 정보)
     * @throws io.groo.core.exception.ExternalServiceException SSO 서비스 연동 실패 시
     */
    UserServiceDto.InviteResult inviteUser(UserServiceDto data);

    /**
     * 초대 대기 사용자 정보 수정<br>
     * PENDING 상태의 사용자 정보만 수정 가능
     *
     * @param userId 사용자 ID
     * @param data 수정할 정보
     * @return 수정된 사용자 정보
     * @throws io.groo.core.exception.EntityNotFoundException 사용자를 찾을 수 없는 경우
     * @throws io.groo.core.exception.InvalidValueException PENDING 상태가 아닌 경우
     */
    UserServiceDto editInvitation(String userId, UserServiceDto data);

    /**
     * 초대 재전송<br>
     * SSO에 초대 재전송 요청
     *
     * @param userId 사용자 ID
     * @throws io.groo.core.exception.EntityNotFoundException 사용자를 찾을 수 없는 경우
     * @throws io.groo.core.exception.InvalidValueException PENDING 상태가 아닌 경우
     * @throws io.groo.core.exception.ExternalServiceException SSO 서비스 연동 실패 시
     */
    void resendInvitation(String userId);

    /**
     * 사용자 본인 비밀번호 변경<br>
     * SSO {@code /auth/password/change} 로 위임(서비스 토큰 client_credentials 인증).
     *
     * @param userId          대상 사용자 ID (HR User.id = SSO user_id)
     * @param currentPassword 현재 비밀번호
     * @param newPassword     새 비밀번호
     * @param confirmPassword 새 비밀번호 확인
     * @throws io.groo.core.exception.InvalidValueException    현재 비밀번호 불일치 등 검증 실패 시
     * @throws io.groo.core.exception.ExternalServiceException SSO 서비스 연동 실패 시
     */
    void changePassword(String userId, String currentPassword, String newPassword, String confirmPassword);

    /**
     * 관리자 비밀번호 리셋<br>
     * SSO {@code /auth/password/reset-by-service} 로 위임. SSO 가 임시 비밀번호 자동 생성·발송.
     *
     * @param targetUserId 리셋 대상 사용자 ID (HR User.id = SSO user_id)
     * @throws io.groo.core.exception.ExternalServiceException SSO 서비스 연동 실패 시
     */
    void resetPassword(String targetUserId);
}
