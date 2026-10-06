package com.doggycredit.webhooks.internal;

import static com.doggycredit.webhooks.internal.WireMockRequestHeaders.header;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.doggycredit.TestTenants;
import com.doggycredit.TestcontainersConfiguration;
import com.doggycredit.assessment.AssessmentStatus;
import com.doggycredit.assessment.CreditAssessmentCompleted;
import com.doggycredit.consent.ConsentGranted;
import com.doggycredit.consent.Source;
import com.doggycredit.scoring.ScoreBand;
import com.doggycredit.shared.FieldEncryption;
import com.doggycredit.shared.TenantContext;
import com.doggycredit.tenancy.TenantId;
import com.doggycredit.tenancy.TenantRegistry;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** E-08: outbox y RLS reales en PostgreSQL, entrega HTTP real a WireMock. Nunca se omite sin Docker. */
@SpringBootTest(properties = { "doggycredit.webhooks.allow-private-addresses=true", "doggycredit.webhooks.worker-enabled=false" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class WebhookDeliveryTests {
    private static final UUID PRO = UUID.fromString("9c7e7c1a-0000-4000-8000-000000000002");
    private static final UUID FREE = UUID.fromString("9c7e7c1a-0000-4000-8000-000000000001");
    private static final UUID OTHER = UUID.fromString("b8080000-0000-4000-8000-000000000002");
    private static final String SECRET = "receiver-test-secret-with-32-characters";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final GenericContainer<?> RECEIVER = new GenericContainer<>(DockerImageName.parse("wiremock/wiremock:3.9.1"))
            .withExposedPorts(8080).waitingFor(Wait.forHttp("/__admin/health"));
    static { RECEIVER.start(); }

    @Autowired MockMvc mvc;
    @Autowired WebhookService webhooks;
    @Autowired JdbcTemplate jdbc;
    @Autowired FieldEncryption encryption;
    @Autowired TransactionTemplate transactions;
    @Autowired ApplicationEventPublisher events;
    @Autowired PostgreSQLContainer postgres;
    @Autowired TenantRegistry tenants;

    @BeforeEach
    void reset() {
        tenants.update(new TenantId(PRO), "PRO", null);
        TestTenants.register(postgres, OTHER.toString(), "PRO");
        webhooks.put(PRO, null, List.of(), null);
        webhooks.put(OTHER, null, List.of(), null);
        admin().delete().uri("/__admin/mappings").retrieve().toBodilessEntity();
        admin().delete().uri("/__admin/requests").retrieve().toBodilessEntity();
    }

    @Test
    void freeNoTieneWebhooksProTieneJsonYNoSeConfiaEnElPlanOTenantDelCliente() throws Exception {
        mvc.perform(get("/webhooks").with(as(FREE)).header("X-Tenant-Id", PRO))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FEATURE_NOT_IN_PLAN"));
        mvc.perform(put("/webhooks").with(as(FREE)).contentType(MediaType.APPLICATION_JSON)
                .content(change("/events", SECRET))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FEATURE_NOT_IN_PLAN"));
        mvc.perform(get("/webhooks").with(as(PRO)).header("X-Tenant-Id", FREE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.eventos").isEmpty())
                .andExpect(jsonPath("$.secretoConfigurado").value(false));
        mvc.perform(get("/webhooks").with(jwt().jwt(token -> token.claim("tenant_id", PRO.toString()))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("INSUFFICIENT_ROLE"));
        mvc.perform(get("/webhooks")).andExpect(status().isUnauthorized());
    }

    @Test
    void configuraYConservaElSecretoSinDevolverloYLoCifraPorTenant() throws Exception {
        mvc.perform(put("/webhooks").with(as(PRO)).contentType(MediaType.APPLICATION_JSON).content(change("/events", null)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(put("/webhooks").with(as(PRO)).contentType(MediaType.APPLICATION_JSON).content(change("/events", SECRET)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.url").value(url("/events")))
                .andExpect(jsonPath("$.secreto").doesNotExist()).andExpect(jsonPath("$.secretoConfigurado").value(true));
        String cipher = scoped(PRO, () -> jdbc.queryForObject("select secret_ciphertext from webhooks.subscriptions where tenant_id = ?", String.class, PRO));
        assertThat(cipher).doesNotContain(SECRET);
        assertThat(encryption.decrypt(PRO, cipher)).isEqualTo(SECRET);
        assertThatThrownBy(() -> encryption.decrypt(OTHER, cipher)).isInstanceOf(IllegalStateException.class);
        mvc.perform(put("/webhooks").with(as(PRO)).contentType(MediaType.APPLICATION_JSON).content(change("/events", null)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.secretoConfigurado").value(true));
        assertThat(scoped(PRO, () -> jdbc.queryForObject("select secret_ciphertext from webhooks.subscriptions where tenant_id = ?", String.class, PRO))).isEqualTo(cipher);
        mvc.perform(put("/webhooks").with(as(PRO)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"http://localhost\",\"eventos\":[\"ConsentGranted.v1\",\"ConsentGranted.v1\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void creditAssessmentCompletedLlegaDesdeElOutboxConFirmaValidaYSeDeduplica() throws Exception {
        configure(PRO, "/events");
        stub("/events", 202);
        CreditAssessmentCompleted event = completed(PRO);
        scoped(PRO, () -> transactions.execute(tx -> { events.publishEvent(event); return null; }));
        await().atMost(Duration.ofSeconds(10)).until(() -> count(PRO, event.eventId()) == 1);
        await().atMost(Duration.ofSeconds(10)).until(() -> jdbc.queryForObject("""
            select count(*) from event_publication where listener_id = 'webhooks.assessment'
            and serialized_event like ? and completion_date is not null
            """, Integer.class, "%" + event.eventId() + "%") == 1);
        webhooks.enqueue(PRO, event.eventId(), CreditAssessmentCompleted.SCHEMA, event);
        assertThat(count(PRO, event.eventId())).isEqualTo(1);
        assertThat(webhooks.deliverNext(PRO, Instant.now().plusSeconds(1))).isTrue();
        assertThat(state(PRO, event.eventId())).isEqualTo("DELIVERED");
        JsonNode requests = requests();
        assertThat(requests.size()).isEqualTo(1);
        JsonNode request = requests.get(0).get("request");
        assertValidSignature(request);
        JsonNode body = JSON.readTree(request.get("body").asString());
        assertThat(body.get("eventId").asString()).isEqualTo(event.eventId().toString());
        assertThat(body.get("tenantId").asString()).isEqualTo(PRO.toString());
        assertThat(body.get("assessmentId").asString()).isEqualTo(event.assessmentId().toString());
        assertThat(body.get("score").asInt()).isEqualTo(700);
        assertThat(header(request, "X-DoggyCredit-Event")).isEqualTo(CreditAssessmentCompleted.SCHEMA);
        assertThat(header(request, "X-DoggyCredit-Event-Id")).isEqualTo(event.eventId().toString());
        assertThat(webhooks.deliverNext(PRO, Instant.now().plusSeconds(600))).isFalse();
        assertThat(requests().size()).isEqualTo(1);
    }

    @Test
    void un503SeReintentaConBackoffSinCambiarElEventIdOPayload() throws Exception {
        configure(PRO, "/events");
        stub("/events", 503);
        CreditAssessmentCompleted event = completed(PRO);
        webhooks.enqueue(PRO, event.eventId(), CreditAssessmentCompleted.SCHEMA, event);
        Instant first = Instant.now().plusSeconds(1);
        assertThat(webhooks.deliverNext(PRO, first)).isTrue();
        assertThat(state(PRO, event.eventId())).isEqualTo("PENDING");
        assertThat(webhooks.deliverNext(PRO, first.plusSeconds(29))).isFalse();
        assertThat(requests().size()).isEqualTo(1);
        admin().delete().uri("/__admin/mappings").retrieve().toBodilessEntity();
        stub("/events", 202);
        assertThat(webhooks.deliverNext(PRO, first.plusSeconds(31))).isTrue();
        assertThat(state(PRO, event.eventId())).isEqualTo("DELIVERED");
        JsonNode requests = requests();
        assertThat(requests.size()).isEqualTo(2);
        assertValidSignature(requests.get(0).get("request"));
        assertValidSignature(requests.get(1).get("request"));
        assertThat(requests.get(0).get("request").get("body").asString()).isEqualTo(requests.get(1).get("request").get("body").asString());
    }

    @Test
    void seDetieneTrasCincoIntentosYUnRedireccionamientoNoSeSigue() {
        configure(PRO, "/events");
        admin().post().uri("/__admin/mappings").body(Map.of("request", Map.of("method", "POST", "url", "/events"),
                "response", Map.of("status", 302, "headers", Map.of("Location", url("/should-not-receive")))))
                .retrieve().toBodilessEntity();
        CreditAssessmentCompleted event = completed(PRO);
        webhooks.enqueue(PRO, event.eventId(), CreditAssessmentCompleted.SCHEMA, event);
        for (int i = 0; i < 5; i++) assertThat(webhooks.deliverNext(PRO, Instant.now().plusSeconds(1000L * (i + 1)))).isTrue();
        assertThat(state(PRO, event.eventId())).isEqualTo("FAILED");
        assertThat(webhooks.deliverNext(PRO, Instant.now().plusSeconds(10_000))).isFalse();
        assertThat(requests().size()).isEqualTo(5);
        requests().forEach(request -> assertThat(request.get("request").get("url").asString()).isEqualTo("/events"));
    }

    @Test
    void noEntregaEventosNoSuscritosYElCambioDeReceptorCancelaPendientes() {
        configure(PRO, "/events");
        ConsentGranted unsubscribed = new ConsentGranted(UUID.randomUUID(), Instant.now(), PRO, UUID.randomUUID(),
                List.of(Source.BURO), "evaluacion", 90, Instant.now().plus(Duration.ofDays(90)));
        webhooks.enqueue(PRO, unsubscribed.eventId(), ConsentGranted.SCHEMA, unsubscribed);
        assertThat(count(PRO, unsubscribed.eventId())).isZero();
        CreditAssessmentCompleted event = completed(PRO);
        webhooks.enqueue(PRO, event.eventId(), CreditAssessmentCompleted.SCHEMA, event);
        webhooks.put(PRO, URI.create(url("/new-receiver")), List.of(CreditAssessmentCompleted.SCHEMA), null);
        assertThat(state(PRO, event.eventId())).isEqualTo("CANCELLED");
        assertThat(webhooks.deliverNext(PRO, Instant.now().plusSeconds(1000))).isFalse();
        assertThat(requests().size()).isZero();
    }

    @Test
    void rlsYAislamientoImpidenLeerOEntregarLaColaDelOtroTenant() {
        configure(PRO, "/events");
        configure(OTHER, "/other");
        CreditAssessmentCompleted event = completed(PRO);
        webhooks.enqueue(PRO, event.eventId(), CreditAssessmentCompleted.SCHEMA, event);
        assertThat(scoped(OTHER, () -> jdbc.queryForObject("select count(*) from webhooks.deliveries where tenant_id = ?", Integer.class, PRO))).isZero();
        assertThat(webhooks.deliverNext(OTHER, Instant.now().plusSeconds(1000))).isFalse();
        assertThat(requests().size()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from webhooks.subscriptions", Integer.class)).isZero();
    }

    @Test
    void bajarAFreeODesactivarLaConfiguracionCancelaEntregasPendientes() {
        configure(PRO, "/events");
        CreditAssessmentCompleted event = completed(PRO);
        webhooks.enqueue(PRO, event.eventId(), CreditAssessmentCompleted.SCHEMA, event);
        tenants.update(new TenantId(PRO), "FREE", null);
        try {
            assertThat(webhooks.deliverNext(PRO, Instant.now().plusSeconds(1000))).isFalse();
            assertThat(state(PRO, event.eventId())).isEqualTo("CANCELLED");
            assertThat(requests().size()).isZero();
        } finally { tenants.update(new TenantId(PRO), "PRO", null); }
        configure(PRO, "/events");
        CreditAssessmentCompleted another = completed(PRO);
        webhooks.enqueue(PRO, another.eventId(), CreditAssessmentCompleted.SCHEMA, another);
        webhooks.put(PRO, null, List.of(), null);
        assertThat(state(PRO, another.eventId())).isEqualTo("CANCELLED");
        assertThat(webhooks.get(PRO).secretConfigured()).isFalse();
    }

    private void configure(UUID tenant, String path) {
        webhooks.put(tenant, URI.create(url(path)), List.of(CreditAssessmentCompleted.SCHEMA), SECRET);
    }
    private int count(UUID tenant, UUID event) {
        return scoped(tenant, () -> jdbc.queryForObject("select count(*) from webhooks.deliveries where tenant_id = ? and event_id = ?", Integer.class, tenant, event));
    }
    private String state(UUID tenant, UUID event) {
        return scoped(tenant, () -> jdbc.queryForObject("select status from webhooks.deliveries where tenant_id = ? and event_id = ?", String.class, tenant, event));
    }
    private static CreditAssessmentCompleted completed(UUID tenant) {
        return new CreditAssessmentCompleted(UUID.randomUUID(), Instant.now(), tenant, UUID.randomUUID(), UUID.randomUUID(),
                AssessmentStatus.PREAPROBADO, 700, ScoreBand.B, List.of("Buen historial"), "scoring-v2", true, "corr-webhook-test");
    }
    private static String change(String path, String secret) {
        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("url", url(path)); body.put("eventos", List.of(CreditAssessmentCompleted.SCHEMA));
        if (secret != null) body.put("secreto", secret);
        return JSON.writeValueAsString(body);
    }
    private static void stub(String path, int status) {
        admin().post().uri("/__admin/mappings").body(Map.of("request", Map.of("method", "POST", "url", path), "response", Map.of("status", status)))
                .retrieve().toBodilessEntity();
    }
    private static RestClient admin() {
        return RestClient.builder().baseUrl(url("")).requestFactory(new SimpleClientHttpRequestFactory()).build();
    }
    private static JsonNode requests() {
        return JSON.readTree(admin().get().uri("/__admin/requests").retrieve().body(String.class)).get("requests");
    }
    private static String url(String path) { return "http://" + RECEIVER.getHost() + ":" + RECEIVER.getMappedPort(8080) + path; }
    private static RequestPostProcessor as(UUID tenant) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN"))
                .jwt(token -> token.subject("webhook-admin").claim("tenant_id", tenant.toString()).claim("plan", "PRO"));
    }
    private static <T> T scoped(UUID tenant, Supplier<T> action) {
        UUID previous = TenantContext.currentOrNone();
        TenantContext.set(tenant);
        try { return action.get(); } finally {
            if (previous == null) TenantContext.clear(); else TenantContext.set(previous);
        }
    }
    private static void assertValidSignature(JsonNode request) throws Exception {
        String timestamp = header(request, "X-DoggyCredit-Timestamp");
        String body = request.get("body").asString();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String expected = "sha256=" + HexFormat.of().formatHex(mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
        assertThat(header(request, "X-DoggyCredit-Signature")).isEqualTo(expected);
    }
}
