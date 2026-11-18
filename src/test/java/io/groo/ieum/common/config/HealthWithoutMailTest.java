package io.groo.ieum.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.mail.autoconfigure.MailHealthContributorAutoConfiguration;
import org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.IOException;
import java.io.UncheckedIOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 헬스 응답({@code /actuator/health})에 메일 서버 확인이 들어가지 않는지 본다(보안 점검 INF-16).
 *
 * <p>메일 확인이 켜져 있으면 헬스체크가 돌 때마다 메일 서버에 로그인하고, 메일 키가 죽거나 메일 서버에 장애가 나면
 * 서비스가 멀쩡해도 헬스가 503 이 된다. 컨테이너 헬스체크와 배포의 헬스 대기가 이 응답을 보므로, 그동안 컨테이너가
 * unhealthy 로 뜨고 배포가 실패한다(2026-11-18 메일 키를 바꾸다 9분 동안 실제로 그랬다).
 *
 * <p>배포에 쓰는 {@code application.yml} 을 그대로 읽고, 메일 발송기와 메일 헬스 자동 구성만 띄운다.
 * 메일 서버에는 접속하지 않는다 — 확인 빈이 등록되는지만 본다.
 */
@DisplayName("헬스 응답에서 메일 서버 확인 제외 (INF-16)")
class HealthWithoutMailTest {

    /** 헬스 자동 구성이 만드는 메일 확인 빈의 이름. */
    private static final String MAIL_HEALTH_BEAN = "mailHealthContributor";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(context -> {
                try {
                    for (PropertySource<?> source : new YamlPropertySourceLoader()
                            .load("application.yml", new FileSystemResource("src/main/resources/application.yml"))) {
                        context.getEnvironment().getPropertySources().addLast(source);
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            })
            // application.yml 이 환경변수로 받는 자리
            .withPropertyValues("MAIL_USERNAME=test", "MAIL_PASSWORD=test")
            .withConfiguration(AutoConfigurations.of(
                    MailSenderAutoConfiguration.class, MailHealthContributorAutoConfiguration.class));

    @Test
    @DisplayName("application.yml 그대로면 메일 확인이 헬스에 등록되지 않는다 — 메일 발송기는 그대로 있다")
    void mailCheckIsNotRegistered() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(JavaMailSender.class);
            assertThat(context).doesNotHaveBean(MAIL_HEALTH_BEAN);
        });
    }

    @Test
    @DisplayName("설정을 켜면 등록된다 — 위 시험이 그 한 줄에 달려 있음을 보인다")
    void mailCheckComesBackWhenEnabled() {
        runner.withPropertyValues("management.health.mail.enabled=true")
                .run(context -> assertThat(context).hasBean(MAIL_HEALTH_BEAN));
    }
}
