"""SPIKE-2: PKCE y M2M reales contra Compose, sin password grant ni mocks.
Requiere variables DC_SEED_* ya entregadas por Nicole y la CA local de Caddy.
"""
import argparse
import base64
import hashlib
import http.cookiejar
import json
import os
import secrets
import ssl
import sys
from html.parser import HTMLParser
from pathlib import Path
from urllib.error import HTTPError
from urllib.parse import parse_qs, urlencode, urlsplit
from urllib.request import build_opener, HTTPCookieProcessor, HTTPHandler, HTTPSHandler, HTTPRedirectHandler, Request

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
import yaml

class LoginForm(HTMLParser):
    action = None
    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag == 'form' and attrs.get('id') == 'kc-form-login':
            self.action = attrs['action']

class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None

def claims(token):
    part = token.split('.')[1]
    return json.loads(base64.urlsafe_b64decode(part + '=' * (-len(part) % 4)))

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--ca', help='Certificado raíz Caddy; en VM usa confianza del sistema')
    args = parser.parse_args()
    issuer = os.environ['DC_KEYCLOAK_ISSUER_URI'].rstrip('/')
    parsed = urlsplit(issuer)
    origin = f'{parsed.scheme}://{parsed.netloc}'
    # Keycloak marca sus cookies Secure; los navegadores las aceptan en
    # http://localhost (origen confiable), así que el script hace lo mismo.
    secure_protocols = ('https', 'wss', 'http') if parsed.hostname == 'localhost' else ('https', 'wss')
    policy = http.cookiejar.DefaultCookiePolicy(secure_protocols=secure_protocols)
    jar = http.cookiejar.CookieJar(policy)
    handlers = [HTTPCookieProcessor(jar), NoRedirect()]
    if parsed.scheme == 'https':
        handlers.insert(0, HTTPSHandler(context=ssl.create_default_context(cafile=args.ca)))
    else:
        handlers.insert(0, HTTPHandler())
    opener = build_opener(*handlers)

    def request(url, data=None, token=None):
        headers = {}
        if isinstance(data, dict):
            data = urlencode(data).encode()
            headers['Content-Type'] = 'application/x-www-form-urlencoded'
        if token:
            headers['Authorization'] = 'Bearer ' + token
            headers['Content-Type'] = 'application/json'
        try:
            with opener.open(Request(url, data=data, headers=headers), timeout=20) as response:
                return response.status, response.headers, response.read()
        except HTTPError as error:
            return error.code, error.headers, error.read()

    def request_json(method, url, token, payload=None):
        data = json.dumps(payload).encode() if payload is not None else None
        req = Request(url, data=data, method=method,
                      headers={'Authorization': 'Bearer ' + token, 'Content-Type': 'application/json'})
        try:
            with opener.open(req, timeout=20) as response:
                return response.status, response.read()
        except HTTPError as error:
            return error.code, error.read()

    def pkce_login(user):
        """Flujo humano Authorization Code + PKCE S256 con el formulario real de Keycloak."""
        jar.clear()  # sin la sesión SSO anterior, Keycloak vuelve a pedir usuario y contraseña
        verifier, state, nonce = secrets.token_urlsafe(48), secrets.token_urlsafe(24), secrets.token_urlsafe(24)
        challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).rstrip(b'=').decode()
        query = {'client_id': 'doggycredit-web', 'response_type': 'code', 'scope': 'openid profile',
                 'redirect_uri': origin + '/callback', 'state': state, 'nonce': nonce,
                 'code_challenge': challenge, 'code_challenge_method': 'S256'}
        status, _, body = request(issuer + '/protocol/openid-connect/auth?' + urlencode(query))
        assert status == 200, f'Formulario PKCE HTTP {status}'
        form = LoginForm(); form.feed(body.decode())
        assert form.action, 'No apareció el formulario Keycloak'
        status, headers, _ = request(form.action, {'username': user['username'], 'password': os.environ[user['password_env']]})
        assert status in (302, 303), f'Login HTTP {status}'
        redirect = urlsplit(headers['Location'])
        assert f'{redirect.scheme}://{redirect.netloc}{redirect.path}' == origin + '/callback'
        params = parse_qs(redirect.query)
        assert params['state'] == [state]
        status, _, body = request(issuer + '/protocol/openid-connect/token', {
            'grant_type': 'authorization_code', 'client_id': 'doggycredit-web',
            'code': params['code'][0], 'redirect_uri': origin + '/callback', 'code_verifier': verifier})
        assert status == 200, f'Canje PKCE HTTP {status}'
        tokens = json.loads(body)
        assert claims(tokens['id_token'])['nonce'] == nonce
        return tokens

    def client_credentials(client_id, secret):
        return request(issuer + '/protocol/openid-connect/token', {
            'grant_type': 'client_credentials', 'client_id': client_id, 'client_secret': secret})

    def verify_api(token):
        body = json.dumps({'titularDocumento': document, 'fuentes': ['BURO'],
                           'finalidad': 'Prueba sintética de identidad', 'vigenciaDias': 1}).encode()
        status, _, _ = request(origin + '/api/v1/consentimientos', body, token)
        assert status == 201, f'API rechazó token real: HTTP {status}'

    seed = yaml.safe_load((Path(__file__).resolve().parents[1] / 'seed/tenants.yaml').read_text())
    document = seed['titulares_demo'][0]['documento']
    status, _, _ = request(origin + '/api/v1/consentimientos', b'{}')
    assert status == 401, f'Sin token: HTTP {status}, esperado 401'
    print('OK: API sin token devuelve 401')
    for tenant in seed['tenants']:
        for client in tenant['clientes_m2m']:
            status, _, body = request(issuer + '/protocol/openid-connect/token', {
                'grant_type': 'client_credentials', 'client_id': client['client_id'],
                'client_secret': os.environ[client['secret_env']]})
            assert status == 200, f'M2M HTTP {status}'
            token = json.loads(body)['access_token']
            assert claims(token)['tenant_id'] == tenant['tenant_id']
            verify_api(token)
            print('OK: claim M2M y API para ' + tenant['key'])

    tenant = seed['tenants'][0]
    tokens = pkce_login(tenant['usuarios'][1])
    assert claims(tokens['access_token'])['tenant_id'] == tenant['tenant_id']
    verify_api(tokens['access_token'])
    print('OK: humano PKCE S256, tenant_id y API real')

    # D-09: un TENANT_ADMIN crea una clave de API; la clave obtiene un token del
    # tenant correcto que la API acepta, y al revocarla deja de obtener tokens.
    admin = next(u for u in tenant['usuarios'] if 'TENANT_ADMIN' in u['roles'])
    admin_token = pkce_login(admin)['access_token']
    status, body = request_json('POST', origin + '/api/v1/api-keys', admin_token,
                                {'nombre': 'Smoke e2e', 'entorno': 'sandbox'})
    assert status == 201, f'Crear clave HTTP {status}: {body[:300]}'
    key = json.loads(body)
    status, _, body = client_credentials(key['clientId'], key['secreto'])
    assert status == 200, f'Token con la clave nueva HTTP {status}'
    key_token = json.loads(body)['access_token']
    assert claims(key_token)['tenant_id'] == tenant['tenant_id']
    verify_api(key_token)
    status, body = request_json('GET', origin + '/api/v1/api-keys', admin_token)
    assert status == 200 and key['secreto'].encode() not in body, 'El listado no debe exponer secretos'
    status, _ = request_json('DELETE', origin + '/api/v1/api-keys/' + key['id'], admin_token)
    assert status == 204, f'Revocar clave HTTP {status}'
    status, _, _ = client_credentials(key['clientId'], key['secreto'])
    assert status == 401, f'Clave revocada todavía obtiene tokens: HTTP {status}'
    print('OK: clave de API creada, usada contra la API y revocada')

if __name__ == '__main__': main()
