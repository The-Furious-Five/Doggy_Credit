package com.doggycredit.webhooks.internal;

import static com.doggycredit.webhooks.internal.WireMockRequestHeaders.header;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import tools.jackson.databind.json.JsonMapper;

class WireMockRequestHeadersTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String TIMESTAMP = "1791312000";
    private static final String SIGNATURE = "sha256=3c896c1eab23b30d495d95a25b517c6bae0412d61429253a8447d4feaac56d73";

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void leeCabecerasComoTextoOListaSinCambiarLosBytesYSinDistinguirMayusculas(boolean arrays) {
        String timestamp = JSON.writeValueAsString(TIMESTAMP);
        String signature = JSON.writeValueAsString(SIGNATURE);
        if (arrays) { timestamp = "[" + timestamp + "]"; signature = "[" + signature + "]"; }
        var request = JSON.readTree("""
            {"headers":{"x-doggycredit-timestamp":%s,"X-DOGGYCREDIT-SIGNATURE":%s}}
            """.formatted(timestamp, signature));
        assertThat(header(request, "X-DoggyCredit-Timestamp")).isEqualTo(TIMESTAMP);
        assertThat(header(request, "X-DoggyCredit-Signature")).isEqualTo(SIGNATURE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "17", "false", "{}", "\"\"", "\"  \"", "[]", "[null]", "[17]", "[\"a\",\"b\"]"})
    void rechazaCabecerasInvalidasOMultiplesConUnFalloDescriptivo(String value) {
        var request = JSON.readTree("{\"headers\":{\"X-DoggyCredit-Signature\":" + value + "}}");
        assertThatThrownBy(() -> header(request, "X-DoggyCredit-Signature"))
                .isInstanceOf(AssertionError.class).hasMessageContaining("X-DoggyCredit-Signature");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"headers\":null}", "{\"headers\":{}}"})
    void unaCabeceraAusenteNoSeAceptaComoUnValorVacio(String json) {
        assertThatThrownBy(() -> header(JSON.readTree(json), "X-DoggyCredit-Signature"))
                .isInstanceOf(AssertionError.class).hasMessageContaining("X-DoggyCredit-Signature");
    }

    @Test
    void rechazaNombresDuplicadosAunqueCambieSuCapitalizacion() {
        var request = JSON.readTree("""
            {"headers":{"X-DoggyCredit-Signature":"first","x-doggycredit-signature":"second"}}
            """);
        assertThatThrownBy(() -> header(request, "X-DoggyCredit-Signature"))
                .isInstanceOf(AssertionError.class).hasMessageContaining("X-DoggyCredit-Signature");
    }
}
