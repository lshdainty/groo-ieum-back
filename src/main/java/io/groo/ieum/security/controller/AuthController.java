package io.groo.ieum.security.controller;

import io.groo.core.controller.ApiResponse;
import io.groo.core.exception.ErrorCode;
import io.groo.core.exception.UnauthorizedException;
import io.groo.core.type.YNType;
import io.groo.ieum.security.controller.dto.AuthApiDto;
import io.groo.ieum.security.principal.UserPrincipal;
import io.groo.ieum.user.domain.User;
import io.groo.ieum.user.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

import io.groo.ieum.permission.domain.Role;
import org.springframework.web.bind.annotation.*;

/**
 * 인증/보안 API 컨트롤러
 * JWT 토큰으로 인증된 사용자 정보 조회를 담당합니다.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Auth", description = "인증/보안 API")
public class AuthController implements AuthApi {
    private final UserService userService;

    @Override
    public ApiResponse<AuthApiDto.LoginUserInfo> getUserInfo() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated() ||
                "anonymousUser".equals(authentication.getPrincipal())) {
            throw new UnauthorizedException(ErrorCode.UNAUTHORIZED);
        }

        Object principal = authentication.getPrincipal();

        // UserPrincipal 인터페이스로 통합 처리
        if (!(principal instanceof UserPrincipal)) {
            throw new UnauthorizedException(ErrorCode.UNAUTHORIZED);
        }

        UserPrincipal userPrincipal = (UserPrincipal) principal;

        // 최신 사용자 정보 재조회 (프로필 이미지 변경 등 세션 외 데이터 반영)
        // getUserId()는 JWT Claims에서 직접 반환하므로 DB 조회 없음
        User user = userService.findUserById(userPrincipal.getUserId());

        // 역할 상세 정보 생성
        List<AuthApiDto.RoleInfo> roleInfos = user.getRoles().stream()
                .map(role -> new AuthApiDto.RoleInfo(
                        role.getCode(),
                        role.getName(),
                        role.getPermissions().stream()
                                .map(permission -> new AuthApiDto.PermissionInfo(
                                        permission.getCode(),
                                        permission.getName()
                                ))
                                .collect(Collectors.toList())
                ))
                .collect(Collectors.toList());

        // 모든 권한 코드 목록 (중복 제거)
        List<String> allPermissions = user.getAllAuthorities();

        AuthApiDto.LoginUserInfo result = new AuthApiDto.LoginUserInfo(
                user.getId(),
                user.getName(),
                user.getEmail(),
                roleInfos,  // 역할 상세 정보
                user.getRoles().stream().map(Role::getName).collect(Collectors.toList()),  // 역할 이름 목록 (기존 호환)
                user.getRoles().isEmpty() ? null : user.getRoles().get(0).getName(),  // 첫 번째 역할 (기존 호환)
                allPermissions,  // 모든 권한 코드
                YNType.Y,
                StringUtils.hasText(user.getProfileName()) && StringUtils.hasText(user.getProfileUUID()) ?
                        userService.generateProfileUrl(user.getProfileName(), user.getProfileUUID()) : null,
                YNType.N  // 비밀번호 변경 필요 여부 (SSO에서 관리)
        );

        return ApiResponse.success(result);
    }
}
