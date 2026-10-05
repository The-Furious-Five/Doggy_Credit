package com.doggycredit.recommendation.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.doggycredit.recommendation.internal.CreditProduct.Type;
import com.doggycredit.recommendation.internal.EligibilityEngine.Criterion;
import com.doggycredit.recommendation.internal.EligibilityEngine.Evaluation;
import com.doggycredit.recommendation.internal.EligibilityEngine.HolderType;
import com.doggycredit.recommendation.internal.EligibilityEngine.Request;
import com.doggycredit.scoring.ScoreBand;

/** C-03 (LOS-83): los 3 tipos de producto, el caso «ningún producto aplica» y el orden por ajuste. */
class EligibilityEngineTest {

	private final EligibilityEngine engine = new EligibilityEngine();

	@Test
	void personaConCapitalDeTrabajoSoloCalificaAlMicrocredito() {
		List<Evaluation> result = engine.evaluate(
				new Request(ScoreBand.B, new BigDecimal("5000"), 12, HolderType.PERSONA, false), catalogo());

		assertThat(elegibles(result)).containsExactly(Type.MICROCREDITO);
		assertThat(evaluacion(result, Type.MICROCREDITO).reason()).isEqualTo("Elegible: cumple banda, monto y plazo");
		assertThat(evaluacion(result, Type.VERDE).reason()).contains("requiere un destino verde");
		// Los no elegibles van después de los elegibles.
		assertThat(result.get(0).product().type()).isEqualTo(Type.MICROCREDITO);
	}

	@Test
	void empresaConBandaBCalificaAlCreditoPyme() {
		List<Evaluation> result = engine.evaluate(
				new Request(ScoreBand.B, new BigDecimal("100000"), 36, HolderType.EMPRESA, false), catalogo());

		assertThat(elegibles(result)).containsExactly(Type.PYME);
		assertThat(criterio(evaluacion(result, Type.MICROCREDITO), Criterion.Code.HOLDER_TYPE).met()).isFalse();
	}

	@Test
	void destinoVerdeRecomiendaPrimeroElCreditoEcologico() {
		List<Evaluation> result = engine.evaluate(
				new Request(ScoreBand.C, new BigDecimal("15000"), 24, HolderType.PERSONA, true), catalogo());

		assertThat(elegibles(result)).containsExactly(Type.VERDE, Type.MICROCREDITO);
		assertThat(result.get(0).reason()).startsWith("Recomendado: financia el destino verde");
	}

	@Test
	void empresaConDestinoVerdeCalificaAEcologicoYPyme() {
		List<Evaluation> result = engine.evaluate(
				new Request(ScoreBand.A, new BigDecimal("120000"), 48, HolderType.EMPRESA, true), catalogo());

		assertThat(elegibles(result)).containsExactly(Type.VERDE, Type.PYME);
	}

	@Test
	void ningunProductoAplicaYCadaUnoDiceElPorQue() {
		List<Evaluation> result = engine.evaluate(
				new Request(ScoreBand.D, new BigDecimal("5000"), 12, HolderType.PERSONA, false), catalogo());

		assertThat(elegibles(result)).isEmpty();
		assertThat(result).hasSize(3).allSatisfy(evaluation -> assertThat(evaluation.reason()).isNotBlank());
		assertThat(evaluacion(result, Type.MICROCREDITO).reason()).isEqualTo("Banda D por debajo de la mínima C");
	}

	@Test
	void unProductoInactivoNoSeLista() {
		List<CreditProduct> catalogo = List.of(producto(Type.MICROCREDITO, "1000", "20000", 3, 24, ScoreBand.C, true),
				producto(Type.PYME, "50000", "700000", 6, 84, ScoreBand.B, false));

		List<Evaluation> result = engine.evaluate(
				new Request(ScoreBand.A, new BigDecimal("60000"), 24, HolderType.EMPRESA, false), catalogo);

		assertThat(result).extracting(evaluation -> evaluation.product().type()).containsExactly(Type.MICROCREDITO);
	}

	@Test
	void aIgualDestinoGanaElProductoDondeElMontoQuedaMasCentrado() {
		// Dos productos que cubren 15.000: en el primero queda en un extremo, en el segundo al centro.
		List<CreditProduct> catalogo = List.of(producto(Type.MICROCREDITO, "1000", "16000", 3, 24, ScoreBand.C, true),
				producto(Type.VERDE, "10000", "20000", 3, 24, ScoreBand.C, true));

		List<Evaluation> result = engine.evaluate(
				new Request(ScoreBand.B, new BigDecimal("15000"), 12, HolderType.PERSONA, true), catalogo);

		// El verde gana por destino; sin destino verde, gana el monto más centrado.
		assertThat(elegibles(result)).containsExactly(Type.VERDE, Type.MICROCREDITO);
		List<Evaluation> sinVerde = engine.evaluate(
				new Request(ScoreBand.B, new BigDecimal("15000"), 12, HolderType.PERSONA, false),
				List.of(catalogo.get(0), producto(Type.MICROCREDITO, "10000", "20000", 3, 24, ScoreBand.C, true)));
		assertThat(sinVerde.get(0).product().maxAmount()).isEqualByComparingTo("20000");
	}

	@Test
	void cadaProductoTraeLosCincoCriteriosConSuTexto() {
		Evaluation micro = evaluacion(engine.evaluate(
				new Request(ScoreBand.B, new BigDecimal("25000"), 12, HolderType.PERSONA, false), catalogo()),
				Type.MICROCREDITO);

		assertThat(micro.eligible()).isFalse();
		assertThat(micro.criteria()).extracting(Criterion::code).containsExactly(Criterion.Code.BAND,
				Criterion.Code.AMOUNT, Criterion.Code.TERM, Criterion.Code.HOLDER_TYPE, Criterion.Code.DESTINATION);
		assertThat(criterio(micro, Criterion.Code.AMOUNT).description())
			.isEqualTo("Monto Bs 25.000 fuera del rango Bs 1.000 – 20.000");
		assertThat(criterio(micro, Criterion.Code.BAND).met()).isTrue();
	}

	// --- utilidades ------------------------------------------------------------

	/** El catálogo de la semilla de C-02, con los tres productos activos. */
	private static List<CreditProduct> catalogo() {
		return List.of(producto(Type.MICROCREDITO, "1000", "20000", 3, 24, ScoreBand.C, true),
				producto(Type.VERDE, "10000", "150000", 12, 60, ScoreBand.C, true),
				producto(Type.PYME, "50000", "700000", 6, 84, ScoreBand.B, true));
	}

	private static CreditProduct producto(Type type, String min, String max, int minTerm, int maxTerm,
			ScoreBand minBand, boolean active) {
		return new CreditProduct(UUID.randomUUID(), type, new BigDecimal(min), new BigDecimal(max), minTerm, maxTerm,
				new BigDecimal("11.50"), minBand, active);
	}

	private static List<Type> elegibles(List<Evaluation> result) {
		return result.stream().filter(Evaluation::eligible).map(evaluation -> evaluation.product().type()).toList();
	}

	private static Evaluation evaluacion(List<Evaluation> result, Type type) {
		return result.stream().filter(evaluation -> evaluation.product().type() == type).findFirst().orElseThrow();
	}

	private static Criterion criterio(Evaluation evaluation, Criterion.Code code) {
		return evaluation.criteria().stream().filter(criterion -> criterion.code() == code).findFirst().orElseThrow();
	}

}
