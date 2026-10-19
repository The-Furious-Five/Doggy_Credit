"""Comprueba crear y actualizar el cliente móvil contra el Keycloak local de CI.

Solo usar con el Compose de pruebas: elimina y altera su cliente móvil dedicado.
    python e2e/mobile_client_sync.py [opciones de docker compose]
"""
import json
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
COMPOSE = ['docker', 'compose', '--env-file', '.env.example', '-f', 'infra/compose.yaml']
if __name__ == '__main__' and sys.argv[1:]:
    COMPOSE = ['docker', 'compose', *sys.argv[1:]]


def cli(*args):
    # Las credenciales de prueba permanecen dentro del contenedor.
    script = '''set -euo pipefail
temporary=$(mktemp -d)
trap 'rm -rf "$temporary"' EXIT
kc=/opt/keycloak/bin/kcadm.sh
$kc config credentials --config "$temporary/kcadm.config" --server http://localhost:8080/auth \\
  --realm master --user "$KC_BOOTSTRAP_ADMIN_USERNAME" --password "$KC_BOOTSTRAP_ADMIN_PASSWORD" >&2
$kc "$@" --config "$temporary/kcadm.config"
'''
    result = subprocess.run([*COMPOSE, 'exec', '-T', 'keycloak', 'bash', '-c', script, '--', *args],
                            cwd=ROOT, capture_output=True, text=True, check=True)
    return result.stdout.strip()


def synchronize():
    subprocess.run([*COMPOSE, 'exec', '-T', 'keycloak', 'bash', '-s'], cwd=ROOT,
                   input=(ROOT / 'scripts/sync_mobile_client.sh').read_text(), text=True, check=True)


def read_client():
    clients = json.loads(cli('get', 'clients', '-r', 'doggycredit', '-q', 'clientId=doggycredit-mobile'))
    assert len(clients) == 1, 'Debe existir exactamente un cliente móvil'
    return json.loads(cli('get', 'clients/' + clients[0]['id'], '-r', 'doggycredit'))


def verify():
    actual = read_client()
    expected = json.loads((ROOT / 'infra/keycloak/doggycredit-mobile.json').read_text())
    for key, value in expected.items():
        if key == 'protocolMappers':
            mappers = actual[key]
            assert len(mappers) == len(value), 'No deben duplicarse los mappers'
            for mapper in value:
                match = next(m for m in mappers if m['name'] == mapper['name'])
                for field, setting in mapper.items():
                    assert match[field] == setting, f'Mapper móvil incorrecto: {mapper["name"]}/{field}'
        elif key == 'defaultClientScopes':
            assert sorted(actual[key]) == sorted(value), 'Scopes móviles incompletos'
        elif key == 'attributes':
            # Keycloak agrega atributos internos propios al crear el cliente.
            for name, setting in value.items():
                assert actual[key].get(name) == setting, f'Atributo móvil incorrecto: {name}'
        else:
            assert actual[key] == value, f'Configuración móvil incorrecta: {key}'
    return actual['id']


def main():
    client = read_client()
    cli('delete', 'clients/' + client['id'], '-r', 'doggycredit')
    synchronize()
    identifier = verify()
    print('OK: cliente móvil creado en un realm existente')
    cli('update', 'clients/' + identifier, '-r', 'doggycredit',
        '-s', 'publicClient=false', '-s', 'enabled=false',
        '-s', 'redirectUris=["https://invalid.example/callback"]',
        '-s', 'attributes={"pkce.code.challenge.method":"plain"}',
        '-s', 'protocolMappers=[]', '-s', 'defaultClientScopes=["profile"]')
    for _ in range(2):
        synchronize()
        assert verify() == identifier, 'Actualizar debe conservar el ID y las sesiones del cliente'
    print('OK: configuración corregida y sincronización repetible, sin duplicados')


if __name__ == '__main__':
    main()
