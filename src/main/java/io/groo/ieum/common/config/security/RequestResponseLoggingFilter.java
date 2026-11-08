package io.groo.ieum.common.config.security;

import io.groo.core.logging.SensitiveDataMasker;
import io.groo.core.util.HttpUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 모든 HTTP 요청/응답에 대한 포괄적인 로깅을 수행하는 필터
 * - Trace ID (UUID) 생성 및 MDC 설정
 * - Request/Response Body 캡처
 * - 실행 시간 측정
 * - User ID, Client IP, User-Agent 수집
 * - 가독성 좋은 한 줄 포맷으로 로그 출력
 *
 * <h2>원본은 절대 건드리지 않는다</h2>
 * 마스킹은 {@link SensitiveDataMasker} 가 돌려주는 <b>새 문자열</b>에만 적용되고,
 * 그 문자열은 로그로만 나간다. 본문은 {@code ContentCaching*Wrapper} 가 들고 있는
 * <b>사본</b>({@code getContentAsByteArray()})에서만 읽으므로 스트림이 소비되지 않고
 * 컨트롤러는 요청 본문을 온전히 받는다. 응답은 {@link #doFilterInternal} 의 바깥
 * {@code finally} 에서 반드시 {@code copyBodyToResponse()} 로 흘려보낸다.
 *
 * <h2>헤더는 로깅하지 않는다</h2>
 * User-Agent 만 찍는다. {@code Authorization} · {@code Cookie} · {@code Set-Cookie}
 * (HR 은 JWT 를 {@code hr_access_token} 쿠키로 실어 보낸다)를 로그에 넣지 마라 —
 * 넣는 순간 액세스 토큰 전문이 로그로 나간다.
 */
@Slf4j
@Component
public class RequestResponseLoggingFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_KEY = "requestId";
    private static final int MAX_BODY_LENGTH = 500;
    private static final int MAX_USER_AGENT_LENGTH = 50;
    private static final int CONTENT_CACHE_LIMIT = 10 * 1024; // 10KB
    private static final List<String> EXCLUDED_PATHS = Arrays.asList(
            "/actuator/health",
            "/actuator/prometheus",
            "/favicon.ico"
    );

    /**
     * 마스킹 규칙은 core 한 벌만 쓴다. 같은 코드의 사본이 desk·sso·hr 에 각자 늙어 있었고,
     * 목록이 가장 좁은 사본이 토큰을 가장 많이 다루는 서비스에 붙어 있었다.
     *
     * <p>여기 추가한 키는 HR 고유다(core 기본 목록에는 없다):
     * <ul>
     *   <li>{@code invitation_token} — SSO 초대 토큰. 이 값 하나면 초대 대상의 가입을 끝낼 수 있다.
     *       {@code SsoInviteResponse.invitationToken} 이라 정규화하면 같은 키가 된다.</li>
     *   <li>{@code hr_access_token} — HR JWT 쿠키 이름. 지금은 헤더를 안 찍어 로그에 안 나오지만,
     *       쿼리스트링이나 본문에 실려 오는 경로가 생기면 이름만으로 걸린다.</li>
     * </ul>
     * {@code code} · {@code code_verifier}(인가코드 교환 {@code POST /api/v1/auth/exchange-code})는
     * core 가 <b>값이 43자 이상 base64url 일 때만</b> 가린다 — {@code ApiResponse} 봉투의
     * {@code "code":"COMMON_200"} 을 통째로 가려 로그를 못 쓰게 만들지 않기 위해서다.
     *
     * <p>{@code static final} 로 한 번만 만든다. core 의 정규식은 정적이라 인스턴스를 만들어도
     * 다시 컴파일되지 않는다(예전 판은 요청 하나마다 최대 34번 {@code Pattern.compile} 을 했다).
     */
    private static final SensitiveDataMasker MASKER = SensitiveDataMasker.withExtraKeys(
            Stream.concat(
                    Stream.of(
                            "invitation_token",
                            "hr_access_token",
                            // 사용자 수정 · 초대 본문에 실리는 생년월일 (이메일 계열은 CONTACT_PII_KEYS 가 가린다)
                            "user_birth",
                            "birth"
                    ),
                    SensitiveDataMasker.CONTACT_PII_KEYS.stream()
            ).collect(Collectors.toUnmodifiableSet())
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // 제외할 경로는 로깅 없이 통과
        if (shouldNotFilter(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Trace ID 생성 및 MDC 설정
        String traceId = generateTraceId();
        MDC.put(TRACE_ID_KEY, traceId);

        // Request/Response Body를 여러 번 읽을 수 있도록 래핑
        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request, CONTENT_CACHE_LIMIT);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

        long startTime = System.currentTimeMillis();

        try {
            try {
                // 다음 필터로 전달
                filterChain.doFilter(wrappedRequest, wrappedResponse);
            } finally {
                long executionTime = System.currentTimeMillis() - startTime;

                // 로그 출력. 여기서 무엇이 터지든(Exception 이든 Error 든) 바깥 finally 가
                // copyBodyToResponse() 를 반드시 실행한다 — 안 그러면 클라이언트가 빈 본문을 받는다.
                logRequestResponse(wrappedRequest, wrappedResponse, traceId, executionTime);
            }
        } finally {
            // Response Body를 실제 응답으로 복사 (중요!)
            wrappedResponse.copyBodyToResponse();

            // MDC 정리
            MDC.clear();
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return EXCLUDED_PATHS.stream().anyMatch(path::startsWith);
    }

    /**
     * Trace ID 생성 (UUID 기반 8자리)
     */
    private String generateTraceId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 요청/응답 정보를 가독성 좋은 한 줄 포맷으로 로깅
     * 포맷: [traceId] | status | time | METHOD URI | IP:ip | User:user | Agent:agent | Req:body | Res:body
     * Body가 길 경우 요약본은 INFO/WARN/ERROR로, 전체 원본은 DEBUG로 별도 출력
     *
     * <p>여기 들어오는 문자열은 전부 {@link #MASKER} 를 이미 통과한 사본이다. 새 값을 로그 줄에
     * 붙일 때는 반드시 마스킹을 먼저 태워라 — 잘라 쓰는 것만으로는 안 가려진다.
     *
     * <p>테스트가 "로깅이 터져도 응답이 온전히 나간다"를 확인할 수 있도록 {@code protected} 다.
     */
    protected void logRequestResponse(ContentCachingRequestWrapper request,
                                      ContentCachingResponseWrapper response,
                                      String traceId,
                                      long executionTime) {
        try {
            int status = response.getStatus();
            String method = request.getMethod();
            String uri = request.getRequestURI();
            String queryString = request.getQueryString();
            String clientIp = HttpUtils.getClientIp();
            String userId = getCurrentUserId();
            String userAgent = getUserAgent(request);
            String requestBody = getRequestBody(request);
            String responseBody = getResponseBody(response);

            // URI에 쿼리스트링 포함. 쿼리스트링에도 토큰·인가코드가 실려 오므로 마스킹한다.
            String fullUri = queryString != null ? uri + "?" + mask(queryString) : uri;

            // Body 잘림 여부 확인
            boolean requestBodyTruncated = requestBody != null && requestBody.length() > MAX_BODY_LENGTH;
            boolean responseBodyTruncated = responseBody != null && responseBody.length() > MAX_BODY_LENGTH;

            // 로그 메시지 구성
            StringBuilder logMessage = new StringBuilder();
            logMessage.append(String.format("[%s] | %d | %4dms | %s %s",
                    traceId, status, executionTime, method, fullUri));

            // IP 정보
            logMessage.append(" | IP:").append(clientIp != null ? clientIp : "-");

            // 사용자 정보
            logMessage.append(" | User:").append(userId != null ? userId : "anonymous");

            // User-Agent 정보
            logMessage.append(" | Agent:").append(userAgent != null ? userAgent : "-");

            // Request Body (있는 경우만)
            if (requestBody != null && !requestBody.isEmpty()) {
                logMessage.append(" | Req:").append(truncate(requestBody, MAX_BODY_LENGTH));
            }

            // Response Body (있는 경우만)
            if (responseBody != null && !responseBody.isEmpty()) {
                logMessage.append(" | Res:").append(truncate(responseBody, MAX_BODY_LENGTH));
            }

            // 상태 코드에 따라 로그 레벨 분리
            if (status >= 500) {
                log.error("{}", logMessage);
            } else if (status >= 400) {
                log.warn("{}", logMessage);
            } else {
                log.info("{}", logMessage);
            }

            // Body가 잘린 경우 DEBUG 레벨로 전체 원본 출력
            if (requestBodyTruncated || responseBodyTruncated) {
                logFullBody(traceId, requestBody, responseBody, requestBodyTruncated, responseBodyTruncated);
            }

        } catch (Exception e) {
            log.error("Failed to log request/response", e);
        }
    }

    /**
     * 잘린 Body의 전체 원본을 DEBUG 레벨로 출력
     *
     * <p><b>여기 들어오는 두 문자열은 이미 마스킹된 사본이다</b>({@link #getRequestBody} ·
     * {@link #getResponseBody} 가 태운다). 한 줄 로그는 500자에서 잘리지만 이 경로는 전문을 찍으므로,
     * 마스킹을 우회해 원본을 넘기면 잘려서 안 보이던 토큰 뒷부분이 그대로 로그에 남는다.
     */
    private void logFullBody(String traceId, String requestBody, String responseBody,
                              boolean requestBodyTruncated, boolean responseBodyTruncated) {
        if (requestBodyTruncated && requestBody != null) {
            log.debug("[{}] Full Request Body: {}", traceId, sanitizeForLog(requestBody));
        }
        if (responseBodyTruncated && responseBody != null) {
            log.debug("[{}] Full Response Body: {}", traceId, sanitizeForLog(responseBody));
        }
    }

    /**
     * 로그 출력을 위해 줄바꿈 제거
     */
    private String sanitizeForLog(String str) {
        if (str == null) {
            return null;
        }
        return str.replace("\n", " ").replace("\r", "");
    }

    /**
     * User-Agent 헤더 추출 (길이 제한 적용)
     */
    private String getUserAgent(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        if (userAgent != null && !userAgent.isEmpty()) {
            return truncate(userAgent, MAX_USER_AGENT_LENGTH);
        }
        return null;
    }

    /**
     * 문자열을 최대 길이로 자르고 말줄임표 추가
     */
    private String truncate(String str, int maxLength) {
        if (str == null) {
            return null;
        }
        // 줄바꿈 제거하여 한 줄로 만듦
        str = str.replace("\n", " ").replace("\r", "");
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength) + "...";
    }

    /**
     * Request Body 추출 (캐시된 사본에서만 읽는다 — 스트림은 건드리지 않는다)
     */
    private String getRequestBody(ContentCachingRequestWrapper request) {
        byte[] content = request.getContentAsByteArray();
        if (content.length > 0) {
            return mask(new String(content, StandardCharsets.UTF_8));
        }
        return null;
    }

    /**
     * Response Body 추출 (캐시된 사본에서만 읽는다 — 원본은 copyBodyToResponse 로 그대로 나간다)
     */
    private String getResponseBody(ContentCachingResponseWrapper response) {
        byte[] content = response.getContentAsByteArray();
        if (content.length > 0) {
            return mask(new String(content, StandardCharsets.UTF_8));
        }
        return null;
    }

    /**
     * 로그로 나갈 문자열의 민감값을 가린다. 입력은 변하지 않고 <b>새 문자열</b>이 나온다.
     * core 의 {@code apply()} 는 어떤 입력에도 예외를 던지지 않는다.
     */
    private String mask(String text) {
        return MASKER.apply(text);
    }

    /**
     * 현재 인증된 사용자 ID 추출
     */
    private String getCurrentUserId() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
                return authentication.getName();
            }
        } catch (Exception e) {
            log.debug("Failed to get current user", e);
        }
        return null;
    }
}
