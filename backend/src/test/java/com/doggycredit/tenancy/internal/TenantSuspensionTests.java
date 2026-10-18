package com.doggycredit.tenancy.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.doggycredit.TestTenants;
import com.doggycredit.TestcontainersConfiguration;
import com.doggycredit.billing.OperatorUsageQuery;
import com.doggycredit.shared.TenantContext;
import com.doggycredit.tenancy.TenantId;
import com.doggycredit.tenancy.TenantRegistry;

/** E-14: cadena Bearer real, persistencia y RLS contra PostgreSQL 17 de Testcontainers. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TenantSuspensionTests {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PostgreSQLContainer postgres;
    @Autowired TenantRegistry registry;
    @Autowired TenantRepository tenants;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired OperatorUsageQuery operatorUsage;
    // Solo se sustituye la validación criptográfica; el filtro Bearer, roles y tenant_id sí se ejecutan.
    @MockitoBean JwtDecoder decoder;

    private UUID target;
    private UUID operator;
    private UUID empty;
    private static final String MONTH = YearMonth.now(ZoneOffset.UTC).toString();

    @BeforeEach
    void fixture() {
        target = register("FREE");
        operator = register("PRO");
        empty = register("ENTERPRISE");
        counter(target, MONTH, 17);
        counter(operator, MONTH, 43);
        counter(target, YearMonth.parse(MONTH).minusMonths(1).toString(), 999);
        when(decoder.decode(anyString())).thenAnswer(invocation -> {
            String token = invocation.getArgument(0);
            String role = switch (token) {
                case "operator" -> "PLATFORM_ADMIN";
                case "tenant-admin" -> "TENANT_ADMIN";
                case "api-key" -> "API_CLIENT";
                default -> "ANALYST";
            };
            UUID tenant = token.equals("operator") || token.equals("other-person") ? operator : target;
            return Jwt.withTokenValue(token).header("alg", "RS256")
                    .subject(token.equals("api-key") ? "service-account-dc-api-test" : "person-" + token)
                    .claim("azp", token.equals("api-key") ? "dc-api-test" : "doggycredit-web")
                    .claim("tenant_id", tenant.toString())
                    .claim("realm_access", Map.of("roles", List.of(role)))
                    .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
        });
    }

    @Test
    void suspendedPeopleAndApiKeysGet403BeforeAnyEndpointOrValidation() throws Exception {
        for (String token : List.of("person", "tenant-admin", "api-key")) {
            mvc.perform(get("/tenant/actual").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }
        setActive(target, false, "suspend-many").andExpect(status().isOk());

        for (String token : List.of("person", "tenant-admin", "api-key")) {
            for (String path : List.of("/tenant/actual", "/tenant/actual/uso", "/solicitantes",
                    "/evaluaciones", "/integraciones", "/admin/tenants", "/unknown-endpoint")) {
                mvc.perform(get(path).header("Authorization", "Bearer " + token)
                        .header("X-Correlation-Id", "blocked-" + token)
                        .header("X-Tenant-Id", operator.toString()))
                        .andExpect(status().isForbidden())
                        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                        .andExpect(jsonPath("$.status").value(403))
                        .andExpect(jsonPath("$.code").value("TENANT_SUSPENDED"))
                        .andExpect(jsonPath("$.type").value("https://docs.doggycredit.example/errors/tenant-suspended"))
                        .andExpect(jsonPath("$.correlationId").value("blocked-" + token))
                        .andExpect(jsonPath("$.timestamp").isNotEmpty());
                assertThat(TenantContext.currentOrNone()).as("limpieza incluso al rechazar").isNull();
            }
            mvc.perform(post("/evaluaciones").header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("TENANT_SUSPENDED"));
        }
        mvc.perform(get("/tenant/actual").header("Authorization", "Bearer other-person"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(operator.toString()));
        assertThat(ownCount(target)).isEqualTo(17);
    }

    @Test
    void reactivationRestoresTheSameTokensAndAuditsOnlyActualTransitions() throws Exception {
        setActive(target, false, "suspend-correlation").andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));
        setActive(target, false, "duplicate-suspend").andExpect(status().isOk());
        mvc.perform(get("/tenant/actual").header("Authorization", "Bearer api-key"))
                .andExpect(status().isForbidden());

        setActive(target, true, "reactivate-correlation").andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(true));
        setActive(target, true, "duplicate-reactivate").andExpect(status().isOk());
        for (String token : List.of("person", "tenant-admin", "api-key")) {
            mvc.perform(get("/tenant/actual").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(true))
                    .andExpect(jsonPath("$.evaluacionesMes").doesNotExist())
                    .andExpect(jsonPath("$.limiteMensual").doesNotExist());
        }
        var audit = scoped(target, () -> jdbc.queryForList("""
                select action, actor, resource_type, resource_id, correlation_id
                  from audit.access_log where tenant_id = ? order by chain_seq
                """, target));
        assertThat(audit).containsExactly(
                Map.of("action", "TENANT_SUSPENDED", "actor", "person-operator", "resource_type", "TENANT",
                        "resource_id", target, "correlation_id", "suspend-correlation"),
                Map.of("action", "TENANT_REACTIVATED", "actor", "person-operator", "resource_type", "TENANT",
                        "resource_id", target, "correlation_id", "reactivate-correlation"));
        assertThat(scoped(target, () -> jdbc.queryForList("select * from audit.verify_chain(?)", target))).isEmpty();
        assertThat(scoped(operator, () -> jdbc.queryForObject("select count(*) from audit.access_log",
                Integer.class))).isZero();
        assertThat(ownCount(target)).as("conserva el consumo").isEqualTo(17);
    }

    @Test
    void platformAdminStillOperatesWhenItsOwnTenantIsSuspended() throws Exception {
        setActive(operator, false, "suspend-operator").andExpect(status().isOk());
        mvc.perform(get("/admin/tenants").header("Authorization", "Bearer operator"))
                .andExpect(status().isOk());
        mvc.perform(get("/tenant/actual").header("Authorization", "Bearer operator"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(false));
        mvc.perform(get("/tenant/actual").header("Authorization", "Bearer other-person"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("TENANT_SUSPENDED"));
        setActive(operator, true, "recover-operator").andExpect(status().isOk());
    }

    @Test
    void onlyPlatformAdminMayListSuspendOrReactivateTenants() throws Exception {
        for (String token : List.of("person", "tenant-admin", "api-key")) {
            mvc.perform(get("/admin/tenants").header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("INSUFFICIENT_ROLE"));
            for (boolean active : List.of(false, true)) {
                mvc.perform(patch("/admin/tenants/{id}", operator)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":" + active + "}"))
                        .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("INSUFFICIENT_ROLE"));
            }
        }
        mvc.perform(get("/admin/tenants")).andExpect(status().isUnauthorized());
        assertThat(registry.requireById(TenantId.of(operator)).activo()).isTrue();
    }

    @Test
    void listingIncludesCurrentMonthAndCurrentPlanEvenForSuspendedOrUnusedTenants() throws Exception {
        setActive(target, false, "usage-suspension").andExpect(status().isOk());
        mvc.perform(get("/admin/tenants").header("Authorization", "Bearer operator"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(row(target) + ".evaluacionesMes", contains(17)))
                .andExpect(jsonPath(row(target) + ".limiteMensual", contains(500)))
                .andExpect(jsonPath(row(target) + ".activo", contains(false)))
                .andExpect(jsonPath(row(operator) + ".evaluacionesMes", contains(43)))
                .andExpect(jsonPath(row(operator) + ".limiteMensual", contains(0)))
                .andExpect(jsonPath(row(empty) + ".evaluacionesMes", contains(0)))
                .andExpect(jsonPath(row(empty) + ".limiteMensual", contains(0)));
        assertThat(ownCount(target)).as("el contador propio coincide con el agregado del operador").isEqualTo(17);
    }

    @Test
    void aggregateFunctionKeepsForcedRlsAndCannotGrantAccessToOtherTenantsRows() {
        scoped(target, () -> {
            assertThat(jdbc.queryForObject("select current_user", String.class)).isEqualTo("doggycredit_app");
            assertThat(jdbc.queryForList("select distinct tenant_id from billing.monthly_usage", UUID.class))
                    .containsExactly(target);
            assertThat(jdbc.queryForList("select * from billing.monthly_usage where tenant_id = ?", operator)).isEmpty();
            assertThat(jdbc.queryForObject("select evaluaciones from billing.operator_monthly_usage(?) where tenant_id = ?",
                    Integer.class, MONTH, operator)).isEqualTo(43);
            assertThat(jdbc.update("update billing.monthly_usage set evaluaciones = 0 where tenant_id = ?", operator)).isZero();
            assertThatThrownBy(() -> jdbc.update("insert into billing.monthly_usage values (?, '2099-01', 1)", operator))
                    .isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> jdbc.execute("set role doggycredit_usage_reader"))
                    .isInstanceOf(DataAccessException.class);
            return null;
        });
        assertThat(jdbc.queryForObject("""
                select relrowsecurity and relforcerowsecurity from pg_class
                 where oid = 'billing.monthly_usage'::regclass
                """, Boolean.class)).isTrue();
        assertThat(jdbc.queryForList("""
                select rolname from pg_roles where rolname in ('doggycredit_app', 'doggycredit_usage_reader')
                 and (rolsuper or rolbypassrls)
                """, String.class)).isEmpty();
        assertThat(jdbc.queryForObject("""
                select not rolcanlogin and not rolinherit and not rolcreaterole and not rolcreatedb
                  from pg_roles where rolname = 'doggycredit_usage_reader'
                """, Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("select pg_has_role(current_user, 'doggycredit_usage_reader', 'MEMBER')",
                Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("""
                select has_table_privilege('doggycredit_usage_reader', 'billing.monthly_usage', 'INSERT,UPDATE,DELETE')
                    or has_schema_privilege('doggycredit_usage_reader', 'billing', 'CREATE')
                """, Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("""
                select p.prosecdef and pg_get_userbyid(p.proowner) = 'doggycredit_usage_reader'
                    and 'search_path=pg_catalog' = any(p.proconfig)
                    and not exists (select 1 from aclexplode(p.proacl) a where a.grantee = 0)
                  from pg_proc p where p.oid = 'billing.operator_monthly_usage(text)'::regprocedure
                """, Boolean.class)).isTrue();
        assertThatThrownBy(() -> operatorUsage.evaluationsForMonth(YearMonth.parse(MONTH)))
                .isInstanceOf(org.springframework.web.ErrorResponseException.class);
    }

    @Test
    void auditFailureRollsBackSuspensionAndRestoresOperatorContext() {
        var failing = new TenantStatusService(tenants, registry, (tenant, action, type, resource) -> {
            throw new IllegalStateException("audit unavailable");
        }, transactionManager);
        scoped(operator, () -> {
            assertThatThrownBy(() -> failing.setActive(target, false))
                    .isInstanceOf(IllegalStateException.class).hasMessage("audit unavailable");
            assertThat(TenantContext.currentOrNone()).isEqualTo(operator);
            assertThat(registry.requireById(TenantId.of(target)).activo()).isTrue();
            return null;
        });
        assertThat(ownCount(target)).isEqualTo(17);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private UUID register(String plan) {
        UUID id = UUID.randomUUID();
        TestTenants.register(postgres, id.toString(), plan);
        return id;
    }

    private void counter(UUID id, String month, int evaluations) {
        scoped(id, () -> jdbc.update("insert into billing.monthly_usage values (?, ?, ?)", id, month, evaluations));
    }

    private int ownCount(UUID id) {
        return scoped(id, () -> jdbc.queryForObject("select evaluaciones from billing.monthly_usage where year_month = ?",
                Integer.class, MONTH));
    }

    private ResultActions setActive(UUID id, boolean active, String correlation) throws Exception {
        return mvc.perform(patch("/admin/tenants/{id}", id).header("Authorization", "Bearer operator")
                .header("X-Correlation-Id", correlation).contentType(MediaType.APPLICATION_JSON)
                .content("{\"activo\":" + active + "}"));
    }

    private String row(UUID id) {
        return "$[?(@.id == '" + id + "')]";
    }

    private <T> T scoped(UUID id, Supplier<T> operation) {
        UUID previous = TenantContext.currentOrNone();
        TenantContext.set(id);
        try {
            return operation.get();
        } finally {
            if (previous == null) TenantContext.clear();
            else TenantContext.set(previous);
        }
    }
}
