package io.groo.ieum.security.controller;

import io.groo.core.controller.ApiResponse;
import io.groo.ieum.common.config.properties.JwtProperties;
import io.groo.ieum.security.controller.dto.TokenExchangeDto;
import io.groo.ieum.security.service.ServiceLogoutStore;
import io.groo.ieum.security.service.TokenExchangeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 토큰 교환 API 컨트롤러
 * SSO JWT를 HR JWT로 교환하는 엔드포인트를 제공합니다.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Token Exchange", description = "토큰 교환 API")
public class TokenExchangeController {

    private final TokenExchangeService tokenExchangeService;
    private final JwtProperties jwtProperties;
    private final io.groo.ieum.security.jwt.JwtTokenProvider jwtTokenProvider;
    private final io.groo.ieum.security.service.TokenRevocationService tokenRevocationService;
    private final ServiceLogoutStore serviceLogoutStore;

    private static final String ACCESS_TOKEN_COOKIE = "hr_access_token";

    /**
     * OAuth2 인가코드를 HR 토큰으로 교환 (Authorization Code + PKCE)
     * SSO /oauth2/token 에서 code+code_verifier 를 교환한 뒤 HR JWT 를 HttpOnly Cookie 로 설정.
     *
     * @param request 인가코드/code_verifier/redirect_uri 가 포함된 요청
     * @param response HTTP 응답 (쿠키 설정용)
     * @return 사용자 정보 응답 (토큰은 쿠키로 전달)
     */
    @PostMapping("/exchange-code")
    @Operation(summary = "인가코드 교환", description = "OAuth2 인가코드(+PKCE)를 HR JWT로 교환합니다. HR 서비스 접근 권한이 있어야 합니다.")
    public ApiResponse<TokenExchangeDto.Response> exchangeCode(
            @Valid @RequestBody TokenExchangeDto.CodeRequest request,
            HttpServletResponse response) {
        log.debug("Authorization code exchange request");
        TokenExchangeDto.ExchangeResult result = tokenExchangeService.exchangeCode(
                request.getCode(), request.getCodeVerifier(), request.getRedirectUri());

        setAccessTokenCookie(response, result.getAccessToken());

        return ApiResponse.success(TokenExchangeDto.Response.of(result.getExpiresIn(), result.getUser()));
    }

    /**
     * 로그아웃 (쿠키 삭제)
     *
     * @param response HTTP 응답 (쿠키 삭제용)
     * @return 성공 응답
     */
    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "HR JWT 쿠키를 삭제하고 그 토큰을 서버에서도 무효화합니다. "
            + "그 토큰을 만든 SSO 로그인으로는 비밀번호를 다시 입력해야 이음에 들어올 수 있습니다.")
    public ApiResponse<Void> logout(@org.springframework.web.bind.annotation.CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String token,
                                    HttpServletResponse response) {
        revokeIfHrToken(token);
        rememberServiceLogout(token);
        clearAccessTokenCookie(response);
        return ApiResponse.success(null);
    }

    /** 로그아웃하는 HR 토큰의 jti 를 남은 수명만큼 차단 목록에 넣는다. 실패해도 로그아웃(쿠키 삭제)은 진행한다. */
    private void revokeIfHrToken(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        try {
            if (jwtTokenProvider.validateHrToken(token)) {
                tokenRevocationService.revoke(jwtTokenProvider.getHrJti(token),
                        jwtTokenProvider.getRemainingExpiration(token));
            }
        } catch (Exception e) {
            log.warn("로그아웃 토큰 무효화 실패 — 쿠키만 삭제한다: {}", e.getMessage());
        }
    }

    /**
     * 로그아웃하는 HR 토큰이 실고 있는 SSO 로그인 번호(sso_sid)를 "이음에서 로그아웃함" 목록에 넣는다(보안 점검 CLI-6).
     * 이음 로그아웃은 그루 계정(SSO) 로그인을 끊지 않으므로, 적어 두지 않으면 로그인 단추 한 번에 비밀번호 없이
     * 다시 들어온다. jti 차단과 따로 시도한다(한쪽 실패가 다른 쪽을 건너뛰게 하지 않는다).
     * sso_sid 가 없는 옛 토큰은 적을 게 없다. 실패해도 로그아웃(쿠키 삭제)은 진행한다.
     */
    private void rememberServiceLogout(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        try {
            if (jwtTokenProvider.validateHrToken(token)) {
                serviceLogoutStore.remember(jwtTokenProvider.getSsoSidFromHrToken(token));
            }
        } catch (Exception e) {
            log.warn("로그아웃한 SSO 로그인 기록 실패 — 쿠키만 삭제한다: {}", e.getMessage());
        }
    }

    private void setAccessTokenCookie(HttpServletResponse response, String accessToken) {
        ResponseCookie cookie = ResponseCookie.from(ACCESS_TOKEN_COOKIE, accessToken)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(jwtProperties.getHrAccessExpiration() / 1000)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearAccessTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(ACCESS_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
