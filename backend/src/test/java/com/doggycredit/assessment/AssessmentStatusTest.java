package com.doggycredit.assessment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.EnumSet;

import org.junit.jupiter.api.Test;

/** C-05: qué estado se publica y qué decisión corresponde a cada estado interno. */
class AssessmentStatusTest {

	@Test
	void soloSolicitarDatosSePublicaComoRevisionManual() {
		assertThat(AssessmentStatus.SOLICITAR_DATOS.published()).isEqualTo(AssessmentStatus.REVISION_MANUAL);
		Arrays.stream(AssessmentStatus.values()).filter(s -> s != AssessmentStatus.SOLICITAR_DATOS)
			.forEach(s -> assertThat(s.published()).isEqualTo(s));
	}

	@Test
	void parcialNuncaEsUnaPreaprobacionYNingunaDecisionEsUnRechazo() {
		assertThat(AssessmentStatus.PARCIAL.decision()).contains(AssessmentStatus.REVISION_MANUAL);
		assertThat(AssessmentStatus.PENDIENTE.decision()).isEmpty();
		assertThat(AssessmentStatus.FALLIDA.decision()).isEmpty();
		var allowed = EnumSet.of(AssessmentStatus.PREAPROBADO, AssessmentStatus.REVISION_MANUAL,
				AssessmentStatus.SOLICITAR_DATOS);
		Arrays.stream(AssessmentStatus.values())
			.forEach(s -> s.decision().ifPresent(d -> assertThat(allowed).contains(d)));
	}

}
