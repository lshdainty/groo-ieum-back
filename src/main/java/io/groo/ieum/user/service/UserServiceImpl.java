package io.groo.ieum.user.service;

import io.groo.core.exception.EntityNotFoundException;
import io.groo.core.exception.ForbiddenException;
import io.groo.core.exception.ErrorCode;
import io.groo.core.exception.InvalidValueException;
import io.groo.ieum.client.sso.SsoApiClient;
import io.groo.ieum.client.sso.dto.SsoInvitationStatusResponse;
import io.groo.ieum.client.sso.dto.SsoInviteRequest;
import io.groo.ieum.client.sso.dto.SsoInviteResponse;
import io.groo.ieum.common.exception.HrErrorCode;
import io.groo.core.type.YNType;
import io.groo.core.util.MessageResolver;
import io.groo.core.util.FileUploadValidator;
import io.groo.core.util.FileUtils;
import io.groo.ieum.department.repository.DepartmentRepository;
import io.groo.ieum.permission.domain.Role;
import io.groo.ieum.permission.repository.RoleRepository;
import io.groo.ieum.user.domain.User;
import io.groo.ieum.user.repository.UserRepository;
import io.groo.ieum.user.service.dto.UserServiceDto;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {
    private final MessageResolver messageResolver;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final DepartmentRepository departmentRepository;
    private final EntityManager em;
    private final SsoApiClient ssoApiClient;

    @Value("${sso.client-code}")
    private String ssoClientCode;

    private static final Set<String> ALLOWED_PROFILE_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "webp"
    );
    private static final long MAX_PROFILE_SIZE = 5 * 1024 * 1024L; // 5MB

    @Value("${file.root-path}")
    private String fileRootPath;

    @Value("${file.web-url-prefix}")
    private String webUrlPrefix;

    @Value("${file.temp.path.profile}")
    private String tempPath;

    @Value("${file.origin.path.profile}")
    private String originPath;

    @Override
    @Transactional
    public String joinUser(UserServiceDto data) {
        log.debug("사용자 생성 시작: ssoUserRowId={}, id={}, name={}, email={}",
                data.getSsoUserRowId(), data.getId(), data.getName(), data.getEmail());

        if (data.getSsoUserRowId() == null) {
            throw new IllegalArgumentException("ssoUserRowId는 필수입니다. SSO에서 사용자 생성 후 진행해주세요.");
        }

        UserServiceDto profileDto = UserServiceDto.builder().build();
        if (StringUtils.hasText(data.getProfileUUID()) && StringUtils.hasText(data.getProfileUrl())) {
            log.debug("프로필 이미지 복사 진행: uuid={}", data.getProfileUUID());
            profileDto = copyTempProfileToOrigin(data);
        }

        User user = User.createUser(
                data.getSsoUserRowId(),
                data.getId(),
                data.getName(),
                data.getEmail(),
                data.getBirth(),
                data.getCompany(),
                data.getWorkTime(),
                data.getJoinDate(),
                data.getLunarYN(),
                profileDto.getProfileName(),
                profileDto.getProfileUUID(),
                data.getCountryCode()
        );

        userRepository.save(user);
        log.info("사용자 생성 완료: ssoUserRowId={}, id={}", user.getSsoUserRowId(), user.getId());
        return user.getId();
    }

    @Override
    public UserServiceDto searchUser(String userId) {
        log.debug("사용자 조회: userId={}", userId);
        User user = findUserById(userId);

        String mainDepartmentNameKR = user.getUserDepartments().stream()
                .filter(ud -> YNType.isY(ud.getMainYN()) && YNType.isN(ud.getIsDeleted()))
                .findFirst()
                .map(ud -> ud.getDepartment().getNameKR())
                .orElse(null);

        List<UserServiceDto.RoleDetailDto> roleDetails = user.getRoles().stream()
                .map(role -> UserServiceDto.RoleDetailDto.builder()
                        .roleCode(role.getCode())
                        .roleName(role.getName())
                        .permissions(role.getPermissions().stream()
                                .map(permission -> UserServiceDto.PermissionDetailDto.builder()
                                        .permissionCode(permission.getCode())
                                        .permissionName(permission.getName())
                                        .build())
                                .collect(Collectors.toList()))
                        .build())
                .collect(Collectors.toList());

        // SSO에서 초대 상태 조회
        SsoInvitationStatusResponse status = null;
        if (user.getSsoUserRowId() != null) {
            try {
                List<SsoInvitationStatusResponse> statusList = ssoApiClient.getInvitationStatus(List.of(user.getSsoUserRowId()));
                status = statusList.isEmpty() ? null : statusList.get(0);
            } catch (Exception e) {
                log.warn("SSO 초대 상태 조회 실패, 초대 상태 없이 진행: {}", e.getMessage());
            }
        }

        return UserServiceDto.builder()
                .ssoUserRowId(user.getSsoUserRowId())
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .roles(roleDetails)
                .roleNames(user.getRoles().stream().map(Role::getName).collect(Collectors.toList()))
                .allPermissions(user.getAllAuthorities())
                .birth(user.getBirth())
                .workTime(user.getWorkTime())
                .joinDate(user.getJoinDate())
                .company(user.getCompany())
                .lunarYN(user.getLunarYN())
                .countryCode(user.getCountryCode())
                .profileName(user.getProfileName())
                .profileUrl(StringUtils.hasText(user.getProfileName()) && StringUtils.hasText(user.getProfileUUID()) ?
                        generateProfileUrl(user.getProfileName(), user.getProfileUUID()) : null)
                .mainDepartmentNameKR(mainDepartmentNameKR)
                .dashboard(user.getDashboard())
                .invitationSentAt(status != null ? status.getInvitationSentAt() : null)
                .invitationExpiresAt(status != null ? status.getInvitationExpiresAt() : null)
                .invitationStatus(status != null ? status.getInvitationStatus() : null)
                .registeredAt(status != null ? status.getRegisteredAt() : null)
                .build();
    }

    @Override
    public List<UserServiceDto> searchUsers() {
        log.debug("전체 사용자 목록 조회 시작");
        List<User> users = userRepository.findUsersWithRolesAndPermissions();
        log.debug("전체 사용자 목록 조회 완료: count={}", users.size());

        // SSO에서 초대 상태 조회
        List<Long> ssoUserRowIds = users.stream()
                .map(User::getSsoUserRowId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        Map<Long, SsoInvitationStatusResponse> invitationStatusMap = Map.of();
        if (!ssoUserRowIds.isEmpty()) {
            try {
                invitationStatusMap = ssoApiClient.getInvitationStatus(ssoUserRowIds).stream()
                        .collect(Collectors.toMap(
                                SsoInvitationStatusResponse::getUserNo,
                                Function.identity(),
                                (existing, replacement) -> existing
                        ));
                log.debug("SSO 초대 상태 조회 완료: count={}", invitationStatusMap.size());
            } catch (Exception e) {
                log.warn("SSO 초대 상태 조회 실패, 초대 상태 없이 진행: {}", e.getMessage());
            }
        }

        Map<Long, SsoInvitationStatusResponse> finalInvitationStatusMap = invitationStatusMap;
        return users.stream()
                .map(user -> {
                    String mainDepartmentNameKR = user.getUserDepartments().stream()
                            .filter(ud -> YNType.isY(ud.getMainYN()) && YNType.isN(ud.getIsDeleted()))
                            .findFirst()
                            .map(ud -> ud.getDepartment().getNameKR())
                            .orElse(null);

                    List<UserServiceDto.RoleDetailDto> roleDetails = user.getRoles().stream()
                            .map(role -> UserServiceDto.RoleDetailDto.builder()
                                    .roleCode(role.getCode())
                                    .roleName(role.getName())
                                    .permissions(role.getPermissions().stream()
                                            .map(permission -> UserServiceDto.PermissionDetailDto.builder()
                                                    .permissionCode(permission.getCode())
                                                    .permissionName(permission.getName())
                                                    .build())
                                            .collect(Collectors.toList()))
                                    .build())
                            .collect(Collectors.toList());

                    // SSO 초대 상태 매핑
                    SsoInvitationStatusResponse status = user.getSsoUserRowId() != null
                            ? finalInvitationStatusMap.get(user.getSsoUserRowId())
                            : null;

                    return UserServiceDto.builder()
                            .ssoUserRowId(user.getSsoUserRowId())
                            .id(user.getId())
                            .name(user.getName())
                            .email(user.getEmail())
                            .roles(roleDetails)
                            .roleNames(user.getRoles().stream().map(Role::getName).collect(Collectors.toList()))
                            .allPermissions(user.getAllAuthorities())
                            .birth(user.getBirth())
                            .workTime(user.getWorkTime())
                            .joinDate(user.getJoinDate())
                            .company(user.getCompany())
                            .lunarYN(user.getLunarYN())
                            .countryCode(user.getCountryCode())
                            .profileName(user.getProfileName())
                            .profileUrl(StringUtils.hasText(user.getProfileName()) && StringUtils.hasText(user.getProfileUUID()) ?
                                    generateProfileUrl(user.getProfileName(), user.getProfileUUID()) : null)
                            .mainDepartmentNameKR(mainDepartmentNameKR)
                            .dashboard(user.getDashboard())
                            .invitationSentAt(status != null ? status.getInvitationSentAt() : null)
                            .invitationExpiresAt(status != null ? status.getInvitationExpiresAt() : null)
                            .invitationStatus(status != null ? status.getInvitationStatus() : null)
                            .registeredAt(status != null ? status.getRegisteredAt() : null)
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void editUser(UserServiceDto data, String loginUserId, boolean canManage) {
        log.debug("사용자 수정 시작: id={}", data.getId());
        User user = checkUserExist(data.getId());

        if (!canManage) {
            if (!user.getId().equals(loginUserId)) {
                log.warn("사용자 수정 접근 거부 - 로그인 유저: {}, 대상 유저: {}", loginUserId, data.getId());
                throw new ForbiddenException(HrErrorCode.PERMISSION_DENIED);
            }
            if (data.getRoleNames() != null) {
                Set<String> current = user.getRoles().stream().map(Role::getCode).collect(Collectors.toSet());
                if (!current.equals(new HashSet<>(data.getRoleNames()))) {
                    log.warn("역할 변경 접근 거부 - 로그인 유저: {}, 대상 유저: {}", loginUserId, data.getId());
                    throw new ForbiddenException(HrErrorCode.PERMISSION_DENIED);
                }
            }
        }

        UserServiceDto profileDto = UserServiceDto.builder().build();
        if (StringUtils.hasText(data.getProfileUUID()) && !data.getProfileUUID().equals(user.getProfileUUID())) {
            log.debug("프로필 이미지 변경: uuid={}", data.getProfileUUID());
            profileDto = copyTempProfileToOrigin(data);
        }

        List<Role> roles = null;
        if (data.getRoleNames() != null) {
            roles = data.getRoleNames().stream()
                    .map(code -> roleRepository.findByCode(code).orElseThrow(() -> new IllegalArgumentException("Role not found: " + code)))
                    .collect(Collectors.toList());
        }

        user.updateUser(null, data.getName(), data.getEmail(), roles, data.getBirth(),
                data.getCompany(),
                data.getWorkTime(),
                data.getLunarYN(),
                profileDto.getProfileName(),
                profileDto.getProfileUUID(),
                data.getDashboard(),
                data.getCountryCode()
        );
        log.info("사용자 수정 완료: id={}", data.getId());
    }

    @Override
    @Transactional
    public void deleteUser(String userId) {
        log.debug("사용자 삭제 시작: userId={}", userId);
        User user = checkUserExist(userId);
        user.deleteUser();
        log.info("사용자 삭제 완료: userId={}", userId);
    }

    @Override
    public UserServiceDto saveProfileImgInTempFolder(MultipartFile file) {
        log.debug("프로필 이미지 임시 저장 시작: filename={}", file.getOriginalFilename());

        FileUploadValidator.validate(file, ALLOWED_PROFILE_EXTENSIONS, MAX_PROFILE_SIZE);

        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());

        String uuid = UUID.randomUUID().toString();

        String physicalFilename = FileUtils.generatePhysicalFilename(originalFilename, uuid);

        FileUtils.save(file, tempPath, physicalFilename, messageResolver);

        String absolutePath = Paths.get(tempPath, physicalFilename).toString();

        log.debug("프로필 이미지 임시 저장 완료: uuid={}, path={}", uuid, absolutePath);
        return UserServiceDto.builder()
                .profileUrl(absolutePath.replace(fileRootPath, webUrlPrefix))
                .profileUUID(uuid)
                .build();
    }

    @Override
    public User checkUserExist(String userId) {
        Optional<User> findUser = userRepository.findById(userId);
        if ((findUser.isEmpty()) || YNType.isY(findUser.get().getIsDeleted())) {
            log.warn("사용자 조회 실패 - 존재하지 않거나 삭제된 사용자: userId={}", userId);
            throw new EntityNotFoundException(HrErrorCode.USER_NOT_FOUND);
        }
        return findUser.get();
    }

    @Override
    public User findUserById(String userId) {
        Optional<User> findUser = userRepository.findByIdWithRolesAndPermissions(userId);
        if ((findUser.isEmpty()) || YNType.isY(findUser.get().getIsDeleted())) {
            log.warn("사용자 조회 실패 - 존재하지 않거나 삭제된 사용자: userId={}", userId);
            throw new EntityNotFoundException(HrErrorCode.USER_NOT_FOUND);
        }
        return findUser.get();
    }

    @Override
    public String extractPhysicalFileNameFromUrl(String profileUrl) {
        if (!StringUtils.hasText(profileUrl)) {
            return null;
        }

        String relativePath = profileUrl.replace(webUrlPrefix, "");
        Path path = Paths.get(relativePath);
        return path.getFileName().toString();
    }

    @Override
    public String generateProfileUrl(String originalFilename, String uuid) {
        String physicalFilename = FileUtils.generatePhysicalFilename(originalFilename, uuid);
        if (physicalFilename == null) {
            return null;
        }

        String absolutePath = Paths.get(originPath, physicalFilename).toString();
        return absolutePath.replace(fileRootPath, webUrlPrefix);
    }

    @Override
    public UserServiceDto copyTempProfileToOrigin(UserServiceDto data) {
        String profileName = null;
        String profileUUID = null;

        String physicalFileName = extractPhysicalFileNameFromUrl(data.getProfileUrl());
        if (physicalFileName != null) {
            String tempFilePath = Paths.get(tempPath, physicalFileName).toString();
            String originFilePath = Paths.get(originPath, physicalFileName).toString();

            if (FileUtils.copy(tempFilePath, originFilePath, messageResolver)) {
                profileName = FileUtils.extractOriginalFilename(physicalFileName, null);
                profileUUID = data.getProfileUUID();
            }
        }

        return UserServiceDto.builder()
                .profileName(profileName)
                .profileUUID(profileUUID)
                .build();
    }

    @Override
    public boolean checkUserIdDuplicate(String userId) {
        Optional<User> existingUser = userRepository.findById(userId);
        return existingUser.isPresent();
    }

    @Override
    public YNType checkUserHasMainDepartment(String userId) {
        checkUserExist(userId);

        boolean hasMainDepartment = departmentRepository.hasMainDepartment(userId);

        return hasMainDepartment ? YNType.Y : YNType.N;
    }

    @Override
    @Transactional
    public UserServiceDto updateDashboard(String loginUserId, String userId, String dashboard) {
        if (!userId.equals(loginUserId)) {
            log.warn("대시보드 수정 접근 거부 - 로그인 유저: {}, 대상 유저: {}", loginUserId, userId);
            throw new ForbiddenException(HrErrorCode.PERMISSION_DENIED);
        }
        log.debug("대시보드 수정 시작: userId={}", userId);
        User user = checkUserExist(userId);
        user.updateDashboard(dashboard);
        log.info("대시보드 수정 완료: userId={}", userId);

        return UserServiceDto.builder()
                .id(user.getId())
                .dashboard(user.getDashboard())
                .build();
    }

    @Override
    public List<UserServiceDto> getUserApprovers(String userId) {
        log.debug("승인권자 목록 조회 시작: userId={}", userId);
        checkUserExist(userId);

        List<io.groo.ieum.department.domain.Department> approverDepartments =
                departmentRepository.findApproversByUserId(userId);
        log.debug("승인권자 목록 조회 완료: userId={}, count={}", userId, approverDepartments.size());

        return approverDepartments.stream()
                .map(dept -> {
                    if (dept.getHeadUser() == null) {
                        return null;
                    }
                    User approver = userRepository.findByIdWithRolesAndPermissions(dept.getHeadUser().getId())
                            .orElse(null);

                    if (approver == null || YNType.isY(approver.getIsDeleted())) {
                        return null;
                    }

                    List<UserServiceDto.RoleDetailDto> roleDetails = approver.getRoles().stream()
                            .map(role -> UserServiceDto.RoleDetailDto.builder()
                                    .roleCode(role.getCode())
                                    .roleName(role.getName())
                                    .permissions(role.getPermissions().stream()
                                            .map(permission -> UserServiceDto.PermissionDetailDto.builder()
                                                    .permissionCode(permission.getCode())
                                                    .permissionName(permission.getName())
                                                    .build())
                                            .collect(Collectors.toList()))
                                    .build())
                            .collect(Collectors.toList());

                    return UserServiceDto.builder()
                            .id(approver.getId())
                            .name(approver.getName())
                            .email(approver.getEmail())
                            .roles(roleDetails)
                            .roleNames(approver.getRoles().stream().map(Role::getName).collect(Collectors.toList()))
                            .allPermissions(approver.getAllAuthorities())
                            .departmentId(dept.getRowId())
                            .departmentName(dept.getName())
                            .departmentNameKR(dept.getNameKR())
                            .departmentLevel(dept.getLevel())
                            .build();
                })
                .filter(dto -> dto != null)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<User> getUserWithRolesById(String userId) {
        List<User> result = em.createQuery(
                "select distinct u from User u " +
                "left join fetch u.userRoles ur " +
                "left join fetch ur.role r " +
                "where u.id = :userId and u.isDeleted = :isDeleted",
                User.class)
                .setParameter("userId", userId)
                .setParameter("isDeleted", YNType.N)
                .getResultList();

        if (result.isEmpty()) {
            return Optional.empty();
        }

        User user = result.get(0);

        if (!user.getUserRoles().isEmpty()) {
            em.createQuery(
                "select distinct r from Role r " +
                "left join fetch r.rolePermissions rp " +
                "left join fetch rp.permission p " +
                "where r in :roles",
                Role.class)
                .setParameter("roles", user.getRoles())
                .getResultList();
        }

        return Optional.of(user);
    }

    @Override
    public List<User> findAllUsers() {
        log.debug("전체 사용자 엔티티 목록 조회 시작");
        List<User> users = userRepository.findUsersWithRolesAndPermissions();
        log.debug("전체 사용자 엔티티 목록 조회 완료: count={}", users.size());
        return users;
    }

    @Override
    @Transactional
    public UserServiceDto.InviteResult inviteUser(UserServiceDto data) {
        log.debug("사용자 초대 시작: userId={}, email={}", data.getId(), data.getEmail());

        // 1. SSO API 호출 (동기적)
        SsoInviteResponse ssoResponse = ssoApiClient.inviteUser(
                SsoInviteRequest.builder()
                        .clientCode(ssoClientCode)
                        .userId(data.getId())
                        .name(data.getName())
                        .email(data.getEmail())
                        .build()
        );

        // 2. 기존 SSO 사용자인 경우 (자동 연결)
        if (ssoResponse.isAlreadyExists()) {
            log.info("기존 SSO 사용자 연결: ssoUserRowId={}, userId={}",
                    ssoResponse.getUserNo(), ssoResponse.getUserId());

            // HR에 이미 존재하는지 확인
            Optional<User> existingUser = userRepository.findBySsoUserRowId(ssoResponse.getUserNo());
            if (existingUser.isPresent()) {
                User user = existingUser.get();
                return UserServiceDto.InviteResult.builder()
                        .alreadyExists(true)
                        .ssoUserRowId(user.getSsoUserRowId())
                        .userId(user.getId())
                        .name(user.getName())
                        .email(user.getEmail())
                        .message(ssoResponse.getMessage())
                        .invitationSentAt(ssoResponse.getInvitationSentAt())
                        .invitationExpiresAt(ssoResponse.getInvitationExpiresAt())
                        .invitationStatus(ssoResponse.getInvitationStatus())
                        .build();
            }

            // HR에 없으면 새로 생성
            User user = User.createUser(
                    ssoResponse.getUserNo(),
                    ssoResponse.getUserId(),
                    ssoResponse.getName(),
                    ssoResponse.getEmail(),
                    data.getBirth(),
                    data.getCompany(),
                    data.getWorkTime(),
                    data.getJoinDate(),
                    data.getLunarYN() != null ? data.getLunarYN() : YNType.N,
                    data.getProfileName(),
                    data.getProfileUUID(),
                    data.getCountryCode()
            );
            userRepository.save(user);

            return UserServiceDto.InviteResult.builder()
                    .alreadyExists(true)
                    .ssoUserRowId(user.getSsoUserRowId())
                    .userId(user.getId())
                    .name(user.getName())
                    .email(user.getEmail())
                    .message(ssoResponse.getMessage())
                    .invitationSentAt(ssoResponse.getInvitationSentAt())
                    .invitationExpiresAt(ssoResponse.getInvitationExpiresAt())
                    .invitationStatus(ssoResponse.getInvitationStatus())
                    .build();
        }

        // 3. 신규 사용자 - HR DB에 저장
        User user = User.createUser(
                ssoResponse.getUserNo(),
                data.getId(),
                data.getName(),
                data.getEmail(),
                data.getBirth(),
                data.getCompany(),
                data.getWorkTime(),
                data.getJoinDate(),
                data.getLunarYN() != null ? data.getLunarYN() : YNType.N,
                data.getProfileName(),
                data.getProfileUUID(),
                data.getCountryCode()
        );
        userRepository.save(user);

        log.info("사용자 초대 완료: ssoUserRowId={}, userId={}", ssoResponse.getUserNo(), data.getId());

        return UserServiceDto.InviteResult.builder()
                .alreadyExists(false)
                .ssoUserRowId(ssoResponse.getUserNo())
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .message(ssoResponse.getMessage())
                .invitationSentAt(ssoResponse.getInvitationSentAt())
                .invitationExpiresAt(ssoResponse.getInvitationExpiresAt())
                .invitationStatus(ssoResponse.getInvitationStatus())
                .build();
    }

    @Override
    @Transactional
    public UserServiceDto editInvitation(String userId, UserServiceDto data) {
        log.debug("사용자 정보 수정: userId={}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException(HrErrorCode.USER_NOT_FOUND));

        // 초대 수정은 아직 수락하지 않은(PENDING) 초대에만 허용한다 — 가입을 마친 사용자는 본인 정보 수정 경로를 쓴다
        if (!isInvitationPending(user)) {
            log.warn("초대 수정 거부 - 대기 중인 초대가 아님: userId={}", userId);
            throw new InvalidValueException(ErrorCode.INVALID_PARAMETER);
        }

        // 정보 수정 (비밀번호 제외 필드만)
        user.updateUser(
                null, // userId - SSO 관리 필드이므로 HR에서 변경하지 않음
                data.getName(),
                data.getEmail(),
                null, // roles
                data.getBirth(),
                data.getCompany(),
                data.getWorkTime(),
                data.getLunarYN(),
                data.getProfileName(),
                data.getProfileUUID(),
                null, // dashboard
                data.getCountryCode()
        );

        log.info("사용자 정보 수정 완료: userId={}", userId);

        return UserServiceDto.builder()
                .ssoUserRowId(user.getSsoUserRowId())
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .company(user.getCompany())
                .workTime(user.getWorkTime())
                .joinDate(user.getJoinDate())
                .countryCode(user.getCountryCode())
                .build();
    }

    private boolean isInvitationPending(User user) {
        if (user.getSsoUserRowId() == null) {
            return false;
        }
        List<SsoInvitationStatusResponse> statusList = ssoApiClient.getInvitationStatus(List.of(user.getSsoUserRowId()));
        return statusList != null && !statusList.isEmpty()
                && "PENDING".equals(statusList.get(0).getInvitationStatus());
    }

    @Override
    @Transactional
    public void resendInvitation(String userId) {
        log.debug("초대 재전송: userId={}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException(HrErrorCode.USER_NOT_FOUND));

        // SSO API 호출 (초대 상태 확인은 SSO에서 처리)
        ssoApiClient.resendInvitation(userId);

        log.info("초대 재전송 완료: userId={}", userId);
    }

    @Override
    public void changePassword(String userId, String currentPassword, String newPassword, String confirmPassword) {
        log.debug("비밀번호 변경 요청: userId={}", userId);

        // 비밀번호 검증·변경은 SSO 가 최종 처리(서비스 토큰 client_credentials 위임)
        ssoApiClient.changePassword(userId, currentPassword, newPassword, confirmPassword);

        log.info("비밀번호 변경 완료: userId={}", userId);
    }

    @Override
    public void resetPassword(String targetUserId) {
        log.debug("비밀번호 리셋 요청: userId={}", targetUserId);

        // 임시 비밀번호 자동 생성·이메일 발송·강제 변경은 SSO 가 처리(평문 미반환)
        ssoApiClient.resetPassword(targetUserId);

        log.info("비밀번호 리셋 완료: userId={}", targetUserId);
    }
}
