package com.doggycredit.tenancy.internal;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.ErrorResponseException;

import com.doggycredit.audit.AuditTrail;
import com.doggycredit.billing.PlanCatalog;
import com.doggycredit.billing.PlanInfo;
import com.doggycredit.shared.ErrorCode;
import com.doggycredit.tenancy.TenantId;
import com.doggycredit.tenancy.TenantRegistry;

/**
 * A-10 (LOS-71): invitación al equipo. El límite es del plan vigente
 * ({@code billing.plans.max_usuarios}, 0 = sin límite) y cuenta las cuentas humanas
 * habilitadas del tenant en Keycloak, las invitaciones pendientes incluidas: una
 * invitación ocupa su lugar desde que se crea.
 */
@Service
class TeamInvitationService {

	private final TenantRegistry tenants;
	private final PlanCatalog plans;
	private final KeycloakClients keycloak;
	private final AuditTrail audit;

	TeamInvitationService(TenantRegistry tenants, PlanCatalog plans, KeycloakClients keycloak, AuditTrail audit) {
		this.tenants = tenants;
		this.plans = plans;
		this.keycloak = keycloak;
		this.audit = audit;
	}

	/** @throws ErrorResponseException 409 PLAN_LIMIT_REACHED si el plan no admite otra cuenta */
	KeycloakClients.TeamMember invite(UUID tenantId, KeycloakClients.NewMember member) {
		PlanInfo plan = plans.requireById(tenants.requireById(TenantId.of(tenantId)).plan());
		long members = keycloak.listTeamMembers(tenantId).stream().filter(KeycloakClients.TeamMember::enabled).count();
		if (plan.maxUsuarios() > 0 && members >= plan.maxUsuarios()) {
			throw new ErrorResponseException(HttpStatus.CONFLICT, ErrorCode.PLAN_LIMIT_REACHED.toProblemDetail(
					"El plan " + plan.nombre() + " permite un máximo de " + plan.maxUsuarios() + " usuarios."), null);
		}
		KeycloakClients.TeamMember invited = keycloak.invite(tenantId, member);
		audit.record(tenantId, "TEAM_MEMBER_INVITED", "TENANT", tenantId);
		return invited;
	}

}
