package io.groo.ieum.notice.controller;

import io.groo.core.controller.ApiResponse;
import io.groo.core.type.DisplayType;
import io.groo.ieum.notice.controller.dto.NoticeApiDto;
import io.groo.ieum.notice.service.NoticeService;
import io.groo.ieum.notice.service.dto.NoticeServiceDto;
import io.groo.ieum.notice.type.NoticeType;
import io.groo.ieum.user.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
public class NoticeApiController implements NoticeApi {
    private final NoticeService noticeService;
    private final MessageSource messageSource;

    @Override
    @PreAuthorize("hasAuthority('NOTICE:MANAGE')")
    public ApiResponse createNotice(NoticeApiDto.CreateNoticeReq data, User loginUser) {
        // 작성자는 본문 writer_id 가 아니라 로그인 사용자다 (남의 이름으로 공지 방지)
        Long noticeId = noticeService.createNotice(NoticeServiceDto.builder()
                .writerId(loginUser.getId())
                .title(data.getTitle())
                .content(data.getContent())
                .type(data.getNoticeType())
                .isPinned(data.getIsPinned())
                .startDate(data.getStartDate())
                .endDate(data.getEndDate())
                .build()
        );

        return ApiResponse.success(new NoticeApiDto.CreateNoticeResp(noticeId));
    }

    @Override
    @PreAuthorize("hasAuthority('NOTICE:READ')")
    public ApiResponse searchNotice(Long noticeId) {
        NoticeServiceDto notice = noticeService.searchNoticeAndIncreaseViewCount(noticeId);

        return ApiResponse.success(new NoticeApiDto.SearchNoticeResp(
                notice.getId(),
                notice.getWriterId(),
                notice.getWriterName(),
                notice.getTitle(),
                notice.getContent(),
                notice.getType(),
                getTranslatedName(notice.getType()),
                notice.getIsPinned(),
                notice.getViewCount(),
                notice.getStartDate(),
                notice.getEndDate(),
                notice.getCreateDate(),
                notice.getModifyDate()
        ));
    }

    @Override
    @PreAuthorize("hasAuthority('NOTICE:READ')")
    public ApiResponse searchNotices(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<NoticeServiceDto> noticePage = noticeService.searchNotices(pageable);

        return ApiResponse.success(buildPageResponse(noticePage));
    }

    @Override
    @PreAuthorize("hasAuthority('NOTICE:READ')")
    public ApiResponse searchNoticesByType(NoticeType type, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<NoticeServiceDto> noticePage = noticeService.searchNoticesByType(type, pageable);

        return ApiResponse.success(buildPageResponse(noticePage));
    }

    @Override
    @PreAuthorize("hasAuthority('NOTICE:READ')")
    public ApiResponse searchNoticesByKeyword(String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<NoticeServiceDto> noticePage = noticeService.searchNoticesByKeyword(keyword, pageable);

        return ApiResponse.success(buildPageResponse(noticePage));
    }

    @Override
    @PreAuthorize("hasAuthority('NOTICE:READ')")
    public ApiResponse searchActiveNotices(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<NoticeServiceDto> noticePage = noticeService.searchActiveNotices(pageable);

        return ApiResponse.success(buildPageResponse(noticePage));
    }

    @Override
    @PreAuthorize("hasAuthority('NOTICE:READ')")
    public ApiResponse searchPinnedNotices(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<NoticeServiceDto> noticePage = noticeService.searchPinnedNotices(pageable);

        return ApiResponse.success(buildPageResponse(noticePage));
    }

    @Override
    @PreAuthorize("hasAuthority('NOTICE:MANAGE')")
    public ApiResponse updateNotice(Long noticeId, NoticeApiDto.UpdateNoticeReq data) {
        noticeService.updateNotice(noticeId, NoticeServiceDto.builder()
                .title(data.getTitle())
                .content(data.getContent())
                .type(data.getNoticeType())
                .isPinned(data.getIsPinned())
                .startDate(data.getStartDate())
                .endDate(data.getEndDate())
                .build()
        );

        return ApiResponse.success();
    }

    @Override
    @PreAuthorize("hasAuthority('NOTICE:MANAGE')")
    public ApiResponse deleteNotice(Long noticeId) {
        noticeService.deleteNotice(noticeId);
        return ApiResponse.success();
    }

    private String getTranslatedName(DisplayType type) {
        if (type == null) return null;
        return messageSource.getMessage(type.getMessageKey(), null, LocaleContextHolder.getLocale());
    }

    private Map<String, Object> buildPageResponse(Page<NoticeServiceDto> noticePage) {
        List<NoticeApiDto.SearchNoticesResp> content = noticePage.getContent().stream()
                .map(notice -> new NoticeApiDto.SearchNoticesResp(
                        notice.getId(),
                        notice.getWriterId(),
                        notice.getWriterName(),
                        notice.getTitle(),
                        notice.getType(),
                        getTranslatedName(notice.getType()),
                        notice.getIsPinned(),
                        notice.getViewCount(),
                        notice.getStartDate(),
                        notice.getEndDate(),
                        notice.getCreateDate()
                ))
                .toList();

        Map<String, Object> response = new HashMap<>();
        response.put("content", content);
        response.put("page", noticePage.getNumber());
        response.put("size", noticePage.getSize());
        response.put("totalElements", noticePage.getTotalElements());
        response.put("totalPages", noticePage.getTotalPages());
        response.put("first", noticePage.isFirst());
        response.put("last", noticePage.isLast());

        return response;
    }
}
