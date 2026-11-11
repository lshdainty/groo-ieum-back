package io.groo.ieum.vacation.controller.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("휴가(보상휴가) 신청 요청 검증 - 시작 ≤ 끝")
class RequestVacationReqValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private VacationApiDto.RequestVacationReq req(LocalDateTime start, LocalDateTime end) {
        return new VacationApiDto.RequestVacationReq(
                1L, "초과근무 보상", List.of("admin1"), new BigDecimal("3.0"), start, end, "사유");
    }

    @Test
    @DisplayName("끝이 다음 날인 초과근무(22:00 → 다음 날 01:00)를 받는다")
    void acceptsEndOnNextDay() {
        var violations = validator.validate(req(
                LocalDateTime.of(2026, 11, 6, 22, 0), LocalDateTime.of(2026, 11, 7, 1, 0)));
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("시작과 끝이 같아도 받는다(allowEqual 기본 true)")
    void acceptsEqual() {
        LocalDateTime t = LocalDateTime.of(2026, 11, 6, 22, 0);
        assertThat(validator.validate(req(t, t))).isEmpty();
    }

    @Test
    @DisplayName("끝이 시작보다 이르면(같은 날 01:00) 거절한다 — 옛 웹이 보내던 값")
    void rejectsEndBeforeStart() {
        var violations = validator.validate(req(
                LocalDateTime.of(2026, 11, 6, 22, 0), LocalDateTime.of(2026, 11, 6, 1, 0)));
        assertThat(violations).hasSize(1);
    }

    @Test
    @DisplayName("끝이 없으면(하루짜리 신청) 받는다")
    void acceptsNullEnd() {
        assertThat(validator.validate(req(LocalDateTime.of(2026, 11, 6, 9, 0), null))).isEmpty();
    }
}
