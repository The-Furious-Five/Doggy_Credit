"""E-13: el smoke test de la release, sin depender de la VM."""
from email.message import Message
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import threading
import unittest

from smoke_release import REALM, http_get, run

ORIGIN = "https://demo.doggycredit.example"
SHA = "35baded0123456789abcdef0123456789abcdef0"


def headers(**values):
    message = Message()
    for name, value in values.items():
        message[name.replace("_", "-")] = value
    return message


def healthy_site(origin=ORIGIN, release=SHA, hsts="max-age=31536000; includeSubDomains"):
    """Respuestas de una release sana, por ruta."""
    return {
        "/actuator/health": (200, headers(), b'{"status":"UP"}'),
        "/release.json": (200, headers(Content_Type="application/json"), json.dumps({"release": release}).encode()),
        "/": (
            200,
            headers(Content_Type="text/html; charset=utf-8", X_Frame_Options="DENY", X_Content_Type_Options="nosniff"),
            b'<app-root></app-root><script src="main-Z6GOYQKP.js" type="module"></script>',
        ),
        "/main-Z6GOYQKP.js": (200, headers(Content_Type="text/javascript"), b""),
        REALM + "/.well-known/openid-configuration": (200, headers(), json.dumps({"issuer": origin + REALM}).encode()),
        "/api/v1/tenant/actual": (401, headers(Content_Type="application/problem+json;charset=UTF-8"), b"{}"),
        "/actuator/env": (404, headers(), b""),
        REALM: (200, headers(Strict_Transport_Security=hsts), b"{}"),
        REALM + '/protocol/openid-connect/auth': (
            200, headers(Content_Type='text/html'),
            b'<form id="kc-form-login" action="/auth/login-actions/authenticate"></form>',
        ),
    }


class SmokeReleaseTest(unittest.TestCase):
    def smoke(self, site, origin=ORIGIN, release=SHA):
        lines = []
        get = lambda url: site.get(url[len(origin):].split('?')[0], (404, headers(), b""))
        ok = run(origin, release, get, attempts=1, delay=0, out=lines.append)
        return ok, "\n".join(lines)

    def test_healthy_release_passes_every_check(self):
        ok, report = self.smoke(healthy_site())
        self.assertTrue(ok, report)
        self.assertIn(f"release en línea: {SHA}", report)
        self.assertNotIn("FALLA", report)

    def test_previous_release_still_online_fails(self):
        ok, report = self.smoke(healthy_site(release="5e1e942"))
        self.assertFalse(ok)
        self.assertIn(f"FALLA versión: en línea está la release '5e1e942', se esperaba '{SHA}'", report)

    def test_release_marker_missing_fails(self):
        site = healthy_site()
        site["/release.json"] = (200, headers(Content_Type="text/html"), b"<!doctype html>")  # fallback de la SPA
        ok, report = self.smoke(site)
        self.assertFalse(ok)
        self.assertIn("FALLA versión: /release.json no devolvió JSON", report)

    def test_api_down_fails(self):
        site = healthy_site()
        site["/actuator/health"] = (503, headers(), b'{"status":"DOWN"}')
        ok, report = self.smoke(site)
        self.assertFalse(ok)
        self.assertIn("FALLA salud", report)

    def test_missing_bundle_fails(self):
        site = healthy_site()
        del site["/main-Z6GOYQKP.js"]
        ok, report = self.smoke(site)
        self.assertFalse(ok)
        self.assertIn("FALLA SPA: main-Z6GOYQKP.js respondió 404", report)

    def test_wrong_issuer_fails(self):
        ok, report = self.smoke(healthy_site(origin="https://localhost"))
        self.assertFalse(ok)
        self.assertIn("FALLA identidad", report)

    def test_missing_mobile_client_fails_release(self):
        site = healthy_site()
        site[REALM + '/protocol/openid-connect/auth'] = (400, headers(), b'{"error":"Cliente no encontrado"}')
        ok, report = self.smoke(site)
        self.assertFalse(ok)
        self.assertIn('FALLA acceso móvil', report)

    def test_mobile_error_page_with_http_200_is_not_a_login(self):
        site = healthy_site()
        site[REALM + '/protocol/openid-connect/auth'] = (200, headers(), b'<p>Invalid redirect URI</p>')
        ok, report = self.smoke(site)
        self.assertFalse(ok)
        self.assertIn('no presenta el formulario', report)

    def test_api_without_token_must_be_rejected(self):
        site = healthy_site()
        site["/api/v1/tenant/actual"] = (200, headers(Content_Type="application/json"), b"{}")
        ok, report = self.smoke(site)
        self.assertFalse(ok)
        self.assertIn("FALLA API", report)

    def test_exposed_actuator_fails(self):
        site = healthy_site()
        site["/actuator/env"] = (200, headers(), b"{}")
        ok, report = self.smoke(site)
        self.assertFalse(ok)
        self.assertIn("FALLA actuator", report)

    def test_public_domain_must_keep_hsts(self):
        ok, report = self.smoke(healthy_site(hsts="max-age=0"))
        self.assertFalse(ok)
        self.assertIn("FALLA HSTS", report)

    def test_localhost_must_cancel_hsts(self):
        local = "https://localhost"
        ok, report = self.smoke(healthy_site(origin=local, release="local", hsts="max-age=0"), local, "local")
        self.assertTrue(ok, report)
        ok, report = self.smoke(healthy_site(origin=local, release="local"), local, "local")
        self.assertFalse(ok)
        self.assertIn("FALLA HSTS: en localhost el HSTS debe anularse", report)

    def test_retries_until_the_new_release_is_online(self):
        site = healthy_site(release="5e1e942")
        calls = []

        def get(url):
            path = url[len(ORIGIN):].split('?')[0]
            if path == "/release.json":
                calls.append(path)
                if len(calls) == 3:
                    site["/release.json"] = healthy_site()["/release.json"]
            return site.get(path, (404, headers(), b""))

        lines = []
        self.assertTrue(run(ORIGIN, SHA, get, attempts=3, delay=0, out=lines.append), "\n".join(lines))
        self.assertEqual(len(calls), 3)


class HttpGetTest(unittest.TestCase):
    """http_get devuelve también los 4xx/5xx, sin excepciones, contra un servidor HTTP real."""

    def test_reads_errors_as_responses(self):
        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                code = 401 if self.path == "/api" else 200
                self.send_response(code)
                self.send_header("Content-Type", "application/problem+json" if code == 401 else "text/plain")
                self.end_headers()
                self.wfile.write(b"cuerpo")

            def log_message(self, *args):
                pass

        server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        threading.Thread(target=server.serve_forever, daemon=True).start()
        try:
            base = f"http://127.0.0.1:{server.server_port}"
            status, response_headers, body = http_get(base + "/api")
            self.assertEqual((status, body), (401, b"cuerpo"))
            self.assertIn("problem+json", response_headers.get("Content-Type"))
            self.assertEqual(http_get(base + "/")[0], 200)
        finally:
            server.shutdown()
            server.server_close()


if __name__ == "__main__":
    unittest.main()
