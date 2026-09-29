package com.doggycredit.scoring;

/** Razón explicable y su aporte de puntos; la lista se ordena por impacto absoluto. */
public record ScoreReason(String code, int pointImpact) {
}
