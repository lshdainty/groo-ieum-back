package io.groo.ieum.security.controller;

import io.groo.core.controller.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@Tag(name = "IpBlacklist", description = "IP 블랙리스트 API (local, dev 전용)")
public interface IpBlacklistApi {

    // ========== IP 블랙리스트 관리 (개발 환경 전용) ==========

    @Operation(
            summary = "런타임 블랙리스트 조회",
            description = "현재 런타임에 추가된 IP 블랙리스트를 조회합니다. (설정 파일 기반 블랙리스트는 제외)"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "블랙리스트 조회 성공",
                    content = @Content(schema = @Schema(implementation = Set.class))
            )
    })
    @GetMapping("/api/v1/security/ip-blacklist")
    ApiResponse<Set<String>> getRuntimeBlacklist();

    @Operation(
            summary = "IP 블랙리스트에 추가",
            description = "런타임에 특정 IP를 블랙리스트에 추가합니다. 즉시 차단이 적용됩니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "블랙리스트 추가 성공"
            )
    })
    @PostMapping("/api/v1/security/ip-blacklist")
    ApiResponse<String> addToBlacklist(
            @Parameter(description = "차단할 IP 주소", example = "192.168.1.100", required = true)
            @RequestParam String ip
    );

    @Operation(
            summary = "IP 블랙리스트에서 제거",
            description = "런타임 블랙리스트에서 특정 IP를 제거합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "블랙리스트 제거 성공"
            )
    })
    @DeleteMapping("/api/v1/security/ip-blacklist")
    ApiResponse<String> removeFromBlacklist(
            @Parameter(description = "차단 해제할 IP 주소", example = "192.168.1.100", required = true)
            @RequestParam String ip
    );

    @Operation(
            summary = "IP 차단 상태 확인",
            description = "특정 IP가 현재 블랙리스트에 있는지 확인합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "차단 상태 확인 성공",
                    content = @Content(schema = @Schema(implementation = Boolean.class))
            )
    })
    @GetMapping("/api/v1/security/ip-blacklist/check")
    ApiResponse<Boolean> checkIpStatus(
            @Parameter(description = "확인할 IP 주소", example = "192.168.1.100", required = true)
            @RequestParam String ip
    );
}
