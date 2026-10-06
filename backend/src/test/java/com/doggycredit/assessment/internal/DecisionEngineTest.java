package com.doggycredit.assessment.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.doggycredit.assessment.AssessmentStatus;
import com.doggycredit.assessment.internal.PolicyRule.DecisionFacts;
import com.doggycredit.scoring.ScoreBand;

/** C-05: una prueba por regla, el caso PARCIAL y "nunca hay rechazo automático". */
class DecisionEngineTest {

	private static final Set<PolicyRule> ALL = EnumSet.allOf(PolicyRule.class);

	/** Titular sólido: score alto, banda A, resultado completo, monto moderado y 3 fuentes. */
	private static DecisionFacts solid() {
		return new DecisionFacts(820, ScoreBand.A, false, new BigDecimal("5000"), 3);
	}

	@Test
	void preaprobacionPorScore() {
		assertThat(DecisionEngine.decide(solid(), Set.of(PolicyRule.PREAPROBACION_SCORE)))
			.isEqualTo(AssessmentStatus.PREAPROBADO);
		var bajo = new DecisionFacts(649, ScoreBand.B, false, new BigDecimal("5000"), 3);
		assertThat(DecisionEngine.decide(bajo, Set.of(PolicyRule.PREAPROBACION_SCORE)))
			.isEqualTo(AssessmentStatus.REVISION_MANUAL);
	}

	@Test
	void montoAltoDerivaARevisionAunqueElScoreSeaAlto() {
		var alto = new DecisionFacts(820, ScoreBand.A, false, new BigDecimal("200000.01"), 3);
		assertThat(DecisionEngine.decide(alto, ALL)).isEqualTo(AssessmentStatus.REVISION_MANUAL);
		var justo = new DecisionFacts(820, ScoreBand.A, false, new BigDecimal("200000"), 3);
		assertThat(DecisionEngine.decide(justo, Set.of(PolicyRule.MONTO_ALTO, PolicyRule.PREAPROBACION_SCORE)))
			.isEqualTo(AssessmentStatus.PREAPROBADO);
	}

	@Test
	void bandaBajaDerivaARevision() {
		var bandaD = new DecisionFacts(340, ScoreBand.D, false, new BigDecimal("5000"), 3);
		assertThat(DecisionEngine.decide(bandaD, Set.of(PolicyRule.BANDA_BAJA)))
			.isEqualTo(AssessmentStatus.REVISION_MANUAL);
		assertThat(PolicyRule.BANDA_BAJA.matches(solid())).isFalse();
	}

	@Test
	void datosInsuficientesPideDatosYGanaALasDemas() {
		var unaFuente = new DecisionFacts(820, ScoreBand.A, false, new BigDecimal("300000"), 1);
		assertThat(DecisionEngine.decide(unaFuente, ALL)).isEqualTo(AssessmentStatus.SOLICITAR_DATOS);
		assertThat(PolicyRule.DATOS_INSUFICIENTES.matches(solid())).isFalse();
	}

	@Test
	void unaReglaPausadaNoSeAplica() {
		var unaFuente = new DecisionFacts(820, ScoreBand.A, false, new BigDecimal("5000"), 1);
		var activas = EnumSet.complementOf(EnumSet.of(PolicyRule.DATOS_INSUFICIENTES));
		assertThat(DecisionEngine.decide(unaFuente, activas)).isEqualTo(AssessmentStatus.PREAPROBADO);
	}

	@Test
	void sinReglasQueApliquenQuedaEnRevisionManual() {
		assertThat(DecisionEngine.decide(solid(), Set.of())).isEqualTo(AssessmentStatus.REVISION_MANUAL);
	}

	@Test
	void unResultadoParcialNuncaSePreaprueba() {
		var parcial = new DecisionFacts(820, ScoreBand.A, true, new BigDecimal("5000"), 2);
		assertThat(DecisionEngine.decide(parcial, ALL)).isEqualTo(AssessmentStatus.PARCIAL);
		assertThat(DecisionEngine.decide(parcial, Set.of(PolicyRule.PREAPROBACION_SCORE)))
			.isEqualTo(AssessmentStatus.PARCIAL);
	}

	@Test
	void ningunaCombinacionProduceUnRechazoNiPreapruebaUnParcial() {
		List<Set<PolicyRule>> subsets = new ArrayList<>();
		for (int mask = 0; mask < (1 << PolicyRule.values().length); mask++) {
			Set<PolicyRule> subset = EnumSet.noneOf(PolicyRule.class);
			for (PolicyRule rule : PolicyRule.values()) {
				if ((mask & (1 << rule.ordinal())) != 0) {
					subset.add(rule);
				}
			}
			subsets.add(subset);
		}
		int checked = 0;
		for (Set<PolicyRule> rules : subsets) {
			for (int score : new int[] { 0, 299, 450, 649, 650, 1000 }) {
				for (ScoreBand band : ScoreBand.values()) {
					for (boolean partial : new boolean[] { false, true }) {
						for (BigDecimal amount : new BigDecimal[] { null, BigDecimal.ONE, new BigDecimal("999999") }) {
							for (int sources = 0; sources <= 3; sources++) {
								var facts = new DecisionFacts(score, band, partial, amount, sources);
								AssessmentStatus decision = DecisionEngine.decide(facts, rules);
								assertThat(decision).isIn(DecisionEngine.POSSIBLE_DECISIONS);
								if (partial) {
									assertThat(decision).isEqualTo(AssessmentStatus.PARCIAL);
								}
								checked++;
							}
						}
					}
				}
			}
		}
		assertThat(checked).isGreaterThan(10_000);
		// Ninguna regla tiene un efecto distinto de los permitidos (no existe un "RECHAZADO").
		assertThat(PolicyRule.values()).extracting(PolicyRule::effect)
			.allMatch(DecisionEngine.POSSIBLE_DECISIONS::contains);
	}

}
