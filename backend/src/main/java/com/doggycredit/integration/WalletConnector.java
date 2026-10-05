package com.doggycredit.integration;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.doggycredit.consent.Source;
import com.doggycredit.integration.internal.ConnectorHttp;
import com.doggycredit.integration.internal.WalletJson;
import com.doggycredit.shared.DocumentNumber;

/**
 * Conector de la billetera móvil (B-05), contrato en contracts/connectors/wallet.v1.yaml.
 * Misma semántica que {@link BureauConnector}: 200 → canónico; 404
 * {@code TITULAR_NO_REGISTRADO} → vacío (sin billetera); cualquier otro estado,
 * timeout o JSON inválido → {@link SourceUnavailableException}.
 * Se invoca solo a través de {@link ConnectorGateway}.
 */
@Component
public class WalletConnector implements SourceConnector<Optional<WalletReport>> {

	private final RestClient http;

	WalletConnector(@Value("${doggycredit.integration.wallet.url}") String url,
			@Value("${doggycredit.integration.wallet.connect-timeout}") Duration connectTimeout,
			@Value("${doggycredit.integration.wallet.read-timeout}") Duration readTimeout) {
		this.http = ConnectorHttp.client(url, connectTimeout, readTimeout);
	}

	@Override
	public Source source() {
		return Source.WALLET;
	}

	@Override
	public Optional<WalletReport> fetch(UUID tenantId, String documento) {
		String doc = DocumentNumber.normalize(documento);
		if (doc.isEmpty()) {
			throw new IllegalArgumentException("documento vacío");
		}
		return ConnectorHttp.get(http, Source.WALLET, "/wallet/v1/titulares/{doc}", doc,
				body -> WalletJson.toReport(body, doc, Instant.now()), body -> WalletJson.isNotFound(body, doc));
	}

}
