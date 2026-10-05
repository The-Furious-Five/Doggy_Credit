package com.doggycredit.scoring;

import java.util.UUID;

/**
 * Score de una evaluación según el plan del tenant (RF-SCO-01/02, D-02): PRO
 * usa el modelo avanzado ({@link ScoringModel}, ONNX) y FREE el scorecard
 * básico. Si el modelo avanzado falla, degrada al básico: nunca cae la
 * evaluación por el scoring. La versión usada queda en
 * {@link BasicScoreResult#modelVersion()}.
 */
public interface CreditScoring {

	BasicScoreResult score(UUID tenantId, ScorecardInput basicInput, ScoringFeatures features);

}
