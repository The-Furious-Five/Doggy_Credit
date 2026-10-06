package com.doggycredit.recommendation.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.doggycredit.recommendation.CreditNeeds.CreditNeed;
import com.doggycredit.recommendation.CreditNeeds.HolderType;
import com.doggycredit.recommendation.CreditNeeds.NeedInput;
import com.doggycredit.recommendation.NeedCategory;

/** D-06 — criterio de "Lista cuando": pruebas por tipo de titular, con las reglas CA-01 a CA-04. */
class CreditNeedAnalyzerTest {

	private final CreditNeedAnalyzer analyzer = new CreditNeedAnalyzer(new CreditNeedAnalyzer.Properties(
			Map.of(NeedCategory.CAPITAL_TRABAJO, 0.115, NeedCategory.INVERSION_PRODUCTIVA, 0.115,
					NeedCategory.EFICIENCIA_ENERGETICA, 0.115, NeedCategory.CONSUMO_CONSOLIDACION, 0.18),
			Map.of(NeedCategory.CAPITAL_TRABAJO, 12, NeedCategory.INVERSION_PRODUCTIVA, 36,
					NeedCategory.EFICIENCIA_ENERGETICA, 36, NeedCategory.CONSUMO_CONSOLIDACION, 24)));

	@Nested
	class Persona {

		/** Reglas de Negocio §5.3: el ejemplo trabajado de Doña Justina. */
		@Test
		void donaJustinaCapitalDeTrabajoConFlujosObservados() {
			CreditNeed need = analyzer.analyze(new NeedInput(HolderType.PERSONA, "Capital de trabajo para su puesto",
					null, bd("4000"), List.of(bd("3400"), bd("3600"), bd("3500"), bd("3450"), bd("3550"), bd("3500")),
					bd("400"), null, 1, bd("15000"), 12));

			assertThat(need.category()).isEqualTo(NeedCategory.CAPITAL_TRABAJO);
			assertThat(need.netMonthlyIncome()).isEqualByComparingTo("3500.00"); // CA-01: menor(3.500, 4.000)
			assertThat(need.observedIncome()).isTrue();
			assertThat(need.maxInstallment()).isEqualByComparingTo("1000.00"); // CA-03: 40 % × 3.500 − 400
			// CA-04: el valor presente de Bs 1.000 a 12 meses (11,5 % anual) es menor que lo pedido.
			assertThat(need.suggestedAmount()).isEqualByComparingTo("11284.76");
			assertThat(need.suggestedTermMonths()).isEqualTo(12);
		}

		@Test
		void sinFlujosObservadosUsaEl70PorCientoDelDeclarado() {
			CreditNeed need = analyzer.analyze(new NeedInput(HolderType.PERSONA, "capital de trabajo", null,
					bd("4000"), List.of(), BigDecimal.ZERO, null, 0, bd("5000"), 12));
			assertThat(need.netMonthlyIncome()).isEqualByComparingTo("2800.00"); // CA-02
			assertThat(need.observedIncome()).isFalse();
			assertThat(need.maxInstallment()).isEqualByComparingTo("1120.00");
			assertThat(need.suggestedAmount()).isEqualByComparingTo("5000.00"); // cabe: se sugiere lo pedido
			assertThat(need.signals()).anyMatch(s -> s.contains("70 %"));
		}

		@Test
		void variosAcreedoresSinDestinoEsConsolidacion() {
			CreditNeed need = analyzer.analyze(new NeedInput(HolderType.PERSONA, null, null, bd("6000"),
					List.of(bd("5000")), bd("800"), null, 3, null, null));
			assertThat(need.category()).isEqualTo(NeedCategory.CONSUMO_CONSOLIDACION);
			assertThat(need.suggestedTermMonths()).isEqualTo(24);
			assertThat(need.signals()).anyMatch(s -> s.contains("3 acreedores"));
		}

		@Test
		void sinCapacidadDePagoNoSugiereMonto() {
			CreditNeed need = analyzer.analyze(new NeedInput(HolderType.PERSONA, "Consolidar deudas", null,
					bd("3000"), List.of(bd("3000")), bd("1500"), null, 4, bd("10000"), 24));
			assertThat(need.maxInstallment()).isEqualByComparingTo("0.00");
			assertThat(need.suggestedAmount()).isEqualByComparingTo("0.00");
			assertThat(need.signals()).anyMatch(s -> s.contains("Sin capacidad de pago"));
		}

	}

	@Nested
	class Pyme {

		@Test
		void paneleSolaresConCodigoVerdeEsEficienciaEnergetica() {
			CreditNeed need = analyzer.analyze(new NeedInput(HolderType.PYME, "Paneles solares para el taller",
					"VER-SOL", bd("20000"), List.of(bd("12000"), bd("13000")), bd("2000"), null, 2, null, null));
			assertThat(need.category()).isEqualTo(NeedCategory.EFICIENCIA_ENERGETICA);
			assertThat(need.netMonthlyIncome()).isEqualByComparingTo("12500.00");
			assertThat(need.maxInstallment()).isEqualByComparingTo("3000.00");
			assertThat(need.suggestedTermMonths()).isEqualTo(36);
			assertThat(need.suggestedAmount()).isEqualByComparingTo("90975.24"); // VP de 3.000 a 36 meses
		}

		@Test
		void maquinariaEsInversionProductiva() {
			CreditNeed need = analyzer.analyze(new NeedInput(HolderType.PYME, "Compra de maquinaría nueva", null,
					null, List.of(bd("9000")), bd("0"), null, 1, bd("20000"), 24));
			assertThat(need.category()).isEqualTo(NeedCategory.INVERSION_PRODUCTIVA);
			assertThat(need.suggestedAmount()).isEqualByComparingTo("20000.00");
		}

		@Test
		void ventasCrecientesSinDestinoEsCapitalDeTrabajo() {
			CreditNeed need = analyzer.analyze(new NeedInput(HolderType.PYME, null, null, bd("15000"),
					List.of(bd("10000")), bd("1000"), List.of(bd("40000"), bd("41000"), bd("48000"), bd("50000")), 1,
					null, null));
			assertThat(need.category()).isEqualTo(NeedCategory.CAPITAL_TRABAJO);
			assertThat(need.signals()).anyMatch(s -> s.contains("Ventas crecientes"));
		}

	}

	@Test
	void unCodigoVerdeGanaAlDestinoDeclaradoParaCualquierTitular() {
		CreditNeed need = analyzer.analyze(new NeedInput(HolderType.PERSONA, "consumo", "VER-EFI", bd("5000"),
				List.of(bd("5000")), BigDecimal.ZERO, null, 0, null, null));
		assertThat(need.category()).isEqualTo(NeedCategory.EFICIENCIA_ENERGETICA);
	}

	private static BigDecimal bd(String value) {
		return new BigDecimal(value);
	}

}
