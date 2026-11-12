package io.groo.ieum.security.service;

import io.groo.core.exception.EntityNotFoundException;
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
     * 토큰의 SSO 사용자로 들어갈 이음 행을 찾는다.
     *
     * <p>로그인 아이디만 믿지 않는다 — SSO 사용자 번호(userNo)가 이음 행의 연결 번호(ssoUserRowId)와 같아야 그 행으로
     * 로그인시킨다. 이음 행의 아이디가 (위조 이벤트 등으로) 다른 사람 것으로 바뀌어도 그 사람의 SSO 토큰이 남의 행
     * (예: 관리자)으로 들어오지 못한다(보안 점검 IEU-6).
     *
     * <p>못 찾으면 한 번 다시 묶어 본다(SSO-2 운영 위험) — 초대 수락 알림(USER_LINKED)을 놓치면 이음 행은 지워진
     * 대기 계정 번호에 남는다. SSO 에 "이 계정에 합쳐진 대기 번호" 를 물어(서비스 토큰 + 이 계정의 이음 접근 필요)
     * 그 번호의 행만 이 계정에 다시 묶는다. 아무 행이나 이메일로 고르지 않는다.
     */
    private User findLinkedUser(String ssoToken, String userId, Long ssoUserNo) {
        User user = null;
        try {
            user = userService.findUserById(userId);
        } catch (EntityNotFoundException e) {
            // 아래에서 다시 묶어 본다
        }
        if (user != null && user.isLinkedToSsoUser(ssoUserNo)) {
            return user;
        }
        List<Long> previous = deletedPendingOnly(ssoApiClient.getLinkedFrom(ssoUserNo));
        if (!previous.isEmpty() && userService.relinkMergedSsoUser(ssoUserNo, previous, userId,
                jwtTokenProvider.getNameFromSsoToken(ssoToken), jwtTokenProvider.getEmailFromSsoToken(ssoToken))) {
            User relinked = userService.findUserById(userId);
            if (relinked.isLinkedToSsoUser(ssoUserNo)) {
                return relinked;
            }
        }
        if (user == null) {
            throw new EntityNotFoundException(HrErrorCode.USER_NOT_FOUND);
        }
        log.warn("토큰 교환 거부 - SSO 사용자 번호와 이음 연결 번호 불일치: userId={}", userId);
        throw new UnauthorizedException(ErrorCode.UNAUTHORIZED);
    }

    /**
     * 다시 묶어도 되는 번호만 — 초대 대기 계정이었던(합쳐지며 SSO 에서 지워진) 번호(보안 점검 SSO-13).
     *
     * <p>이음 행에는 초대 상태가 없다. 초대 수락은 SSO 의 대기 계정을 지우므로, "그 번호의 SSO 계정이 더는 없다" 가
     * 대기 행이었다는 증거다. SSO 에 아직 있는 계정(관리자 등 살아 있는 계정)의 행은 합침 기록에 있더라도 묶지 않는다 —
     * 기록이 위조돼도 남의 행을 가져가지 못하게. 확인하지 못하면(SSO 오류) 묶지 않는다.
     */
    private List<Long> deletedPendingOnly(List<Long> previous) {
        if (previous == null || previous.isEmpty()) {
            return List.of();
        }
        try {
            java.util.Set<Long> stillThere = ssoApiClient.getInvitationStatus(previous).stream()
                    .map(io.groo.ieum.client.sso.dto.SsoInvitationStatusResponse::getUserNo)
                    .collect(Collectors.toSet());
            if (!stillThere.isEmpty()) {
                log.warn("재연결 후보 중 SSO 에 살아 있는 계정은 뺀다: {}", stillThere);
            }
            return previous.stream().filter(no -> !stillThere.contains(no)).toList();
        } catch (Exception e) {
            log.warn("재연결 후보 확인 실패 — 다시 묶지 않는다: {}", e.getMessage());
            return List.of();
        }
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
        Long ssoUserNo = jwtTokenProvider.getUserNoFromSsoToken(ssoToken);
        User user = findLinkedUser(ssoToken, userId, ssoUserNo);

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
