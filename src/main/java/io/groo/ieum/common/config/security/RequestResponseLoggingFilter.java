package io.groo.ieum.common.config.security;

import io.groo.core.logging.AbstractRequestResponseLoggingFilter;
import io.groo.core.logging.SensitiveDataMasker;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 이음의 요청·응답 로그 필터 — 동작은 core {@link AbstractRequestResponseLoggingFilter}, 여기는 이음만의 마스킹 키만 둔다
 * (core 3.1.0 에서 세 서비스의 복사본을 하나로 합쳤다). 제외 경로는 core 기본값 그대로다.
 *
 * <h2>이음 고유 마스킹 키</h2>
 * <ul>
 *   <li>{@code invitation_token} — SSO 초대 토큰. 이 값 하나면 초대 대상의 가입을 끝낼 수 있다.
 *       {@code SsoInviteResponse.invitationToken} 이라 정규화하면 같은 키가 된다.</li>
 *   <li>{@code hr_access_token} — HR JWT 쿠키 이름. 지금은 헤더를 안 찍어 로그에 안 나오지만, 쿼리스트링이나 본문에
 *       실려 오는 경로가 생기면 이름만으로 걸린다.</li>
 *   <li>{@code user_birth}·{@code birth} — 사용자 수정·초대 본문의 생년월일. 이메일·전화번호는
 *       {@code CONTACT_PII_KEYS} 가 가린다(HR 은 연락처가 업무 데이터라 담다·sso 와 달리 가린다).</li>
 * </ul>
 * {@code code}·{@code code_verifier}(인가코드 교환 {@code POST /api/v1/auth/exchange-code})는 core 가 <b>값이 43자 이상
 * base64url 일 때만</b> 가린다 — {@code ApiResponse} 봉투의 {@code "code":"COMMON_200"} 을 통째로 가리지 않기 위해서다.
 */
@Component
public class RequestResponseLoggingFilter extends AbstractRequestResponseLoggingFilter {

    private static final SensitiveDataMasker MASKER = SensitiveDataMasker.withExtraKeys(
            Stream.concat(
                    Stream.of("invitation_token", "hr_access_token", "user_birth", "birth"),
                    SensitiveDataMasker.CONTACT_PII_KEYS.stream()
            ).collect(Collectors.toUnmodifiableSet()));

    public RequestResponseLoggingFilter() {
        super(MASKER);
    }
}
