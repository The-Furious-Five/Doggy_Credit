package com.doggycredit.recommendation.internal;

import java.math.BigDecimal;
import java.math.MathContext;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.doggycredit.recommendation.internal.CreditProduct.Type;
import com.doggycredit.scoring.ScoreBand;

/**
 * C-03 (LOS-83): elegibilidad y ranking de los productos del tenant. Lógica pura y
 * determinista: recibe la solicitud y el catálogo, y devuelve cada producto activo con
 * los criterios que cumple o no (RF-REC: "solo se listan productos activos") y una
 * razón legible.
 * <p>
 * Orden por ajuste (informe de arquitectura): primero los elegibles; entre ellos,
 * coincidencia del destino (destino verde → crédito ecológico) y luego cercanía del monto
 * pedido al centro del rango del producto. La holgura de la carga financiera, el tercer
 * criterio del informe, necesita los flujos de banco y billetera (B-04): queda fuera.
 */
@Component
class EligibilityEngine {

	/** Solicitud a evaluar: banda del score, monto y plazo pedidos, tipo de titular y destino. */
	record Request(ScoreBand band, BigDecimal requestedAmount, int termMonths, HolderType holderType,
			boolean greenDestination) {

		Request {
			Objects.requireNonNull(band, "band");
			Objects.requireNonNull(requestedAmount, "requestedAmount");
			Objects.requireNonNull(holderType, "holderType");
		}
	}

	enum HolderType {
		PERSONA, EMPRESA
	}

	/** Un criterio evaluado, con su texto para la UI. */
	record Criterion(Code code, boolean met, String description) {

		enum Code {
			BAND, AMOUNT, TERM, HOLDER_TYPE, DESTINATION
		}
	}

	/** Resultado por producto: si es elegible, la razón y el detalle de cada criterio. */
	record Evaluation(CreditProduct product, boolean eligible, String reason, List<Criterion> criteria) {
	}

	List<Evaluation> evaluate(Request request, List<CreditProduct> catalog) {
		return catalog.stream()
			.filter(CreditProduct::active)
			.map(product -> evaluate(request, product))
			.sorted(byFit(request))
			.toList();
	}

	private Evaluation evaluate(Request request, CreditProduct product) {
		List<Criterion> criteria = List.of(band(request, product), amount(request, product),
				term(request, product), holderType(request, product), destination(request, product));
		boolean eligible = criteria.stream().allMatch(Criterion::met);
		String reason = eligible ? eligibleReason(request, product)
				: criteria.stream().filter(criterion -> !criterion.met())
					.min(Comparator.comparingInt(criterion -> REASON_PRIORITY.indexOf(criterion.code())))
					.orElseThrow().description();
		return new Evaluation(product, eligible, reason, criteria);
	}

	/**
	 * La razón de un no elegible es su motivo de fondo: primero el que no se arregla
	 * cambiando la solicitud (tipo de titular, destino) y después los numéricos. Un
	 * crédito ecológico sin destino verde no aplica aunque el monto encaje.
	 */
	private static final List<Criterion.Code> REASON_PRIORITY = List.of(Criterion.Code.HOLDER_TYPE,
			Criterion.Code.DESTINATION, Criterion.Code.BAND, Criterion.Code.AMOUNT, Criterion.Code.TERM);

	// --- criterios ---------------------------------------------------------------

	private static Criterion band(Request request, CreditProduct product) {
		// A es la mejor banda: cumple si está en la mínima o por encima (ordinal menor o igual).
		boolean met = request.band().ordinal() <= product.minBand().ordinal();
		return new Criterion(Criterion.Code.BAND, met, met
				? "Banda " + request.band() + " cumple la mínima " + product.minBand()
				: "Banda " + request.band() + " por debajo de la mínima " + product.minBand());
	}

	private static Criterion amount(Request request, CreditProduct product) {
		boolean met = request.requestedAmount().compareTo(product.minAmount()) >= 0
				&& request.requestedAmount().compareTo(product.maxAmount()) <= 0;
		String range = "Bs " + money(product.minAmount()) + " – " + money(product.maxAmount());
		return new Criterion(Criterion.Code.AMOUNT, met, "Monto Bs " + money(request.requestedAmount())
				+ (met ? " dentro del rango " : " fuera del rango ") + range);
	}

	private static Criterion term(Request request, CreditProduct product) {
		boolean met = request.termMonths() >= product.minTermMonths() && request.termMonths() <= product.maxTermMonths();
		String range = product.minTermMonths() + " a " + product.maxTermMonths() + " meses";
		return new Criterion(Criterion.Code.TERM, met, "Plazo de " + request.termMonths() + " meses"
				+ (met ? " dentro de " : " fuera de ") + range);
	}

	private static Criterion holderType(Request request, CreditProduct product) {
		return switch (product.type()) {
			case PYME -> request.holderType() == HolderType.EMPRESA
					? new Criterion(Criterion.Code.HOLDER_TYPE, true, "Titular empresa")
					: new Criterion(Criterion.Code.HOLDER_TYPE, false, "El crédito PYME es solo para empresas");
			case MICROCREDITO -> request.holderType() == HolderType.PERSONA
					? new Criterion(Criterion.Code.HOLDER_TYPE, true, "Titular persona")
					: new Criterion(Criterion.Code.HOLDER_TYPE, false, "El microcrédito es solo para personas");
			// El verde aplica las reglas de MICRO o PYME según el titular (informe de arquitectura).
			case VERDE -> new Criterion(Criterion.Code.HOLDER_TYPE, true, "Aplica a personas y empresas");
		};
	}

	private static Criterion destination(Request request, CreditProduct product) {
		if (product.type() != Type.VERDE) {
			return new Criterion(Criterion.Code.DESTINATION, true, "Admite este destino");
		}
		return request.greenDestination()
				? new Criterion(Criterion.Code.DESTINATION, true, "Destino verde")
				: new Criterion(Criterion.Code.DESTINATION, false,
						"El crédito ecológico requiere un destino verde (p. ej. eficiencia energética)");
	}

	// --- ranking -----------------------------------------------------------------

	private static String eligibleReason(Request request, CreditProduct product) {
		if (destinationMatches(request, product)) {
			return "Recomendado: financia el destino verde y cumple banda, monto y plazo";
		}
		return "Elegible: cumple banda, monto y plazo";
	}

	private static Comparator<Evaluation> byFit(Request request) {
		return Comparator.comparing((Evaluation evaluation) -> !evaluation.eligible())
			.thenComparing(evaluation -> !destinationMatches(request, evaluation.product()))
			.thenComparing(evaluation -> distanceToRangeCenter(request, evaluation.product()))
			.thenComparing(evaluation -> evaluation.product().type());
	}

	private static boolean destinationMatches(Request request, CreditProduct product) {
		return request.greenDestination() && product.type() == Type.VERDE;
	}

	/** 0 = el monto pedido cae justo en el centro del rango; 0,5 = en un extremo. */
	private static BigDecimal distanceToRangeCenter(Request request, CreditProduct product) {
		BigDecimal width = product.maxAmount().subtract(product.minAmount());
		if (width.signum() == 0) {
			return BigDecimal.ZERO;
		}
		BigDecimal center = product.minAmount().add(width.divide(BigDecimal.TWO, MathContext.DECIMAL64));
		return request.requestedAmount().subtract(center).abs().divide(width, MathContext.DECIMAL64);
	}

	private static String money(BigDecimal amount) {
		DecimalFormatSymbols symbols = new DecimalFormatSymbols();
		symbols.setGroupingSeparator('.');
		symbols.setDecimalSeparator(',');
		return new DecimalFormat("#,##0.##", symbols).format(amount);
	}

}
