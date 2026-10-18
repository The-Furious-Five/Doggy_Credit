package com.doggycredit.recommendation;

import java.util.List;
import java.util.UUID;

/** RF-REC-02: resultado de los criterios de un producto activo al evaluar. */
public record ProductEligibility(UUID productId, String productType, String currency, boolean eligible,
        List<Criterion> criteria) {

    public ProductEligibility {
        criteria = List.copyOf(criteria);
    }

    public record Criterion(Code code, boolean met, String detail) { }

    public enum Code {
        BANDA_MINIMA, RANGO_MONTO, RANGO_PLAZO, TIPO_TITULAR, DESTINO, CAPACIDAD_PAGO
    }
}
