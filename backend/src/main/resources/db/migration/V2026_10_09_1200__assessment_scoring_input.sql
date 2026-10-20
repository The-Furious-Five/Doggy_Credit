-- C-12 (LOS-113), RF-SCO-04 / RF-ML-01: cada evaluación guarda el vector de variables con
-- el que se calculó su score, para poder recalcularlo con la misma versión de modelo y
-- comprobar que da exactamente el mismo resultado.
--
-- scoring_input es TEXT y no JSONB a propósito: JSONB reordena y normaliza el JSON, y el
-- hash tiene que calcularse sobre los mismos bytes que se guardaron.
-- En claro bajo RLS (clasificación de datos del informe de arquitectura: «los necesitan el
-- scoring, la analítica y la reproducibilidad»). La tabla ya tiene RLS y la app solo SELECT, INSERT:
-- el vector se escribe una vez, junto con la evaluación, y no se puede cambiar.
--
-- Las evaluaciones anteriores quedan con NULL: no se guardó con qué datos se calcularon y
-- no se inventa un vector para ellas.
ALTER TABLE assessment.credit_assessments
    ADD COLUMN scoring_input        TEXT,
    ADD COLUMN scoring_input_sha256 VARCHAR(64),
    ADD CONSTRAINT credit_assessments_scoring_input_ck
        CHECK ((scoring_input IS NULL) = (scoring_input_sha256 IS NULL));
