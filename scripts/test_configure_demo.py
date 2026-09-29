import unittest
from configure_demo import ROOT, caddyfile_for, public_origin

class ConfigureDemoTest(unittest.TestCase):
    def setUp(self):
        self.caddyfile = (ROOT / 'infra/caddy/Caddyfile').read_text()

    def test_public_domain_uses_trusted_certificate(self):
        url, origin = public_origin('https://demo.doggycredit.example/auth/realms/doggycredit')
        result = caddyfile_for(self.caddyfile, url, origin)
        self.assertIn('https://demo.doggycredit.example {', result)
        self.assertNotIn('https://localhost {', result)
        self.assertNotIn('tls internal', result)

    def test_localhost_keeps_internal_ca(self):
        url, origin = public_origin('https://localhost/auth/realms/doggycredit')
        self.assertIn('tls internal', caddyfile_for(self.caddyfile, url, origin))

    def test_rejects_plain_http_issuer(self):
        with self.assertRaises(SystemExit):
            public_origin('http://demo.doggycredit.example/auth/realms/doggycredit')

if __name__ == '__main__': unittest.main()
