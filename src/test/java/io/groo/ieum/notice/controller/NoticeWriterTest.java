package io.groo.ieum.notice.controller;

import io.groo.core.type.CountryCode;
import io.groo.core.type.YNType;
import io.groo.ieum.notice.controller.dto.NoticeApiDto;
import io.groo.ieum.notice.service.NoticeService;
import io.groo.ieum.notice.service.dto.NoticeServiceDto;
import io.groo.ieum.notice.type.NoticeType;
import io.groo.ieum.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.MessageSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("공지 작성자 테스트")
class NoticeWriterTest {
    private final NoticeService noticeService = mock(NoticeService.class);
    private final NoticeApiController controller = new NoticeApiController(noticeService, mock(MessageSource.class));
    private final User me = User.createUser(
            null, "me", "나", "me@test.com", LocalDate.of(1990, 1, 1), "NONE", "9 ~ 18",
            LocalDate.now(), YNType.N, null, null, CountryCode.KR);

    @Test
    @DisplayName("본문의 writer_id 가 남의 id 여도 작성자는 로그인 사용자다")
    void writerIsLoginUserNotBody() {
        given(noticeService.createNotice(any())).willReturn(1L);
        NoticeApiDto.CreateNoticeReq req = new NoticeApiDto.CreateNoticeReq(
                "someone-else", "제목", "내용", NoticeType.GENERAL, YNType.N, null, null);

        controller.createNotice(req, me);

        ArgumentCaptor<NoticeServiceDto> captor = ArgumentCaptor.forClass(NoticeServiceDto.class);
        verify(noticeService).createNotice(captor.capture());
        assertThat(captor.getValue().getWriterId()).isEqualTo("me");
    }
}
