package com.doggycredit.billing.internal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.doggycredit.billing.PlanCatalog;
import com.doggycredit.billing.PlanInfo;
import com.doggycredit.billing.QuotaExceededException;
import com.doggycredit.billing.UsageMeter;

class QuotaGuardImplTest {
    private final PlanCatalog plans = mock(PlanCatalog.class);
    private final UsageMeter usage = mock(UsageMeter.class);
    private final QuotaGuardImpl guard = new QuotaGuardImpl(plans, usage);
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void configurePlans() {
        when(plans.requireById("FREE")).thenReturn(new PlanInfo("FREE", "Gratis", 500, 3, 1, false, false, false, false, false));
        when(plans.requireById("PRO")).thenReturn(new PlanInfo("PRO", "Pro", 0, 50, 999, true, true, true, true, true));
    }

    @Test
    void reservesUsageAtomicallyWhenUnderTheMonthlyLimit() {
        when(usage.tryRecordEvaluation(tenantId, 500)).thenReturn(true);

        assertThatCode(() -> guard.requireEvaluationQuota(tenantId, "FREE")).doesNotThrowAnyException();
        verify(usage).tryRecordEvaluation(tenantId, 500);
    }

    @Test
    void rejectsWhenTheAtomicReservationFindsTheQuotaExhausted() {
        when(usage.tryRecordEvaluation(tenantId, 500)).thenReturn(false);

        assertThatThrownBy(() -> guard.requireEvaluationQuota(tenantId, "FREE"))
                .isInstanceOf(QuotaExceededException.class);
    }

    @Test
    void recordsUsageForUnlimitedPlansWithoutRejectingIt() {
        when(usage.tryRecordEvaluation(tenantId, 0)).thenReturn(true);

        assertThatCode(() -> guard.requireEvaluationQuota(tenantId, "PRO")).doesNotThrowAnyException();
        verify(usage).tryRecordEvaluation(tenantId, 0);
    }
}
