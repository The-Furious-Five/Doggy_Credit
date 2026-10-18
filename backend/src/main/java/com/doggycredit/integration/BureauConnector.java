package com.doggycredit.integration;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.doggycredit.consent.Source;
import com.doggycredit.integration.internal.BureauXml;
import com.doggycredit.integration.internal.ConnectorHttp;
import com.doggycredit.shared.DocumentNumber;

/**
 * Conector del buró (RF-INT-02.1), contrato en contracts/connectors/bureau.v1.yaml.
 * Siempre llama por HTTP: no hay modo local. Se invoca solo a través de
 * {@link ConnectorGateway#fetch}.
 * <ul>
 * <li>200 → informe mapeado al canónico v1.</li>
 * <li>404 {@code DOCUMENTO_NO_ENCONTRADO} → {@link Optional#empty()}: titular sin registro en el buró.</li>
 * <li>cualquier otro estado, timeout, error de red o XML inválido → {@link SourceUnavailableException}.</li>
 * </ul>
 */
@Component
public class BureauConnector implements SourceConnector<Optional<BureauReport>> {

	private final RestClient http;
	private final ConnectorCredentials credentials;

	@Autowired
	BureauConnector(@Value("${doggycredit.integration.bureau.url}") String url,
			@Value("${doggycredit.integration.bureau.connect-timeout}") Duration connectTimeout,
			@Value("${doggycredit.integration.bureau.read-timeout}") Duration readTimeout,
			ConnectorCredentials credentials) {
		this.credentials = credentials;
		this.http = ConnectorHttp.client(url, connectTimeout, readTimeout);
	}

	BureauConnector(String url, Duration connectTimeout, Duration readTimeout) {
		this(url, connectTimeout, readTimeout, (tenant, provider) -> "");
	}

	@Override
	public Source source() {
		return Source.BURO;
	}

	@Override
	public Optional<BureauReport> fetch(UUID tenantId, String documento) {
		String doc = DocumentNumber.normalize(documento);
		if (doc.isEmpty()) {
			throw new IllegalArgumentException("documento vacío");
		}
		return ConnectorHttp.get(http, Source.BURO, "/bureau/v1/informe/{doc}", doc,
				credentials.apiKey(tenantId, ConnectorCredentials.Provider.BURO),
				body -> BureauXml.toReport(body, doc, Instant.now()), body -> BureauXml.isDocumentNotFound(body, doc));
	}

}
