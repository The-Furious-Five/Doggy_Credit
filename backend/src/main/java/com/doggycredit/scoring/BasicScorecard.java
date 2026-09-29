package com.doggycredit.scoring;

/** Scorecard transparente del plan Free; no depende del modelo ONNX. */
public interface BasicScorecard {

	BasicScoreResult score(ScorecardInput input);

}
