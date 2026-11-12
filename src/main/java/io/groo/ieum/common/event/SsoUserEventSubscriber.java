package io.groo.ieum.common.event;

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
 */
@Slf4j
@Component
public class SsoUserEventSubscriber {

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final TokenRevocationService tokenRevocationService;

    public SsoUserEventSubscriber(UserRepository userRepository, TokenRevocationService tokenRevocationService) {
        this.userRepository = userRepository;
        this.objectMapper = JsonMapper.builder().build();
        this.tokenRevocationService = tokenRevocationService;
    }

    /**
     * SSO 사용자 이벤트 처리
     * Redis MessageListenerAdapter에서 호출됨
     */
    @Transactional
    public void handleUserEvent(String message) {
        log.debug("Received user event: {}", message);

        try {
            UserEvent event = objectMapper.readValue(message, UserEvent.class);

            switch (event.getType()) {
                case USER_CREATED -> handleUserCreated(event);
                case USER_UPDATED -> handleUserUpdated(event);
                case USER_DELETED -> handleUserDeleted(event);
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

        Optional<User> userOpt = userRepository.findBySsoUserRowId(event.getUserNo());
        if (userOpt.isEmpty()) {
            log.warn("User not found in HR for update: ssoUserRowId={}", event.getUserNo());
            return;
        }

        syncSsoFields(userOpt.get(), event);
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
