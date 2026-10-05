-- =============================================================================
-- V2026_10_04_2100__debt018_app_role_own_login.sql
-- DEBT-018: doggycredit_app gets its own LOGIN with password, so the app no
-- longer needs SET ROLE from a superuser connection. Flyway keeps running as
-- the admin (doggy_app / POSTGRES_USER), the app connects directly as
-- doggycredit_app.
-- =============================================================================

ALTER ROLE doggycredit_app LOGIN PASSWORD '${appDbPassword}';
