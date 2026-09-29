package com.doggycredit.shared.internal;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.doggycredit.shared.DocumentReference;

/** HMAC-SHA-256 con separación de dominio y tenant para referencias de documentos. */
@Component
class HmacDocumentReference implements DocumentReference {

	private final byte[] masterKey;

	HmacDocumentReference(MasterKeyProperties properties) {
		this.masterKey = Objects.requireNonNull(properties.getMasterKey(), "masterKey")
			.getBytes(StandardCharsets.UTF_8);
	}

	@Override
	public String create(UUID tenantId, String normalizedDocument) {
		Objects.requireNonNull(tenantId, "tenantId");
		Objects.requireNonNull(normalizedDocument, "normalizedDocument");
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(masterKey, "HmacSHA256"));
			String material = "doggycredit:document-ref:v1:" + tenantId + ":" + normalizedDocument;
			return HexFormat.of().formatHex(mac.doFinal(material.getBytes(StandardCharsets.UTF_8)));
		}
		catch (GeneralSecurityException exception) {
			throw new IllegalStateException("No se pudo crear la referencia del documento", exception);
		}
	}

}
