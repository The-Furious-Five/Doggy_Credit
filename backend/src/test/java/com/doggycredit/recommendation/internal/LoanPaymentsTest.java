package com.doggycredit.recommendation.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class LoanPaymentsTest {

    @Test
    void ceroInteresYUnMesRespetanElLimiteExacto() {
        assertThat(LoanPayments.installment(new BigDecimal("1200"), BigDecimal.ZERO, 12))
                .isEqualByComparingTo("100.00");
        assertThat(LoanPayments.installment(new BigDecimal("1000"), new BigDecimal("24"), 1))
                .isEqualByComparingTo("1020.00");
    }

    @Test
    void elMontoRedondeadoHaciaAbajoNoProduceUnaCuotaSuperiorALaCapacidad() {
        for (String rate : java.util.List.of("0", "0.01", "11.50", "99.99")) {
            for (int term : java.util.List.of(1, 12, 60, 360)) {
                for (String limit : java.util.List.of("0.01", "100.00", "1300.33")) {
                    BigDecimal capacity = new BigDecimal(limit);
                    BigDecimal amount = LoanPayments.affordableAmount(capacity, new BigDecimal(rate), term);
                    assertThat(LoanPayments.installment(amount, new BigDecimal(rate), term))
                            .as("tasa=%s, plazo=%s, capacidad=%s", rate, term, limit)
                            .isLessThanOrEqualTo(capacity);
                }
            }
        }
    }
}
