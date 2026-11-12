package io.groo.ieum.security.service;

import io.groo.core.exception.ErrorCode;
import io.groo.core.exception.ForbiddenException;
import io.groo.core.exception.UnauthorizedException;
import io.groo.ieum.client.sso.SsoApiClient;
import io.groo.ieum.common.exception.HrErrorCode;
import io.groo.ieum.permission.domain.Role;
import io.groo.ieum.security.controller.dto.TokenExchangeDto;
import io.groo.ieum.security.jwt.JwtTokenProvider;
import io.groo.ieum.user.domain.User;
import io.groo.ieum.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 토큰 교환 서비스
 * SSO JWT를 검증하고 HR JWT를 발급합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TokenExchangeService {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserService userService;
    private final SsoApiClient ssoApiClient;

    private static final String HR_SERVICE_CODE = "hr";

    /**
     * OAuth2 인가코드(code)를 HR 토큰으로 교환 (Authorization Code + PKCE)<br>
     * SSO {@code /oauth2/token} 에서 code+code_verifier 를 SSO access token 으로 교환한 뒤,
     * 기존 {@link #exchange(String)} 로직을 재사용해 자체 HR JWT 를 발급한다.
     *
     * @param code         인가 코드
     * @param codeVerifier PKCE code_verifier
     * @param redirectUri  authorize 때와 동일한 redirect_uri
     * @return HR JWT 토큰 응답
     */
    public TokenExchangeDto.ExchangeResult exchangeCode(String code, String codeVerifier, String redirectUri) {
        log.debug("Authorization code exchange request received");
        String ssoToken = ssoApiClient.exchangeOAuthCode(code, codeVerifier, redirectUri);
        return exchange(ssoToken);
    }

    /**
     * SSO 토큰을 HR 토큰으로 교환
     *
     * @param ssoToken SSO에서 발급한 JWT 토큰
     * @return HR JWT 토큰 응답
     */
    public TokenExchangeDto.ExchangeResult exchange(String ssoToken) {
        log.debug("Token exchange request received");

        // 1. SSO JWT 검증
        if (!jwtTokenProvider.validateSsoToken(ssoToken)) {
            log.warn("Invalid SSO token");
            throw new UnauthorizedException(ErrorCode.UNAUTHORIZED, "Invalid SSO token");
        }

        // 2. SSO JWT에서 서비스 접근 목록 추출
        List<String> services = jwtTokenProvider.getServicesFromSsoToken(ssoToken);
        log.debug("Services from SSO token: {}", services);

        // 3. HR 서비스 접근 권한 확인
        if (!services.contains(HR_SERVICE_CODE)) {
            log.warn("User does not have access to HR service");
            throw new ForbiddenException(HrErrorCode.PERMISSION_DENIED, "HR service access denied");
        }

        // 4. 사용자 정보 추출
        String userId = jwtTokenProvider.getUserIdFromSsoToken(ssoToken);

        // 5. HR DB에서 사용자 조회
        User user = userService.findUserById(userId);

        // 5-1. 로그인 아이디만 믿지 않는다 — SSO 사용자 번호(userNo)가 이음 행의 연결 번호(ssoUserRowId)와
        //      같아야 그 행으로 로그인시킨다. 이음 행의 아이디가 (위조 이벤트 등으로) 다른 사람 것으로 바뀌어도
        //      그 사람의 SSO 토큰이 남의 행(예: 관리자)으로 들어오지 못한다(보안 점검 IEU-6)
        Long ssoUserNo = jwtTokenProvider.getUserNoFromSsoToken(ssoToken);
        if (!user.isLinkedToSsoUser(ssoUserNo)) {
            log.warn("토큰 교환 거부 - SSO 사용자 번호와 이음 연결 번호 불일치: userId={}", userId);
            throw new UnauthorizedException(ErrorCode.UNAUTHORIZED);
        }

        // 6. 역할/권한 조회
        List<String> roles = user.getRoles().stream()
                .map(Role::getCode)
                .collect(Collectors.toList());
        List<String> permissions = user.getAllAuthorities();

        // 7. HR JWT 발급
        //    SSO 가 임시 비밀번호 상태로 표시한 사용자는 pwdChange=Y 를 HR 토큰에 옮겨 싣는다
        boolean passwordChangeRequired = jwtTokenProvider.isPasswordChangeRequiredFromSsoToken(ssoToken);
        String hrToken = jwtTokenProvider.createHrAccessToken(user, roles, permissions, passwordChangeRequired);
        long expiresIn = jwtTokenProvider.getHrAccessExpirationSeconds();

        // 8. 응답 생성
        TokenExchangeDto.UserInfo userInfo = TokenExchangeDto.UserInfo.builder()
                .userNo(user.getSsoUserRowId())
                .userId(user.getId())
                .userName(user.getName())
                .userEmail(user.getEmail())
                .roles(roles)
                .permissions(permissions)
                .build();

        log.info("Token exchanged successfully for user: {}", userId);

        return TokenExchangeDto.ExchangeResult.builder()
                .accessToken(hrToken)
                .expiresIn(expiresIn)
                .user(userInfo)
                .build();
    }
}
