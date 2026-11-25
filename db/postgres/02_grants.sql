-- =============================================================================
-- CineConecta · PostgreSQL · 02_grants.sql
-- Permisos con mínimo privilegio.
--
--   cine_app:    nada se borra (sin DELETE en ninguna tabla) y el historial no se
--                modifica (SUBSCRIPTIONS y PAYMENTS solo admiten INSERT).
--                Los UPDATE se limitan por columna: por ejemplo, el precio y el
--                máximo de perfiles de un plan no se pueden cambiar.
--   cine_report: solo lectura.
--
-- Nota para el mapeo JPA: como el UPDATE es por columna, las columnas que no
-- aparecen abajo deben marcarse @Column(updatable = false) en las entidades
-- (o usar @DynamicUpdate); si no, Hibernate intentará actualizarlas y fallará.
-- =============================================================================

SET ROLE cine_owner;
SET search_path = cineconecta, public;

REVOKE ALL ON SCHEMA cineconecta FROM PUBLIC;
GRANT USAGE ON SCHEMA cineconecta TO cine_app, cine_report;

-- ---------------- cine_app ----------------
GRANT SELECT, INSERT ON accounts TO cine_app;

GRANT SELECT, INSERT ON profiles TO cine_app;
GRANT UPDATE (name, maxAgeRating, isActive) ON profiles TO cine_app;

GRANT SELECT, INSERT ON plans TO cine_app;
GRANT UPDATE (name, isActive) ON plans TO cine_app;

GRANT SELECT, INSERT ON subscriptions TO cine_app;
GRANT SELECT, INSERT ON payments TO cine_app;

GRANT SELECT ON v_current_subscription TO cine_app;

-- Secuencias de las columnas IDENTITY (para leer el ID recién generado, p. ej. con currval).
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA cineconecta TO cine_app;

-- ---------------- cine_report ----------------
GRANT SELECT ON ALL TABLES IN SCHEMA cineconecta TO cine_report;   -- incluye la vista

RESET ROLE;
