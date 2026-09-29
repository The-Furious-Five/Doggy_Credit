"""Configura URLs públicas usando DC_KEYCLOAK_ISSUER_URI ya declarada."""
import json
import os
import re
from pathlib import Path
from urllib.parse import urlsplit

import yaml

import generate_realm

ROOT = Path(__file__).resolve().parents[1]


def public_origin(issuer):
    url = urlsplit(issuer)
    if url.scheme != "https" or url.path != "/auth/realms/doggycredit" or url.query or url.fragment or url.username:
        raise SystemExit("Issuer requerido: https://DOMINIO/auth/realms/doggycredit")
    return url, "https://" + url.netloc


def caddyfile_for(text, url, origin):
    """Apunta el sitio al dominio público; fuera de localhost Caddy obtiene un certificado público."""
    if "https://localhost {" not in text:
        raise SystemExit("Caddyfile sin el sitio https://localhost")
    text = text.replace("https://localhost {", origin + " {")
    if url.hostname != "localhost":
        # La CA interna solo sirve en local; en la VM el certificado debe ser de confianza pública.
        text = re.sub(r"^[ \t]*tls internal[ \t]*\n", "", text, flags=re.MULTILINE)
    return text


if __name__ == "__main__":
    url, origin = public_origin(os.environ["DC_KEYCLOAK_ISSUER_URI"])
    realm = generate_realm.generate(yaml.safe_load((ROOT / "seed/tenants.yaml").read_text()), origin)
    (ROOT / "infra/keycloak/doggycredit-realm.json").write_text(json.dumps(realm, indent=2, ensure_ascii=False) + "\n")
    caddyfile = ROOT / "infra/caddy/Caddyfile"
    caddyfile.write_text(caddyfile_for(caddyfile.read_text(), url, origin))
