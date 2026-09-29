package com.doggycredit.scoring;

import java.util.List;

public record BasicScoreResult(int score, ScoreBand band, List<ScoreReason> reasons, String modelVersion) {
}
