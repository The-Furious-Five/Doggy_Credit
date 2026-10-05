package com.doggycredit.integration.internal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Currency;
import java.util.Set;
import java.util.regex.Pattern;

import com.doggycredit.shared.Money;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lectura estricta de los JSON de proveedores. Todo desvío del contrato es
 * {@link IllegalArgumentException}; la capa anticorrupción de cada fuente la
 * convierte en {@code SourceUnavailableException} (todo o nada, como el buró).
 */
final class JsonFields {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final Pattern AMOUNT = Pattern.compile("\\d+\\.\\d{2}");

	private static final Set<String> CURRENCIES = Set.of("BOB", "USD");

	private JsonFields() {
	}

	static JsonNode parse(byte[] body) {
		JsonNode root = JSON.readTree(body);
		if (root == null || !root.isObject()) {
			throw new IllegalArgumentException("se esperaba un objeto JSON");
		}
		return root;
	}

	static JsonNode field(JsonNode node, String name) {
		JsonNode value = node.get(name);
		if (value == null || value.isNull()) {
			throw new IllegalArgumentException("falta '" + name + "'");
		}
		return value;
	}

	static String text(JsonNode node, String name) {
		JsonNode value = field(node, name);
		if (!value.isString()) {
			throw new IllegalArgumentException("'" + name + "' no es texto");
		}
		return value.asString();
	}

	static int nonNegativeInt(JsonNode node, String name) {
		JsonNode value = field(node, name);
		if (!value.isInt() || value.asInt() < 0) {
			throw new IllegalArgumentException("'" + name + "' no es un entero no negativo");
		}
		return value.asInt();
	}

	static JsonNode array(JsonNode node, String name, int maxItems) {
		JsonNode value = field(node, name);
		if (!value.isArray() || value.size() > maxItems) {
			throw new IllegalArgumentException("'" + name + "' no es una lista de hasta " + maxItems);
		}
		return value;
	}

	static String currency(JsonNode node, String name) {
		String code = text(node, name);
		if (!CURRENCIES.contains(code)) {
			throw new IllegalArgumentException("moneda no soportada: " + code);
		}
		return code;
	}

	/** Monto "1234.50" (string, nunca float) en la moneda indicada. */
	static Money money(JsonNode node, String name, String currency) {
		String amount = text(node, name);
		if (!AMOUNT.matcher(amount).matches()) {
			throw new IllegalArgumentException("monto inválido en '" + name + "'");
		}
		return new Money(new BigDecimal(amount), Currency.getInstance(currency));
	}

	static LocalDate date(JsonNode node, String name) {
		return LocalDate.parse(text(node, name));
	}

	static YearMonth month(JsonNode node, String name) {
		return YearMonth.parse(text(node, name));
	}

}
