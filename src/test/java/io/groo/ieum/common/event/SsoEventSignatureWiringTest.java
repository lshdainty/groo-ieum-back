package io.groo.ieum.common.event;

import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import io.groo.core.security.event.SsoEventSignature;
import io.groo.core.security.event.SsoEventSigner;
import io.groo.core.security.event.SsoEventVerifier;
import io.groo.core.type.CountryCode;
import io.groo.core.type.YNType;
import io.groo.ieum.security.service.TokenRevocationService;
import io.groo.ieum.user.domain.User;
import io.groo.ieum.user.repository.UserRepository;
import io.jsonwebtoken.Locator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.Key;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 보안 점검 IEU-6 — 이음 구독자는 SSO 서명을 확인한 이벤트만 처리한다.
 *
 * <p>예전엔 Redis 로 들어온 이벤트를 그대로 믿어, Redis 에 발행할 수 있는 누구든 위조 USER_DELETED ·
 * SESSION_REVOKED_ALL 로 사용자를 지우거나 토큰을 끊을 수 있었다. 발행은 core {@link SsoEventSigner}(SSO 와 같은
 * 코드)로, 검증은 이음과 같은 {@link SsoEventVerifier} 로 돌린다. 공개키는 JWKS 대신 고정 키를 돌려준다.
 */
@DisplayName("SSO 이벤트 서명 확인 (IEU-6)")
class SsoEventSignatureWiringTest {

    private static final String SESSION = "{\"type\":\"SESSION_REVOKED_ALL\",\"ssoUserRowId\":42,\"reason\":\"ADMIN\"}";
    private static final String DELETED = "{\"type\":\"USER_DELETED\",\"userNo\":42}";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final TokenRevocationService tokenRevocationService = mock(TokenRevocationService.class);
    private RSAKey ssoKey;
    private SsoEventSigner signer;
    private User kim;

    @BeforeEach
    void setUp() throws Exception {
        ssoKey = new RSAKeyGenerator(2048).keyID("k1").generate();
        signer = new SsoEventSigner(ssoKey.toRSAPrivateKey(), "k1");
        kim = User.createUser(42L, "kim", "kim", "kim@test.com", LocalDate.of(1990, 1, 1), "NONE", "9 ~ 18",
                LocalDate.now(), YNType.N, null, null, CountryCode.KR);
        given(userRepository.findBySsoUserRowId(42L)).willReturn(Optional.of(kim));
    }

    private SsoEventVerifier verifier(boolean required) throws Exception {
        Key publicKey = ssoKey.toRSAPublicKey();
        Locator<Key> locator = header -> publicKey;
        return new SsoEventVerifier(locator, null, required);
    }

    @Test
    @DisplayName("SSO 가 서명한 세션 폐기는 처리한다")
    void signedSessionEventProcessed() throws Exception {
        var subscriber = new SsoSessionEventSubscriber(userRepository, tokenRevocationService, verifier(true));

        subscriber.handleSessionEvent(signer.sign(SsoEventSignature.SESSION_EVENTS_CHANNEL, SESSION));

        verify(tokenRevocationService).revokeAllForUser("kim");
    }

    @Test
    @DisplayName("다른 키로 서명한 세션 폐기 · 사용자 삭제는 처리하지 않는다(위조)")
    void forgedEventsIgnored() throws Exception {
        RSAKey attacker = new RSAKeyGenerator(2048).keyID("k1").generate();
        SsoEventSigner forger = new SsoEventSigner(attacker.toRSAPrivateKey(), "k1");
        var session = new SsoSessionEventSubscriber(userRepository, tokenRevocationService, verifier(false));
        var user = new SsoUserEventSubscriber(userRepository, tokenRevocationService, verifier(false), new SsoUserUpdateOrder(org.mockito.Mockito.mock(org.springframework.data.redis.core.StringRedisTemplate.class)));

        session.handleSessionEvent(forger.sign(SsoEventSignature.SESSION_EVENTS_CHANNEL, SESSION));
        user.handleUserEvent(forger.sign(SsoEventSignature.USER_EVENTS_CHANNEL, DELETED));

        verify(tokenRevocationService, never()).revokeAllForUser(anyString());
        assertThat(kim.getIsDeleted()).isEqualTo(YNType.N);
    }

    @Test
    @DisplayName("서명 강제가 켜지면 서명 없는 이벤트는 처리하지 않는다 — 꺼져 있으면(전환 기간) 처리한다")
    void unsignedDependsOnFlag() throws Exception {
        new SsoUserEventSubscriber(userRepository, tokenRevocationService, verifier(true), new SsoUserUpdateOrder(org.mockito.Mockito.mock(org.springframework.data.redis.core.StringRedisTemplate.class))).handleUserEvent(DELETED);
        assertThat(kim.getIsDeleted()).isEqualTo(YNType.N);

        new SsoUserEventSubscriber(userRepository, tokenRevocationService, verifier(false), new SsoUserUpdateOrder(org.mockito.Mockito.mock(org.springframework.data.redis.core.StringRedisTemplate.class))).handleUserEvent(DELETED);
        assertThat(kim.getIsDeleted()).isEqualTo(YNType.Y);
    }
}
