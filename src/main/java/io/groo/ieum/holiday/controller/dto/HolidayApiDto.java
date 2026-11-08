package io.groo.ieum.holiday.controller.dto;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;
import io.groo.core.type.CountryCode;
import io.groo.core.type.YNType;
import io.groo.ieum.holiday.type.HolidayType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

public class HolidayApiDto {
    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "공휴일 등록 요청")
    public static class RegistHolidayReq {
        @Schema(description = "공휴일 이름", example = "설날")
        private String holidayName;

        @Schema(description = "공휴일 날짜 (YYYY-MM-DD)", example = "2024-02-10")
        private LocalDate holidayDate;

        @Schema(description = "공휴일 타입", example = "NATIONAL")
        private HolidayType holidayType;

        @Schema(description = "국가 코드", example = "KR")
        private CountryCode countryCode;

        @Schema(description = "음력 여부", example = "Y")
        private YNType lunarYn;

        @Schema(description = "음력 날짜 (YYYY-MM-DD)", example = "2024-01-01")
        private LocalDate lunarDate;

        @Schema(description = "매년 반복 여부", example = "Y")
        private YNType isRecurring;

        @Schema(description = "아이콘", example = "🎉")
        private String holidayIcon;
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "공휴일 등록 응답")
    public static class RegistHolidayResp {
        @Schema(description = "등록된 공휴일 아이디", example = "1")
        private Long holidayId;
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "공휴일 수정 요청")
    public static class EditHolidayReq {
        @Schema(description = "공휴일 이름", example = "설날")
        private String holidayName;

        @Schema(description = "공휴일 날짜 (YYYY-MM-DD)", example = "2024-02-10")
        private LocalDate holidayDate;

        @Schema(description = "공휴일 타입", example = "NATIONAL")
        private HolidayType holidayType;

        @Schema(description = "국가 코드", example = "KR")
        private CountryCode countryCode;

        @Schema(description = "음력 여부", example = "Y")
        private YNType lunarYn;

        @Schema(description = "음력 날짜 (YYYY-MM-DD)", example = "2024-01-01")
        private LocalDate lunarDate;

        @Schema(description = "매년 반복 여부", example = "Y")
        private YNType isRecurring;

        @Schema(description = "아이콘", example = "🎉")
        private String holidayIcon;
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "공휴일 조회 응답")
    public static class SearchHolidaysResp {
        @Schema(description = "공휴일 아이디", example = "1")
        private Long holidayId;

        @Schema(description = "공휴일 이름", example = "설날")
        private String holidayName;

        @Schema(description = "공휴일 날짜 (YYYY-MM-DD)", example = "2024-02-10")
        private LocalDate holidayDate;

        @Schema(description = "공휴일 타입", example = "NATIONAL")
        private HolidayType holidayType;

        @Schema(description = "국가 코드", example = "KR")
        private CountryCode countryCode;

        @Schema(description = "음력 여부", example = "Y")
        private YNType lunarYn;

        @Schema(description = "음력 날짜 (YYYY-MM-DD)", example = "2024-01-01")
        private LocalDate lunarDate;

        @Schema(description = "매년 반복 여부", example = "Y")
        private YNType isRecurring;

        @Schema(description = "아이콘", example = "🎉")
        private String holidayIcon;
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "공휴일 수정 응답")
    public static class EditHolidayResp {
        @Schema(description = "공휴일 아이디", example = "1")
        private Long holidayId;

        @Schema(description = "공휴일 이름", example = "설날")
        private String holidayName;

        @Schema(description = "공휴일 날짜 (YYYY-MM-DD)", example = "2024-02-10")
        private LocalDate holidayDate;

        @Schema(description = "공휴일 타입", example = "NATIONAL")
        private HolidayType holidayType;

        @Schema(description = "국가 코드", example = "KR")
        private CountryCode countryCode;

        @Schema(description = "음력 여부", example = "Y")
        private YNType lunarYn;

        @Schema(description = "음력 날짜 (YYYY-MM-DD)", example = "2024-01-01")
        private LocalDate lunarDate;

        @Schema(description = "매년 반복 여부", example = "Y")
        private YNType isRecurring;

        @Schema(description = "아이콘", example = "🎉")
        private String holidayIcon;
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "반복 공휴일 프리뷰 응답")
    public static class PreviewRecurringHolidaysResp {
        @Schema(description = "공휴일 이름", example = "설날")
        private String holidayName;

        @Schema(description = "공휴일 날짜 (양력, YYYY-MM-DD)", example = "2025-01-29")
        private LocalDate holidayDate;

        @Schema(description = "공휴일 타입", example = "PUBLIC")
        private HolidayType holidayType;

        @Schema(description = "국가 코드", example = "KR")
        private CountryCode countryCode;

        @Schema(description = "음력 여부", example = "Y")
        private YNType lunarYn;

        @Schema(description = "음력 날짜 (YYYY-MM-DD)", example = "2025-01-01")
        private LocalDate lunarDate;

        @Schema(description = "매년 반복 여부", example = "N")
        private YNType isRecurring;

        @Schema(description = "아이콘", example = "🎆")
        private String holidayIcon;
    }

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "공휴일 일괄 저장 요청 (개별 항목)")
    public static class BulkSaveHolidayItem {
        @Schema(description = "공휴일 이름", example = "설날")
        private String holidayName;

        @Schema(description = "공휴일 날짜 (YYYY-MM-DD)", example = "2025-01-29")
        private LocalDate holidayDate;

        @Schema(description = "공휴일 타입", example = "PUBLIC")
        private HolidayType holidayType;

        @Schema(description = "국가 코드", example = "KR")
        private CountryCode countryCode;

        @Schema(description = "음력 여부", example = "Y")
        private YNType lunarYn;

        @Schema(description = "음력 날짜 (YYYY-MM-DD)", example = "2025-01-01")
        private LocalDate lunarDate;

        @Schema(description = "매년 반복 여부", example = "N")
        private YNType isRecurring;

        @Schema(description = "아이콘", example = "🎆")
        private String holidayIcon;
    }

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "공휴일 일괄 저장 요청")
    public static class BulkSaveHolidaysReq {
        @Schema(description = "저장할 공휴일 목록")
        @Size(max = 1000)
        private List<BulkSaveHolidayItem> holidays;
    }

    @Getter
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "공휴일 일괄 저장 응답")
    public static class BulkSaveHolidaysResp {
        @Schema(description = "저장된 공휴일 수", example = "15")
        private Integer savedCount;
    }
}