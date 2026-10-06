#!/usr/bin/env python3
"""Genera datos públicos de k6 desde seed/tenants.yaml; nunca copia secretos."""

import argparse
import json
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[2]


def build_fixture(seed):
    tenants = []
    for plan in ("FREE", "PRO"):
        matches = [tenant for tenant in seed["tenants"] if tenant["plan"] == plan]
        if len(matches) != 1:
            raise ValueError(f"Se requiere exactamente un tenant {plan}")
        tenant = matches[0]
        clients = [client for client in tenant["clientes_m2m"]
                   if {"assessments:write", "assessments:read", "consents:write"} <= set(client["scopes"])]
        if len(clients) != 1 or not clients[0].get("secret_env"):
            raise ValueError(f"Falta un cliente M2M con scopes de evaluación y consentimiento para {plan}")
        client = clients[0]
        tenants.append({"id": tenant["tenant_id"], "key": tenant["key"], "plan": plan,
                        "clientId": client["client_id"], "secretEnv": client["secret_env"]})
    if tenants[0]["id"] == tenants[1]["id"] or tenants[0]["clientId"] == tenants[1]["clientId"]:
        raise ValueError("Los tenants y sus clientes M2M deben ser distintos")
    documents = seed.get("titulares_demo", [])
    if not documents or not documents[0].get("documento"):
        raise ValueError("Falta el documento sintético de referencia")
    return {"tenants": tenants, "document": documents[0]["documento"]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--seed", type=Path, default=ROOT / "seed/tenants.yaml")
    parser.add_argument("--output", type=Path, default=Path(__file__).parent / ".generated/seed.json")
    args = parser.parse_args()
    fixture = build_fixture(yaml.safe_load(args.seed.read_text(encoding="utf-8")))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(fixture, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Fixture k6 generado: {args.output}")


if __name__ == "__main__":
    main()
