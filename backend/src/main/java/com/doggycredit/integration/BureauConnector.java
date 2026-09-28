package com.doggycredit.integration;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.doggycredit.consent.Source;
import com.doggycredit.integration.internal.BureauXml;
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

	BureauConnector(@Value("${doggycredit.integration.bureau.url}") String url,
			@Value("${doggycredit.integration.bureau.connect-timeout}") Duration connectTimeout,
			@Value("${doggycredit.integration.bureau.read-timeout}") Duration readTimeout) {
		HttpClient client = HttpClient.newBuilder()
			.version(HttpClient.Version.HTTP_1_1)
			.connectTimeout(connectTimeout)
			.build();
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
		factory.setReadTimeout(readTimeout);
		this.http = RestClient.builder().baseUrl(url).requestFactory(factory).build();
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
		try {
			return http.get().uri("/bureau/v1/informe/{doc}", doc).exchange((request, response) -> {
				byte[] body = response.getBody().readAllBytes();
				int status = response.getStatusCode().value();
				if (status == 200) {
					return Optional.of(BureauXml.toReport(body, doc, Instant.now()));
				}
				// Un 404 que no es el del buró (URL mal configurada, proxy) es una caída, no "sin registro".
				if (status == 404 && BureauXml.isDocumentNotFound(body, doc)) {
					return Optional.empty();
				}
				throw new SourceUnavailableException(Source.BURO, "HTTP " + status, null);
			});
		}
		catch (RestClientException e) {
			throw new SourceUnavailableException(Source.BURO, e.getMessage(), e);
		}
	}

}
