-- =============================================================================
-- CineConecta · PostgreSQL · 07_export_viewers.sql
-- Exporta, como una línea de JavaScript, los perfiles activos y el historial de
-- suscripciones de cada cuenta. Lo consume db/demos/mongo/02_synthetic_sessions.js
-- para generar sesiones de reproducción con el plan vigente en cada fecha.
--
-- Salida (una sola línea):
--   const VIEWERS = [{"accountId":1,"profileId":3,"maxAgeRating":18,
--                     "subscriptions":[{"planId":1,"startsAt":<ms>,"endsAt":<ms>}, ...]}, ...];
--
-- Solo lee. Incluye las cuentas de los scripts de demostración (01 y 05) y
-- cualquier otra cuenta con suscripciones ya iniciadas; deja fuera las cuentas de
-- volumen (@volumen.cineconecta.local), que no hacen falta para la demo.
--
-- No se ejecuta solo: ver el comando completo en 02_synthetic_sessions.js.
-- Usar psql con -qAt para que la salida sea únicamente la línea de JavaScript.
-- =============================================================================

SELECT 'const VIEWERS = '
       || COALESCE(json_agg(v ORDER BY v."profileId"), '[]'::json)::text
       || ';'
FROM (
    SELECT p.accountid    AS "accountId",
           p.profileid    AS "profileId",
           p.maxagerating AS "maxAgeRating",
           (SELECT json_agg(json_build_object(
                       'planId',   s.planid,
                       'startsAt', (EXTRACT(EPOCH FROM s.startsat) * 1000)::bigint,
                       'endsAt',   (EXTRACT(EPOCH FROM s.endsat)   * 1000)::bigint)
                   ORDER BY s.startsat)
            FROM cineconecta.subscriptions s
            WHERE s.accountid = p.accountid
              AND s.startsat < now()) AS "subscriptions"
    FROM cineconecta.profiles p
    JOIN cineconecta.accounts a ON a.accountid = p.accountid
    WHERE p.isactive = 'Y'
      AND LOWER(a.email) NOT LIKE '%@volumen.cineconecta.local'
      AND EXISTS (SELECT 1
                  FROM cineconecta.subscriptions s
                  WHERE s.accountid = p.accountid
                    AND s.startsat < now())
) v;
