package io.groo.ieum.security.controller;

import io.groo.core.controller.ApiResponse;
import io.groo.ieum.security.controller.dto.AuthApiDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth", description = "인증 및 보안 API")
public interface AuthApi {

    @Operation(
            summary = "로그인 사용자 정보 조회",
            description = "현재 로그인한 사용자의 상세 정보를 조회합니다. 사용자 ID, 이름, 이메일, 역할, 권한, 프로필 이미지 URL 등을 포함합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "사용자 정보 조회 성공",
                    content = @Content(schema = @Schema(implementation = AuthApiDto.LoginUserInfo.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 필요 (로그인되지 않음)"
            )
    })
    @GetMapping("/api/v1/login/check")
    ApiResponse<AuthApiDto.LoginUserInfo> getUserInfo();
}
