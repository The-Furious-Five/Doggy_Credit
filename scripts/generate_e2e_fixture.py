#!/usr/bin/env python3
"""Deriva los datos de Playwright de la semilla común, sin copiar contraseñas."""

import argparse
import json
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[1]


def build_fixture(seed: dict) -> dict:
    tenants = []
    for plan in ("FREE", "PRO"):
        matches = [tenant for tenant in seed["tenants"] if tenant["plan"] == plan]
        if len(matches) != 1:
            raise ValueError(f"La suite requiere exactamente un tenant {plan}")
        tenant = matches[0]
        admins = [user for user in tenant["usuarios"] if "TENANT_ADMIN" in user["roles"]]
        if len(admins) != 1:
            raise ValueError(f"El tenant {plan} requiere exactamente un TENANT_ADMIN")
        admin = admins[0]
        if not admin.get("password_env"):
            raise ValueError(f"Falta password_env para el administrador {plan}")
        tenants.append({
            "id": tenant["tenant_id"], "name": tenant["nombre"], "plan": plan,
            "username": admin["username"], "passwordEnv": admin["password_env"],
        })
    if tenants[0]["id"] == tenants[1]["id"]:
        raise ValueError("Los tenants FREE y PRO deben tener IDs distintos")
    documents = seed.get("titulares_demo", [])
    if not documents or not documents[0].get("documento"):
        raise ValueError("Falta el documento sintético de referencia")
    return {"tenants": tenants, "document": documents[0]["documento"]}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--seed", type=Path, default=ROOT / "seed/tenants.yaml")
    parser.add_argument("--output", type=Path, default=ROOT / "e2e/.generated/seed.json")
    args = parser.parse_args()
    fixture = build_fixture(yaml.safe_load(args.seed.read_text(encoding="utf-8")))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(fixture, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Fixture E2E generado: {args.output}")


if __name__ == "__main__":
    main()
