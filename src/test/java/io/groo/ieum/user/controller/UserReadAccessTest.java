package io.groo.ieum.user.controller;

import io.groo.core.exception.ForbiddenException;
import io.groo.core.type.CountryCode;
import io.groo.core.type.YNType;
import io.groo.ieum.user.domain.User;
import io.groo.ieum.user.service.UserService;
import io.groo.ieum.user.service.dto.UserServiceDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

@DisplayName("사용자 상세 조회 범위 테스트 (본인 또는 사용자/권한 관리)")
class UserReadAccessTest {
    private final UserService userService = mock(UserService.class);
    private final UserApiController controller = new UserApiController(userService, mock(MessageSource.class),
            mock(io.groo.ieum.security.jwt.JwtTokenProvider.class), mock(io.groo.ieum.common.config.properties.JwtProperties.class));
    private final User me = User.createUser(
            null, "me", "나", "me@test.com", LocalDate.of(1990, 1, 1), "NONE", "9 ~ 18",
            LocalDate.now(), YNType.N, null, null, CountryCode.KR);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void loginWith(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("me", "n", authorities));
    }

    @Test
    @DisplayName("거절 - USER:READ 만 가진 사용자가 남의 상세 정보를 읽으면 서비스까지 가지 못한다")
    void ordinaryUserCannotReadOther() {
        loginWith("USER:READ", "USER:EDIT");
        assertThatThrownBy(() -> controller.searchUser("other", me)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("통과 - 본인 정보는 읽는다")
    void ownerCanReadOwn() {
        loginWith("USER:READ");
        given(userService.searchUser("me")).willReturn(UserServiceDto.builder().id("me").build());
        assertThatCode(() -> controller.searchUser("me", me)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("통과 - 사용자 관리 · 권한 관리 권한자는 남의 상세 정보를 읽는다")
    void managersCanReadOther() {
        given(userService.searchUser("other")).willReturn(UserServiceDto.builder().id("other").build());
        loginWith("USER:READ", "USER:MANAGE");
        assertThatCode(() -> controller.searchUser("other", me)).doesNotThrowAnyException();
        loginWith("USER:READ", "ROLE:MANAGE");
        assertThatCode(() -> controller.searchUser("other", me)).doesNotThrowAnyException();
    }

    private UserServiceDto fullUser() {
        return UserServiceDto.builder().id("other").name("남").email("o@test.com")
                .birth(LocalDate.of(1991, 2, 3)).workTime("9 ~ 18").joinDate(LocalDate.of(2020, 1, 1))
                .company("NONE").lunarYN(YNType.N).countryCode(CountryCode.KR).dashboard("{}")
                .roleNames(java.util.List.of("USER")).profileUrl("/p.png")
                .invitationStatus("ACTIVE").build();
    }

    @SuppressWarnings("unchecked")
    private java.util.List<io.groo.ieum.user.controller.dto.UserApiDto.SearchUserResp> listUsers() {
        given(userService.searchUsers()).willReturn(java.util.List.of(fullUser()));
        return (java.util.List<io.groo.ieum.user.controller.dto.UserApiDto.SearchUserResp>) controller.searchUsers().getData();
    }

    @Test
    @DisplayName("가림 - 일반 직원의 직원 목록은 화면이 쓰는 칸만 내려가고 이메일 · 권한 · 초대 상태는 비어 있다")
    void ordinaryUserGetsTrimmedList() {
        loginWith("USER:READ");
        var r = listUsers().get(0);
        org.assertj.core.api.Assertions.assertThat(r.getUserName()).isEqualTo("남");
        org.assertj.core.api.Assertions.assertThat(r.getUserBirth()).isEqualTo(LocalDate.of(1991, 2, 3));
        org.assertj.core.api.Assertions.assertThat(r.getUserWorkTime()).isEqualTo("9 ~ 18");
        org.assertj.core.api.Assertions.assertThat(r.getProfileUrl()).isEqualTo("/p.png");
        org.assertj.core.api.Assertions.assertThat(r.getUserEmail()).isNull();
        org.assertj.core.api.Assertions.assertThat(r.getInvitationStatus()).isNull();
        org.assertj.core.api.Assertions.assertThat(r.getPermissions()).isEmpty();
        org.assertj.core.api.Assertions.assertThat(r.getRoles()).isEmpty();
        org.assertj.core.api.Assertions.assertThat(r.getDashboard()).isNull();
    }

    @Test
    @DisplayName("전체 - 사용자 관리 · 권한 관리 권한자는 지금 응답 그대로 받는다")
    void managerGetsFullList() {
        loginWith("USER:READ", "USER:MANAGE");
        var r = listUsers().get(0);
        org.assertj.core.api.Assertions.assertThat(r.getUserEmail()).isEqualTo("o@test.com");
        org.assertj.core.api.Assertions.assertThat(r.getInvitationStatus()).isEqualTo("ACTIVE");
        org.assertj.core.api.Assertions.assertThat(r.getJoinDate()).isEqualTo(LocalDate.of(2020, 1, 1));
    }

    @Test
    @DisplayName("거절 - 남의 승인권자 목록은 본인 · 사용자 관리 · 휴가 관리 권한자만 본다")
    void approversOfOthers() {
        loginWith("USER:READ");
        assertThatThrownBy(() -> controller.getUserApprovers("other", me)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(userService);

        given(userService.getUserApprovers(org.mockito.ArgumentMatchers.anyString())).willReturn(java.util.List.of());
        assertThatCode(() -> controller.getUserApprovers("me", me)).doesNotThrowAnyException();
        loginWith("VACATION:MANAGE");
        assertThatCode(() -> controller.getUserApprovers("other", me)).doesNotThrowAnyException();
        loginWith("USER:MANAGE");
        assertThatCode(() -> controller.getUserApprovers("other", me)).doesNotThrowAnyException();
    }
}
