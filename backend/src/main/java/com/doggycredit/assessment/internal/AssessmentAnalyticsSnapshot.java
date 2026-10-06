package com.doggycredit.assessment.internal;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.doggycredit.billing.Feature;
import com.doggycredit.billing.PlanGate;
import com.doggycredit.assessment.AssessmentCommand;
import com.doggycredit.consent.Source;
import com.doggycredit.customer.ApplicantProfile;
import com.doggycredit.customer.ApplicantSegment;
import com.doggycredit.integration.BankReport;
import com.doggycredit.integration.BureauReport;
import com.doggycredit.integration.WalletReport;
import com.doggycredit.recommendation.CreditNeeds;
import com.doggycredit.recommendation.CreditNeeds.HolderType;
import com.doggycredit.recommendation.ProductRecommendations;
import com.doggycredit.scoring.ScoreBand;

/** Captura D-06/C-03 una vez; analítica nunca recalcula el histórico. */
@Component
class AssessmentAnalyticsSnapshot {

    private final CreditNeeds needs;
    private final ProductRecommendations recommendations;
    private final PlanGate planGate;

    AssessmentAnalyticsSnapshot(CreditNeeds needs, ProductRecommendations recommendations, PlanGate planGate) {
        this.needs = needs;
        this.recommendations = recommendations;
        this.planGate = planGate;
    }

    Snapshot capture(AssessmentCommand command, ApplicantProfile applicant, SourceCollection collection,
            ScoreBand band, String currency, String planId) {
        var bureau = SynchronousAssessmentService.bureauReport(collection);
        HolderType type = applicant.segment() == ApplicantSegment.PYME
                || bureau.map(report -> report.tipo() == BureauReport.TipoTitular.PYME).orElse(false)
                ? HolderType.PYME : HolderType.PERSONA;
        BigDecimal installments = bureau.map(report -> report.endeudamiento().stream()
                .map(debt -> debt.cuota().amount()).reduce(BigDecimal.ZERO, BigDecimal::add)).orElse(null);
        String destination = command.purpose() == null || command.purpose().isBlank()
                ? applicant.creditDestination() : command.purpose();
        var need = needs.analyze(new CreditNeeds.NeedInput(type, destination, null,
                applicant.monthlyIncome().amount(), observedFlows(collection), installments, List.of(),
                bureau.map(report -> report.endeudamiento().size()).orElse(null),
                command.requestedAmount(), command.termMonths()));
        // No mezclar monedas ni emitir una recomendación con fuentes fallidas.
        var recommendation = collection.partial() || !currency.equals(applicant.monthlyIncome().currency().getCurrencyCode())
                || !planGate.isAvailable(planId, Feature.RECOMENDACIONES)
                ? Optional.<ProductRecommendations.Recommendation>empty()
                : recommendations.first(command.tenantId(), currency, band, type, need);
        return new Snapshot(need.category().name(), recommendation.map(ProductRecommendations.Recommendation::productType).orElse(null),
                recommendation.map(ProductRecommendations.Recommendation::amount).orElse(null));
    }

    private List<BigDecimal> observedFlows(SourceCollection collection) {
        var bank = collection.payload(Source.BANCO).filter(BankReport.class::isInstance).map(BankReport.class::cast);
        if (bank.isPresent()) {
            List<BigDecimal> flows = new ArrayList<>();
            var report = bank.get();
            for (int i = Math.max(0, report.ingresosMensuales().size() - 6);
                    i < Math.min(report.ingresosMensuales().size(), report.egresosMensuales().size()); i++) {
                flows.add(report.ingresosMensuales().get(i).amount().subtract(report.egresosMensuales().get(i).amount()));
            }
            return flows;
        }
        return collection.payload(Source.WALLET).filter(WalletReport.class::isInstance).map(WalletReport.class::cast)
                .map(report -> report.meses().stream().skip(Math.max(0, report.meses().size() - 6))
                        .map(month -> month.recibido().amount().subtract(month.enviado().amount())).toList())
                .orElse(List.of());
    }

    record Snapshot(String needCategory, String productType, BigDecimal recommendedAmount) { }
}
