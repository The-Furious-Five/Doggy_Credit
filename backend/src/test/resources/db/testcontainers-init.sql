-- Solo para Testcontainers: corre como superusuario ANTES de Flyway.
-- Crea el rol de la aplicación tal como exige el capítulo 5.4 de la guía:
-- sin superusuario, sin BYPASSRLS y sin ser dueño de ninguna tabla (las
-- tablas las crea Flyway con el usuario del contenedor).
-- La creación definitiva de roles en Compose es parte de SPIKE-1.
CREATE ROLE doggy_app LOGIN PASSWORD 'doggy_app'
    NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE;
