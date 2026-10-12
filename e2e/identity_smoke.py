"""SPIKE-2: PKCE y M2M reales contra Compose, sin password grant ni mocks.
Requiere variables DC_SEED_* ya entregadas por Nicole y la CA local de Caddy.
"""
import argparse
import base64
import hashlib
import http.cookiejar
import hmac
import json
import os
import secrets
import ssl
import struct
import sys
import time
from html.parser import HTMLParser
from pathlib import Path
from urllib.error import HTTPError
from urllib.parse import parse_qs, urlencode, urlsplit
from urllib.request import build_opener, HTTPCookieProcessor, HTTPHandler, HTTPSHandler, HTTPRedirectHandler, Request

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
import yaml

class LoginForm(HTMLParser):
    """Acción del formulario de Keycloak con ese id y los campos ocultos de la página."""
    def __init__(self, form_id='kc-form-login'):
        super().__init__()
        self.form_id, self.action, self.hidden = form_id, None, {}
    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag == 'form' and attrs.get('id') == self.form_id:
            self.action = attrs['action']
        if tag == 'input' and attrs.get('type') == 'hidden' and attrs.get('name'):
            self.hidden[attrs['name']] = attrs.get('value', '')

class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None

# A-14 (LOS-118): los TENANT_ADMIN configuran MFA en su primer inicio de sesión. El
# secreto queda fuera del repositorio, en el mismo archivo que usa Playwright
# (e2e/tests/support.ts), para poder volver a entrar mientras viva el volumen de Keycloak.
TOTP_STORE = Path(__file__).resolve().parent / '.generated' / 'totp.json'

def totp(secret, counter):
    # Keycloak firma con los bytes del secreto tal cual; el Base32 es solo lo que muestra al usuario.
    digest = hmac.new(secret.encode(), struct.pack('>Q', counter), hashlib.sha1).digest()
    offset = digest[-1] & 0x0f
    return f'{(struct.unpack(">I", digest[offset:offset + 4])[0] & 0x7fffffff) % 1000000:06d}'

def totp_store():
    return json.loads(TOTP_STORE.read_text()) if TOTP_STORE.is_file() else {}

def next_totp(key, secret):
    """Keycloak no acepta dos veces el mismo código: se usa el del paso siguiente al
    último usado (su ventana admite uno de adelanto) y, si aún no vale, se espera."""
    store = totp_store()
    counter = max(int(time.time() // 30), store.get(key, {}).get('counter', 0) + 1)
    wait = (counter - 1) * 30 - time.time()
    if wait > 0:
        time.sleep(wait + 1)
    store[key] = {'secret': secret, 'counter': counter}
    TOTP_STORE.parent.mkdir(exist_ok=True)
    TOTP_STORE.write_text(json.dumps(store, indent=2))
    return totp(secret, counter)

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
        status, headers, body = request(form.action, {'username': user['username'], 'password': os.environ[user['password_env']]})
        mfa = origin + '|' + user['username']
        if status == 200:
            # MFA ya configurado en este Keycloak: tras la contraseña pide el código.
            otp_form = LoginForm('kc-otp-login-form'); otp_form.feed(body.decode())
            assert otp_form.action, 'Login HTTP 200 sin formulario de código MFA'
            saved = totp_store().get(mfa)
            assert saved, (f'Keycloak pide el código MFA de {user["username"]} y {TOTP_STORE} no tiene su secreto: '
                           'recrea el volumen de Keycloak (docker compose down -v)')
            status, headers, _ = request(otp_form.action, {'otp': next_totp(mfa, saved['secret'])})
        assert status in (302, 303), f'Login HTTP {status}'
        location = headers['Location']
        if 'required-action' in location:
            # Primer inicio de sesión de un TENANT_ADMIN sembrado: Keycloak obliga a configurar MFA.
            assert 'CONFIGURE_TOTP' in location, f'Acción requerida inesperada: {location}'
            status, _, body = request(location)
            assert status == 200, f'Pantalla de configuración MFA HTTP {status}'
            setup = LoginForm('kc-totp-settings-form'); setup.feed(body.decode())
            secret = setup.hidden.get('totpSecret')
            assert setup.action and secret, 'No apareció el formulario de configuración MFA'
            status, headers, _ = request(setup.action, {'totp': next_totp(mfa, secret), 'totpSecret': secret,
                                                        'userLabel': 'e2e'})
            assert status in (302, 303), f'Registro MFA HTTP {status}'
            location = headers['Location']
            print('OK: ' + user['username'] + ' tuvo que configurar MFA para entrar')

        redirect = urlsplit(location)
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
