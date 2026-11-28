-- =============================================================================
-- CineConecta · PostgreSQL · 08_volume_subscriptions.sql
-- VOLUMEN de suscripciones y pagos para las cuentas de 06_volume_accounts.sql, para
-- que el historial, el informe de ingresos y las mediciones trabajen con un volumen
-- realista (con 20 000 cuentas: ≈ 110 000 suscripciones y otros tantos pagos).
--
-- Qué genera para cada cuenta de volumen (determinista: depende solo del número de cuenta):
--   - ≈ 8 % nunca contrató; el resto tiene de 1 a 12 períodos de 30 días.
--   - ≈ 65 % sigue vigente hoy; el resto venció hace 1 a 120 días.
--   - ≈ 30 % de las cuentas con 4 o más períodos tiene una pausa de 5 a 44 días.
--   - ≈ 20 % de las cuentas con 3 o más períodos sube de plan a mitad del historial.
--   - Plan: Básico, Estándar o Premium, nunca con menos perfiles que los activos de la
--     cuenta (la misma regla que aplica la contratación, RI-04).
--   - Un pago por suscripción, por el precio del plan, en el instante de inicio
--     (referencia VOL-<subscriptionId>).
--   - Ningún período empieza antes del registro de la cuenta (se recorta el historial).
--
-- Respeta las reglas de la base: 30 días exactos (CHECK), sin superposición (EXCLUDE),
-- y cada suscripción con su pago en la misma transacción (trigger diferido).
--
-- Requisitos: 03_plans.sql (planes Básico, Estándar y Premium) y 06_volume_accounts.sql.
-- Idempotente: solo genera para las cuentas de volumen que todavía no tienen suscripciones.
--
-- Uso:
--   docker compose exec -T postgres psql -U cine -d cineconecta -v ON_ERROR_STOP=1 \
--       < db/demos/postgres/08_volume_subscriptions.sql
-- =============================================================================

SET search_path = cineconecta, public;

-- Los tres planes del catálogo inicial, por nombre (los ids dependen del orden de carga).
DO $$
BEGIN
    IF (SELECT COUNT(*) FROM plans WHERE name IN ('Básico', 'Estándar', 'Premium')) < 3 THEN
        RAISE EXCEPTION 'Faltan los planes Básico, Estándar y Premium (db/postgres/03_plans.sql).';
    END IF;
END $$;

SELECT
    (SELECT planid FROM plans WHERE name = 'Básico')   AS plan_basico,
    (SELECT planid FROM plans WHERE name = 'Estándar') AS plan_estandar,
    (SELECT planid FROM plans WHERE name = 'Premium')  AS plan_premium
\gset

\echo 'Generando suscripciones y pagos para las cuentas de volumen...'
\timing on

BEGIN;

WITH cuentas AS (
    SELECT a.accountid,
           a.createdat,
           substring(LOWER(a.email) FROM 'vol\.(\d{6})@')::int AS n,
           (SELECT COUNT(*) FROM profiles p WHERE p.accountid = a.accountid AND p.isactive = 'Y') AS activos
    FROM accounts a
    WHERE LOWER(a.email) LIKE 'vol.%@volumen.cineconecta.local'
      AND NOT EXISTS (SELECT 1 FROM subscriptions s WHERE s.accountid = a.accountid)
),
parametros AS (
    SELECT c.*,
           -- Períodos: 0 (nunca contrató) o de 1 a 12.
           CASE WHEN (c.n * 37) % 100 < 8 THEN 0 ELSE 1 + (c.n * 53) % 12 END        AS periodos,
           (c.n * 71) % 100 < 65                                                       AS vigente,
           1 + (c.n * 29) % 120                                                        AS dias_vencida,
           -- Nivel mínimo según los perfiles activos (1: Básico, 2: Estándar, 3: Premium)...
           CASE WHEN c.activos <= 1 THEN 1 WHEN c.activos <= 3 THEN 2 ELSE 3 END        AS nivel_minimo,
           -- ...y el que eligió el cliente (40 % Básico, 40 % Estándar, 20 % Premium).
           CASE WHEN (c.n * 19) % 100 < 40 THEN 1 WHEN (c.n * 19) % 100 < 80 THEN 2 ELSE 3 END AS nivel_elegido
    FROM cuentas c
),
plan_cuenta AS (
    SELECT p.*,
           GREATEST(p.nivel_minimo, p.nivel_elegido)                                    AS nivel,
           -- Pausa: después del período "pausa_tras" (empezando en 0), de 5 a 44 días.
           CASE WHEN p.periodos >= 4 AND (p.n * 43) % 100 < 30
                THEN (p.n * 17) % (p.periodos - 2) ELSE NULL END                       AS pausa_tras,
           5 + (p.n * 11) % 40                                                          AS dias_pausa,
           -- Subida de plan a partir de la mitad del historial.
           p.periodos >= 3 AND (p.n * 61) % 100 < 20                                    AS sube,
           -- Inicio del último período: vigente → empezó hace menos de 29 días;
           -- vencida → terminó hace dias_vencida días.
           CASE WHEN (p.n * 71) % 100 < 65
                THEN now() - make_interval(mins => (p.n * 97) % (29 * 24 * 60))
                ELSE now() - make_interval(days => 1 + (p.n * 29) % 120) - INTERVAL '720 hours'
           END                                                                          AS ultimo_inicio
    FROM parametros p
    WHERE p.periodos > 0
),
periodos AS (
    SELECT pc.accountid,
           pc.createdat,
           -- Los períodos se calculan hacia atrás desde el último; los anteriores a la
           -- pausa se corren dias_pausa días más atrás.
           pc.ultimo_inicio
               - make_interval(hours => 720 * (pc.periodos - 1 - i))
               - CASE WHEN pc.pausa_tras IS NOT NULL AND i <= pc.pausa_tras
                      THEN make_interval(days => pc.dias_pausa) ELSE INTERVAL '0' END       AS inicio,
           LEAST(3, pc.nivel + CASE WHEN pc.sube AND i >= pc.periodos / 2 THEN 1 ELSE 0 END) AS nivel
    FROM plan_cuenta pc
    CROSS JOIN LATERAL generate_series(0, pc.periodos - 1) AS i
),
nuevas AS (
    INSERT INTO subscriptions (accountid, planid, startsat, endsat)
    SELECT p.accountid,
           CASE p.nivel WHEN 1 THEN :plan_basico WHEN 2 THEN :plan_estandar ELSE :plan_premium END,
           p.inicio,
           p.inicio + INTERVAL '720 hours'
    FROM periodos p
    WHERE p.inicio >= p.createdat              -- no antes del registro de la cuenta
    ORDER BY p.accountid, p.inicio
    RETURNING subscriptionid, planid, startsat
)
INSERT INTO payments (subscriptionid, paidat, amount, reference)
SELECT n.subscriptionid, n.startsat, pl.price, 'VOL-' || n.subscriptionid
FROM nuevas n
JOIN plans pl ON pl.planid = n.planid;

COMMIT;

-- Estadísticas al día para que el planificador use los datos nuevos en las mediciones.
ANALYZE subscriptions;
ANALYZE payments;

\timing off

-- Tamaño resultante (indicarlo en el informe de mediciones)
SELECT 'cuentas de volumen con suscripciones (vigentes hoy)' AS dato,
       COUNT(DISTINCT s.accountid) || ' (' ||
       COUNT(DISTINCT s.accountid) FILTER (WHERE s.startsat <= now() AND now() < s.endsat) || ')' AS valor
FROM subscriptions s JOIN accounts a ON a.accountid = s.accountid
WHERE LOWER(a.email) LIKE '%@volumen.cineconecta.local'
UNION ALL
SELECT 'suscripciones de volumen por plan',
       string_agg(t.name || ': ' || t.total, ' · ' ORDER BY t.planid)
FROM (SELECT pl.planid, pl.name, COUNT(*) AS total
      FROM subscriptions s
      JOIN accounts a ON a.accountid = s.accountid
      JOIN plans pl ON pl.planid = s.planid
      WHERE LOWER(a.email) LIKE '%@volumen.cineconecta.local'
      GROUP BY pl.planid, pl.name) t
UNION ALL
SELECT 'total filas subscriptions / payments',
       (SELECT COUNT(*) FROM subscriptions) || ' / ' || (SELECT COUNT(*) FROM payments)
UNION ALL
SELECT 'pagos de volumen (suma)',
       to_char((SELECT SUM(p.amount) FROM payments p WHERE p.reference LIKE 'VOL-%'), 'FM999G999G999G990D00')
UNION ALL
SELECT 'tamaño subscriptions (tabla + índices)', pg_size_pretty(pg_total_relation_size('subscriptions'))
UNION ALL
SELECT 'tamaño payments (tabla + índices)', pg_size_pretty(pg_total_relation_size('payments'));
