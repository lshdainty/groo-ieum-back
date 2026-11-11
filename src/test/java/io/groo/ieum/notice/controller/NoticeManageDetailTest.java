package io.groo.ieum.notice.controller;

import io.groo.core.controller.ApiResponse;
import io.groo.core.type.YNType;
import io.groo.ieum.notice.controller.dto.NoticeApiDto;
import io.groo.ieum.notice.service.NoticeService;
import io.groo.ieum.notice.service.dto.NoticeServiceDto;
import io.groo.ieum.notice.type.NoticeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("공지 관리용 상세 조회 테스트")
class NoticeManageDetailTest {
    private final NoticeService noticeService = mock(NoticeService.class);
    private final NoticeApiController controller = new NoticeApiController(noticeService, mock(MessageSource.class));

    @Test
    @DisplayName("관리용 상세는 본문까지 주고 조회수는 올리지 않는다")
    void manageDetailReturnsContentWithoutViewCount() {
        given(noticeService.searchNotice(7L)).willReturn(NoticeServiceDto.builder()
                .id(7L)
                .title("제목")
                .content("본문 그대로")
                .type(NoticeType.GENERAL)
                .isPinned(YNType.N)
                .viewCount(3L)
                .build());

        ApiResponse resp = controller.searchNoticeForManage(7L);

        NoticeApiDto.SearchNoticeResp body = (NoticeApiDto.SearchNoticeResp) resp.getData();
        assertThat(body.getNoticeId()).isEqualTo(7L);
        assertThat(body.getContent()).isEqualTo("본문 그대로");
        assertThat(body.getViewCount()).isEqualTo(3L);
        verify(noticeService).searchNotice(7L);
        verify(noticeService, never()).searchNoticeAndIncreaseViewCount(anyLong());
    }

    @Test
    @DisplayName("관리용 상세는 NOTICE:MANAGE 가 있어야 한다 (읽기 권한만으로는 못 본다)")
    void manageDetailRequiresNoticeManage() throws Exception {
        PreAuthorize pa = NoticeApiController.class
                .getMethod("searchNoticeForManage", Long.class)
                .getAnnotation(PreAuthorize.class);

        assertThat(pa).isNotNull();
        assertThat(pa.value()).isEqualTo("hasAuthority('NOTICE:MANAGE')");
    }

    @Test
    @DisplayName("관리용 상세 주소는 /api/v1/notice/{id}/manage 다")
    void manageDetailPath() throws Exception {
        GetMapping mapping = NoticeApi.class
                .getMethod("searchNoticeForManage", Long.class)
                .getAnnotation(GetMapping.class);

        assertThat(mapping.value()).containsExactly("/api/v1/notice/{id}/manage");
    }

    @Test
    @DisplayName("일반 상세는 여전히 조회수를 올린다")
    void readerDetailStillIncreasesViewCount() {
        given(noticeService.searchNoticeAndIncreaseViewCount(7L)).willReturn(NoticeServiceDto.builder()
                .id(7L).title("제목").content("본문").type(NoticeType.GENERAL).isPinned(YNType.N).viewCount(4L).build());

        controller.searchNotice(7L);

        verify(noticeService).searchNoticeAndIncreaseViewCount(7L);
        verify(noticeService, never()).searchNotice(anyLong());
    }
}
