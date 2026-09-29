"""Genera configuración sin secretos: Keycloak resuelve ${ENV} al importar."""
import argparse
import json
import re
from pathlib import Path
from uuid import UUID
from urllib.parse import urlsplit
import yaml

ROOT = Path(__file__).resolve().parents[1]

def mapper(name, kind, config):
    return {"name": name, "protocol": "openid-connect", "protocolMapper": kind,
            "config": {"access.token.claim": "true", "id.token.claim": "true",
                       "userinfo.token.claim": "true", "jsonType.label": "String", **config}}

def person_name(full_name):
    """Keycloak rechaza ()<>&... en nombres y forzaría VERIFY_PROFILE en cada login."""
    first, _, last = re.sub(r"[()<>&\"$%!#?§;*~/\\|^=\[\]{}]", "", full_name).strip().partition(" ")
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
                "protocolMappers": [tenant_mapper, audience]}]
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
            users.append({"username": user["username"], "enabled": True,
                          "email": user["email"], "emailVerified": True,
                          "firstName": first_name, "lastName": last_name,
                          "attributes": {"tenant_id": [tid]}, "realmRoles": user["roles"],
                          "credentials": [{"type": "password", "temporary": False,
                                           "value": "${" + user["password_env"] + "}"}]})
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
    return {"realm": "doggycredit", "enabled": True, "sslRequired": "external",
            "registrationAllowed": False, "resetPasswordAllowed": False,
            "editUsernameAllowed": False, "bruteForceProtected": True,
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
    issuer = env_values.get("DC_KEYCLOAK_ISSUER_URI", "http://localhost:8080/auth/realms/doggycredit")
    default_origin = issuer.removesuffix("/auth/realms/doggycredit")
    parser.add_argument("--origin", default=default_origin)
    parser.add_argument("--output", type=Path, default=ROOT / "infra/keycloak/doggycredit-realm.json")
    args = parser.parse_args()
    realm = generate(yaml.safe_load((ROOT / "seed/tenants.yaml").read_text()), args.origin)
    args.output.write_text(json.dumps(realm, indent=2, ensure_ascii=False) + "\n")
