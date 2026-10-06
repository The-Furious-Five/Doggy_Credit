package com.doggycredit.recommendation.internal;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;

import com.doggycredit.recommendation.CreditNeeds;
import com.doggycredit.recommendation.NeedCategory;

/**
 * D-06: necesidad de crédito y capacidad de pago (Reglas de Negocio §5.2).
 * <ul>
 * <li>CA-01: ingreso neto = promedio de los flujos netos observados; si además
 * hay declarado, el menor de los dos.</li>
 * <li>CA-02: sin flujos observados, 70 % del declarado (y no se preaprueba).</li>
 * <li>CA-03: cuota máxima = 40 % del ingreso neto − cuotas vigentes del buró.</li>
 * <li>CA-04: monto sugerido = mínimo entre lo solicitado y el valor presente
 * de la cuota máxima al plazo y la tasa de referencia de la necesidad. El
 * máximo y la tasa de cada producto los aplica C-03.</li>
 * </ul>
 */
@Service
class CreditNeedAnalyzer implements CreditNeeds {

	private static final BigDecimal UNOBSERVED_INCOME_FACTOR = new BigDecimal("0.70");
	private static final BigDecimal MAX_INSTALLMENT_RATIO = new BigDecimal("0.40");
	private static final MathContext MC = MathContext.DECIMAL64;

	/** Palabras del destino declarado, ya sin tildes y en minúsculas, por necesidad. */
	private static final Map<NeedCategory, List<String>> KEYWORDS = Map.of(
			NeedCategory.EFICIENCIA_ENERGETICA, List.of("solar", "panel", "energ", "eficien", "electric", "led",
					"aislamiento", "biodigestor", "riego tecnificado"),
			NeedCategory.INVERSION_PRODUCTIVA, List.of("maquinaria", "maquina", "equipo", "activo fijo", "ampliacion",
					"vehiculo de trabajo", "infraestructura", "local", "inversion", "herramienta"),
			NeedCategory.CAPITAL_TRABAJO, List.of("capital", "mercaderia", "inventario", "stock", "insumo",
					"materia prima", "temporada", "compra de productos"),
			NeedCategory.CONSUMO_CONSOLIDACION, List.of("consolid", "deuda", "consumo", "personal", "estudio",
					"salud", "vivienda", "viaje", "electrodomestico"));

	private final Properties properties;

	CreditNeedAnalyzer(Properties properties) {
		this.properties = properties;
	}

	@Override
	public CreditNeed analyze(NeedInput input) {
		Objects.requireNonNull(input.holderType(), "holderType");
		List<String> signals = new ArrayList<>();
		NeedCategory category = category(input, signals);

		List<BigDecimal> flows = input.observedNetFlows() == null ? List.of()
				: input.observedNetFlows().stream().filter(Objects::nonNull).limit(6).toList();
		boolean observed = !flows.isEmpty();
		BigDecimal declared = input.declaredMonthlyIncome();
		BigDecimal netIncome;
		if (observed) {
			BigDecimal average = flows.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
				.divide(BigDecimal.valueOf(flows.size()), MC);
			netIncome = declared == null ? average : average.min(declared);
		}
		else {
			netIncome = declared == null ? BigDecimal.ZERO : declared.multiply(UNOBSERVED_INCOME_FACTOR, MC);
			signals.add("Ingreso sin flujos observados: se toma el 70 % del declarado");
		}
		netIncome = netIncome.max(BigDecimal.ZERO);

		BigDecimal installments = input.currentInstallments() == null ? BigDecimal.ZERO : input.currentInstallments();
		BigDecimal maxInstallment = netIncome.multiply(MAX_INSTALLMENT_RATIO, MC).subtract(installments).max(BigDecimal.ZERO);
		if (maxInstallment.signum() == 0) {
			signals.add("Sin capacidad de pago: las cuotas vigentes ya alcanzan el 40 % del ingreso");
		}

		int term = input.requestedTermMonths() != null && input.requestedTermMonths() > 0 ? input.requestedTermMonths()
				: properties.defaultTermMonths().getOrDefault(category, 12);
		BigDecimal affordable = presentValue(maxInstallment, properties.referenceAnnualRate().get(category), term);
		BigDecimal suggested = input.requestedAmount() != null && input.requestedAmount().signum() > 0
				? affordable.min(input.requestedAmount()) : affordable;

		return new CreditNeed(category, money(netIncome), observed, money(maxInstallment), money(suggested), term,
				List.copyOf(signals));
	}

	private static NeedCategory category(NeedInput input, List<String> signals) {
		if (input.greenDestinationCode() != null && input.greenDestinationCode().startsWith("VER-")) {
			signals.add("Destino verde declarado (" + input.greenDestinationCode() + ")");
			return NeedCategory.EFICIENCIA_ENERGETICA;
		}
		String destination = normalize(input.declaredDestination());
		for (NeedCategory category : List.of(NeedCategory.EFICIENCIA_ENERGETICA, NeedCategory.INVERSION_PRODUCTIVA,
				NeedCategory.CONSUMO_CONSOLIDACION, NeedCategory.CAPITAL_TRABAJO)) {
			if (KEYWORDS.get(category).stream().anyMatch(destination::contains)) {
				signals.add("El destino declarado indica " + category.label().toLowerCase(Locale.ROOT));
				return category;
			}
		}
		if (input.holderType() == HolderType.PYME) {
			if (growing(input.monthlySales())) {
				signals.add("Ventas crecientes: la PYME necesita financiar su operación");
			}
			else {
				signals.add("Empresa sin destino específico: se asume capital de trabajo");
			}
			return NeedCategory.CAPITAL_TRABAJO;
		}
		if (input.creditors() != null && input.creditors() >= 2) {
			signals.add(input.creditors() + " acreedores vigentes: conviene consolidar");
			return NeedCategory.CONSUMO_CONSOLIDACION;
		}
		signals.add("Sin destino específico: se asume consumo");
		return NeedCategory.CONSUMO_CONSOLIDACION;
	}

	/** Ventas crecientes: el promedio de la segunda mitad supera en 10 % al de la primera. */
	private static boolean growing(List<BigDecimal> sales) {
		if (sales == null || sales.size() < 4) {
			return false;
		}
		int half = sales.size() / 2;
		BigDecimal first = average(sales.subList(0, half));
		BigDecimal second = average(sales.subList(sales.size() - half, sales.size()));
		return second.compareTo(first.multiply(new BigDecimal("1.10"), MC)) > 0;
	}

	private static BigDecimal average(List<BigDecimal> values) {
		return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(values.size()), MC);
	}

	/** Valor presente de una cuota fija mensual: C · (1 − (1 + i)^−n) / i. */
	static BigDecimal presentValue(BigDecimal installment, double annualRate, int months) {
		if (installment.signum() == 0) {
			return BigDecimal.ZERO;
		}
		double monthly = annualRate / 12;
		double factor = monthly == 0 ? months : (1 - Math.pow(1 + monthly, -months)) / monthly;
		return installment.multiply(BigDecimal.valueOf(factor), MC);
	}

	private static String normalize(String text) {
		if (text == null) {
			return "";
		}
		return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
	}

	private static BigDecimal money(BigDecimal value) {
		return value.setScale(2, RoundingMode.HALF_UP);
	}

	/**
	 * @param referenceAnnualRate tasa anual de referencia por necesidad, solo para el monto
	 *                            sugerido; cada producto aplica después la suya (C-03)
	 * @param defaultTermMonths   plazo sugerido cuando la solicitud no trae uno
	 */
	@ConfigurationProperties("doggycredit.needs")
	record Properties(Map<NeedCategory, Double> referenceAnnualRate, Map<NeedCategory, Integer> defaultTermMonths) {
	}

}
