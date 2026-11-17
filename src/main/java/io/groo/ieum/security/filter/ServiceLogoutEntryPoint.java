package io.groo.ieum.security.filter;

import io.groo.core.security.web.SecurityErrorResponder;
import io.groo.ieum.common.exception.HrErrorCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/**
 * 인증 실패(401) 응답 — 이음에서 로그아웃한 SSO 로그인의 토큰이라 인증하지 않은 요청은 코드 AUTH_010 으로 내보낸다
 * (보안 점검 CLI-6). 그 밖의 인증 실패는 원래 응답({@code delegate})을 그대로 쓴다.
 *
 * <p>{@link JwtAuthenticationFilter} 는 그런 토큰을 무효 토큰처럼 다룬다 — 인증을 세우지 않고 넘기므로 보호된 경로에서만
 * 여기로 온다. 그대로 두면 "인증이 필요합니다" 와 구분되지 않아, 부르는 쪽이 비밀번호를 다시 받아야 하는 상황인지 알 수
 * 없다. 인가코드 교환의 거절과 같은 코드를 쓴다. 빈이 아니라 {@code SecurityConfig} 에서 직접 만든다.
 */
@RequiredArgsConstructor
public class ServiceLogoutEntryPoint implements AuthenticationEntryPoint {

    private final SecurityErrorResponder responder;
    private final AuthenticationEntryPoint delegate;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {
        if (Boolean.TRUE.equals(request.getAttribute(JwtAuthenticationFilter.SSO_LOGIN_LOGGED_OUT_ATTRIBUTE))) {
            responder.write(request, response, HrErrorCode.SSO_LOGIN_LOGGED_OUT);
            return;
        }
        delegate.commence(request, response, authException);
    }
}
