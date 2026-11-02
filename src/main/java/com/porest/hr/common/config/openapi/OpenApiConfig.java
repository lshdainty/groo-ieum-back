package com.porest.hr.common.config.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(apiInfo())
                .servers(List.of(
                        new Server().url("http://localhost:8000").description("로컬 개발 서버"),
                        new Server().url("https://hangroo.io").description("운영 서버")
                ))
                .components(securitySchemes())
                .addSecurityItem(securityRequirement());
    }

    private Info apiInfo() {
        return new Info()
                .title("Porest HR API")
                .description("Porest HR 백엔드 API 문서")
                .version("v1.0.0");
    }

    private Components securitySchemes() {
        return new Components()
                .addSecuritySchemes("session-auth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("JSESSIONID")
                                .description("세션 기반 인증"));
    }

    private SecurityRequirement securityRequirement() {
        return new SecurityRequirement()
                .addList("session-auth");
    }
}
