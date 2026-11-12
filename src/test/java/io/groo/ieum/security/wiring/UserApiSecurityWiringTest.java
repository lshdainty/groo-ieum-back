package io.groo.ieum.security.wiring;

import io.groo.core.security.jwt.SsoJwksKeyLocator;
import io.groo.ieum.common.config.properties.JwtProperties;
import io.groo.ieum.permission.domain.Role;
import io.groo.ieum.security.jwt.JwtTokenProvider;
import io.groo.ieum.security.filter.JwtAuthenticationFilter;
import io.groo.ieum.security.resolver.LoginUserArgumentResolver;
import io.groo.ieum.security.service.TokenRevocationService;
import io.groo.ieum.user.controller.UserApiController;
import io.groo.ieum.user.domain.User;
import io.groo.ieum.user.service.UserService;
import io.groo.ieum.user.service.dto.UserServiceDto;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 보안 배선 관통 테스트 — 단위 테스트가 따로 확인한 조각들이 실제로 이어지는지 본다.
 *
 * <p>진짜 {@link JwtAuthenticationFilter}(진짜 HR 토큰 서명 · 파싱) → Spring Security 필터 체인 → {@code @PreAuthorize}
 * (메서드 보안 프록시) → 진짜 {@link UserApiController} → (목) {@link UserService}. 서비스는 목이라 컨트롤러가 넘기는
 * 권한 플래그를 그대로 잡아 볼 수 있다.
 * <ul>
 *   <li>IEU-2: 상태를 바꾸는 요청은 토큰이 아니라 DB 의 지금 권한으로 @PreAuthorize 와 컨트롤러 판단을 탄다</li>
 *   <li>IEU-3 · IEU-7: 컨트롤러가 사용자 관리(canManage) · 역할 관리(canManageRoles) 플래그와 소속 값을 서비스로 넘긴다</li>
 *   <li>IEU-2 후속: 사용자 단위로 차단된 토큰은 401</li>
 * </ul>
 * 전체 애플리케이션 컨텍스트(@SpringBootTest)는 Redis · 메일 · 파일 경로가 필요해, 보안 · MVC 만 담은 작은 컨텍스트로 띄운다.
 */
@SpringJUnitWebConfig(UserApiSecurityWiringTest.Config.class)
@DisplayName("보안 배선 관통 — 필터 → @PreAuthorize → 컨트롤러 → 서비스 플래그")
class UserApiSecurityWiringTest {

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @EnableMethodSecurity(proxyTargetClass = true)  // 운영(Spring Boot)처럼 클래스 프록시 — 컨트롤러가 인터페이스를 구현해도 매핑이 맞는다
    static class Config implements WebMvcConfigurer {
        @Bean
        JwtProperties jwtProperties() {
            JwtProperties props = new JwtProperties();
            props.setHrSecret("0123456789abcdef0123456789abcdef-wiring-test");
            props.setHrAccessExpiration(3_600_000L);
            return props;
        }

        @Bean
        JwtTokenProvider jwtTokenProvider(JwtProperties props) {
            JwtTokenProvider provider = new JwtTokenProvider(props, mock(SsoJwksKeyLocator.class));
            provider.init();
            return provider;
        }

        @Bean
        UserService userService() {
            return mock(UserService.class);
        }

        @Bean
        TokenRevocationService tokenRevocationService() {
            return mock(TokenRevocationService.class);
        }

        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter(JwtTokenProvider provider, UserService userService,
                                                        JwtProperties props, TokenRevocationService revocation) {
            return new JwtAuthenticationFilter(provider, userService, props, revocation, JsonMapper.builder().build());
        }

        @Bean
        UserApiController userApiController(UserService userService, JwtTokenProvider provider, JwtProperties props) {
            return new UserApiController(userService, mock(MessageSource.class), provider, props);
        }

        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception {
            http.csrf(csrf -> csrf.disable())
                    .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .exceptionHandling(e -> e
                            .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                            .accessDeniedHandler((req, res, ex) -> res.setStatus(HttpStatus.FORBIDDEN.value())))
                    .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
            return http.build();
        }

        @Override
        public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
            resolvers.add(new LoginUserArgumentResolver());
        }
    }

    @Autowired private WebApplicationContext context;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private UserService userService;
    @Autowired private TokenRevocationService tokenRevocationService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        reset(userService, tokenRevocationService);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        when(userService.searchUser(anyString())).thenAnswer(inv -> UserServiceDto.builder().id(inv.getArgument(0)).build());
    }

    /** 토큰은 관리자 시절에 발급됐다 — 관리자 역할 · 사용자/역할 관리 권한을 싣고 있다 */
    private Cookie adminEraToken() {
        String token = jwtTokenProvider.createHrAccessToken("me1", 7L, "나", "me1@test.com",
                List.of("ADMIN"), List.of("ADMIN", "USER:EDIT", "USER:MANAGE", "ROLE:MANAGE"));
        return new Cookie("hr_access_token", token);
    }

    /** DB 의 지금 행 — 연결 번호 7, 권한은 주어진 것 */
    private void dbUser(String roleCode, String... authorities) {
        User user = mock(User.class);
        Role role = mock(Role.class);
        lenient().when(role.getCode()).thenReturn(roleCode);
        lenient().when(user.isLinkedToSsoUser(any())).thenCallRealMethod();
        lenient().when(user.getSsoUserRowId()).thenReturn(7L);
        lenient().when(user.getId()).thenReturn("me1");
        lenient().when(user.getRoles()).thenReturn(List.of(role));
        lenient().when(user.getAllAuthorities()).thenReturn(List.of(authorities));
        when(userService.findUserById("me1")).thenReturn(user);
    }

    private static final String EDIT_BODY = "{\"user_name\":\"나\",\"user_roles\":[\"ADMIN\"],\"user_company_type\":\"SYSTEM\"}";

    @Test
    @DisplayName("IEU-2 · IEU-7 · IEU-3: 강등된 관리자의 PUT — DB 권한으로 인가되고, 컨트롤러는 관리 플래그 둘 다 false 와 소속 값을 서비스에 넘긴다")
    void demotedAdminEditGetsDbFlags() throws Exception {
        dbUser("USER", "USER", "USER:EDIT");

        mvc.perform(put("/api/v1/users/me1").cookie(adminEraToken())
                        .contentType(MediaType.APPLICATION_JSON).content(EDIT_BODY))
                .andExpect(status().isOk());

        ArgumentCaptor<UserServiceDto> dto = ArgumentCaptor.forClass(UserServiceDto.class);
        verify(userService).editUser(dto.capture(), eq("me1"), eq(false), eq(false));
        assertThat(dto.getValue().getRoleNames()).containsExactly("ADMIN");
        assertThat(dto.getValue().getCompany()).isEqualTo("SYSTEM");
    }

    @Test
    @DisplayName("IEU-7: DB 에 사용자 관리만 있으면 canManage=true · canManageRoles=false, 역할 관리까지 있으면 둘 다 true")
    void flagsFollowDbAuthorities() throws Exception {
        dbUser("HR", "HR", "USER:EDIT", "USER:MANAGE");
        mvc.perform(put("/api/v1/users/me1").cookie(adminEraToken())
                        .contentType(MediaType.APPLICATION_JSON).content(EDIT_BODY))
                .andExpect(status().isOk());
        verify(userService).editUser(any(), eq("me1"), eq(true), eq(false));

        reset(userService);
        when(userService.searchUser(anyString())).thenAnswer(inv -> UserServiceDto.builder().id(inv.getArgument(0)).build());
        dbUser("ADMIN", "ADMIN", "USER:EDIT", "USER:MANAGE", "ROLE:MANAGE");
        mvc.perform(put("/api/v1/users/me1").cookie(adminEraToken())
                        .contentType(MediaType.APPLICATION_JSON).content(EDIT_BODY))
                .andExpect(status().isOk());
        verify(userService).editUser(any(), eq("me1"), eq(true), eq(true));
    }

    @Test
    @DisplayName("IEU-2: 토큰엔 USER:MANAGE 가 있어도 DB 에 없으면 사용자 삭제(@PreAuthorize USER:MANAGE)가 403 이고 서비스까지 가지 않는다")
    void demotedAdminDeleteForbidden() throws Exception {
        dbUser("USER", "USER", "USER:EDIT");

        mvc.perform(delete("/api/v1/users/other").cookie(adminEraToken()))
                .andExpect(status().isForbidden());

        verify(userService, never()).deleteUser(anyString());
    }

    @Test
    @DisplayName("IEU-6 잔여: 같은 아이디의 행이 다른 연결 번호면(아이디 바꿔치기) 쓰기 요청은 401")
    void swappedRowUnauthorized() throws Exception {
        User adminRow = mock(User.class);
        lenient().when(adminRow.isLinkedToSsoUser(any())).thenCallRealMethod();
        lenient().when(adminRow.getSsoUserRowId()).thenReturn(1L);
        lenient().when(adminRow.getAllAuthorities()).thenReturn(List.of("ADMIN", "USER:MANAGE", "ROLE:MANAGE"));
        when(userService.findUserById("me1")).thenReturn(adminRow);

        mvc.perform(delete("/api/v1/users/other").cookie(adminEraToken()))
                .andExpect(status().isUnauthorized());

        verify(userService, never()).deleteUser(anyString());
    }

    @Test
    @DisplayName("IEU-2 후속: 사용자 단위로 차단된 토큰은 읽기 · 쓰기 모두 401")
    void userRevokedTokenUnauthorized() throws Exception {
        dbUser("ADMIN", "ADMIN", "USER:EDIT", "USER:MANAGE", "ROLE:MANAGE");
        when(tokenRevocationService.isUserRevoked(eq("me1"), any())).thenReturn(true);

        mvc.perform(delete("/api/v1/users/other").cookie(adminEraToken()))
                .andExpect(status().isUnauthorized());

        verify(userService, never()).deleteUser(anyString());
        verify(userService, never()).editUser(any(), anyString(), anyBoolean(), anyBoolean());
    }
}
