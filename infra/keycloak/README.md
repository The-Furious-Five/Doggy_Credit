# Realm DoggyCredit

Generado exclusivamente desde `seed/tenants.yaml` mediante
`python scripts/generate_realm.py` (dependencias en `scripts/requirements.txt`).
El JSON versionado contiene referencias a variables, nunca secretos reales.
Compose pasa las variables DC_SEED_* ya declaradas y ejecuta `--import-realm`.

Cliente SPA público `doggycredit-web`, Authorization Code + PKCE S256 obligatorio,
sin password grant. Clientes M2M separados por institución y cuentas de servicio
con `tenant_id`. Usuarios y servicios comparten el mapper de tenant y la audiencia
`doggycredit-api`. Los roles provienen de la semilla.

El volumen persiste el realm. La importación solo crea realms ausentes; no borra
ni actualiza los existentes. Ver [runbook de Franco](../../docs/runbooks/franco-sprint1.md).
