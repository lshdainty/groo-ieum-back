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
    private final UserApiController controller = new UserApiController(userService, mock(MessageSource.class));
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
}
