package com.doggycredit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.doggycredit.assessment.AssessmentStatus;
import com.doggycredit.assessment.CreditAssessmentCompleted;
import com.doggycredit.consent.ConsentGranted;
import com.doggycredit.consent.ConsentRevoked;
import com.doggycredit.consent.Source;
import com.doggycredit.scoring.ScoreBand;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * D-03: lo que el outbox serializa de cada evento de dominio cumple su esquema
 * publicado en {@code contracts/events/} — mismos campos, ninguno de más,
 * requeridos presentes y tipos, formatos y enums correctos. Si alguien cambia
 * un record sin versionar el esquema, esto falla.
 */
class DomainEventSchemaTests {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	static Stream<Arguments> events() {
		UUID tenant = UUID.randomUUID();
		return Stream.of(
				Arguments.of(ConsentGranted.SCHEMA, new ConsentGranted(UUID.randomUUID(), Instant.now(), tenant,
						UUID.randomUUID(), List.of(Source.BANCO, Source.BURO), "Evaluación de crédito", 90,
						Instant.now())),
				Arguments.of(ConsentRevoked.SCHEMA,
						ConsentRevoked.of(tenant, UUID.randomUUID(), List.of(Source.BANCO), List.of(Source.BURO))),
				Arguments.of(CreditAssessmentCompleted.SCHEMA, new CreditAssessmentCompleted(UUID.randomUUID(),
						Instant.now(), tenant, UUID.randomUUID(), UUID.randomUUID(), AssessmentStatus.PARCIAL, 640,
						ScoreBand.B, List.of("MORA_DESCONOCIDA"), "basic-v1", true, "corr-1")));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("events")
	void elEventoSerializadoCumpleSuEsquema(String schemaName, Object event) throws IOException {
		JsonNode schema = JSON.readTree(Files.readString(repoPath("contracts/events/" + schemaName + ".schema.json")));
		JsonNode payload = JSON.valueToTree(event);

		List<String> declared = schema.get("properties").propertyNames().stream().toList();
		List<String> serialized = payload.propertyNames().stream().toList();
		assertThat(serialized).as("campos del evento vs. propiedades del esquema")
			.containsExactlyInAnyOrderElementsOf(declared);
		schema.get("required").forEach(field -> assertThat(payload.has(field.asString()))
			.as("requerido %s", field.asString()).isTrue());

		List<String> errors = new ArrayList<>();
		for (Map.Entry<String, JsonNode> property : schema.get("properties").properties()) {
			validate(property.getKey(), property.getValue(), payload.get(property.getKey()), errors);
		}
		assertThat(errors).isEmpty();
	}

	private static void validate(String path, JsonNode spec, JsonNode value, List<String> errors) {
		String type = spec.get("type").asString();
		boolean typeOk = switch (type) {
			case "string" -> value.isString();
			case "integer" -> value.isIntegralNumber();
			case "boolean" -> value.isBoolean();
			case "array" -> value.isArray();
			default -> false;
		};
		if (!typeOk) {
			errors.add(path + ": se esperaba " + type + " y llegó " + value);
			return;
		}
		if (spec.has("enum") && spec.get("enum").valueStream().noneMatch(value::equals)) {
			errors.add(path + ": " + value + " no está en " + spec.get("enum"));
		}
		if (spec.has("format")) {
			try {
				switch (spec.get("format").asString()) {
					case "uuid" -> UUID.fromString(value.asString());
					case "date-time" -> Instant.parse(value.asString());
					default -> errors.add(path + ": formato no soportado por esta prueba");
				}
			}
			catch (RuntimeException e) {
				errors.add(path + ": " + value + " no es " + spec.get("format").asString());
			}
		}
		if (type.equals("array")) {
			for (int i = 0; i < value.size(); i++) {
				validate(path + "[" + i + "]", spec.get("items"), value.get(i), errors);
			}
		}
	}

	private static Path repoPath(String relative) {
		for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
			if (Files.exists(dir.resolve(relative))) {
				return dir.resolve(relative);
			}
		}
		throw new IllegalStateException("No se encontró " + relative + " subiendo desde " + Path.of("").toAbsolutePath());
	}

}
