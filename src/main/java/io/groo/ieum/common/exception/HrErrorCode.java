package io.groo.ieum.common.exception;

import io.groo.core.exception.ErrorCodeProvider;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * HR 도메인 에러 코드 정의
 * ErrorCodeProvider 인터페이스를 구현하여 Core의 예외 처리와 호환
 * 실제 메시지는 messages.properties에서 다국어로 관리
 */
@Getter
@RequiredArgsConstructor
public enum HrErrorCode implements ErrorCodeProvider {

    // ========================================
    // USER (사용자)
    // ========================================
    USER_NOT_FOUND("USER_001", "error.notfound.user", HttpStatus.NOT_FOUND),
    USER_ALREADY_EXISTS("USER_002", "error.user.already.exists", HttpStatus.CONFLICT),
    USER_INVALID_PASSWORD("USER_003", "error.user.invalid.password", HttpStatus.BAD_REQUEST),
    USER_INACTIVE("USER_004", "error.user.inactive", HttpStatus.FORBIDDEN),
    USER_DUPLICATE_EMAIL("USER_005", "error.user.duplicate.email", HttpStatus.CONFLICT),
    USER_EMAIL_MISMATCH("USER_006", "error.user.email.mismatch", HttpStatus.BAD_REQUEST),
    USER_PASSWORD_CONFIRM_MISMATCH("USER_007", "error.user.password.confirm.mismatch", HttpStatus.BAD_REQUEST),
    USER_SAME_PASSWORD("USER_008", "error.user.same.password", HttpStatus.BAD_REQUEST),

    // ========================================
    // VACATION (휴가)
    // ========================================
    VACATION_NOT_FOUND("VACATION_001", "error.notfound.vacation", HttpStatus.NOT_FOUND),
    VACATION_INSUFFICIENT_BALANCE("VACATION_002", "error.validate.notEnoughRemainTime", HttpStatus.BAD_REQUEST),
    VACATION_INVALID_DATE("VACATION_003", "error.vacation.invalid.date", HttpStatus.BAD_REQUEST),
    VACATION_ALREADY_APPROVED("VACATION_004", "error.vacation.already.approved", HttpStatus.CONFLICT),
    VACATION_ALREADY_REJECTED("VACATION_005", "error.vacation.already.rejected", HttpStatus.CONFLICT),
    VACATION_CANNOT_CANCEL("VACATION_006", "error.vacation.cannot.cancel", HttpStatus.BAD_REQUEST),
    VACATION_POLICY_NOT_FOUND("VACATION_007", "error.notfound.vacation.policy", HttpStatus.NOT_FOUND),
    VACATION_GRANT_NOT_FOUND("VACATION_008", "error.notfound.vacation.grant", HttpStatus.NOT_FOUND),
    VACATION_APPROVER_COUNT_MISMATCH("VACATION_009", "error.vacation.approver.count.mismatch", HttpStatus.BAD_REQUEST),
    VACATION_DUPLICATE_APPROVER("VACATION_010", "error.vacation.duplicate.approver", HttpStatus.BAD_REQUEST),
    VACATION_SELF_APPROVAL_NOT_ALLOWED("VACATION_011", "error.vacation.self.approval.not.allowed", HttpStatus.BAD_REQUEST),
    VACATION_MINUTE_USAGE_NOT_ALLOWED("VACATION_012", "error.vacation.minute.usage.not.allowed", HttpStatus.BAD_REQUEST),
    VACATION_ACCESS_DENIED("VACATION_013", "error.vacation.access.denied", HttpStatus.FORBIDDEN),
    VACATION_POLICY_NAME_DUPLICATE("VACATION_014", "error.duplicate.vacation.policy.name", HttpStatus.CONFLICT),
    VACATION_NO_APPROVAL_LINE("VACATION_015", "error.vacation.no.approval.line", HttpStatus.BAD_REQUEST),

    // ========================================
    // VACATION PLAN (휴가 플랜)
    // ========================================
    VACATION_PLAN_NOT_FOUND("VACATION_PLAN_001", "error.notfound.vacation.plan", HttpStatus.NOT_FOUND),
    VACATION_PLAN_ALREADY_EXISTS("VACATION_PLAN_002", "error.duplicate.vacation.plan", HttpStatus.CONFLICT),
    VACATION_PLAN_POLICY_NOT_FOUND("VACATION_PLAN_003", "error.notfound.vacation.plan.policy", HttpStatus.NOT_FOUND),
    USER_VACATION_PLAN_NOT_FOUND("VACATION_PLAN_004", "error.notfound.user.vacation.plan", HttpStatus.NOT_FOUND),
    USER_VACATION_PLAN_ALREADY_EXISTS("VACATION_PLAN_005", "error.duplicate.user.vacation.plan", HttpStatus.CONFLICT),
    VACATION_PLAN_POLICY_ALREADY_EXISTS("VACATION_PLAN_006", "error.duplicate.vacation.plan.policy", HttpStatus.CONFLICT),

    // ========================================
    // WORK (근무)
    // ========================================
    WORK_NOT_FOUND("WORK_001", "error.notfound.work.history", HttpStatus.NOT_FOUND),
    WORK_ALREADY_STARTED("WORK_002", "error.work.already.started", HttpStatus.CONFLICT),
    WORK_ALREADY_ENDED("WORK_003", "error.work.already.ended", HttpStatus.CONFLICT),
    WORK_INVALID_TIME("WORK_004", "error.validate.worktime.startEndTime", HttpStatus.BAD_REQUEST),
    WORK_CODE_NOT_FOUND("WORK_005", "error.notfound.work.code", HttpStatus.NOT_FOUND),
    WORK_CODE_DUPLICATE("WORK_006", "error.work.code.duplicate", HttpStatus.CONFLICT),
    WORK_CODE_INVALID_PARENT("WORK_007", "error.work.code.invalid.parent", HttpStatus.BAD_REQUEST),
    WORK_CODE_REQUIRED("WORK_008", "error.work.code.required", HttpStatus.BAD_REQUEST),
    WORK_YEAR_MONTH_REQUIRED("WORK_009", "error.work.year.month.required", HttpStatus.BAD_REQUEST),

    // ========================================
    // DEPARTMENT (부서)
    // ========================================
    DEPARTMENT_NOT_FOUND("DEPARTMENT_001", "error.notfound.department", HttpStatus.NOT_FOUND),
    DEPARTMENT_ALREADY_EXISTS("DEPARTMENT_002", "error.validate.notnull.department", HttpStatus.CONFLICT),
    DEPARTMENT_HAS_MEMBERS("DEPARTMENT_003", "error.validate.has.children.department", HttpStatus.CONFLICT),
    DEPARTMENT_COMPANY_MISMATCH("DEPARTMENT_004", "error.validate.different.company", HttpStatus.BAD_REQUEST),
    DEPARTMENT_SELF_REFERENCE("DEPARTMENT_005", "error.validate.self.parent", HttpStatus.BAD_REQUEST),
    DEPARTMENT_CIRCULAR_REFERENCE("DEPARTMENT_006", "error.validate.circular.reference", HttpStatus.BAD_REQUEST),

    // ========================================
    // SCHEDULE (일정)
    // ========================================
    SCHEDULE_NOT_FOUND("SCHEDULE_001", "error.notfound.schedule", HttpStatus.NOT_FOUND),
    SCHEDULE_INVALID_DATE("SCHEDULE_002", "error.schedule.invalid.date", HttpStatus.BAD_REQUEST),
    SCHEDULE_CONFLICT("SCHEDULE_003", "error.schedule.conflict", HttpStatus.CONFLICT),
    SCHEDULE_ACCESS_DENIED("SCHEDULE_004", "error.schedule.access.denied", HttpStatus.FORBIDDEN),

    // ========================================
    // PERMISSION (권한)
    // ========================================
    PERMISSION_DENIED("PERMISSION_001", "error.permission.denied", HttpStatus.FORBIDDEN),
    ROLE_NOT_FOUND("PERMISSION_002", "error.notfound.role", HttpStatus.NOT_FOUND),
    ROLE_ALREADY_EXISTS("PERMISSION_003", "error.role.already.exists", HttpStatus.CONFLICT),
    PERMISSION_NOT_FOUND("PERMISSION_004", "error.notfound.permission", HttpStatus.NOT_FOUND),
    PERMISSION_ALREADY_EXISTS("PERMISSION_005", "error.permission.already.exists", HttpStatus.CONFLICT),

    // ========================================
    // DUES (회비)
    // ========================================
    DUES_NOT_FOUND("DUES_001", "error.notfound.dues", HttpStatus.NOT_FOUND),
    DUES_ALREADY_PAID("DUES_002", "error.dues.already.paid", HttpStatus.CONFLICT),
    DUES_INVALID_AMOUNT("DUES_003", "error.dues.invalid.amount", HttpStatus.BAD_REQUEST),

    // ========================================
    // HOLIDAY (공휴일)
    // ========================================
    HOLIDAY_NOT_FOUND("HOLIDAY_001", "error.notfound.holiday", HttpStatus.NOT_FOUND),
    HOLIDAY_ALREADY_EXISTS("HOLIDAY_002", "error.holiday.already.exists", HttpStatus.CONFLICT),

    // ========================================
    // COMPANY (회사)
    // ========================================
    COMPANY_NOT_FOUND("COMPANY_001", "error.notfound.company", HttpStatus.NOT_FOUND),
    COMPANY_ALREADY_EXISTS("COMPANY_002", "error.company.already.exists", HttpStatus.CONFLICT),

    // ========================================
    // INVITATION (초대)
    // ========================================
    INVITATION_NOT_FOUND("INVITATION_001", "error.notfound.invitation", HttpStatus.NOT_FOUND),
    INVITATION_EXPIRED("INVITATION_002", "error.validate.expired.invitation", HttpStatus.BAD_REQUEST),

    // ========================================
    // AUTH (인증)
    // ========================================
    UNSUPPORTED_OAUTH_PROVIDER("AUTH_001", "error.auth.unsupported.provider", HttpStatus.BAD_REQUEST),
    OAUTH_PROVIDER_NOT_LINKED("AUTH_002", "error.oauth.provider.not.linked", HttpStatus.NOT_FOUND),
    PASSWORD_CHANGE_REQUIRED("AUTH_003", "error.auth.password.change.required", HttpStatus.FORBIDDEN),

    // ========================================
    // NOTICE (공지사항)
    // ========================================
    NOTICE_NOT_FOUND("NOTICE_001", "error.notfound.notice", HttpStatus.NOT_FOUND),
    NOTICE_INVALID_DATE("NOTICE_002", "error.notice.invalid.date", HttpStatus.BAD_REQUEST),

    // ========================================
    // SSO (SSO 연동)
    // ========================================
    SSO_SERVICE_ERROR("SSO_001", "error.sso.service.error", HttpStatus.BAD_GATEWAY),
    SSO_USER_NOT_FOUND("SSO_002", "error.sso.user.not.found", HttpStatus.NOT_FOUND),
    SSO_INVITATION_FAILED("SSO_003", "error.sso.invitation.failed", HttpStatus.BAD_GATEWAY),

    ;

    /**
     * 응답 코드 (예: USER_001, VACATION_002)
     */
    private final String code;

    /**
     * 메시지 키 (messages.properties의 키)
     */
    private final String messageKey;

    /**
     * HTTP 상태 코드
     */
    private final HttpStatus httpStatus;
}
