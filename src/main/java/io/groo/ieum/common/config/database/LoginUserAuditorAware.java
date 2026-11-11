package io.groo.ieum.common.config.database;

import io.groo.core.security.AbstractAuditorAware;
import org.springframework.stereotype.Component;

/**
 * 작성자·수정자 기록 — core {@link AbstractAuditorAware} 기본 동작 그대로다(core 3.1.0 에서 공통화).
 * 로그인 없는 쓰기와 낯선 주체는 비워 둔다. 담다만 {@code "system"} 으로 남긴다.
 */
@Component("auditorAware")
public class LoginUserAuditorAware extends AbstractAuditorAware {
}
