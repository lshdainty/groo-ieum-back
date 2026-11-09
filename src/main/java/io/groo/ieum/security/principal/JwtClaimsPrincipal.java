package io.groo.ieum.security.principal;

import io.groo.ieum.user.domain.User;
import lombok.Getter;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * JWT Claims 기반 UserPrincipal 구현체
 * JWT 토큰에서 추출한 정보만 보관하며, User 엔티티는 필요할 때만 조회합니다 (Lazy Loading).
 * DB 조회 없이 인증/인가 처리가 가능하여 성능이 향상됩니다.
 */
@Getter
public class JwtClaimsPrincipal implements UserPrincipal {

    private final String userId;
    private final Long ssoUserRowId;
    private final String name;
    private final String email;
    private final List<String> roles;
    private final List<String> permissions;
    private final Supplier<User> userSupplier;
    private final boolean passwordChangeRequired;

    // Lazy loaded User
    private User user;
    private boolean userLoaded = false;

    /**
     * JWT Claims 기반 Principal 생성
     *
     * @param userId 사용자 ID (JWT subject)
     * @param ssoUserRowId SSO 사용자 번호
     * @param name 사용자 이름
     * @param email 사용자 이메일
     * @param roles 역할 목록
     * @param permissions 권한 목록
     * @param userSupplier User 엔티티 조회 함수 (Lazy Loading용)
     */
    public JwtClaimsPrincipal(String userId, Long ssoUserRowId, String name, String email,
                               List<String> roles, List<String> permissions,
                               Supplier<User> userSupplier) {
        this(userId, ssoUserRowId, name, email, roles, permissions, userSupplier, false);
    }

    /**
     * @param passwordChangeRequired HR 토큰의 pwdChange 클레임 값
     */
    public JwtClaimsPrincipal(String userId, Long ssoUserRowId, String name, String email,
                               List<String> roles, List<String> permissions,
                               Supplier<User> userSupplier, boolean passwordChangeRequired) {
        this.passwordChangeRequired = passwordChangeRequired;
        this.userId = userId;
        this.ssoUserRowId = ssoUserRowId;
        this.name = name;
        this.email = email;
        this.roles = roles != null ? roles : Collections.emptyList();
        this.permissions = permissions != null ? permissions : Collections.emptyList();
        this.userSupplier = userSupplier;
    }

    /**
     * User 엔티티 반환 (Lazy Loading)
     * 최초 호출 시에만 DB 조회, 이후 캐시된 결과 반환
     *
     * @return User 엔티티
     */
    @Override
    public User getUser() {
        if (!userLoaded && userSupplier != null) {
            this.user = userSupplier.get();
            this.userLoaded = true;
        }
        return user;
    }

    /**
     * JPA Auditing을 위한 사용자 ID 반환
     * User 엔티티 조회 없이 JWT Claims에서 직접 반환
     *
     * @return 사용자 ID
     */
    @Override
    public String getUserId() {
        return userId;
    }

    /**
     * User 엔티티 로드 여부 확인
     *
     * @return User가 이미 로드되었으면 true
     */
    public boolean isUserLoaded() {
        return userLoaded;
    }

    /**
     * 모든 권한 목록 반환 (roles + permissions)
     *
     * @return 권한 코드 목록
     */
    public List<String> getAllAuthorities() {
        List<String> allAuthorities = new java.util.ArrayList<>(roles);
        allAuthorities.addAll(permissions);
        return allAuthorities;
    }
}
