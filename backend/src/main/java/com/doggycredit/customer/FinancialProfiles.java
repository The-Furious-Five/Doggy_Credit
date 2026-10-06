package com.doggycredit.customer;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.doggycredit.consent.Source;

/** Perfil Financiero 360 (B-04): snapshots inmutables, cifrados y aislados por tenant. */
public interface FinancialProfiles {

	/**
	 * Une las fuentes en el canónico v1 y guarda un snapshot nuevo. Una fuente que
	 * falta en {@code results} cuenta como {@code NO_CONSULTADA}.
	 *
	 * @throws ApplicantNotFoundException si el solicitante no es de este tenant
	 */
	FinancialProfile360 consolidate(UUID tenantId, UUID applicantId, Map<Source, SourceResult> results);

	/** El snapshot más reciente del solicitante, si alguna vez se consolidó. */
	Optional<FinancialProfile360> latest(UUID tenantId, UUID applicantId);

}
