-- =============================================================================
-- CineConecta · PostgreSQL · 05_synthetic_accounts.sql
-- Datos sintéticos de CUENTAS y PERFILES para verificar sus reglas (RF-01 a RF-09).
-- Complementa 01_synthetic_billing.sql (Ana, Carlos, Lucía, Mateo) sin repetir sus casos.
--
-- Contraseña de todas las cuentas: Demo123*   (mismo hash BCrypt que las demás cuentas demo)
--
-- | Cuenta                              | Plan / estado        | Perfiles                       | Qué permite probar                                  |
-- |-------------------------------------|----------------------|--------------------------------|-----------------------------------------------------|
-- | familia.premium@cineconecta.local   | Premium vigente      | 5 activos (0, 12, 16, 18, 18)  | Límite del plan alcanzado: crear un 6.º se rechaza; |
-- |                                     |                      |                                | cada perfil ve un catálogo distinto por clasificación|
-- | kids.basico@cineconecta.local       | Básico vigente       | 1 activo (7) + 1 inactivo (18) | Rechazo por clasificación; reactivar se rechaza     |
-- |                                     |                      |                                | (cupo lleno) hasta desactivar el otro               |
-- | todos.inactivos@cineconecta.local   | Estándar vigente     | 2 inactivos                    | No hay perfiles para elegir; reactivar sí se permite|
-- | jorge.vencida@cineconecta.local     | Estándar vencido     | 2 activos (12, 16)             | Puede elegir perfil, pero el catálogo lo manda a    |
-- |                                     |                      |                                | planes (rechazo por suscripción vencida)            |
-- | valentina.nueva@cineconecta.local   | Nunca ha contratado  | Ninguno                        | Estado recién registrado: no puede crear perfiles   |
-- | Sofia.Mayus@CineConecta.local       | Básico vigente       | 1 activo (18)                  | Correo con mayúsculas: el login con                 |
-- |                                     |                      |                                | sofia.mayus@cineconecta.local funciona y no se      |
-- |                                     |                      |                                | puede registrar otra cuenta con ese correo          |
--
-- Es idempotente: se puede ejecutar varias veces sin duplicar nada.
-- Ejecutar con el superusuario del contenedor (o cine_owner):
--   docker compose exec -T postgres psql -U cine -d cineconecta -v ON_ERROR_STOP=1 < db/demos/postgres/05_synthetic_accounts.sql
-- =============================================================================

SET search_path = cineconecta, public;

BEGIN;

-- 1. Cuentas ------------------------------------------------------------------
--    ON CONFLICT sobre el índice ux_accounts_email_ci (LOWER(email)): si el correo ya existe,
--    con cualquier combinación de mayúsculas, no se inserta.
INSERT INTO accounts (fullname, email, passwordhash, createdat)
VALUES
    ('Familia Restrepo', 'familia.premium@cineconecta.local',
     '$2a$10$O5AtctlJPnwGiViRRj3uE.lNMBxWEYNFFEMGkrVxfGxNMXRvhnFVi', now() - INTERVAL '200 days'),
    ('Patricia Díaz',    'kids.basico@cineconecta.local',
     '$2a$10$O5AtctlJPnwGiViRRj3uE.lNMBxWEYNFFEMGkrVxfGxNMXRvhnFVi', now() - INTERVAL '120 days'),
    ('Andrés Castro',    'todos.inactivos@cineconecta.local',
     '$2a$10$O5AtctlJPnwGiViRRj3uE.lNMBxWEYNFFEMGkrVxfGxNMXRvhnFVi', now() - INTERVAL '90 days'),
    ('Jorge Medina',     'jorge.vencida@cineconecta.local',
     '$2a$10$O5AtctlJPnwGiViRRj3uE.lNMBxWEYNFFEMGkrVxfGxNMXRvhnFVi', now() - INTERVAL '150 days'),
    ('Valentina Ruiz',   'valentina.nueva@cineconecta.local',
     '$2a$10$O5AtctlJPnwGiViRRj3uE.lNMBxWEYNFFEMGkrVxfGxNMXRvhnFVi', now() - INTERVAL '1 day'),
    ('Sofía Mayus',      'Sofia.Mayus@CineConecta.local',
     '$2a$10$O5AtctlJPnwGiViRRj3uE.lNMBxWEYNFFEMGkrVxfGxNMXRvhnFVi', now() - INTERVAL '30 days')
ON CONFLICT ((LOWER(email))) DO NOTHING;

-- 2. Perfiles -----------------------------------------------------------------
--    ON CONFLICT sobre ux_profiles_name_ci (accountId, LOWER(name)).
INSERT INTO profiles (accountid, name, maxagerating, isactive)
SELECT a.accountid, p.name, p.rating, p.active
FROM (VALUES
    -- Premium (5 perfiles): justo en el máximo del plan
    ('familia.premium@cineconecta.local', 'Papá',     18, 'Y'),
    ('familia.premium@cineconecta.local', 'Mamá',     18, 'Y'),
    ('familia.premium@cineconecta.local', 'Sara',     16, 'Y'),
    ('familia.premium@cineconecta.local', 'Tomás',    12, 'Y'),
    ('familia.premium@cineconecta.local', 'Bebé',      0, 'Y'),
    -- Básico (1 perfil): el activo es infantil; el adulto quedó desactivado
    ('kids.basico@cineconecta.local',     'Juanito',   7, 'Y'),
    ('kids.basico@cineconecta.local',     'Patricia', 18, 'N'),
    -- Estándar vigente pero sin perfiles activos
    ('todos.inactivos@cineconecta.local', 'Andrés',   18, 'N'),
    ('todos.inactivos@cineconecta.local', 'Invitado', 12, 'N'),
    -- Suscripción vencida
    ('jorge.vencida@cineconecta.local',   'Jorge',    16, 'Y'),
    ('jorge.vencida@cineconecta.local',   'Mariana',  12, 'Y'),
    -- Correo con mayúsculas
    ('sofia.mayus@cineconecta.local',     'Sofía',    18, 'Y')
) AS p(email, name, rating, active)
JOIN accounts a ON LOWER(a.email) = LOWER(p.email)
ON CONFLICT (accountid, (LOWER(name))) DO NOTHING;

-- 3. Suscripciones y pagos ----------------------------------------------------
--    Cada suscripción con su pago en la misma transacción (lo exige el trigger diferido
--    tg_subscription_requires_payment). Solo si la cuenta aún no tiene suscripciones.
DO $$
DECLARE
    v_now      TIMESTAMPTZ := date_trunc('second', now());
    v_basico   INTEGER;
    v_estandar INTEGER;
    v_premium  INTEGER;
    v_account  INTEGER;
    v_sub      INTEGER;
    r          RECORD;
BEGIN
    SELECT planid INTO v_basico   FROM plans WHERE name = 'Básico';
    SELECT planid INTO v_estandar FROM plans WHERE name = 'Estándar';
    SELECT planid INTO v_premium  FROM plans WHERE name = 'Premium';

    -- (correo, plan, días desde el inicio, precio, referencia)
    FOR r IN
        SELECT * FROM (VALUES
            -- Premium: venció una vez y se renovó (historial + vigente)
            ('familia.premium@cineconecta.local', v_premium,  40, 38900.00, 'SEED-ACC-FAM-001'),
            ('familia.premium@cineconecta.local', v_premium,  10, 38900.00, 'SEED-ACC-FAM-002'),
            ('kids.basico@cineconecta.local',     v_basico,    3, 16900.00, 'SEED-ACC-KIDS-001'),
            ('todos.inactivos@cineconecta.local', v_estandar, 12, 26900.00, 'SEED-ACC-INAC-001'),
            -- Vencida hace 20 días (inició hace 50)
            ('jorge.vencida@cineconecta.local',   v_estandar, 50, 26900.00, 'SEED-ACC-JORGE-001'),
            ('sofia.mayus@cineconecta.local',     v_basico,    8, 16900.00, 'SEED-ACC-SOFIA-001')
        ) AS t(email, planid, days_ago, amount, reference)
        ORDER BY email, days_ago DESC
    LOOP
        SELECT accountid INTO v_account FROM accounts WHERE LOWER(email) = LOWER(r.email);

        CONTINUE WHEN EXISTS (SELECT 1 FROM payments WHERE reference = r.reference);

        INSERT INTO subscriptions (accountid, planid, startsat, endsat)
        VALUES (v_account, r.planid,
                v_now - make_interval(days => r.days_ago),
                v_now - make_interval(days => r.days_ago) + INTERVAL '720 hours')
        RETURNING subscriptionid INTO v_sub;

        INSERT INTO payments (subscriptionid, paidat, amount, reference)
        VALUES (v_sub, v_now - make_interval(days => r.days_ago), r.amount, r.reference);
    END LOOP;
END $$;

COMMIT;

-- 4. Resumen (para revisar que todo quedó como describe la tabla de arriba) ----
SELECT a.email,
       COALESCE(c.planname, '— sin suscripción vigente —')            AS plan_vigente,
       COUNT(p.*) FILTER (WHERE p.isactive = 'Y')                     AS perfiles_activos,
       COUNT(p.*) FILTER (WHERE p.isactive = 'N')                     AS perfiles_inactivos,
       string_agg(p.name || ' (' || p.maxagerating || CASE WHEN p.isactive = 'N' THEN ', inactivo' ELSE '' END || ')',
                  ', ' ORDER BY p.maxagerating DESC, p.name)          AS perfiles
FROM accounts a
LEFT JOIN profiles p ON p.accountid = a.accountid
LEFT JOIN LATERAL (
    SELECT pl.name AS planname
    FROM subscriptions s JOIN plans pl ON pl.planid = s.planid
    WHERE s.accountid = a.accountid AND s.startsat <= now() AND now() < s.endsat
) c ON TRUE
WHERE LOWER(a.email) IN ('familia.premium@cineconecta.local', 'kids.basico@cineconecta.local',
                         'todos.inactivos@cineconecta.local', 'jorge.vencida@cineconecta.local',
                         'valentina.nueva@cineconecta.local', 'sofia.mayus@cineconecta.local')
GROUP BY a.email, c.planname
ORDER BY a.email;
