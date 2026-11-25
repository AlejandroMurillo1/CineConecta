-- =============================================================================
-- CineConecta · PostgreSQL · 03_demo_tx_rollback.sql
-- Demostración de atomicidad y fallo antes de completar el pago:
--   1. Si falla entre INSERT INTO subscriptions y INSERT INTO payments,
--      la transacción hace ROLLBACK y no queda suscripción ni pago.
--   2. Incluso si se intenta hacer COMMIT sin pago, el trigger diferido
--      tg_subscription_requires_payment rechaza el COMMIT con SQLSTATE 23514.
--
-- Ejecutar como cine_app:
--   docker exec -i cineconecta-postgres psql -U cine_app -d cineconecta < db/demos/postgres/03_demo_tx_rollback.sql
-- =============================================================================

\echo '--- Conteo inicial de suscripciones y pagos ---'
SELECT (SELECT COUNT(*) FROM cineconecta.subscriptions) AS total_subscriptions,
       (SELECT COUNT(*) FROM cineconecta.payments)      AS total_payments;

\echo '--- Caso A: fallo antes del pago + ROLLBACK ---'
BEGIN;
INSERT INTO cineconecta.subscriptions (accountid, planid, startsat, endsat)
SELECT accountid, 1, now(), now() + INTERVAL '720 hours'
FROM cineconecta.accounts
WHERE LOWER(email) = 'mateo.sinplan@cineconecta.local';
-- Simulación de caída antes de insertar en payments:
ROLLBACK;

\echo '--- Caso B: intento de COMMIT sin pago (dispara trigger diferido 23514) ---'
BEGIN;
INSERT INTO cineconecta.subscriptions (accountid, planid, startsat, endsat)
SELECT accountid, 1, now(), now() + INTERVAL '720 hours'
FROM cineconecta.accounts
WHERE LOWER(email) = 'mateo.sinplan@cineconecta.local';
COMMIT;
-- Debe fallar al COMMIT: ERROR: La suscripción X no tiene un pago registrado (23514)
ROLLBACK;

\echo '--- Conteo final (sin registros parciales) ---'
SELECT (SELECT COUNT(*) FROM cineconecta.subscriptions) AS total_subscriptions,
       (SELECT COUNT(*) FROM cineconecta.payments)      AS total_payments;
