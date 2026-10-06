package com.doggycredit.integration.internal;

import java.net.http.HttpClient;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.doggycredit.consent.Source;
import com.doggycredit.integration.SourceUnavailableException;

/**
 * HTTP común de los conectores JSON (banco, billetera), con la misma semántica
 * que el del buró: 200 → informe; el 404 propio del proveedor → vacío
 * ("sin registro"); cualquier otra cosa → {@link SourceUnavailableException}.
 */
public final class ConnectorHttp {

	private ConnectorHttp() {
	}

	public static RestClient client(String url, Duration connectTimeout, Duration readTimeout) {
		// HTTP/1.1: el HttpClient del JDK intenta h2c y WireMock corta la conexión.
		HttpClient client = HttpClient.newBuilder()
			.version(HttpClient.Version.HTTP_1_1)
			.connectTimeout(connectTimeout)
			.build();
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
		factory.setReadTimeout(readTimeout);
		return RestClient.builder().baseUrl(url).requestFactory(factory).build();
	}

	public static <T> Optional<T> get(RestClient http, Source source, String uri, String documento,
			Function<byte[], T> onOk, Predicate<byte[]> isNotFound) {
		try {
			return http.get().uri(uri, documento).exchange((request, response) -> {
				byte[] body = response.getBody().readAllBytes();
				int status = response.getStatusCode().value();
				if (status == 200) {
					return Optional.of(onOk.apply(body));
				}
				// Un 404 que no es el del proveedor (URL mal configurada, proxy) es una caída.
				if (status == 404 && isNotFound.test(body)) {
					return Optional.empty();
				}
				throw new SourceUnavailableException(source, "HTTP " + status, null);
			});
		}
		catch (RestClientException e) {
			throw new SourceUnavailableException(source, e.getMessage(), e);
		}
	}

	static String sha256(byte[] body) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

}
