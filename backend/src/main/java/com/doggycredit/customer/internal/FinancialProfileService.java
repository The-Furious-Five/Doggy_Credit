package com.doggycredit.customer.internal;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.doggycredit.audit.AccessSubject;
import com.doggycredit.audit.AuditTrail;
import com.doggycredit.consent.Source;
import com.doggycredit.customer.ApplicantNotFoundException;
import com.doggycredit.customer.FinancialProfile360;
import com.doggycredit.customer.FinancialProfiles;
import com.doggycredit.customer.SourceResult;
import com.doggycredit.customer.internal.ProfileConsolidation.Snapshot;
import com.doggycredit.shared.FieldEncryption;
import com.doggycredit.shared.Money;

import tools.jackson.databind.JsonNode;

@Service
class FinancialProfileService implements FinancialProfiles {

	private final ApplicantRepository applicants;
	private final FinancialProfileRepository profiles;
	private final FieldEncryption encryption;
	private final AuditTrail audit;

	FinancialProfileService(ApplicantRepository applicants, FinancialProfileRepository profiles,
			FieldEncryption encryption, AuditTrail audit) {
		this.applicants = applicants;
		this.profiles = profiles;
		this.encryption = encryption;
		this.audit = audit;
	}

	@Override
	@Transactional
	public FinancialProfile360 consolidate(UUID tenantId, UUID applicantId, Map<Source, SourceResult> results) {
		Applicant applicant = applicants.findByIdAndTenantId(applicantId, tenantId)
			.orElseThrow(ApplicantNotFoundException::new);
		Snapshot snapshot = ProfileConsolidation.consolidate(UUID.randomUUID(),
				Instant.now().truncatedTo(ChronoUnit.MICROS), applicantId, applicant.documentRef(),
				new Money(applicant.monthlyIncome(), applicant.incomeCurrency()), results);
		FinancialProfile360 view = snapshot.view();
		String payload = ProfileConsolidation.JSON
			.writeValueAsString(Map.of("perfil", snapshot.canonical(), "resumen", view));
		profiles.save(new FinancialProfileSnapshot(view.id(), tenantId, applicantId, view.hash(),
				encryption.encrypt(tenantId, payload), view.createdAt()));
		audit.record(tenantId, "FINANCIAL_PROFILE_CREATED", "FINANCIAL_PROFILE", view.id(),
				AccessSubject.applicant(applicantId));
		return view;
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<FinancialProfile360> latest(UUID tenantId, UUID applicantId) {
		return profiles.findFirstByTenantIdAndApplicantIdOrderByCreatedAtDesc(tenantId, applicantId).map(snapshot -> {
			JsonNode payload = ProfileConsolidation.JSON.readTree(encryption.decrypt(tenantId, snapshot.payloadCiphertext()));
			return ProfileConsolidation.JSON.treeToValue(payload.get("resumen"), FinancialProfile360.class);
		});
	}

}
