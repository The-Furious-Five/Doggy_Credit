package com.doggycredit.scoring;

/**
 * @param probabilityOfDefault en [0,1]. "pd" en el contrato del informe
 *                             (§11.4): {score, banda, pd?, reasonCodes[],
 *                             modelVersion}. El score 0-1000 y las bandas
 *                             A-E se derivan de este valor en un paso
 *                             posterior (RF-SCO-02, Sprint 3), fuera del
 *                             alcance de SPIKE-3.
 * @param modelVersion         ej. "v1", tal como en model_card.json.
 */
public record ScoringResult(float probabilityOfDefault, String modelVersion) {
}
