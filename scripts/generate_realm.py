"""Genera configuración sin secretos: Keycloak resuelve ${ENV} al importar."""
import argparse
import json
import re
from pathlib import Path
from uuid import UUID
from urllib.parse import urlsplit
import yaml

ROOT = Path(__file__).resolve().parents[1]

# D-09: cuenta de servicio con la que la API crea y borra los clientes de las
# claves de API. Sin mapper de tenant ni audiencia doggycredit-api: su token
# no sirve contra la API (la rechazan la audiencia y TenantClaimFilter).
API_ADMIN_CLIENT = "doggycredit-api-admin"
API_ADMIN_ROLES = ["manage-clients", "view-clients", "manage-users", "view-users", "view-realm"]
MOBILE_CLIENT_ID = "doggycredit-mobile"

def user_profile():
    """Keycloak 26 oculta atributos no gestionados incluso en la Admin REST API.
    tenant_id debe ser visible para la API administradora, sin ser editable
    por el usuario. Mantener las validaciones del perfil estándar de Keycloak."""
    permissions = {"view": ["admin", "user"], "edit": ["admin", "user"]}
    attributes = [
        {"name": "username", "displayName": "${username}", "multivalued": False,
         "permissions": permissions,
         "validations": {"length": {"min": 3, "max": 255},
                         "username-prohibited-characters": {}, "up-username-not-idn-homograph": {}}},
        {"name": "email", "displayName": "${email}", "multivalued": False,
         "required": {"roles": ["user"]}, "permissions": permissions,
         "validations": {"email": {}, "length": {"max": 255}}},
    ]
    for field in ("firstName", "lastName"):
        attributes.append({"name": field, "displayName": "${" + field + "}", "multivalued": False,
                           "required": {"roles": ["user"]}, "permissions": permissions,
                           "validations": {"length": {"max": 255},
                                           "person-name-prohibited-characters": {}}})
    attributes.append({"name": "tenant_id", "displayName": "Tenant", "multivalued": False,
                       "permissions": {"view": ["admin"], "edit": ["admin"]},
                       "validations": {"pattern": {
                           "pattern": "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"}}})
    # En Keycloak 26.4 la política Disabled es null/ausente; no existe en el enum.
    return {"attributes": attributes,
            "groups": [{"name": "user-metadata", "displayHeader": "User metadata",
                        "displayDescription": "Attributes, which refer to user metadata"}]}

def mapper(name, kind, config):
    return {"name": name, "protocol": "openid-connect", "protocolMapper": kind,
            "config": {"access.token.claim": "true", "id.token.claim": "true",
                       "userinfo.token.claim": "true", "jsonType.label": "String", **config}}

def mobile_client():
    """Una sola definición para importaciones nuevas y despliegues con realm existente."""
    return {"clientId": MOBILE_CLIENT_ID, "enabled": True, "publicClient": True,
            "protocol": "openid-connect", "standardFlowEnabled": True,
            "implicitFlowEnabled": False, "directAccessGrantsEnabled": False,
            "serviceAccountsEnabled": False, "bearerOnly": False,
            "redirectUris": ["doggycredit://oauth/callback"], "webOrigins": [],
            "attributes": {"pkce.code.challenge.method": "S256"},
            "defaultClientScopes": ["basic", "profile", "email", "roles"],
            "optionalClientScopes": [],
            "protocolMappers": [
                mapper("tenant_id", "oidc-usermodel-attribute-mapper",
                       {"user.attribute": "tenant_id", "claim.name": "tenant_id"}),
                mapper("api-audience", "oidc-audience-mapper",
                       {"included.custom.audience": "doggycredit-api", "id.token.claim": "false"})]}

def write_realm(realm, output):
    output.write_text(json.dumps(realm, indent=2, ensure_ascii=False) + "\n")
    # Fuera de data/import: este archivo representa un cliente, no otro realm.
    output.with_name("doggycredit-mobile.json").write_text(
        json.dumps(mobile_client(), indent=2, ensure_ascii=False) + "\n")

def person_name(full_name):
    """Keycloak rechaza ()<>&... en nombres y forzaría VERIFY_PROFILE en cada login.
    Las anotaciones de la semilla, como "(demo)", no forman parte del nombre visible."""
    name = re.sub(r"\([^)]*\)", "", full_name)
    first, _, last = re.sub(r"[()<>&\"$%!#?§;*~/\\|^=\[\]{}]", "", name).strip().partition(" ")
    return first, " ".join(last.split()) or first

def generate(seed, origin):
    url = urlsplit(origin)
    local_http = url.scheme == "http" and url.hostname == "localhost"
    secure_origin = url.scheme == "https" and bool(url.hostname)
    if (not (local_http or secure_origin) or url.username or url.password
            or url.path or url.query or url.fragment):
        raise ValueError("Usa un origen https válido o http://localhost[:puerto] solo para desarrollo.")
    tenant_mapper = mapper("tenant_id", "oidc-usermodel-attribute-mapper",
                           {"user.attribute": "tenant_id", "claim.name": "tenant_id"})
    audience = mapper("api-audience", "oidc-audience-mapper",
                      {"included.custom.audience": "doggycredit-api", "id.token.claim": "false"})
    clients = [{"clientId": "doggycredit-web", "publicClient": True,
                "protocol": "openid-connect", "standardFlowEnabled": True,
                "directAccessGrantsEnabled": False, "serviceAccountsEnabled": False,
                "redirectUris": [origin + "/callback"], "webOrigins": [origin],
                "attributes": {"pkce.code.challenge.method": "S256",
                               "post.logout.redirect.uris": origin + "/"},
                "defaultClientScopes": ["basic", "profile", "email", "roles"],
                "protocolMappers": [tenant_mapper, audience]}, mobile_client()]
    users, seen = [], set()
    for tenant in seed["tenants"]:
        tid = str(UUID(tenant["tenant_id"]))
        if tid in seen:
            raise ValueError("tenant_id duplicado")
        seen.add(tid)
        for user in tenant["usuarios"]:
            if not set(user["roles"]) <= set(seed["roles"]):
                raise ValueError("Rol desconocido")
            first_name, last_name = person_name(user["nombre_completo"])
            # A-14 (LOS-118): MFA obligatorio para administradores de tenant.
            user_payload = {"username": user["username"], "enabled": True,
                            "email": user["email"], "emailVerified": True,
                            "firstName": first_name, "lastName": last_name,
                            "attributes": {"tenant_id": [tid]}, "realmRoles": user["roles"],
                            "credentials": [{"type": "password", "temporary": False,
                                             "value": "${" + user["password_env"] + "}"}]}
            if "TENANT_ADMIN" in user["roles"]:
                user_payload["requiredActions"] = ["CONFIGURE_TOTP"]
            users.append(user_payload)
        for client in tenant["clientes_m2m"]:
            clients.append({"clientId": client["client_id"], "protocol": "openid-connect",
                            "publicClient": False, "standardFlowEnabled": False,
                            "directAccessGrantsEnabled": False, "serviceAccountsEnabled": True,
                            "secret": "${" + client["secret_env"] + "}",
                            "defaultClientScopes": ["basic", "roles", *client["scopes"]],
                            "protocolMappers": [tenant_mapper, audience]})
            users.append({"username": "service-account-" + client["client_id"],
                          "enabled": True, "serviceAccountClientId": client["client_id"],
                          "attributes": {"tenant_id": [tid]}, "realmRoles": ["API_CLIENT"]})
    clients.append({"clientId": API_ADMIN_CLIENT, "protocol": "openid-connect",
                    "publicClient": False, "standardFlowEnabled": False,
                    "directAccessGrantsEnabled": False, "serviceAccountsEnabled": True,
                    "secret": "${DC_KEYCLOAK_API_ADMIN_SECRET}",
                    "defaultClientScopes": ["basic"], "protocolMappers": []})
    users.append({"username": "service-account-" + API_ADMIN_CLIENT, "enabled": True,
                  "serviceAccountClientId": API_ADMIN_CLIENT,
                  "clientRoles": {"realm-management": API_ADMIN_ROLES}})
    scopes = sorted({s for t in seed["tenants"] for c in t["clientes_m2m"] for s in c["scopes"]})
    standard_scopes = [
        # Keycloak 25+ emite `sub` solo desde el scope `basic`; al declarar los
        # scopes explícitamente hay que incluirlo o el access token humano sale sin `sub`.
        {"name": "basic", "protocol": "openid-connect",
         "attributes": {"include.in.token.scope": "false", "display.on.consent.screen": "false"},
         "protocolMappers": [
            mapper("sub", "oidc-sub-mapper", {"introspection.token.claim": "true"}),
            mapper("auth_time", "oidc-usersessionmodel-note-mapper",
                   {"user.session.note": "AUTH_TIME", "claim.name": "auth_time", "jsonType.label": "long"})]},
        {"name": "profile", "protocol": "openid-connect", "protocolMappers": [
            mapper("username", "oidc-usermodel-property-mapper", {"user.attribute": "username", "claim.name": "preferred_username"}),
            mapper("full name", "oidc-full-name-mapper", {})]},
        {"name": "email", "protocol": "openid-connect", "protocolMappers": [
            mapper("email", "oidc-usermodel-property-mapper", {"user.attribute": "email", "claim.name": "email"})]},
        {"name": "roles", "protocol": "openid-connect", "protocolMappers": [
            mapper("realm roles", "oidc-usermodel-realm-role-mapper",
                   {"claim.name": "realm_access.roles", "multivalued": "true"})]},
    ]
    return {"realm": "doggycredit", "displayName": "DoggyCredit", "enabled": True,
            # Wordmark y tema con los colores del Design System (infra/keycloak/themes).
            "displayNameHtml": 'Doggy<span class="dc-wordmark-accent">Credit</span>',
            "loginTheme": "doggycredit",
            # La pantalla de acceso de Keycloak también es UI: en español (guía §6.3).
            "internationalizationEnabled": True, "supportedLocales": ["es"], "defaultLocale": "es",
            "sslRequired": "external",
            "registrationAllowed": False, "resetPasswordAllowed": False,
            "editUsernameAllowed": False, "bruteForceProtected": True,
            "components": {"org.keycloak.userprofile.UserProfileProvider": [
                {"providerId": "declarative-user-profile", "subComponents": {},
                 "config": {"kc.user.profile.config": [json.dumps(user_profile(), ensure_ascii=False)]}}]},
            "roles": {"realm": [{"name": r} for r in seed["roles"]]},
            "defaultDefaultClientScopes": ["basic", "profile", "email", "roles"],
            "clientScopes": standard_scopes + [{"name": s, "protocol": "openid-connect",
                              "attributes": {"include.in.token.scope": "true"}} for s in scopes],
            "clients": clients, "users": users}

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    local_env = ROOT / ".env"
    env_values = {}
    if local_env.is_file():
        env_values = dict(line.split("=", 1) for line in local_env.read_text().splitlines()
                          if "=" in line and not line.lstrip().startswith("#"))
    issuer = env_values.get("DC_KEYCLOAK_ISSUER_URI", "https://localhost/auth/realms/doggycredit")
    default_origin = issuer.removesuffix("/auth/realms/doggycredit")
    parser.add_argument("--origin", default=default_origin)
    parser.add_argument("--output", type=Path, default=ROOT / "infra/keycloak/doggycredit-realm.json")
    args = parser.parse_args()
    realm = generate(yaml.safe_load((ROOT / "seed/tenants.yaml").read_text()), args.origin)
    write_realm(realm, args.output)
