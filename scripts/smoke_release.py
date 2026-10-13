"""E-13: comprueba desde fuera que la release publicada es la esperada y que la demo responde.

    python scripts/smoke_release.py https://DOMINIO --release <sha>
    python scripts/smoke_release.py https://localhost --release local --insecure   # Compose local

Solo hace GET de lectura: no inicia sesión, no crea datos y no consume cuota.
Reintenta mientras la release nueva termina de arrancar; sale con 1 si algo falla.
"""
import argparse
from html.parser import HTMLParser
import json
import re
import ssl
import sys
import time
import urllib.error
import urllib.request
from urllib.parse import urlsplit, urlencode

REALM = "/auth/realms/doggycredit"


def mobile_authorization_url(origin):
    return origin + REALM + "/protocol/openid-connect/auth?" + urlencode({
        "client_id": "doggycredit-mobile", "redirect_uri": "doggycredit://oauth/callback",
        "response_type": "code", "scope": "openid profile", "state": "release-smoke",
        "code_challenge": "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", "code_challenge_method": "S256",
    })


class MobileLoginForm(HTMLParser):
    found = False

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag == "form" and attrs.get("id") == "kc-form-login" and attrs.get("action"):
            self.found = True


class SmokeFailure(Exception):
    pass


def http_get(url, context=None, timeout=15):
    """(status, cabeceras, cuerpo) también para respuestas 4xx/5xx."""
    request = urllib.request.Request(url, headers={"User-Agent": "doggycredit-smoke-release"})
    try:
        with urllib.request.urlopen(request, timeout=timeout, context=context) as response:
            return response.status, response.headers, response.read()
    except urllib.error.HTTPError as error:
        with error:
            return error.code, error.headers, error.read()


def _json(body, what):
    try:
        return json.loads(body)
    except ValueError:
        raise SmokeFailure(f"{what} no devolvió JSON")


def _expect_status(status, expected, what):
    if status != expected:
        raise SmokeFailure(f"{what} respondió {status}, se esperaba {expected}")


def check_health(origin, get, release):
    status, _, body = get(origin + "/actuator/health")
    _expect_status(status, 200, "/actuator/health")
    state = _json(body, "/actuator/health").get("status")
    if state != "UP":
        raise SmokeFailure(f"la API informa {state!r}, se esperaba 'UP'")
    return "API saludable (UP)"


def check_release(origin, get, release):
    status, _, body = get(origin + "/release.json")
    _expect_status(status, 200, "/release.json")
    published = _json(body, "/release.json").get("release")
    if published != release:
        raise SmokeFailure(f"en línea está la release {published!r}, se esperaba {release!r}")
    return f"release en línea: {published}"


def check_spa(origin, get, release):
    status, headers, body = get(origin + "/")
    _expect_status(status, 200, "/")
    if "text/html" not in headers.get("Content-Type", ""):
        raise SmokeFailure("/ no devolvió HTML")
    if headers.get("X-Frame-Options") != "DENY" or headers.get("X-Content-Type-Options") != "nosniff":
        raise SmokeFailure("la SPA perdió X-Frame-Options DENY o X-Content-Type-Options nosniff")
    bundle = re.search(r'<script src="(main-[\w-]+\.js)"', body.decode("utf-8", "replace"))
    if not bundle:
        raise SmokeFailure("index.html no referencia el bundle main-*.js")
    status, _, _ = get(f"{origin}/{bundle.group(1)}")
    _expect_status(status, 200, bundle.group(1))
    return f"SPA y bundle {bundle.group(1)}"


def check_identity(origin, get, release):
    status, _, body = get(origin + REALM + "/.well-known/openid-configuration")
    _expect_status(status, 200, "la configuración OIDC")
    issuer = _json(body, "la configuración OIDC").get("issuer")
    if issuer != origin + REALM:
        raise SmokeFailure(f"Keycloak publica el issuer {issuer!r}, se esperaba {origin + REALM!r}")
    return "Keycloak con el issuer del dominio"


def check_api_requires_token(origin, get, release):
    status, headers, _ = get(origin + "/api/v1/tenant/actual")
    _expect_status(status, 401, "/api/v1/tenant/actual sin token")
    if "problem+json" not in headers.get("Content-Type", ""):
        raise SmokeFailure("el 401 de la API no es un Problem Details (RFC 9457)")
    return "la API exige token (401 RFC 9457)"


def check_mobile_login(origin, get, release):
    status, _, body = get(mobile_authorization_url(origin))
    _expect_status(status, 200, "el acceso móvil PKCE")
    form = MobileLoginForm()
    form.feed(body.decode("utf-8", "replace"))
    if not form.found:
        raise SmokeFailure("Keycloak no presenta el formulario del cliente doggycredit-mobile")
    return "cliente móvil disponible con callback doggycredit://oauth/callback y PKCE S256"


def check_actuator_closed(origin, get, release):
    status, _, _ = get(origin + "/actuator/env")
    _expect_status(status, 404, "/actuator/env")
    return "actuator cerrado salvo /actuator/health"


def check_hsts(origin, get, release):
    _, headers, _ = get(origin + REALM)
    hsts = headers.get("Strict-Transport-Security") or ""
    max_age = re.search(r"max-age=(\d+)", hsts)
    local = urlsplit(origin).hostname in ("localhost", "127.0.0.1")
    if local and (not max_age or max_age.group(1) != "0"):
        raise SmokeFailure("en localhost el HSTS debe anularse con max-age=0 (PR #42)")
    if not local and (not max_age or max_age.group(1) == "0"):
        raise SmokeFailure(f"el dominio público debe enviar HSTS con max-age > 0 (recibido: {hsts!r})")
    return f"HSTS {'anulado en local' if local else 'activo'} ({hsts})"


CHECKS = (
    ("salud", check_health),
    ("versión", check_release),
    ("SPA", check_spa),
    ("identidad", check_identity),
    ("acceso móvil", check_mobile_login),
    ("API", check_api_requires_token),
    ("actuator", check_actuator_closed),
    ("HSTS", check_hsts),
)


def run(origin, release, get, attempts=20, delay=3, out=print):
    """Repite todas las comprobaciones hasta que pasen o se agoten los intentos."""
    origin = origin.rstrip("/")
    for attempt in range(1, attempts + 1):
        results = []
        for name, check in CHECKS:
            try:
                results.append((name, True, check(origin, get, release)))
            except (SmokeFailure, OSError) as problem:
                results.append((name, False, str(problem)))
        if all(ok for _, ok, _ in results) or attempt == attempts:
            break
        time.sleep(delay)
    for name, ok, detail in results:
        out(f"{'OK   ' if ok else 'FALLA'} {name}: {detail}")
    return all(ok for _, ok, _ in results)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("origin", help="https://DOMINIO (sin barra final)")
    parser.add_argument("--release", required=True, help="SHA que debe estar en línea ('local' en Compose)")
    parser.add_argument("--attempts", type=int, default=20)
    parser.add_argument("--delay", type=float, default=3)
    parser.add_argument("--insecure", action="store_true", help="aceptar la CA interna de Caddy (solo local)")
    args = parser.parse_args(argv)
    context = ssl._create_unverified_context() if args.insecure else None
    ok = run(args.origin, args.release, lambda url: http_get(url, context), args.attempts, args.delay)
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
