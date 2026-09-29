import unittest
import yaml
from generate_realm import generate, ROOT

class RealmTest(unittest.TestCase):
    def setUp(self):
        self.seed = yaml.safe_load((ROOT / 'seed/tenants.yaml').read_text())
        self.realm = generate(self.seed, 'http://localhost:8080')

    def test_pkce_and_no_password_grant(self):
        client = self.realm['clients'][0]
        self.assertTrue(client['publicClient'])
        self.assertEqual(client['attributes']['pkce.code.challenge.method'], 'S256')
        self.assertEqual(client['redirectUris'], ['http://localhost:8080/callback'])
        self.assertTrue(all(not c['directAccessGrantsEnabled'] for c in self.realm['clients']))

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

    def test_human_names_pass_keycloak_profile_validation(self):
        prohibited = set('<>&"$%!#?§;*~/\\|^=[]{}()')
        for user in self.realm['users']:
            for field in ('firstName', 'lastName'):
                if field in user:
                    self.assertTrue(user[field])
                    self.assertFalse(prohibited & set(user[field]), user[field])

    def test_all_clients_issue_subject(self):
        scopes = {s['name']: s for s in self.realm['clientScopes']}
        mappers = [m['protocolMapper'] for m in scopes['basic']['protocolMappers']]
        self.assertIn('oidc-sub-mapper', mappers)
        self.assertIn('basic', self.realm['defaultDefaultClientScopes'])
        for client in self.realm['clients']:
            self.assertIn('basic', client['defaultClientScopes'])

    def test_reject_duplicate_tenants(self):
        self.seed['tenants'][1]['tenant_id'] = self.seed['tenants'][0]['tenant_id']
        with self.assertRaises(ValueError): generate(self.seed, 'http://localhost:8080')

    def test_all_clients_issue_audience_and_tenant_claim(self):
        for client in self.realm['clients']:
            mappers = {m['name']: m for m in client['protocolMappers']}
            self.assertEqual(mappers['tenant_id']['config']['claim.name'], 'tenant_id')
            self.assertEqual(mappers['api-audience']['config']['included.custom.audience'], 'doggycredit-api')

if __name__ == '__main__': unittest.main()
