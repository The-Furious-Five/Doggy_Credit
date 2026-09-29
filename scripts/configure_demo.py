"""Configura URLs públicas usando DC_KEYCLOAK_ISSUER_URI ya declarada."""
import os
from pathlib import Path
from urllib.parse import urlsplit
issuer = os.environ["DC_KEYCLOAK_ISSUER_URI"]
url = urlsplit(issuer)
if url.scheme != "https" or url.path != "/auth/realms/doggycredit" or url.query or url.fragment or url.username:
    raise SystemExit("Issuer requerido: https://DOMINIO/auth/realms/doggycredit")
root = Path(__file__).resolve().parents[1]
origin = "https://" + url.netloc
import generate_realm
import yaml, json
realm = generate_realm.generate(yaml.safe_load((root / "seed/tenants.yaml").read_text()), origin)
(root / "infra/keycloak/doggycredit-realm.json").write_text(json.dumps(realm, indent=2) + "\n")
p = root / "infra/caddy/Caddyfile"
s = p.read_text().replace("https://localhost {", origin + " {")
if url.hostname != "localhost":
    s = s.replace("    tls internal\n", "")
p.write_text(s)
