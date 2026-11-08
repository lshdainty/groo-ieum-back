package io.groo.ieum.security.controller;

import io.groo.ieum.security.service.IpBlacklistService;
import io.groo.ieum.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A-5 — IP 블랙리스트 관리 API 는 local/dev 에서만 존재해야 한다.
 * 메서드 단위 @Profile 은 컨트롤러 핸들러에 효력이 없으므로 클래스(빈) 단위로 닫는다.
 */
@DisplayName("IP 블랙리스트 API 프로필 게이트")
class IpBlacklistProfileTest {

    private ApplicationContextRunner runner(String profile) {
        return new ApplicationContextRunner()
                .withPropertyValues("spring.profiles.active=" + profile)
                .withBean(IpBlacklistService.class, () -> Mockito.mock(IpBlacklistService.class))
                .withBean(UserService.class, () -> Mockito.mock(UserService.class))
                .withUserConfiguration(IpBlacklistController.class);
    }

    @Test
    @DisplayName("운영(prod) 프로필에서는 컨트롤러 빈이 없다 (404)")
    void absentInProd() {
        runner("prod").run(ctx -> assertThat(ctx).doesNotHaveBean(IpBlacklistController.class));
    }

    @Test
    @DisplayName("프로필이 없어도 빈이 없다")
    void absentWithoutProfile() {
        new ApplicationContextRunner()
                .withBean(IpBlacklistService.class, () -> Mockito.mock(IpBlacklistService.class))
                .withUserConfiguration(IpBlacklistController.class)
                .run(ctx -> assertThat(ctx).doesNotHaveBean(IpBlacklistController.class));
    }

    @Test
    @DisplayName("dev 프로필에서는 빈이 있다")
    void presentInDev() {
        runner("dev").run(ctx -> assertThat(ctx).hasSingleBean(IpBlacklistController.class));
    }

    @Test
    @DisplayName("local 프로필에서는 빈이 있다")
    void presentInLocal() {
        runner("local").run(ctx -> assertThat(ctx).hasSingleBean(IpBlacklistController.class));
    }

    @Test
    @DisplayName("상시 존재하는 AuthController 에는 ip-blacklist 매핑이 없다")
    void authControllerHasNoBlacklistMapping() {
        for (Method m : AuthController.class.getMethods()) {
            RequestMapping rm = AnnotatedElementUtils.findMergedAnnotation(m, RequestMapping.class);
            if (rm == null) continue;
            assertThat(String.join(",", rm.path())).doesNotContain("ip-blacklist");
        }
        for (Method m : AuthApi.class.getMethods()) {
            RequestMapping rm = AnnotatedElementUtils.findMergedAnnotation(m, RequestMapping.class);
            if (rm == null) continue;
            assertThat(String.join(",", rm.path())).doesNotContain("ip-blacklist");
        }
    }
}
