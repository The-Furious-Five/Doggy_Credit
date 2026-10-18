package com.doggycredit.recommendation.internal;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/** Anualidad fija sin comisiones; tasa anual porcentual del catálogo. Nunca redondea a favor del monto. */
final class LoanPayments {

    private static final MathContext MC = MathContext.DECIMAL128;

    private LoanPayments() { }

    static BigDecimal affordableAmount(BigDecimal maxInstallment, BigDecimal annualRate, int months) {
        return maxInstallment.multiply(factor(annualRate, months), MC).setScale(2, RoundingMode.DOWN);
    }

    static BigDecimal installment(BigDecimal amount, BigDecimal annualRate, int months) {
        return amount.divide(factor(annualRate, months), 2, RoundingMode.CEILING);
    }

    private static BigDecimal factor(BigDecimal annualRate, int months) {
        if (months <= 0) {
            throw new IllegalArgumentException("El plazo debe ser positivo");
        }
        if (annualRate.signum() == 0) {
            return BigDecimal.valueOf(months);
        }
        BigDecimal monthly = annualRate.divide(new BigDecimal("1200"), MC);
        BigDecimal accumulated = BigDecimal.ONE.add(monthly, MC).pow(months, MC);
        return BigDecimal.ONE.subtract(BigDecimal.ONE.divide(accumulated, MC), MC).divide(monthly, MC);
    }
}
