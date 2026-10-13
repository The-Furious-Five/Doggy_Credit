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
import com.doggycredit.integration.internal.BankJson;
import com.doggycredit.integration.internal.ConnectorHttp;
import com.doggycredit.shared.DocumentNumber;

/**
 * Conector del core bancario (B-03), contrato en contracts/connectors/bank.v1.yaml.
 * Misma semántica que {@link BureauConnector}: 200 → canónico; 404
 * {@code CLIENTE_NO_ENCONTRADO} → vacío (sin cuenta en el banco); cualquier otro
 * estado, timeout o JSON inválido → {@link SourceUnavailableException}.
 * Se invoca solo a través de {@link ConnectorGateway}. Proveedor por defecto del
 * core bancario; el segundo proveedor es {@link BankDosConnector} (B-10),
 * seleccionado por {@code doggycredit.integration.bank.provider}.
 */
@Component
@ConditionalOnProperty(prefix = "doggycredit.integration.bank", name = "provider", havingValue = "uno", matchIfMissing = true)
public class BankConnector implements SourceConnector<Optional<BankReport>> {

	private final RestClient http;

	BankConnector(@Value("${doggycredit.integration.bank.url}") String url,
			@Value("${doggycredit.integration.bank.connect-timeout}") Duration connectTimeout,
			@Value("${doggycredit.integration.bank.read-timeout}") Duration readTimeout) {
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
		return ConnectorHttp.get(http, Source.BANCO, "/bank/v1/clientes/{doc}", doc,
				body -> BankJson.toReport(body, doc, Instant.now()), body -> BankJson.isNotFound(body, doc));
	}

}
