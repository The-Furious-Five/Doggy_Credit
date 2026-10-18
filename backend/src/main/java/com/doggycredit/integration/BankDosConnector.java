package com.doggycredit.integration;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.doggycredit.consent.Source;
import com.doggycredit.integration.internal.BankDosJson;
import com.doggycredit.integration.internal.ConnectorHttp;
import com.doggycredit.shared.DocumentNumber;

/**
 * Conector del segundo core bancario (B-10), contrato en
 * contracts/connectors/bank-dos.v1.yaml. Mismo {@link Source#BANCO} y mismo
 * {@link BankReport} de salida que {@link BankConnector}: un proveedor
 * distinto del mismo contrato estándar (FF-15), seleccionado por
 * {@code doggycredit.integration.bank.provider} (nunca los dos conectores
 * activos a la vez: colisionarían en {@code Source.BANCO}).
 * Se invoca solo a través de {@link ConnectorGateway}.
 */
@Component
@ConditionalOnProperty(prefix = "doggycredit.integration.bank", name = "provider", havingValue = "dos")
public class BankDosConnector implements SourceConnector<Optional<BankReport>> {

	private final RestClient http;

	BankDosConnector(@Value("${doggycredit.integration.bank-dos.url}") String url,
			@Value("${doggycredit.integration.bank-dos.connect-timeout}") Duration connectTimeout,
			@Value("${doggycredit.integration.bank-dos.read-timeout}") Duration readTimeout) {
		this.http = ConnectorHttp.client(url, connectTimeout, readTimeout);
	}

	@Override
	public Source source() {
		return Source.BANCO;
	}

	@Override
	public Optional<BankReport> fetch(UUID tenantId, String documento) {
		String doc = DocumentNumber.normalize(documento);
		if (doc.isEmpty()) {
			throw new IllegalArgumentException("documento vacío");
		}
		return ConnectorHttp.get(http, Source.BANCO, "/bank-dos/v1/cuentas/{doc}", doc,
				body -> BankDosJson.toReport(body, doc, Instant.now()), body -> BankDosJson.isNotFound(body, doc));
	}

}
