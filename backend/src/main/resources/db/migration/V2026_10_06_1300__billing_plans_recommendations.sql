-- C-06 / A-06: recomendaciones[] en la evaluación es una función solo de los planes PRO y
-- ENTERPRISE (FEATURE_NOT_IN_PLAN en FREE). Columna aditiva: false por defecto.
ALTER TABLE billing.plans ADD COLUMN recomendaciones boolean NOT NULL DEFAULT false;

UPDATE billing.plans SET recomendaciones = true WHERE id IN ('PRO', 'ENTERPRISE');
