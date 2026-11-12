package io.groo.ieum.common.event;

import io.groo.core.security.event.SsoEventSignature;
import io.groo.core.security.event.SsoEventVerifier;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import io.groo.core.util.MaskUtils;
import io.groo.ieum.security.service.TokenRevocationService;
import io.groo.ieum.user.domain.User;
import io.groo.ieum.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * SSO 사용자 이벤트 구독자
 * Redis Pub/Sub을 통해 SSO 서비스로부터 이벤트 수신
 *
 * 현재 구현:
 * - USER_CREATED: HR에 이미 존재하는 사용자면 정보 업데이트, 없으면 로깅만 수행
 *   (HR 관리자가 회사, 근무시간 등 HR 필수 정보와 함께 사용자를 생성해야 함)
 * - USER_UPDATED: 기존 사용자의 SSO 관리 필드(name, email) 동기화
 * - USER_DELETED: 기존 사용자 Soft Delete
 * - USER_LINKED: 초대 대기 계정 번호로 만든 행을 기존 계정 번호로 다시 묶는다(SSO-2)
 */
@Slf4j
@Component
public class SsoUserEventSubscriber {

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final TokenRevocationService tokenRevocationService;
    private final SsoEventVerifier ssoEventVerifier;
    private final SsoUserUpdateOrder updateOrder;

    public SsoUserEventSubscriber(UserRepository userRepository, TokenRevocationService tokenRevocationService,
                                  SsoEventVerifier ssoEventVerifier, SsoUserUpdateOrder updateOrder) {
        this.userRepository = userRepository;
        this.ssoEventVerifier = ssoEventVerifier;
        this.updateOrder = updateOrder;
        this.objectMapper = JsonMapper.builder().build();
        this.tokenRevocationService = tokenRevocationService;
    }

    /**
     * SSO 사용자 이벤트 처리
     * Redis MessageListenerAdapter에서 호출됨
     */
    @Transactional
    public void handleUserEvent(String message) {
        log.debug("Received user event");

        // 서명 확인(보안 점검 IEU-6) — 통과하면 서명 안의 원문을 쓴다. 거절 이유는 검증기가 warn 으로 남긴다
        Optional<String> verified = ssoEventVerifier.verify(SsoEventSignature.USER_EVENTS_CHANNEL, message);
        if (verified.isEmpty()) {
            return;
        }
        try {
            UserEvent event = objectMapper.readValue(verified.get(), UserEvent.class);

            switch (event.getType()) {
                case USER_CREATED -> handleUserCreated(event);
                case USER_UPDATED -> handleUserUpdated(event);
                case USER_DELETED -> handleUserDeleted(event);
                case USER_LINKED -> handleUserLinked(event);
                default -> log.warn("Unknown event type: {}", event.getType());
            }
        } catch (JacksonException e) {
            log.error("Failed to parse user event: {}", message, e);
        } catch (Exception e) {
            log.error("Failed to handle user event: {}", message, e);
        }
    }

    /**
     * 사용자 생성 이벤트 처리
     * SSO에서 사용자가 생성되면 HR에 이미 존재하는지 확인하고 동기화
     * HR 사용자가 없으면 로깅만 수행 (HR 관리자가 직접 생성해야 함)
     */
    private void handleUserCreated(UserEvent event) {
        log.info("Processing USER_CREATED event: ssoUserRowId={}, userId={}", event.getUserNo(), event.getUserId());

        Optional<User> existingUser = userRepository.findBySsoUserRowId(event.getUserNo());
        if (existingUser.isPresent()) {
            log.info("User already exists in HR, syncing SSO fields: ssoUserRowId={}", event.getUserNo());
            syncSsoFields(existingUser.get(), event);
            return;
        }

        // HR에 사용자가 없으면 로깅만 수행
        // HR 관리자가 회사, 근무시간, 입사일 등 HR 필수 정보와 함께 사용자를 생성해야 함
        log.info("User not found in HR, awaiting HR admin creation: ssoUserRowId={}, userId={}, email={}",
                event.getUserNo(), event.getUserId(), MaskUtils.email(event.getEmail()));
    }

    /**
     * 사용자 수정 이벤트 처리
     * SSO에서 사용자 정보가 변경되면 HR 캐시도 업데이트
     */
    private void handleUserUpdated(UserEvent event) {
        log.info("Processing USER_UPDATED event: ssoUserRowId={}, userId={}", event.getUserNo(), event.getUserId());

        // 이미 적용한 변경보다 이른 변경은 버린다 — 늦게 도착한 옛 값이 새 값을 덮지 않게(보안 점검 IEU-6 후속)
        if (!updateOrder.isNotOlder(event.getUserNo(), event.getTimestamp())) {
            log.warn("오래된 USER_UPDATED — 버린다: ssoUserRowId={}, changedAt={}", event.getUserNo(), event.getTimestamp());
            return;
        }

        Optional<User> userOpt = userRepository.findBySsoUserRowId(event.getUserNo());
        if (userOpt.isEmpty()) {
            log.warn("User not found in HR for update: ssoUserRowId={}", event.getUserNo());
            return;
        }

        syncSsoFields(userOpt.get(), event);
        updateOrder.applied(event.getUserNo(), event.getTimestamp());
        log.info("User updated from SSO event: ssoUserRowId={}", event.getUserNo());
    }

    /**
     * 사용자 삭제 이벤트 처리
     * SSO에서 사용자가 삭제되면 HR에서도 Soft Delete
     */
    private void handleUserDeleted(UserEvent event) {
        log.info("Processing USER_DELETED event: ssoUserRowId={}", event.getUserNo());

        Optional<User> userOpt = userRepository.findBySsoUserRowId(event.getUserNo());
        if (userOpt.isEmpty()) {
            log.warn("User not found in HR for deletion: ssoUserRowId={}", event.getUserNo());
            return;
        }

        User user = userOpt.get();
        user.deleteUser();
        tokenRevocationService.revokeAllForUser(user.getId());
        log.info("User deleted from SSO event: ssoUserRowId={}", event.getUserNo());
    }

    /**
     * 초대 대기 계정 → 기존 계정 합침 처리(groo-sso-back 보안 점검 SSO-2).
     *
     * <p>SSO 는 이제 이메일이 같아도 초대를 기존 계정에 자동으로 붙이지 않는다. 초대하면 대기 계정이 생기고
     * 이음은 그 번호로 행을 만든다. 초대 메일을 받은 사람이 기존 계정 비밀번호로 수락하면 SSO 가 대기 계정을
     * 지우고 이 이벤트를 보낸다 — 그 행을 기존 계정 번호에 다시 묶고 이름 · 이메일 · 아이디를 맞춘다.
     * 기존 계정이 이미 이음 행을 갖고 있으면(같은 사람을 두 번 초대) 대기 행은 지운다.
     */
    private void handleUserLinked(UserEvent event) {
        log.info("Processing USER_LINKED event: ssoUserRowId={}, previous={}", event.getUserNo(), event.getPreviousUserNo());
        if (event.getUserNo() == null || event.getPreviousUserNo() == null) {
            log.warn("USER_LINKED event without user numbers - ignored");
            return;
        }

        Optional<User> pending = userRepository.findBySsoUserRowId(event.getPreviousUserNo());
        Optional<User> already = userRepository.findBySsoUserRowId(event.getUserNo());

        if (pending.isEmpty()) {
            // 이음에서 초대한 계정이 아니다(다른 서비스 초대) — 기존 행이 있으면 정보만 맞춘다
            already.ifPresent(u -> syncSsoFields(u, event));
            return;
        }
        if (already.isPresent()) {
            pending.get().deleteUser();
            syncSsoFields(already.get(), event);
            log.info("USER_LINKED: existing HR user kept, pending row deleted. ssoUserRowId={}", event.getUserNo());
            return;
        }
        User user = pending.get();
        user.linkToSso(event.getUserNo());
        syncSsoFields(user, event);
        log.info("USER_LINKED: pending row relinked. {} -> {}", event.getPreviousUserNo(), event.getUserNo());
    }

    /**
     * SSO 관리 필드 동기화 (name, email)
     */
    private void syncSsoFields(User user, UserEvent event) {
        user.updateUser(
                event.getUserId(),
                event.getName(),
                event.getEmail(),
                null,  // roles - 변경하지 않음
                null,  // birth - 변경하지 않음
                null,  // company - 변경하지 않음
                null,  // workTime - 변경하지 않음
                null,  // lunarYN - 변경하지 않음
                null,  // profileName - 변경하지 않음
                null,  // profileUUID - 변경하지 않음
                null,  // dashboard - 변경하지 않음
                null   // countryCode - 변경하지 않음
        );
    }
}
