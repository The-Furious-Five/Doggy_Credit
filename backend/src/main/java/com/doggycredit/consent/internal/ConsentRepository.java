package com.doggycredit.consent.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ConsentRepository extends JpaRepository<Consent, UUID> {

	List<Consent> findByTenantIdAndTitularDocumentoOrderByOtorgadoEnDesc(UUID tenantId, String titularDocumento);

	Optional<Consent> findByIdAndTenantId(UUID id, UUID tenantId);

	/** Vigente = ACTIVO, no expirado y con la fuente entre sus fuentes; siempre filtrado por tenant. */
	@Query(value = """
			select exists (
				select 1 from consent.consents
				where tenant_id = :tenantId
				  and titular_documento = :documento
				  and estado = 'ACTIVO'
				  and expira_en > now()
				  and :fuente = any (fuentes)
			)""", nativeQuery = true)
	boolean existsActive(UUID tenantId, String documento, String fuente);

	/** Igual que {@link #existsActive}, pero exige el consentimiento concreto enviado por la evaluación. */
	@Query(value = """
			select exists (
				select 1 from consent.consents
				where id = :consentId
				  and tenant_id = :tenantId
				  and titular_documento = :documento
				  and estado = 'ACTIVO'
				  and expira_en > now()
				  and :fuente = any (fuentes)
			)""", nativeQuery = true)
	boolean existsActive(UUID tenantId, UUID consentId, String documento, String fuente);

}
