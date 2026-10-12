package com.doggycredit.tenancy.internal;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.doggycredit.shared.RoleGuard;
import com.doggycredit.shared.contract.api.PlanesApi;
import com.doggycredit.shared.contract.model.BajadaPlan;
import com.doggycredit.shared.contract.model.BajadaPlanNueva;
import com.doggycredit.shared.contract.model.CodigoPlan;
import com.doggycredit.shared.contract.model.EstadoPlan;
import com.doggycredit.shared.contract.model.FuenteIntegracion;
import com.doggycredit.shared.contract.model.GraciaPlan;
import com.doggycredit.shared.contract.model.PeriodoPlan;
import com.doggycredit.tenancy.internal.PlanChangeService.State;

/**
 * A-12 (RF-PLN-05): el tenant consulta su plan y programa o cancela su bajada.
 * El tenant sale exclusivamente del JWT: no hay forma de nombrar a otro.
 */
@RestController
class PlanController implements PlanesApi {

    private final PlanChangeService planChanges;

    PlanController(PlanChangeService planChanges) {
        this.planChanges = planChanges;
    }

    @Override
    public ResponseEntity<EstadoPlan> obtenerEstadoPlan() {
        Jwt jwt = ((JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication()).getToken();
        return ResponseEntity.ok(toContract(planChanges.state(tenantId(jwt))));
    }

    @Override
    public ResponseEntity<EstadoPlan> programarBajadaPlan(@Valid @RequestBody BajadaPlanNueva body) {
        Jwt jwt = RoleGuard.requireJwt("TENANT_ADMIN").getToken();
        String solicitadaPor = jwt.hasClaim("preferred_username") ? jwt.getClaimAsString("preferred_username")
                : jwt.getSubject();
        return ResponseEntity.ok(toContract(
                planChanges.scheduleDowngrade(tenantId(jwt), body.getPlanDestino().getValue(), solicitadaPor)));
    }

    @Override
    public ResponseEntity<EstadoPlan> cancelarBajadaPlan() {
        Jwt jwt = RoleGuard.requireJwt("TENANT_ADMIN").getToken();
        return ResponseEntity.ok(toContract(planChanges.cancelDowngrade(tenantId(jwt))));
    }

    private static EstadoPlan toContract(State state) {
        EstadoPlan dto = new EstadoPlan(CodigoPlan.fromValue(state.plan()),
                new PeriodoPlan(utc(state.periodoInicio()), utc(state.periodoFin())));
        if (state.bajada() != null) {
            dto.setBajadaProgramada(new BajadaPlan(CodigoPlan.fromValue(state.bajada().planDestino()),
                    utc(state.bajada().efectivaEn()), sources(state.bajada().integracionesADesactivar()),
                    state.bajada().solicitadaPor(), utc(state.bajada().solicitadaEn())));
        }
        if (state.gracia() != null) {
            dto.setGracia(new GraciaPlan(CodigoPlan.fromValue(state.gracia().planAnterior()),
                    utc(state.gracia().desde()), utc(state.gracia().hasta()),
                    sources(state.gracia().integracionesDesactivadas())));
        }
        return dto;
    }

    private static List<FuenteIntegracion> sources(List<String> names) {
        return names.stream().map(FuenteIntegracion::fromValue).toList();
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static UUID tenantId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("tenant_id"));
    }

}
