package io.groo.ieum.security.controller;

import io.groo.core.controller.ApiResponse;
import io.groo.ieum.security.service.IpBlacklistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * IP 블랙리스트 관리 (개발 환경 전용).
 * 클래스 단위 @Profile 이라 local/dev 가 아니면 빈 자체가 없어 운영에서는 404 다.
 * (메서드 단위 @Profile 은 컨트롤러 핸들러에 효력이 없다.)
 *
 * <p><b>관리자(ROLE:MANAGE)만 부른다</b>(보안 점검 N-1). 예전엔 권한 검사가 없어 dev 에서 로그인한 사용자
 * 누구나 IP 를 차단·해제할 수 있었다 — 남의 IP 를 막아 개발 서버를 못 쓰게 하거나, 막힌 IP 를 풀 수 있었다.
 * ROLE:MANAGE 는 ADMIN 역할에만 매핑된 권한이다(groo-sql hr/data/5. role_permission.sql).
 * 클래스에 걸어 조회 · 확인까지 네 핸들러 모두 막는다 — 새 핸들러를 더해도 빠지지 않는다.
 */
@RestController
@Profile({"local", "dev"})
@PreAuthorize("hasAuthority('ROLE:MANAGE')")
@RequiredArgsConstructor
@Slf4j
public class IpBlacklistController implements IpBlacklistApi {
    private final IpBlacklistService ipBlacklistService;

    @Override
    public ApiResponse<Set<String>> getRuntimeBlacklist() {
        Set<String> blacklist = ipBlacklistService.getRuntimeBlacklist();
        log.info("Runtime blacklist retrieved: {} IPs", blacklist.size());
        return ApiResponse.success(blacklist);
    }

    @Override
    public ApiResponse<String> addToBlacklist(String ip) {
        ipBlacklistService.addToBlacklist(ip);
        log.warn("⚠️ IP manually added to blacklist: {}", ip);
        return ApiResponse.success("IP " + ip + " has been added to blacklist");
    }

    @Override
    public ApiResponse<String> removeFromBlacklist(String ip) {
        ipBlacklistService.removeFromBlacklist(ip);
        log.info("IP removed from blacklist: {}", ip);
        return ApiResponse.success("IP " + ip + " has been removed from blacklist");
    }

    @Override
    public ApiResponse<Boolean> checkIpStatus(String ip) {
        boolean isBlocked = ipBlacklistService.isBlocked(ip);
        return ApiResponse.success(isBlocked);
    }
}
