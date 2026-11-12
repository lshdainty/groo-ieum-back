package io.groo.ieum.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 보안 점검 IEU-6 ③ — 서명 없는 SSO 이벤트는 기본으로 처리하지 않는다.
 * 설정 파일 기본값과 코드 기본값(설정이 아예 없을 때)이 둘 다 "강제" 여야 한다.
 */
@DisplayName("SSO 이벤트 서명 강제 기본값 (IEU-6 ③)")
class SsoEventRequireSignatureDefaultTest {

    @Test
    @DisplayName("application.yml 기본값은 true(env 로만 끈다)")
    void yamlDefaultIsTrue() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        // 테스트 클래스패스의 application.yml 이 운영 파일을 가리므로 파일로 직접 읽는다
        yaml.setResources(new org.springframework.core.io.FileSystemResource("src/main/resources/application.yml"));
        String raw = yaml.getObject().getProperty("sso.events.require-signature");

        assertThat(raw).isEqualTo("${SSO_EVENTS_REQUIRE_SIGNATURE:true}");
    }

    @Test
    @DisplayName("설정이 없을 때의 코드 기본값도 true")
    void codeDefaultIsTrue() throws Exception {
        var method = RedisConfig.class.getMethod("ssoEventVerifier",
                io.groo.core.security.jwt.SsoJwksKeyLocator.class,
                org.springframework.data.redis.core.StringRedisTemplate.class, boolean.class);
        Value value = method.getParameters()[2].getAnnotation(Value.class);

        assertThat(value.value()).isEqualTo("${sso.events.require-signature:true}");
    }
}
