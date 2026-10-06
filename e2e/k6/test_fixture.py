import copy
import json
import unittest

import yaml

from generate_fixture import ROOT, build_fixture


class LoadFixtureTest(unittest.TestCase):
    def setUp(self):
        self.seed = yaml.safe_load((ROOT / "seed/tenants.yaml").read_text(encoding="utf-8"))

    def test_canonical_tenants_scoped_clients_and_document_without_credentials(self):
        for tenant in self.seed["tenants"]:
            for client in tenant["clientes_m2m"]:
                client["secret"] = "never-serialize-this-secret"
        fixture = build_fixture(self.seed)
        self.assertEqual(["FREE", "PRO"], [item["plan"] for item in fixture["tenants"]])
        for item in fixture["tenants"]:
            source = next(tenant for tenant in self.seed["tenants"] if tenant["plan"] == item["plan"])
            self.assertEqual(source["tenant_id"], item["id"])
            self.assertEqual(source["clientes_m2m"][0]["client_id"], item["clientId"])
            self.assertEqual(source["clientes_m2m"][0]["secret_env"], item["secretEnv"])
        self.assertEqual(self.seed["titulares_demo"][0]["documento"], fixture["document"])
        self.assertNotIn("never-serialize-this-secret", json.dumps(fixture))
        self.assertNotIn("password", json.dumps(fixture))

    def test_rejects_missing_plan_duplicate_tenants_or_unscoped_client(self):
        for case in ("missing", "duplicate", "id", "client", "scopes", "secret", "document"):
            with self.subTest(case=case):
                seed = copy.deepcopy(self.seed)
                if case == "missing":
                    seed["tenants"].pop()
                elif case == "duplicate":
                    seed["tenants"].append(seed["tenants"][0])
                elif case == "id":
                    seed["tenants"][1]["tenant_id"] = seed["tenants"][0]["tenant_id"]
                elif case == "client":
                    seed["tenants"][1]["clientes_m2m"][0]["client_id"] = seed["tenants"][0]["clientes_m2m"][0]["client_id"]
                elif case == "scopes":
                    seed["tenants"][0]["clientes_m2m"][0]["scopes"] = ["consents:write"]
                elif case == "secret":
                    seed["tenants"][0]["clientes_m2m"][0].pop("secret_env")
                else:
                    seed["titulares_demo"] = []
                with self.assertRaises(ValueError):
                    build_fixture(seed)
