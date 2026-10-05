package com.doggycredit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.doggycredit.shared.TenantContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class TenantIsolationTests {

    private static final String TENANT_A = "9c7e7c1a-0000-4000-8000-000000000001"; // Progreso Andino (FREE)
    private static final String TENANT_B = "9c7e7c1a-0000-4000-8000-000000000002"; // Fintech Vecina (PRO)

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void consentIsolation() throws Exception {
        UUID consentId = UUID.randomUUID();
        String document = "ISO-CONS-001";

        // Create a consent as Tenant A
        comoTenant(TENANT_A, () -> {
            jdbc.update("""
                insert into consent.consents (id, tenant_id, titular_documento, fuentes, finalidad, vigencia_dias,
                  otorgado_en, expira_en, estado, otorgado_por, payload_sha256)
                values (?, ?::uuid, ?, '{BURO}'::text[], 'evaluacion', 90, now(), now() + interval '1 day',
                  'ACTIVO', 'test', 'x')
                on conflict do nothing
                """, consentId, TENANT_A, document);
            return null;
        });

        // Visible to Tenant A
        comoTenant(TENANT_A, () -> {
            Integer count = jdbc.queryForObject("select count(*) from consent.consents where id = ?", Integer.class, consentId);
            assertThat(count).isEqualTo(1);
            return null;
        });

        // Not visible to Tenant B
        comoTenant(TENANT_B, () -> {
            Integer count = jdbc.queryForObject("select count(*) from consent.consents where id = ?", Integer.class, consentId);
            assertThat(count).isZero();
            return null;
        });
    }

    @Test
    void applicantIsolation() throws Exception {
        UUID applicantId = UUID.randomUUID();
        String docRef = "hash-" + applicantId;
        String docCrypt = "crypt-" + applicantId;

        // Create an applicant as Tenant A
        comoTenant(TENANT_A, () -> {
            jdbc.update("""
                insert into customer.applicants (id, tenant_id, document_ref, document_ciphertext, activity,
                  activity_seniority_months, monthly_income, income_currency, credit_destination, created_at)
                values (?, ?::uuid, ?, ?, 'Comercio ais', 12, 1000, 'BOB', 'Cap ais', now())
                on conflict do nothing
                """, applicantId, TENANT_A, docRef, docCrypt);
            return null;
        });

        // Visible to Tenant A
        comoTenant(TENANT_A, () -> {
            Integer count = jdbc.queryForObject("select count(*) from customer.applicants where id = ?", Integer.class, applicantId);
            assertThat(count).isEqualTo(1);
            return null;
        });

        // Not visible to Tenant B
        comoTenant(TENANT_B, () -> {
            Integer count = jdbc.queryForObject("select count(*) from customer.applicants where id = ?", Integer.class, applicantId);
            assertThat(count).isZero();
            return null;
        });
    }

    @Test
    void assessmentIsolation() throws Exception {
        UUID consentId = UUID.randomUUID();
        UUID applicantId = UUID.randomUUID();
        UUID assessmentId = UUID.randomUUID();
        String document = "ISO-ASSESS-001";

        // Create base data and evaluation as Tenant A
        comoTenant(TENANT_A, () -> {
            jdbc.update("""
                insert into consent.consents (id, tenant_id, titular_documento, fuentes, finalidad, vigencia_dias,
                  otorgado_en, expira_en, estado, otorgado_por, payload_sha256)
                values (?, ?::uuid, ?, '{BURO}'::text[], 'evaluacion ais', 90, now(), now() + interval '1 day',
                  'ACTIVO', 'test', 'x')
                on conflict do nothing
                """, consentId, TENANT_A, document);

            jdbc.update("""
                insert into customer.applicants (id, tenant_id, document_ref, document_ciphertext, activity,
                  activity_seniority_months, monthly_income, income_currency, credit_destination, created_at)
                values (?, ?::uuid, ?, ?, 'Comercio ais', 12, 1000, 'BOB', 'Cap ais', now())
                on conflict do nothing
                """, applicantId, TENANT_A, document, document);

            jdbc.update("""
                insert into assessment.credit_assessments (id, tenant_id, idempotency_key, request_hash, applicant_id,
                  consent_id, purpose, requested_amount, term_months, status, score, band, reasons, model_version,
                  correlation_id, created_at)
                values (?, ?::uuid, ?, 'hash-ais', ?::uuid, ?::uuid, 'Cap ais', 3000, 12, 'PREAPROBADO', 800, 'A',
                  '{r1,r2,r3}'::text[], 'basic-v1', 'corr-ais', now())
                on conflict do nothing
                """, assessmentId, TENANT_A, "idem-" + assessmentId, applicantId, consentId);
            return null;
        });

        // Try to get it as Tenant A -> 200 OK
        mvc.perform(get("/credit-assessments/{id}", assessmentId)
                .with(jwt().jwt(j -> j.subject("analista-iso").claim("tenant_id", TENANT_A))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(assessmentId.toString()));

        // Try to get it as Tenant B -> 404 Not Found
        mvc.perform(get("/credit-assessments/{id}", assessmentId)
                .with(jwt().jwt(j -> j.subject("analista-iso").claim("tenant_id", TENANT_B))))
            .andExpect(status().isNotFound());
    }

    @Test
    void tenantEndpointIsolation() throws Exception {
        // GET /tenant/actual with Tenant A token returns Tenant A data
        mvc.perform(get("/tenant/actual")
                .with(jwt().jwt(j -> j.subject("analista-iso").claim("tenant_id", TENANT_A))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(TENANT_A))
            .andExpect(jsonPath("$.plan").value("FREE"));

        // GET /tenant/actual with Tenant B token returns Tenant B data
        mvc.perform(get("/tenant/actual")
                .with(jwt().jwt(j -> j.subject("analista-iso").claim("tenant_id", TENANT_B))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(TENANT_B))
            .andExpect(jsonPath("$.plan").value("PRO"));
    }

    @Test
    void monthlyUsageIsIsolatedAcrossTenants() throws Exception {
        String month = java.time.YearMonth.now(java.time.ZoneOffset.UTC).toString();
        comoTenant(TENANT_A, () -> {
            jdbc.update("""
                    insert into billing.monthly_usage (tenant_id, year_month, evaluaciones)
                    values (?::uuid, ?, 17) on conflict (tenant_id, year_month)
                    do update set evaluaciones = excluded.evaluaciones
                    """, TENANT_A, month);
            assertThat(jdbc.queryForObject("select evaluaciones from billing.monthly_usage where tenant_id = ?::uuid and year_month = ?",
                    Integer.class, TENANT_A, month)).isEqualTo(17);
            return null;
        });
        comoTenant(TENANT_B, () -> {
            Integer visible = jdbc.queryForObject("select count(*) from billing.monthly_usage where tenant_id = ?::uuid and year_month = ?",
                    Integer.class, TENANT_A, month);
            assertThat(visible).isZero();
            return null;
        });
    }

    @Test
    void securityPreferencesAreIsolatedAcrossTenants() throws Exception {
        comoTenant(TENANT_A, () -> {
            jdbc.update("""
                    insert into tenancy.tenant_preferences (tenant_id, enmascarar_datos) values (?::uuid, false)
                    on conflict (tenant_id) do update set enmascarar_datos = excluded.enmascarar_datos
                    """, TENANT_A);
            assertThat(jdbc.queryForObject("select enmascarar_datos from tenancy.tenant_preferences where tenant_id = ?::uuid",
                    Boolean.class, TENANT_A)).isFalse();
            return null;
        });
        comoTenant(TENANT_B, () -> {
            Integer visible = jdbc.queryForObject("select count(*) from tenancy.tenant_preferences where tenant_id = ?::uuid",
                    Integer.class, TENANT_A);
            assertThat(visible).isZero();
            return null;
        });
    }

    private <T> T comoTenant(String tenant, Callable<T> work) throws Exception {
        TenantContext.set(UUID.fromString(tenant));
        try {
            return work.call();
        } finally {
            TenantContext.clear();
        }
    }
}
