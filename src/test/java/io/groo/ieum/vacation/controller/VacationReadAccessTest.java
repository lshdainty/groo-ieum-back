package io.groo.ieum.vacation.controller;

import io.groo.core.exception.ForbiddenException;
import io.groo.core.type.CountryCode;
import io.groo.core.type.YNType;
import io.groo.ieum.user.domain.User;
import io.groo.ieum.vacation.service.VacationPlanService;
import io.groo.ieum.vacation.service.VacationService;
import io.groo.ieum.vacation.service.VacationTimeFormatter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

@DisplayName("휴가 조회 범위 테스트 (본인 또는 관리/승인 권한)")
class VacationReadAccessTest {
    private VacationService vacationService;
    private VacationApiController controller;
    private final User me = User.createUser(
            null, "me", "나", "me@test.com", LocalDate.of(1990, 1, 1), "NONE", "9 ~ 18",
            LocalDate.now(), YNType.N, null, null, CountryCode.KR);

    @BeforeEach
    void setUp() {
        vacationService = mock(VacationService.class);
        controller = new VacationApiController(vacationService, mock(VacationPlanService.class),
                mock(MessageSource.class), mock(VacationTimeFormatter.class));
        given(vacationService.getAvailableVacations(anyString(), any())).willReturn(List.of());
        given(vacationService.getUserMonthlyVacationStats(anyString(), anyString())).willReturn(List.of());
        given(vacationService.getAllRequestedVacationsByUserId(anyString(), anyInt())).willReturn(List.of());
        given(vacationService.getAllVacationsByApprover(anyString(), anyInt(), isNull())).willReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void loginWith(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("me", "n", authorities));
    }

    private List<Runnable> readsOf(String targetUserId) {
        LocalDateTime now = LocalDateTime.of(2030, 1, 1, 0, 0);
        return List.of(
                () -> controller.getUserVacationHistory(targetUserId, 2030, me),
                () -> controller.getAvailableVacations(targetUserId, now, me),
                () -> controller.getUserMonthlyVacationStats(targetUserId, "2030", me),
                () -> controller.getUserVacationStats(targetUserId, now, me),
                () -> controller.getAllVacationsByApprover(targetUserId, 2030, null, me),
                () -> controller.getUserRequestedVacations(targetUserId, 2030, me),
                () -> controller.getUserRequestedVacationStats(targetUserId, 2030, me));
    }

    @Test
    @DisplayName("거절 - 일반 사용자가 남의 휴가 기록을 읽으면 서비스까지 가지 못한다")
    void ordinaryUserCannotReadOthers() {
        loginWith("VACATION:READ", "VACATION:REQUEST");
        for (Runnable read : readsOf("other")) {
            assertThatThrownBy(read::run).isInstanceOf(ForbiddenException.class);
        }
        verifyNoInteractions(vacationService);
    }

    @Test
    @DisplayName("거절 - 승인 권한이 있어도 남의 승인 대기함(approverId)은 읽지 못한다")
    void approverCannotReadOthersApprovalBox() {
        loginWith("VACATION:APPROVE");
        assertThatThrownBy(() -> controller.getAllVacationsByApprover("otherApprover", 2030, null, me))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(vacationService);
    }

    @Test
    @DisplayName("통과 - 본인 기록은 권한 외 조건 없이 읽는다")
    void ownerCanReadOwn() {
        loginWith("VACATION:READ", "VACATION:REQUEST", "VACATION:APPROVE");
        assertThatCode(() -> {
            controller.getAvailableVacations("me", LocalDateTime.of(2030, 1, 1, 0, 0), me);
            controller.getUserMonthlyVacationStats("me", "2030", me);
            controller.getUserRequestedVacations("me", 2030, me);
            controller.getAllVacationsByApprover("me", 2030, null, me);
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("통과 - 휴가 승인 권한자는 승인 화면에서 다른 사용자의 기록을 읽는다")
    void approverCanReadOthers() {
        loginWith("VACATION:READ", "VACATION:REQUEST", "VACATION:APPROVE");
        assertThatCode(() -> {
            controller.getAvailableVacations("other", LocalDateTime.of(2030, 1, 1, 0, 0), me);
            controller.getUserMonthlyVacationStats("other", "2030", me);
            controller.getUserRequestedVacations("other", 2030, me);
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("통과 - 휴가 관리 권한자는 다른 사용자의 기록과 다른 승인자의 대기함을 읽는다")
    void managerCanReadOthers() {
        loginWith("VACATION:READ", "VACATION:MANAGE");
        assertThatCode(() -> {
            controller.getAvailableVacations("other", LocalDateTime.of(2030, 1, 1, 0, 0), me);
            controller.getAllVacationsByApprover("other", 2030, null, me);
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("거절 - 일반 사용자는 남의 기간별 사용 내역과 전 직원 휴가 기록을 읽지 못한다")
    void ordinaryUserCannotReadBulk() {
        loginWith("VACATION:READ");
        LocalDateTime now = LocalDateTime.of(2030, 1, 1, 0, 0);
        assertThatThrownBy(() -> controller.getUserVacationUsagesByPeriod("other", now, now, me))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> controller.getAllUsersVacationHistory()).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> controller.getVacationUsagesByPeriod(now, now)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(vacationService);
    }

    @Test
    @DisplayName("통과 - 본인 기간별 내역, 그리고 관리 · 승인 권한자의 전 직원 기록")
    void allowedBulk() {
        LocalDateTime now = LocalDateTime.of(2030, 1, 1, 0, 0);
        given(vacationService.getUserVacationUsagesByPeriod(anyString(), any(), any())).willReturn(List.of());
        given(vacationService.getAllUsersVacationHistory()).willReturn(List.of());
        given(vacationService.getVacationUsagesByPeriod(any(), any())).willReturn(List.of());
        loginWith("VACATION:READ");
        assertThatCode(() -> controller.getUserVacationUsagesByPeriod("me", now, now, me)).doesNotThrowAnyException();
        loginWith("VACATION:APPROVE");
        assertThatCode(() -> {
            controller.getAllUsersVacationHistory();
            controller.getVacationUsagesByPeriod(now, now);
        }).doesNotThrowAnyException();
        loginWith("VACATION:MANAGE");
        assertThatCode(() -> controller.getAllUsersVacationHistory()).doesNotThrowAnyException();
    }
}
