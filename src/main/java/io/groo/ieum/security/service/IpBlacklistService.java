package io.groo.ieum.security.service;

import io.groo.core.security.web.IpBlocklist;

import java.util.Set;

/**
 * IP 블랙리스트 관리 서비스
 * - 설정 파일 기반 블랙리스트
 * - 외부 파일 기반 블랙리스트
 * - 런타임 동적 추가/제거
 * - CIDR 표기법 지원
 */
public interface IpBlacklistService extends IpBlocklist {

    /**
     * IP가 블랙리스트에 있는지 확인
     *
     * @param ipAddress 확인할 IP 주소
     * @return 차단 대상이면 true
     */
    boolean isBlocked(String ipAddress);

    /**
     * 런타임에 IP를 블랙리스트에 추가
     *
     * @param ipAddress 차단할 IP
     */
    void addToBlacklist(String ipAddress);

    /**
     * 런타임 블랙리스트에서 IP 제거
     *
     * @param ipAddress 차단 해제할 IP
     */
    void removeFromBlacklist(String ipAddress);

    /**
     * 현재 런타임 블랙리스트 조회
     */
    Set<String> getRuntimeBlacklist();
}
