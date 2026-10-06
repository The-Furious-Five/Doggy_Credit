-- VARCHAR en vez de CHAR para que Hibernate valide el esquema (ddl-auto: validate),
-- igual que se hizo en recommendation.products (LOS-82).
ALTER TABLE billing.monthly_usage ALTER COLUMN year_month TYPE varchar(7);
