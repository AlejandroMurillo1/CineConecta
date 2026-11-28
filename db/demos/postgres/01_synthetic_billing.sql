-- =============================================================================
-- CineConecta · PostgreSQL · 01_synthetic_billing.sql
-- Datos sintéticos de billing para pruebas locales y sustentación:
--   - Cuentas de demostración
--   - Perfiles activos e inactivos (para probar regla de reducción de perfiles)
--   - Suscripciones vencidas (historial) y vigentes (intervalo [startsAt, endsAt))
--   - Pagos asociados (1:1 con cada suscripción)
--
-- Ejecutar con cine_owner o el superusuario del contenedor:
--   psql -U cine -d cineconecta -v ON_ERROR_STOP=1 -f db/demos/postgres/01_synthetic_billing.sql
-- =============================================================================

SET search_path = cineconecta, public;

BEGIN;

-- 1. Cuentas de prueba (si no existen por correo)
--    Contraseña de todas las cuentas de demostración: Demo123*  (hash BCrypt de abajo)
INSERT INTO accounts (fullname, email, passwordhash)
SELECT v.fullname, v.email, '$2a$10$O5AtctlJPnwGiViRRj3uE.lNMBxWEYNFFEMGkrVxfGxNMXRvhnFVi'
FROM (VALUES
    ('Ana María Gómez',    'ana.vigente@cineconecta.local'),
    ('Carlos Pérez',       'carlos.vencida@cineconecta.local'),
    ('Lucía Martínez',     'lucia.downgrade@cineconecta.local'),
    ('Mateo Rojas',        'mateo.sinplan@cineconecta.local')
) AS v(fullname, email)
WHERE NOT EXISTS (
    SELECT 1 FROM accounts a WHERE LOWER(a.email) = LOWER(v.email)
);

-- Si las cuentas ya existían con el hash anterior (sin contraseña conocida), se actualiza.
UPDATE accounts
SET passwordhash = '$2a$10$O5AtctlJPnwGiViRRj3uE.lNMBxWEYNFFEMGkrVxfGxNMXRvhnFVi'
WHERE LOWER(email) IN ('ana.vigente@cineconecta.local', 'carlos.vencida@cineconecta.local',
                       'lucia.downgrade@cineconecta.local', 'mateo.sinplan@cineconecta.local')
  AND passwordhash <> '$2a$10$O5AtctlJPnwGiViRRj3uE.lNMBxWEYNFFEMGkrVxfGxNMXRvhnFVi';

-- 2. Perfiles de prueba
-- Ana: 2 perfiles activos (plan Estándar vigente)
INSERT INTO profiles (accountid, name, maxagerating, isactive)
SELECT a.accountid, p.name, p.rating, p.active
FROM accounts a
JOIN (VALUES
    ('ana.vigente@cineconecta.local',   'Ana Principal', 18, 'Y'),
    ('ana.vigente@cineconecta.local',   'Ana Kids',       7, 'Y'),
    ('carlos.vencida@cineconecta.local','Carlos',        18, 'Y'),
    ('lucia.downgrade@cineconecta.local','Lucía',        18, 'Y'),
    ('lucia.downgrade@cineconecta.local','Invitado 1',   13, 'Y'),
    ('lucia.downgrade@cineconecta.local','Invitado 2',   13, 'Y'),
    ('lucia.downgrade@cineconecta.local','Antiguo',       0, 'N'),
    ('mateo.sinplan@cineconecta.local', 'Mateo',         18, 'Y')
) AS p(email, name, rating, active)
  ON LOWER(a.email) = LOWER(p.email)
WHERE NOT EXISTS (
    SELECT 1 FROM profiles pr
    WHERE pr.accountid = a.accountid AND LOWER(pr.name) = LOWER(p.name)
);

-- 3. Suscripción histórica vencida + suscripción vigente para Ana (Estándar)
DO $$
DECLARE
    v_ana_id     INTEGER;
    v_carlos_id  INTEGER;
    v_lucia_id   INTEGER;
    v_basico_id  INTEGER;
    v_estandar_id INTEGER;
    v_premium_id INTEGER;
    v_sub_id     INTEGER;
    v_now        TIMESTAMPTZ := date_trunc('second', now());
BEGIN
    SELECT accountid INTO v_ana_id    FROM accounts WHERE LOWER(email) = 'ana.vigente@cineconecta.local';
    SELECT accountid INTO v_carlos_id FROM accounts WHERE LOWER(email) = 'carlos.vencida@cineconecta.local';
    SELECT accountid INTO v_lucia_id  FROM accounts WHERE LOWER(email) = 'lucia.downgrade@cineconecta.local';

    SELECT planid INTO v_basico_id   FROM plans WHERE name = 'Básico'   OR maxprofiles = 1 ORDER BY planid LIMIT 1;
    SELECT planid INTO v_estandar_id FROM plans WHERE name = 'Estándar' OR maxprofiles = 3 ORDER BY planid LIMIT 1;
    SELECT planid INTO v_premium_id  FROM plans WHERE name = 'Premium'  OR maxprofiles = 5 ORDER BY planid LIMIT 1;

    -- Ana: 1 suscripción vencida (hace 65 días -> hace 35 días) + 1 vigente (hace 5 días -> +25 días)
    IF NOT EXISTS (SELECT 1 FROM subscriptions WHERE accountid = v_ana_id) THEN
        INSERT INTO subscriptions (accountid, planid, startsat, endsat)
        VALUES (v_ana_id, v_basico_id, v_now - INTERVAL '65 days', v_now - INTERVAL '65 days' + INTERVAL '720 hours')
        RETURNING subscriptionid INTO v_sub_id;

        INSERT INTO payments (subscriptionid, paidat, amount, reference)
        VALUES (v_sub_id, v_now - INTERVAL '65 days', 16900.00, 'SEED-ANA-HIST-001');

        INSERT INTO subscriptions (accountid, planid, startsat, endsat)
        VALUES (v_ana_id, v_estandar_id, v_now - INTERVAL '5 days', v_now - INTERVAL '5 days' + INTERVAL '720 hours')
        RETURNING subscriptionid INTO v_sub_id;

        INSERT INTO payments (subscriptionid, paidat, amount, reference)
        VALUES (v_sub_id, v_now - INTERVAL '5 days', 26900.00, 'SEED-ANA-CURR-002');
    END IF;

    -- Carlos: 2 suscripciones vencidas (sin suscripción vigente hoy)
    IF NOT EXISTS (SELECT 1 FROM subscriptions WHERE accountid = v_carlos_id) THEN
        INSERT INTO subscriptions (accountid, planid, startsat, endsat)
        VALUES (v_carlos_id, v_basico_id, v_now - INTERVAL '90 days', v_now - INTERVAL '90 days' + INTERVAL '720 hours')
        RETURNING subscriptionid INTO v_sub_id;

        INSERT INTO payments (subscriptionid, paidat, amount, reference)
        VALUES (v_sub_id, v_now - INTERVAL '90 days', 16900.00, 'SEED-CARLOS-001');

        INSERT INTO subscriptions (accountid, planid, startsat, endsat)
        VALUES (v_carlos_id, v_estandar_id, v_now - INTERVAL '40 days', v_now - INTERVAL '40 days' + INTERVAL '720 hours')
        RETURNING subscriptionid INTO v_sub_id;

        INSERT INTO payments (subscriptionid, paidat, amount, reference)
        VALUES (v_sub_id, v_now - INTERVAL '40 days', 26900.00, 'SEED-CARLOS-002');
    END IF;

    -- Lucía: tuvo Premium vencido (3 perfiles activos hoy; si intenta bajar a Básico, debe desactivar 2)
    IF NOT EXISTS (SELECT 1 FROM subscriptions WHERE accountid = v_lucia_id) THEN
        INSERT INTO subscriptions (accountid, planid, startsat, endsat)
        VALUES (v_lucia_id, v_premium_id, v_now - INTERVAL '35 days', v_now - INTERVAL '35 days' + INTERVAL '720 hours')
        RETURNING subscriptionid INTO v_sub_id;

        INSERT INTO payments (subscriptionid, paidat, amount, reference)
        VALUES (v_sub_id, v_now - INTERVAL '35 days', 38900.00, 'SEED-LUCIA-001');
    END IF;
END $$;

COMMIT;
