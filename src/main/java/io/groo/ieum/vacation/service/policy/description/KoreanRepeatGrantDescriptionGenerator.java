package io.groo.ieum.vacation.service.policy.description;

import io.groo.core.type.YNType;
import io.groo.ieum.vacation.domain.VacationPolicy;
import io.groo.ieum.vacation.type.GrantMethod;
import io.groo.ieum.vacation.type.RepeatUnit;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 한국어 반복 부여 정책 설명 생성기
 * 예시:
 * - "매년 1월 1일 부여"
 * - "2년마다 1월 1일 부여"
 * - "매월 15일 부여" · "2개월마다 1일 부여"
 * - "분기마다 1일 부여" · "2분기마다 1일 부여"
 * - "반기마다 부여" · "3일마다 부여"
 * 간격이 2 이상이면 "{N}{단위}마다" 하나로 말한다 — "2분기 간격으로 분기마다" 처럼 단위를 두 번 말하지 않는다.
 * 간격은 첫 부여일이 든 주기부터 센다(RepeatGrant.calculateNextGrantDate) — 글은 그 계산의 "N 주기마다" 와 같다.
 */
@Component
public class KoreanRepeatGrantDescriptionGenerator implements RepeatGrantDescriptionGenerator {

    @Override
    public String generate(VacationPolicy policy) {
        if (policy == null || policy.getGrantMethod() != GrantMethod.REPEAT_GRANT) {
            return null;
        }

        RepeatUnit repeatUnit = policy.getRepeatUnit();
        Integer repeatInterval = policy.getRepeatInterval();
        Integer specificMonths = policy.getSpecificMonths();
        Integer specificDays = policy.getSpecificDays();
        YNType isRecurring = policy.getIsRecurring();
        Integer maxGrantCount = policy.getMaxGrantCount();

        StringBuilder description = new StringBuilder();

        // 1. 반복 주기 — 간격 1 은 단위 이름(매년 · 매월 · 분기마다 · 반기마다 · 매일), 2 이상은 "{N}{단위}마다"(2년마다 · 2분기마다 · 3일마다)
        if (repeatUnit != null) {
            if (repeatInterval != null && repeatInterval > 1) {
                description.append(repeatInterval).append(getRepeatUnitSuffix(repeatUnit)).append("마다");
            } else {
                description.append(getRepeatUnitName(repeatUnit));
            }
        }

        // 2. 특정 월/일 (반복 단위에 따라)
        switch (repeatUnit) {
            case YEARLY:
                if (specificMonths != null) {
                    description.append(" ").append(specificMonths).append("월");
                }
                if (specificDays != null) {
                    description.append(" ").append(specificDays).append("일");
                }
                break;

            case MONTHLY:
                if (specificDays != null) {
                    description.append(" ").append(specificDays).append("일");
                }
                break;

            case QUARTERLY:
            case HALF:
                if (specificDays != null) {
                    description.append(" ").append(specificDays).append("일");
                }
                break;

            case DAILY:
                break;
        }

        // 3. "부여" 추가
        description.append(" 부여");

        // 4. 1회성 부여 여부
        if (YNType.isN(isRecurring) && maxGrantCount != null) {
            LocalDateTime firstGrantDate = policy.getFirstGrantDate();
            if (firstGrantDate != null && repeatUnit == RepeatUnit.YEARLY && repeatInterval != null) {
                int yearsLater = repeatInterval * (maxGrantCount - 1);
                if (yearsLater > 0) {
                    description.append(", ").append(yearsLater).append("년 후");
                }
            }
            description.append(" ").append(maxGrantCount).append("회 부여");
        }

        return description.toString();
    }

    private String getRepeatUnitName(RepeatUnit repeatUnit) {
        switch (repeatUnit) {
            case YEARLY:
                return "매년";
            case MONTHLY:
                return "매월";
            case DAILY:
                return "매일";
            case QUARTERLY:
                return "분기마다";
            case HALF:
                return "반기마다";
            default:
                return "";
        }
    }

    private String getRepeatUnitSuffix(RepeatUnit repeatUnit) {
        switch (repeatUnit) {
            case YEARLY:
                return "년";
            case MONTHLY:
                return "개월";
            case DAILY:
                return "일";
            case QUARTERLY:
                return "분기";
            case HALF:
                return "반기";
            default:
                return "";
        }
    }
}
