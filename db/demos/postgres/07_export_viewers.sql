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
-- Solo lee. Incluye las cuentas de los scripts de demostración (01 y 05), cualquier
-- otra cuenta con suscripciones ya iniciadas y una muestra de los perfiles de volumen
-- (06 y 08): los primeros N perfiles activos con suscripciones (por defecto 3 400, unas
-- 200 000 sesiones). Con -v perfiles_volumen=0 deja fuera el volumen.
--
-- No se ejecuta solo: ver el comando completo en 02_synthetic_sessions.js.
-- Usar psql con -qAt para que la salida sea únicamente la línea de JavaScript
-- (y -v perfiles_volumen=N para cambiar el tamaño de la muestra).
-- =============================================================================

\if :{?perfiles_volumen}
\else
    \set perfiles_volumen 3400
\endif

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
      AND EXISTS (SELECT 1
                  FROM cineconecta.subscriptions s
                  WHERE s.accountid = p.accountid
                    AND s.startsat < now())
      AND (LOWER(a.email) NOT LIKE '%@volumen.cineconecta.local'
           OR p.profileid IN (
               -- Muestra determinista de perfiles de volumen.
               SELECT pv.profileid
               FROM cineconecta.profiles pv
               JOIN cineconecta.accounts av ON av.accountid = pv.accountid
               WHERE pv.isactive = 'Y'
                 AND LOWER(av.email) LIKE '%@volumen.cineconecta.local'
                 AND EXISTS (SELECT 1 FROM cineconecta.subscriptions sv
                             WHERE sv.accountid = pv.accountid AND sv.startsat < now())
               ORDER BY pv.profileid
               LIMIT :perfiles_volumen))
) v;
