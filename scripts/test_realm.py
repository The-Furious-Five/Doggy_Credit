import json
import unittest
import yaml
from generate_realm import generate, ROOT, API_ADMIN_CLIENT, MOBILE_CLIENT_ID, mobile_client

class RealmTest(unittest.TestCase):
    def setUp(self):
        self.seed = yaml.safe_load((ROOT / 'seed/tenants.yaml').read_text())
        self.realm = generate(self.seed, 'https://localhost')

    def test_pkce_and_no_password_grant(self):
        client = self.realm['clients'][0]
        self.assertTrue(client['publicClient'])
        self.assertEqual(client['attributes']['pkce.code.challenge.method'], 'S256')
        self.assertEqual(client['redirectUris'], ['https://localhost/callback'])
        self.assertTrue(all(not c['directAccessGrantsEnabled'] for c in self.realm['clients']))

    def test_mobile_client_is_public_with_exact_callback_and_required_claims(self):
        client = next(c for c in self.realm['clients'] if c['clientId'] == MOBILE_CLIENT_ID)
        self.assertTrue(client['enabled'])
        self.assertTrue(client['publicClient'])
        self.assertTrue(client['standardFlowEnabled'])
        for grant in ('directAccessGrantsEnabled', 'implicitFlowEnabled', 'serviceAccountsEnabled'):
            self.assertFalse(client[grant])
        self.assertNotIn('secret', client)
        self.assertEqual(['doggycredit://oauth/callback'], client['redirectUris'])
        self.assertEqual('S256', client['attributes']['pkce.code.challenge.method'])
        self.assertEqual(['basic', 'profile', 'email', 'roles'], client['defaultClientScopes'])
        self.assertEqual(mobile_client(), client)
        checked_in = json.loads((ROOT / 'infra/keycloak/doggycredit-mobile.json').read_text())
        self.assertEqual(client, checked_in, 'La actualización del VPS y el realm nuevo deben coincidir')

    def test_human_and_service_account_share_seed_tenant(self):
        users = {u['username']: u for u in self.realm['users']}
        clients = {c['clientId']: c for c in self.realm['clients']}
        for tenant in self.seed['tenants']:
            for user in tenant['usuarios']:
                actual = users[user['username']]
                self.assertEqual(actual['attributes']['tenant_id'], [tenant['tenant_id']])
                self.assertEqual(actual['credentials'][0]['value'], '${' + user['password_env'] + '}')
            for client in tenant['clientes_m2m']:
                actual = users['service-account-' + client['client_id']]
                self.assertEqual(actual['attributes']['tenant_id'], [tenant['tenant_id']])
                self.assertEqual(actual['realmRoles'], ['API_CLIENT'])
                self.assertEqual(clients[client['client_id']]['secret'], '${' + client['secret_env'] + '}')
                self.assertTrue(clients[client['client_id']]['serviceAccountsEnabled'])

    def test_tenant_admins_must_configure_mfa_on_first_login(self):
        """A-14: MFA obligatorio para TENANT_ADMIN, y solo para ellos."""
        users = {u['username']: u for u in self.realm['users']}
        admins = [user for tenant in self.seed['tenants'] for user in tenant['usuarios']
                  if 'TENANT_ADMIN' in user['roles']]
        self.assertTrue(admins, 'La semilla debe tener al menos un TENANT_ADMIN')
        for tenant in self.seed['tenants']:
            for user in tenant['usuarios']:
                expected = ['CONFIGURE_TOTP'] if 'TENANT_ADMIN' in user['roles'] else []
                self.assertEqual(users[user['username']].get('requiredActions', []), expected, user['username'])
        # Las cuentas de servicio no tienen pantalla de acceso: una acción requerida las dejaría sin token.
        for user in self.realm['users']:
            if 'serviceAccountClientId' in user:
                self.assertNotIn('requiredActions', user, user['username'])
        # Sin un segundo factor ya sembrado: cada administrador lo configura él mismo.
        for user in self.realm['users']:
            self.assertFalse([c for c in user.get('credentials', []) if c['type'] != 'password'], user['username'])

    def test_tenant_is_visible_to_admin_and_cannot_be_changed_by_users(self):
        providers = self.realm.get('components', {}).get(
            'org.keycloak.userprofile.UserProfileProvider', [])
        self.assertEqual(len(providers), 1, 'El realm debe declarar el perfil de usuario')
        self.assertEqual(providers[0]['providerId'], 'declarative-user-profile')
        profile = json.loads(providers[0]['config']['kc.user.profile.config'][0])
        attributes = {attribute['name']: attribute for attribute in profile['attributes']}
        tenant = attributes['tenant_id']
        self.assertEqual(tenant['permissions'], {'view': ['admin'], 'edit': ['admin']})
        self.assertFalse(tenant['multivalued'])
        self.assertNotIn('required', tenant, 'La cuenta administradora no tiene tenant')
        # Keycloak 26.4 representa Disabled con la propiedad ausente (null).
        # Su enum sólo acepta ENABLED, ADMIN_VIEW y ADMIN_EDIT.
        self.assertNotIn('unmanagedAttributePolicy', profile,
                         'Usar el valor por defecto de Keycloak 26.4; DISABLED impide importar el realm')
        self.assertEqual(set(attributes), {'username', 'email', 'firstName', 'lastName', 'tenant_id'})
        for field in ('username', 'email', 'firstName', 'lastName'):
            self.assertEqual(attributes[field]['permissions']['view'], ['admin', 'user'])
            self.assertEqual(attributes[field]['permissions']['edit'], ['admin', 'user'])

    def test_checked_in_realm_has_the_generated_profile(self):
        checked_in = json.loads((ROOT / 'infra/keycloak/doggycredit-realm.json').read_text())
        self.assertTrue('components' in self.realm, 'Falta el perfil en el generador')
        self.assertEqual(checked_in.get('components'), self.realm['components'])

    def test_human_names_pass_keycloak_profile_validation(self):
        prohibited = set('<>&"$%!#?§;*~/\\|^=[]{}()')
        for user in self.realm['users']:
            for field in ('firstName', 'lastName'):
                if field in user:
                    self.assertTrue(user[field])
                    self.assertFalse(prohibited & set(user[field]), user[field])
                    self.assertNotIn('demo', user[field].lower(), user[field])

    def test_all_clients_issue_subject(self):
        scopes = {s['name']: s for s in self.realm['clientScopes']}
        mappers = [m['protocolMapper'] for m in scopes['basic']['protocolMappers']]
        self.assertIn('oidc-sub-mapper', mappers)
        self.assertIn('basic', self.realm['defaultDefaultClientScopes'])
        for client in self.realm['clients']:
            self.assertIn('basic', client['defaultClientScopes'])

    def test_login_screen_is_spanish_and_branded(self):
        self.assertEqual(self.realm['displayName'], 'DoggyCredit')
        self.assertTrue(self.realm['internationalizationEnabled'])
        self.assertEqual(self.realm['supportedLocales'], ['es'])
        self.assertEqual(self.realm['defaultLocale'], 'es')

    def test_reject_duplicate_tenants(self):
        self.seed['tenants'][1]['tenant_id'] = self.seed['tenants'][0]['tenant_id']
        with self.assertRaises(ValueError): generate(self.seed, 'https://localhost')

    def test_all_clients_issue_audience_and_tenant_claim(self):
        for client in self.realm['clients']:
            if client['clientId'] == API_ADMIN_CLIENT:
                continue
            mappers = {m['name']: m for m in client['protocolMappers']}
            self.assertEqual(mappers['tenant_id']['config']['claim.name'], 'tenant_id')
            self.assertEqual(mappers['api-audience']['config']['included.custom.audience'], 'doggycredit-api')

    def test_api_admin_client_cannot_call_the_api(self):
        clients = {c['clientId']: c for c in self.realm['clients']}
        admin = clients[API_ADMIN_CLIENT]
        self.assertEqual(admin['secret'], '${DC_KEYCLOAK_API_ADMIN_SECRET}')
        self.assertTrue(admin['serviceAccountsEnabled'])
        self.assertFalse(admin['standardFlowEnabled'])
        # Sin audiencia ni tenant_id: un token de administración nunca entra a la API.
        self.assertEqual(admin['protocolMappers'], [])
        users = {u['username']: u for u in self.realm['users']}
        account = users['service-account-' + API_ADMIN_CLIENT]
        self.assertNotIn('realmRoles', account)
        self.assertNotIn('tenant_id', account.get('attributes', {}))
        self.assertIn('manage-clients', account['clientRoles']['realm-management'])
        self.assertNotIn('realm-admin', account['clientRoles']['realm-management'])

if __name__ == '__main__': unittest.main()
