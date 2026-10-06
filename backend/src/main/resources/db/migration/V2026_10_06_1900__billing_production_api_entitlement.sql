-- LOS-65 / LOS-68: make production API access a database-backed plan entitlement.
ALTER TABLE billing.plans
    ADD COLUMN api_produccion boolean NOT NULL DEFAULT false;

UPDATE billing.plans
SET api_produccion = true
WHERE id IN ('PRO', 'ENTERPRISE');
