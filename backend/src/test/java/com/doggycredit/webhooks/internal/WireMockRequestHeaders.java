package com.doggycredit.webhooks.internal;

import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.JsonNode;

/** Lectura de cabeceras del journal de WireMock, sin iniciar contenedores. */
final class WireMockRequestHeaders {
    private WireMockRequestHeaders() { }

    static String header(JsonNode request, String name) {
        JsonNode headers = request.path("headers");
        assertThat(headers.isObject()).as("Faltan las cabeceras para leer %s", name).isTrue();
        var matches = headers.properties().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name)).toList();
        assertThat(matches.size()).as("Debe existir una sola cabecera %s", name).isEqualTo(1);
        JsonNode value = matches.get(0).getValue();
        // El journal puede representar una cabecera como texto o como lista de valores.
        if (value.isArray()) {
            assertThat(value.size()).as("La cabecera %s debe tener un solo valor", name).isEqualTo(1);
            value = value.get(0);
        }
        assertThat(value.isString()).as("La cabecera %s debe contener texto", name).isTrue();
        String text = value.asString();
        assertThat(text.isBlank()).as("La cabecera %s no debe estar vacía", name).isFalse();
        return text;
    }
}
