package com.doggycredit.integration.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;

import org.junit.jupiter.api.Test;

import com.doggycredit.consent.Source;
import com.doggycredit.integration.BureauReport;
import com.doggycredit.integration.BureauReport.TipoTitular;
import com.doggycredit.integration.SourceUnavailableException;

/** Capa anticorrupción del buró sin red: XML del proveedor → canónico v1. */
class BureauXmlTest {

	private static final String DOC = "CI-DEMO-0001001";

	private static final Instant NOW = Instant.parse("2026-09-28T12:00:00Z");

	private static String informe(String documento, String tipo, String codigo, String deuda, String cuota, String mora) {
		return """
				<?xml version="1.0" encoding="UTF-8"?>
				<InformeCrediticio>
				  <Documento>%s</Documento>
				  <TipoTitular>%s</TipoTitular>
				  <Calificacion codigo="%s">texto libre del proveedor</Calificacion>
				  <DeudaVigente moneda="BOB">%s</DeudaVigente>
				  <CuotaMensual moneda="BOB">%s</CuotaMensual>
				  <MoraMaximaUltimos12Meses dias="%s"/>
				  <AntiguedadSistemaFinancieroMeses>92</AntiguedadSistemaFinancieroMeses>
				  <FechaCorte>2026-09-01</FechaCorte>
				</InformeCrediticio>
				""".formatted(documento, tipo, codigo, deuda, cuota, mora);
	}

	private static BureauReport map(String xml) {
		return BureauXml.toReport(xml.getBytes(StandardCharsets.UTF_8), DOC, NOW);
	}

	@Test
	void mapeaInformeAlCanonico() {
		BureauReport r = map(informe(DOC, "PERSONA", "C", "8580.00", "1930.00", "45"));

		assertThat(r.tipo()).isEqualTo(TipoTitular.PERSONA);
		assertThat(r.antiguedadSistemaFinancieroMeses()).isEqualTo(92);
		assertThat(r.endeudamiento()).singleElement().satisfies(d -> {
			assertThat(d.fuente()).isEqualTo(Source.BURO);
			assertThat(d.tipoCredito()).isEqualTo("CONSOLIDADO");
			assertThat(d.saldo().amount()).hasToString("8580.00");
			assertThat(d.saldo().currency()).isEqualTo(Currency.getInstance("BOB"));
			assertThat(d.cuota().amount()).hasToString("1930.00");
			assertThat(d.diasMoraMax12m()).isEqualTo(45);
			assertThat(d.calificacion()).isEqualTo("C");
			assertThat(d.fechaCorte()).isEqualTo(LocalDate.of(2026, 9, 1));
		});
		assertThat(r.linaje().fuente()).isEqualTo(Source.BURO);
		assertThat(r.linaje().sha256Crudo()).hasSize(64);
		assertThat(r.linaje().obtenidoEn()).isEqualTo(NOW);
	}

	@Test
	void sinHistorialNoTieneEndeudamiento() {
		BureauReport r = map(informe(DOC, "PYME", "N", "0.00", "0.00", "0"));
		assertThat(r.tipo()).isEqualTo(TipoTitular.PYME);
		assertThat(r.endeudamiento()).isEmpty();
	}

	@Test
	void rechazaTodoLoQueNoCuadra() {
		assertMalformed(informe("CI-DEMO-0001002", "PERSONA", "A", "1.00", "1.00", "0")); // otro titular
		assertMalformed(informe(DOC, "EMPRESA", "A", "1.00", "1.00", "0"));
		assertMalformed(informe(DOC, "PERSONA", "Z", "1.00", "1.00", "0"));
		assertMalformed(informe(DOC, "PERSONA", "N", "10.00", "1.00", "0")); // sin historial pero con deuda
		assertMalformed(informe(DOC, "PERSONA", "A", "mucho", "1.00", "0"));
		assertMalformed(informe(DOC, "PERSONA", "A", "-1.00", "1.00", "0"));
		assertMalformed(informe(DOC, "PERSONA", "A", "1.00", "1.00", "-3"));
		assertMalformed(informe(DOC, "PERSONA", "A", "1.00", "1.00", "0").replace("<FechaCorte>2026-09-01</FechaCorte>", ""));
		assertMalformed("<InformeCrediticio><Documento>");
		assertMalformed("");
	}

	@Test
	void rechazaXxe() {
		String xxe = """
				<?xml version="1.0"?>
				<!DOCTYPE InformeCrediticio [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
				<InformeCrediticio><Documento>&xxe;</Documento></InformeCrediticio>
				""";
		assertMalformed(xxe);
		assertThat(BureauXml.isDocumentNotFound(xxe.getBytes(StandardCharsets.UTF_8), DOC)).isFalse();
	}

	@Test
	void reconoceSoloElNoEncontradoDelBuro() {
		String notFound = "<ErrorBuro><Codigo>DOCUMENTO_NO_ENCONTRADO</Codigo><Documento>" + DOC + "</Documento></ErrorBuro>";
		assertThat(BureauXml.isDocumentNotFound(notFound.getBytes(StandardCharsets.UTF_8), DOC)).isTrue();
		assertThat(BureauXml.isDocumentNotFound(notFound.getBytes(StandardCharsets.UTF_8), "OTRO")).isFalse();
		assertThat(BureauXml.isDocumentNotFound("<html>Not Found</html>".getBytes(StandardCharsets.UTF_8), DOC)).isFalse();
	}

	private static void assertMalformed(String xml) {
		assertThatThrownBy(() -> map(xml)).isInstanceOfSatisfying(SourceUnavailableException.class,
				e -> assertThat(e.source()).isEqualTo(Source.BURO));
	}

}
