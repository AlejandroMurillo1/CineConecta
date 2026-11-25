-- =============================================================================
-- CineConecta · PostgreSQL · 02_demo_concurrency.sql
-- Demostración manual de concurrencia (dos contrataciones simultáneas sobre
-- la misma cuenta). Usa el rol de aplicación cine_app.
--
-- Cómo ejecutar en vivo (2 terminales psql conectadas como cine_app):
--   docker exec -it cineconecta-postgres psql -U cine_app -d cineconecta
--
-- Requisito: haber cargado 01_synthetic_billing.sql (cuenta mateo.sinplan@...).
-- =============================================================================

-- ==================== TERMINAL 1 ====================
BEGIN;
-- Obtener accountId de mateo.sinplan@cineconecta.local (sin suscripción vigente)
SELECT accountid FROM cineconecta.accounts WHERE LOWER(email) = 'mateo.sinplan@cineconecta.local';
-- Supongamos accountId = 4, planId = 2 (Estándar, 26900.00):
WITH ins AS (
    INSERT INTO cineconecta.subscriptions (accountid, planid, startsat, endsat)
    SELECT accountid, 2, now(), now() + INTERVAL '720 hours'
    FROM cineconecta.accounts
    WHERE LOWER(email) = 'mateo.sinplan@cineconecta.local'
    RETURNING subscriptionid
)
INSERT INTO cineconecta.payments (subscriptionid, amount, reference)
SELECT subscriptionid, 26900.00, 'DEMO-CONC-TX1' FROM ins;
-- NO HACER COMMIT TODAVÍA: pasar a Terminal 2.

-- ==================== TERMINAL 2 (mientras TX1 sigue abierta) ====================
-- BEGIN;
-- WITH ins AS (
--     INSERT INTO cineconecta.subscriptions (accountid, planid, startsat, endsat)
--     SELECT accountid, 2, now(), now() + INTERVAL '720 hours'
--     FROM cineconecta.accounts
--     WHERE LOWER(email) = 'mateo.sinplan@cineconecta.local'
--     RETURNING subscriptionid
-- )
-- INSERT INTO cineconecta.payments (subscriptionid, amount, reference)
-- SELECT subscriptionid, 26900.00, 'DEMO-CONC-TX2' FROM ins;
-- --> Terminal 2 queda BLOQUEADA esperando el rango GiST de TX1.

-- ==================== VOLVER A TERMINAL 1 ====================
COMMIT;
-- --> Al confirmar TX1, Terminal 2 falla inmediatamente con:
--     ERROR: conflicting key value violates exclusion constraint "ex_subscriptions_no_overlap"
--     SQLSTATE: 23P01
-- --> En Terminal 2 ejecutar ROLLBACK; y verificar que solo existe 1 suscripción y 1 pago.
