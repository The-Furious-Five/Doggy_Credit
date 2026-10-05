package com.doggycredit.scoring.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.doggycredit.scoring.ScoringModel;

/** D-02: el modelo avanzado se carga una sola vez, al arrancar, en la versión configurada. */
@Configuration(proxyBeanMethods = false)
class ScoringConfiguration {

	@Bean(destroyMethod = "close")
	OnnxScoringAdapter scoringModel(@Value("${doggycredit.scoring.model-version}") String modelVersion) {
		return new OnnxScoringAdapter(modelVersion);
	}

	@Bean
	ModelParameters scoringModelParameters(@Value("${doggycredit.scoring.model-version}") String modelVersion) {
		return ModelParameters.load(modelVersion);
	}

	@Bean
	AdvancedScorer advancedScorer(ScoringModel scoringModel, ModelParameters scoringModelParameters) {
		return new AdvancedScorer(scoringModel, scoringModelParameters);
	}

}
