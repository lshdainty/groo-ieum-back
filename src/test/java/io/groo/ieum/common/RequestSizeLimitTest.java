package io.groo.ieum.common;

import io.groo.ieum.holiday.controller.dto.HolidayApiDto;
import io.groo.ieum.notice.controller.NoticeApiController;
import io.groo.ieum.notice.service.NoticeService;
import io.groo.ieum.user.controller.dto.UserApiDto;
import io.groo.ieum.work.controller.dto.WorkApiDto;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("요청 크기 상한 테스트 (웹이 보내는 값 안에서만)")
class RequestSizeLimitTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("공지 목록 size 가 100 을 넘으면 100 으로 줄이고, 웹이 쓰는 50 · 10 은 그대로다")
    void noticePageSizeIsCapped() {
        NoticeService service = mock(NoticeService.class);
        given(service.searchActiveNotices(any())).willReturn(Page.empty());
        NoticeApiController controller = new NoticeApiController(service, mock(MessageSource.class));

        controller.searchActiveNotices(0, 100000);
        controller.searchActiveNotices(0, 50);
        controller.searchActiveNotices(0, 10);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(service, org.mockito.Mockito.times(3)).searchActiveNotices(captor.capture());
        assertThat(captor.getAllValues()).extracting(Pageable::getPageSize).containsExactly(100, 50, 10);
    }

    @Test
    @DisplayName("업무 일괄 등록 본문이 500 건을 넘으면 거절된다")
    void workBulkCapped() {
        assertThat(validator.validate(new WorkApiDto.BulkCreateWorkHistoryReq(Collections.nCopies(501, null)))).isNotEmpty();
        assertThat(validator.validate(new WorkApiDto.BulkCreateWorkHistoryReq(Collections.nCopies(500, null)))).isEmpty();
    }

    @Test
    @DisplayName("공휴일 일괄 저장 본문이 1000 건을 넘으면 거절된다")
    void holidayBulkCapped() {
        HolidayApiDto.BulkSaveHolidaysReq big = new HolidayApiDto.BulkSaveHolidaysReq(
                IntStream.range(0, 1001).mapToObj(i -> (HolidayApiDto.BulkSaveHolidayItem) null).toList());
        assertThat(validator.validate(big)).isNotEmpty();
    }

    @Test
    @DisplayName("대시보드 JSON 이 20000 자를 넘으면 거절된다")
    void dashboardCapped() {
        assertThat(validator.validate(new UserApiDto.UpdateDashboardReq("x".repeat(20001)))).isNotEmpty();
        assertThat(validator.validate(new UserApiDto.UpdateDashboardReq("x".repeat(20000)))).isEmpty();
    }
}
