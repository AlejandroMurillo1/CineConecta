-- =============================================================================
-- CineConecta · PostgreSQL · 03_plans.sql
-- Planes iniciales.
--
-- IMPORTANTE: valores de ejemplo. Si el anexo del enunciado trae los planes
-- suministrados, reemplace estas filas por esos valores exactos.
-- Las cuentas, perfiles, suscripciones y pagos de prueba los carga el seed (Java).
-- =============================================================================

SET ROLE cine_owner;
SET search_path = cineconecta, public;

INSERT INTO plans (name, price, maxProfiles, isActive) VALUES
    ('Básico',   16900.00, 1, 'Y'),
    ('Estándar', 26900.00, 3, 'Y'),
    ('Premium',  38900.00, 5, 'Y');

RESET ROLE;
