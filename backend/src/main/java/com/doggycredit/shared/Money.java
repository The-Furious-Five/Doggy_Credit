package com.doggycredit.shared;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * {@code Dinero} del modelo canónico v1: el monto se serializa como decimal
 * en string (nunca float) y la moneda por su código ISO 4217.
 */
public record Money(@JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount, Currency currency) {

	public Money {
		Objects.requireNonNull(amount, "amount");
		Objects.requireNonNull(currency, "currency");
	}

}
