package com.doggycredit.assessment.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import com.doggycredit.assessment.AssessmentCommand;
import com.doggycredit.assessment.internal.SourceCollection.SourceOutcome;
import com.doggycredit.audit.AuditTrail;
import com.doggycredit.billing.QuotaGuard;
import com.doggycredit.consent.ConsentRequiredException;
import com.doggycredit.consent.Source;
import com.doggycredit.customer.ApplicantProfile;
import com.doggycredit.customer.ApplicantRegistry;
import com.doggycredit.customer.ApplicantSegment;
import com.doggycredit.customer.FinancialProfiles;
import com.doggycredit.customer.SourceResult;
import com.doggycredit.scoring.BasicScoreResult;
import com.doggycredit.scoring.CreditScoring;
import com.doggycredit.scoring.ScoreBand;
import com.doggycredit.shared.Money;
import com.doggycredit.tenancy.TenantId;
import com.doggycredit.tenancy.TenantInfo;
import com.doggycredit.tenancy.TenantRegistry;

class AssessmentProfileTest {

	private final UUID tenant = UUID.randomUUID();
	private final UUID applicant = UUID.randomUUID();
	private final AssessmentCommand command = new AssessmentCommand(tenant, "CI-PERFIL", UUID.randomUUID(),
			"Capital", new BigDecimal("3000"), 12, "profile-key", "correlation");
	private final CreditAssessmentRepository assessments = mock(CreditAssessmentRepository.class);
	private final ApplicantRegistry applicants = mock(ApplicantRegistry.class);
	private final SourceCollector sources = mock(SourceCollector.class);
	private final CreditScoring scoring = mock(CreditScoring.class);
	private final FinancialProfiles profiles = mock(FinancialProfiles.class);
	private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
	private final TenantRegistry tenants = mock(TenantRegistry.class);
	private final PolicyRepository policies = mock(PolicyRepository.class);
	private final AssessmentAnalyticsSnapshot analytics = mock(AssessmentAnalyticsSnapshot.class);
	private final SynchronousAssessmentService service = new SynchronousAssessmentService(assessments, applicants,
			sources, scoring, mock(AuditTrail.class), mock(QuotaGuard.class), tenants, events, policies, analytics, profiles);

	@Test
	void evaluaYConsolidaUnaSolaVezInclusoAlRepetirLaClaveIdempotente() {
		prepareApplicant();
		var collection = new SourceCollection(Map.of(Source.BURO, SourceOutcome.received(Optional.empty()),
				Source.BANCO, SourceOutcome.unavailable(), Source.WALLET, SourceOutcome.notConsented()));
		when(sources.collect(tenant, command.consentId(), command.document())).thenReturn(collection);
		when(scoring.score(any(), any(), any())).thenReturn(new BasicScoreResult(800, ScoreBand.A, List.of(), "basic-v1"));
		when(analytics.capture(any(), any(), any(), any(), any()))
			.thenReturn(new AssessmentAnalyticsSnapshot.Snapshot("CAPITAL_TRABAJO", null, null));

		var result = service.create(command);
		var stored = ArgumentCaptor.forClass(CreditAssessment.class);
		verify(assessments).save(stored.capture());
		when(assessments.findByTenantIdAndIdempotencyKey(tenant, command.idempotencyKey()))
			.thenReturn(Optional.of(stored.getValue()));
		assertThat(service.create(command)).isEqualTo(result);

		verify(profiles).consolidate(tenant, applicant, Map.of(
				Source.BURO, SourceResult.of(Optional.empty()), Source.BANCO, SourceResult.unavailable(),
				Source.WALLET, SourceResult.notConsulted()));
		verify(sources).collect(tenant, command.consentId(), command.document());
		verify(events).publishEvent(any(Object.class));
		verifyNoMoreInteractions(profiles, sources, events);
	}

	@Test
	void consentimientoInvalidoNoProducePerfilNiEvaluacion() {
		prepareApplicant();
		when(sources.collect(tenant, command.consentId(), command.document()))
			.thenThrow(new ConsentRequiredException(Source.BURO));

		assertThatThrownBy(() -> service.create(command)).isInstanceOf(ConsentRequiredException.class);
		verifyNoInteractions(profiles, scoring, events);
		verify(assessments, never()).save(any());
	}

	@Test
	void unFalloAlGuardarElPerfilNoPublicaUnaEvaluacionExitosa() {
		prepareApplicant();
		when(sources.collect(tenant, command.consentId(), command.document()))
			.thenReturn(new SourceCollection(Map.of(Source.BURO, SourceOutcome.received(Optional.empty()))));
		when(scoring.score(any(), any(), any())).thenReturn(new BasicScoreResult(800, ScoreBand.A, List.of(), "basic-v1"));
		when(profiles.consolidate(any(), any(), any())).thenThrow(new IllegalStateException("fallo de persistencia"));

		assertThatThrownBy(() -> service.create(command)).hasMessage("fallo de persistencia");
		verify(assessments, never()).save(any());
		verifyNoInteractions(events);
	}

	private void prepareApplicant() {
		when(tenants.requireById(TenantId.of(tenant)))
			.thenReturn(new TenantInfo(tenant, "Tenant", "NIT", "COOPERATIVA", "FREE", "BO", "BOB", true));
		when(applicants.findRequired(tenant, command.document()))
			.thenReturn(new ApplicantProfile(applicant, command.document(), "Comercio", 60,
					new Money(new BigDecimal("5000"), Currency.getInstance("BOB")), "Capital", Instant.EPOCH,
					ApplicantSegment.PERSONA));
	}
}
