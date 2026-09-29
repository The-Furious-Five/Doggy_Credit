package com.doggycredit.consent.internal;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ConsentRepository extends JpaRepository<Consent, UUID> {

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

}
