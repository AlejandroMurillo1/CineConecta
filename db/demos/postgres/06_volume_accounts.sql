-- =============================================================================
-- CineConecta · PostgreSQL · 06_volume_accounts.sql
-- VOLUMEN de cuentas y perfiles para las mediciones de eficiencia (enunciado: "aumentará
-- el volumen para las mediciones de eficiencia, indicando el tamaño utilizado").
--
-- Genera N cuentas (por defecto 20 000) con 1 a 5 perfiles cada una (≈ 60 000 perfiles):
--   - correo  vol.000001@volumen.cineconecta.local ... (algunos con mayúsculas, para que
--             la búsqueda insensible a mayúsculas del login tenga casos reales)
--   - fecha de registro repartida en los últimos 2 años
--   - clasificación de cada perfil entre 0, 7, 12, 16 y 18
--   - ≈ 15 % de perfiles inactivos (se desactivan, no se borran)
-- Los datos son deterministas (dependen solo del número de cuenta): dos personas que corran
-- el script con el mismo N obtienen exactamente los mismos datos.
--
-- No crea suscripciones (son del módulo billing): estas cuentas sirven para medir las
-- consultas de cuentas y perfiles. Contraseña de todas: Demo123*
--
-- Consultas que se miden con estos datos (ver docs/04_indices_y_mediciones.md):
--   login           WHERE LOWER(email) = LOWER(?)          → ux_accounts_email_ci
--   perfiles        WHERE accountId = ? ORDER BY name      → ux_profiles_name_ci (prefijo accountId)
--   cupo del plan   COUNT(*) WHERE accountId = ? AND isActive = 'Y'
--
-- Uso (N opcional):
--   docker compose exec -T postgres psql -U cine -d cineconecta -v ON_ERROR_STOP=1 -v cuentas=20000 \
--       < db/demos/postgres/06_volume_accounts.sql
--
-- Idempotente. Para quitar el volumen (solo cuentas de este script y sus perfiles):
--   DELETE FROM cineconecta.profiles WHERE accountid IN
--       (SELECT accountid FROM cineconecta.accounts WHERE LOWER(email) LIKE '%@volumen.cineconecta.local');
--   DELETE FROM cineconecta.accounts WHERE LOWER(email) LIKE '%@volumen.cineconecta.local';
-- =============================================================================

\if :{?cuentas}
\else
    \set cuentas 20000
\endif

SET search_path = cineconecta, public;

\echo 'Generando' :cuentas 'cuentas de volumen...'
\timing on

BEGIN;

INSERT INTO accounts (fullname, email, passwordhash, createdat)
SELECT
    (ARRAY['Ana','Luis','Camila','Diego','Valeria','Santiago','Isabella','Juan','Mariana','Andrés'])[1 + n % 10]
        || ' ' ||
    (ARRAY['Gómez','Rodríguez','López','Martínez','García','Pérez','Sánchez','Ramírez','Torres','Díaz'])[1 + (n / 10) % 10],
    -- 1 de cada 7 correos tiene mayúsculas (VOL.000007@Volumen...)
    CASE WHEN n % 7 = 0
         THEN 'VOL.' || lpad(n::text, 6, '0') || '@Volumen.CineConecta.local'
         ELSE 'vol.' || lpad(n::text, 6, '0') || '@volumen.cineconecta.local'
    END,
    '$2a$10$O5AtctlJPnwGiViRRj3uE.lNMBxWEYNFFEMGkrVxfGxNMXRvhnFVi',
    now() - make_interval(mins => (n * 7919) % (730 * 24 * 60))
FROM generate_series(1, :cuentas) AS n
ON CONFLICT ((LOWER(email))) DO NOTHING;

INSERT INTO profiles (accountid, name, maxagerating, isactive)
SELECT a.accountid,
       'Perfil ' || k,
       (ARRAY[18, 16, 12, 7, 0])[1 + (n + k) % 5],
       CASE WHEN (n * 31 + k * 17) % 100 < 15 THEN 'N' ELSE 'Y' END
FROM (
    SELECT accountid,
           substring(LOWER(email) FROM 'vol\.(\d{6})@')::int AS n
    FROM accounts
    WHERE LOWER(email) LIKE 'vol.%@volumen.cineconecta.local'
) a
CROSS JOIN LATERAL generate_series(1, 1 + (a.n * 13) % 5) AS k
ON CONFLICT (accountid, (LOWER(name))) DO NOTHING;

COMMIT;

-- Estadísticas al día para que el planificador use los datos nuevos en las mediciones.
ANALYZE accounts;
ANALYZE profiles;

\timing off

-- Tamaño resultante (indicarlo en el informe de mediciones)
SELECT 'cuentas de volumen' AS dato,
       COUNT(*)::text      AS valor
FROM accounts WHERE LOWER(email) LIKE '%@volumen.cineconecta.local'
UNION ALL
SELECT 'perfiles de volumen (activos / inactivos)',
       COUNT(*) FILTER (WHERE p.isactive = 'Y') || ' / ' || COUNT(*) FILTER (WHERE p.isactive = 'N')
FROM profiles p JOIN accounts a ON a.accountid = p.accountid
WHERE LOWER(a.email) LIKE '%@volumen.cineconecta.local'
UNION ALL
SELECT 'total filas accounts / profiles',
       (SELECT COUNT(*) FROM accounts) || ' / ' || (SELECT COUNT(*) FROM profiles)
UNION ALL
SELECT 'tamaño accounts (tabla + índices)',
       pg_size_pretty(pg_total_relation_size('accounts'))
UNION ALL
SELECT 'tamaño profiles (tabla + índices)',
       pg_size_pretty(pg_total_relation_size('profiles'));
