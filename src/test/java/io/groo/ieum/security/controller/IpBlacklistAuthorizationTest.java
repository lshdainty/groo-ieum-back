package io.groo.ieum.security.controller;

import io.groo.ieum.security.service.IpBlacklistService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * N-1 — IP 블랙리스트 관리 API 는 관리자(ROLE:MANAGE)만 부른다.
 *
 * <p>운영처럼 메서드 보안 프록시(클래스 프록시)를 씌운 작은 컨텍스트에서 컨트롤러를 직접 부른다.
 * 프로필 게이트(local/dev 에서만 빈 존재)는 {@link IpBlacklistProfileTest} 가 본다.
 */
@SpringJUnitConfig(IpBlacklistAuthorizationTest.Config.class)
@DisplayName("IP 블랙리스트 API 권한 — 관리자만")
class IpBlacklistAuthorizationTest {

    @Configuration
    @EnableMethodSecurity(proxyTargetClass = true)
    static class Config {
        @Bean
        IpBlacklistService ipBlacklistService() {
            return mock(IpBlacklistService.class);
        }

        @Bean
        IpBlacklistController ipBlacklistController(IpBlacklistService service) {
            return new IpBlacklistController(service);
        }
    }

    @Autowired private IpBlacklistController controller;
    @Autowired private IpBlacklistService service;

    @BeforeEach
    void setUp() {
        reset(service);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void loginWith(String... authorities) {
        List<SimpleGrantedAuthority> granted = Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", null, granted));
    }

    @Test
    @DisplayName("일반 사용자(관리 권한 없음)는 추가 · 해제 · 조회 · 확인 모두 거절되고 서비스에 닿지 않는다")
    void nonAdmin_denied() {
        loginWith("VACATION:READ", "USER:READ");

        assertThatThrownBy(() -> controller.addToBlacklist("10.0.0.1")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.removeFromBlacklist("10.0.0.1")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.getRuntimeBlacklist()).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.checkIpStatus("10.0.0.1")).isInstanceOf(AccessDeniedException.class);

        verify(service, never()).addToBlacklist("10.0.0.1");
        verify(service, never()).removeFromBlacklist("10.0.0.1");
    }

    @Test
    @DisplayName("다른 관리 권한(USER:MANAGE)만으로는 안 된다 — ROLE:MANAGE 가 있어야 한다")
    void otherManageAuthority_denied() {
        loginWith("USER:MANAGE", "COMPANY:MANAGE");

        assertThatThrownBy(() -> controller.addToBlacklist("10.0.0.1")).isInstanceOf(AccessDeniedException.class);
        verify(service, never()).addToBlacklist("10.0.0.1");
    }

    @Test
    @DisplayName("인증이 없으면 거절")
    void anonymous_denied() {
        assertThatThrownBy(() -> controller.addToBlacklist("10.0.0.1"))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    @DisplayName("관리자(ROLE:MANAGE)는 그대로 쓴다")
    void admin_allowed() {
        loginWith("ROLE:MANAGE");
        when(service.getRuntimeBlacklist()).thenReturn(Set.of("10.0.0.1"));
        when(service.isBlocked("10.0.0.1")).thenReturn(true);

        controller.addToBlacklist("10.0.0.1");
        controller.removeFromBlacklist("10.0.0.1");
        assertThat(controller.getRuntimeBlacklist().getData()).containsExactly("10.0.0.1");
        assertThat(controller.checkIpStatus("10.0.0.1").getData()).isTrue();

        verify(service).addToBlacklist("10.0.0.1");
        verify(service).removeFromBlacklist("10.0.0.1");
    }
}
