package io.groo.ieum.common.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private Frontend frontend = new Frontend();
    private Company company = new Company();
    private Email email = new Email();

    @Getter
    @Setter
    public static class Frontend {
        private String baseUrl;

        /**
         * 브라우저가 이 API 를 부를 수 있는 오리진 목록. CORS 설정과 Origin 검사 필터가 같은 값을 쓴다.
         */
        public List<String> allowedOrigins() {
            return List.of(baseUrl);
        }
    }

    @Getter
    @Setter
    public static class Company {
        private String name;
    }

    @Getter
    @Setter
    public static class Email {
        private Logo logo = new Logo();

        @Getter
        @Setter
        public static class Logo {
            private String path = "templates/email/logo.png";
        }
    }
}
