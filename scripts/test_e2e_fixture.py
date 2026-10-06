import copy
import json
import unittest

import yaml

from generate_e2e_fixture import ROOT, build_fixture


class E2EFixtureTest(unittest.TestCase):
    def setUp(self):
        self.seed = yaml.safe_load((ROOT / "seed/tenants.yaml").read_text(encoding="utf-8"))

    def test_reuses_canonical_ids_admins_and_document_without_credentials(self):
        for tenant in self.seed["tenants"]:
            for user in tenant["usuarios"]:
                user["password"] = "must-not-be-exported"
        fixture = build_fixture(self.seed)
        self.assertEqual(["FREE", "PRO"], [tenant["plan"] for tenant in fixture["tenants"]])
        for item in fixture["tenants"]:
            original = next(t for t in self.seed["tenants"] if t["plan"] == item["plan"])
            admin = next(u for u in original["usuarios"] if "TENANT_ADMIN" in u["roles"])
            self.assertEqual(original["tenant_id"], item["id"])
            self.assertEqual(admin["username"], item["username"])
            self.assertEqual(admin["password_env"], item["passwordEnv"])
        self.assertEqual(self.seed["titulares_demo"][0]["documento"], fixture["document"])
        serialized = json.dumps(fixture)
        self.assertNotIn("must-not-be-exported", serialized)
        self.assertNotIn("client_secret", serialized)
        self.assertNotIn("secret_env", serialized)

    def test_missing_or_ambiguous_plan_fails_instead_of_reusing_another_tenant(self):
        for plan in ("FREE", "PRO"):
            with self.subTest(plan=plan, case="missing"):
                seed = copy.deepcopy(self.seed)
                seed["tenants"] = [t for t in seed["tenants"] if t["plan"] != plan]
                with self.assertRaisesRegex(ValueError, plan):
                    build_fixture(seed)
            with self.subTest(plan=plan, case="duplicate"):
                seed = copy.deepcopy(self.seed)
                seed["tenants"].append(next(t for t in seed["tenants"] if t["plan"] == plan))
                with self.assertRaisesRegex(ValueError, plan):
                    build_fixture(seed)

    def test_requires_admin_password_reference_distinct_ids_and_demo_document(self):
        for case in ("admin", "password", "id", "document"):
            with self.subTest(case=case):
                seed = copy.deepcopy(self.seed)
                if case == "admin":
                    seed["tenants"][0]["usuarios"][0]["roles"] = ["ANALYST"]
                elif case == "password":
                    seed["tenants"][0]["usuarios"][0].pop("password_env")
                elif case == "id":
                    seed["tenants"][1]["tenant_id"] = seed["tenants"][0]["tenant_id"]
                else:
                    seed["titulares_demo"] = []
                with self.assertRaises(ValueError):
                    build_fixture(seed)
