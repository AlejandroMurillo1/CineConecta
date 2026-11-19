-- =============================================================================
-- CineConecta · PostgreSQL · 00_roles.sql
-- Usuarios del motor, extensión y esquema.
--
-- Se ejecuta con un usuario administrador:
--   * Local (Docker): el superusuario POSTGRES_USER (lo hace el contenedor solo).
--   * Neon / Supabase: el usuario dueño del proyecto.
--
-- Las contraseñas de este archivo son SOLO para desarrollo local.
-- En la nube, cámbielas con ALTER ROLE ... PASSWORD '...' y no las suba al repositorio.
-- =============================================================================

-- Dueño del esquema: ejecuta DDL y cargas. La aplicación nunca lo usa.
CREATE ROLE cine_owner LOGIN PASSWORD 'cine_owner_dev';

-- Conexión de la aplicación: SELECT/INSERT/UPDATE acotados, sin DELETE (ver 02_grants.sql).
CREATE ROLE cine_app LOGIN PASSWORD 'cine_app_dev';

-- Informes: solo lectura.
CREATE ROLE cine_report LOGIN PASSWORD 'cine_report_dev';

-- Permite que el administrador actual ejecute los siguientes scripts como cine_owner
-- (en PostgreSQL 16+ quien crea un rol no lo "usa" automáticamente).
GRANT cine_owner TO CURRENT_USER;

-- btree_gist permite combinar "=" sobre enteros con "&&" sobre rangos en una
-- restricción EXCLUDE (sin períodos superpuestos por cuenta).
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE SCHEMA cineconecta AUTHORIZATION cine_owner;

ALTER ROLE cine_owner  SET search_path = cineconecta, public;
ALTER ROLE cine_app    SET search_path = cineconecta, public;
ALTER ROLE cine_report SET search_path = cineconecta, public;
