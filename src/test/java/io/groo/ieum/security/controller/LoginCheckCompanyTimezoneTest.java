package io.groo.ieum.security.controller;

import io.groo.core.time.ServiceClock;
import io.groo.ieum.common.time.CompanyClock;
import io.groo.ieum.company.domain.Company;
import io.groo.ieum.company.repository.CompanyRepository;
import io.groo.ieum.security.controller.dto.AuthApiDto;
import io.groo.ieum.security.principal.JwtClaimsPrincipal;
import io.groo.ieum.user.domain.User;
import io.groo.ieum.user.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 로그인 확인 응답의 회사 타임존 — 웹이 "회사 오늘" 을 서버와 같은 시계로 센다(groo-design chart.md "미리 잡는 일정의 값은 예외" —
 * 지난해 · 올해 · 다음 해와 쓴 · 예정을 가르는 오늘은 같은 회사 기준 시계). 값은 서버가 날짜를 셀 때 쓰는 CompanyClock.zone() 그대로다.
 */
@DisplayName("로그인 확인 — company_timezone")
class LoginCheckCompanyTimezoneTest {
    private UserService userService;
    private User user;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        user = mock(User.class);
        when(user.getId()).thenReturn("user1");
        when(user.getName()).thenReturn("사용자");
        when(user.getEmail()).thenReturn("user1@example.com");
        when(user.getRoles()).thenReturn(new ArrayList<>());
        when(user.getAllAuthorities()).thenReturn(List.of());
        when(userService.findUserById("user1")).thenReturn(user);
        JwtClaimsPrincipal principal = new JwtClaimsPrincipal("user1", 7L, "사용자", "user1@example.com",
                List.of(), List.of(), () -> user, false);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private AuthApiDto.LoginUserInfo loginCheckWith(Optional<Company> company) {
        CompanyRepository companyRepository = mock(CompanyRepository.class);
        when(companyRepository.find()).thenReturn(company);
        CompanyClock companyClock = new CompanyClock(companyRepository, new ServiceClock("Asia/Seoul"));
        return new AuthController(userService, companyClock).getUserInfo().getData();
    }

    @Test
    @DisplayName("회사 타임존(IANA ID)을 그대로 싣는다")
    void carriesCompanyTimezone() {
        AuthApiDto.LoginUserInfo info = loginCheckWith(
                Optional.of(Company.createCompany("c1", "회사", "설명", "America/New_York")));
        assertThat(info.getCompanyTimezone()).isEqualTo("America/New_York");
    }

    @Test
    @DisplayName("회사 타임존이 없으면 서버가 날짜를 셀 때와 같은 서비스 기준(Asia/Seoul)")
    void fallsBackToServiceZone() {
        assertThat(loginCheckWith(Optional.empty()).getCompanyTimezone()).isEqualTo("Asia/Seoul");
    }

    @Test
    @DisplayName("알 수 없는 타임존이면 서비스 기준으로 — 웹에 읽을 수 없는 값을 주지 않는다")
    void unknownTimezoneFallsBack() {
        AuthApiDto.LoginUserInfo info = loginCheckWith(
                Optional.of(Company.createCompany("c1", "회사", "설명", "Mars/Olympus")));
        assertThat(info.getCompanyTimezone()).isEqualTo("Asia/Seoul");
    }
}
