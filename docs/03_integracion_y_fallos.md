# CineConecta — Integración entre bases y matriz de fallos

Este documento responde a dos requerimientos no funcionales:

- **RNF-06.** Las referencias entre bases las verifica la aplicación. No se supone que una transacción relacional confirma o revierte escrituras en MongoDB.
- **RNF-07.** El comportamiento está definido cuando una base no está disponible o una operación se interrumpe.

El detalle de las colecciones, las tablas y los identificadores está en `docs/data-model.md`. Aquí se explica cómo trabajan juntas las dos bases y qué pasa cuando algo falla.

---

## 1. Principio de integración

1. **Cada dato tiene una sola base dueña.**
   - PostgreSQL guarda cuentas, perfiles, planes, suscripciones y pagos.
   - MongoDB guarda contenidos, "Mi lista", sesiones de reproducción y administradores.
   - Ningún dato se copia para mantenerse sincronizado en las dos.
2. **Ninguna operación escribe en las dos bases.**
   - Cada caso de uso escribe en una sola base y, como mucho, lee de la otra para verificar.
   - Por eso no hace falta una transacción distribuida.
3. **Las referencias van de MongoDB hacia PostgreSQL**, nunca al revés.
   - Los documentos de MongoDB guardan identificadores enteros de PostgreSQL (`accountId`, `profileId`, `planIdAtStart`).
   - PostgreSQL no guarda nada de MongoDB.
4. **Primero se verifica, al final se escribe.**
   - Toda operación que escribe en MongoDB a partir de datos de PostgreSQL verifica antes en PostgreSQL.
   - La escritura es lo último que hace. Si algo falla antes, no queda nada a medias.

### 1.1 Qué base toca cada operación

| Operación | Lee en PostgreSQL | Lee en MongoDB | Escribe en |
|---|---|---|---|
| Registro, login y logout de clientes (RF-01 a RF-03) | Cuenta | — | PostgreSQL (registro) |
| Perfiles: crear, editar, desactivar, reactivar, elegir (RF-05 a RF-09) | Cuenta, perfiles, suscripción vigente, plan | — | PostgreSQL |
| Planes, contratación e historial (RF-04, RF-10, RF-11) | Planes, suscripciones, pagos, perfiles activos | — | PostgreSQL (una transacción) |
| Gestión de planes del administrador (RF-12) | Planes, suscripciones vigentes | — | PostgreSQL |
| Login del administrador (RF-31) | — | `admin_users` | — |
| Administración del catálogo (RF-13 a RF-19) | — | `contents` | MongoDB |
| Catálogo del cliente: búsqueda y detalle (RF-20 a RF-22) | Perfil en uso, suscripción vigente | `contents` (y `playback_sessions` para las marcas de visto) | — |
| Mi lista (RF-23 a RF-25) | Perfil en uso, suscripción vigente | `contents`, `my_lists` | MongoDB |
| Reproducción y "Continuar viendo" (RF-26 a RF-30) | Perfil en uso, suscripción vigente, plan en ese instante | `contents`, `playback_sessions` | MongoDB |
| Informe de ingresos (RF-33) | Planes, suscripciones, pagos (con `cine_report`) | — | — |
| Informe de consumo (RF-34) | Nombres de los planes | `playback_sessions` (con `cineReport`) | — |

---

## 2. Identificadores que cruzan de una base a otra

| Identificador | Nace en | Se guarda en MongoDB | Por qué la referencia no queda rota |
|---|---|---|---|
| `accountId` | `accounts` | `playback_sessions` | Se toma del token del usuario autenticado, nunca de un parámetro. Las cuentas no se borran. |
| `profileId` | `profiles` | `_id` de `my_lists`; `playback_sessions` | Se verifica en PostgreSQL en cada petición. Los perfiles se desactivan, no se borran. |
| `planIdAtStart` | `plans` (vía la suscripción vigente) | `playback_sessions` | Se lee de la suscripción vigente al iniciar la sesión. Los planes se desactivan, no se borran. |
| `contentId` | `contents` | `my_lists`, `playback_sessions` | Los contenidos se retiran, no se borran. |
| `episodeId` | `contents.contentDetails.seasons.episodes` | `playback_sessions` | Es un UUID estable, y la aplicación no tiene forma de eliminar episodios. |

Una referencia de MongoDB a PostgreSQL no puede quedar rota, porque `cine_app` no tiene permiso `DELETE` en ninguna tabla (`db/postgres/02_grants.sql`) y los identificadores salen de secuencias `IDENTITY`, que no se reutilizan. Dentro de MongoDB pasa lo mismo: `cineApp` no tiene la acción `remove` en ninguna colección (`db/mongo/init/03_users.js`).

---

## 3. Verificación V1

Se aplica antes de buscar en el catálogo, agregar a "Mi lista" o iniciar y controlar una reproducción. El orden importa: primero PostgreSQL, después MongoDB y al final la escritura.

1. **PostgreSQL: el perfil.** El perfil en uso existe, está activo y pertenece a la cuenta del token (`CurrentProfile`, implementado en `accounts`). Si no se cumple, la aplicación pide elegir un perfil.
2. **PostgreSQL: la suscripción.** La cuenta tiene una suscripción vigente, `startsAt ≤ ahora < endsAt`, evaluada con el reloj de la aplicación (`SubscriptionApi.current`). Si no la tiene, la aplicación lleva a los planes.
3. **MongoDB: el contenido.** El contenido o episodio está publicado (`PUBLISHED`) y su clasificación es menor o igual a la del perfil (`ICatalogApi.findPlayable`).
4. **MongoDB: la escritura.** Solo entonces se escribe en MongoDB. Al iniciar una reproducción, `planIdAtStart` se toma de la suscripción vigente en ese instante (`SubscriptionApi.planAt`).

Dónde se implementa:
- `ViewingAccess`: perfil y suscripción para "Mi lista" y la reproducción.
- `ProfileViewerContext`: perfil y suscripción para el catálogo.
- `PlaybackService.requirePlayable` y `MyListService`: contenido publicado y clasificación.

Al controlar una sesión ya iniciada (pausar, avanzar, avisos periódicos), se repiten los pasos 1 y 2. Así, una suscripción que vence en medio de la reproducción no sigue sumando tiempo.

---

## 4. Atomicidad sin transacción distribuida

| Dónde | Mecanismo | Qué garantiza |
|---|---|---|
| Contratación (PostgreSQL) | Una sola transacción, más un trigger diferido que rechaza al confirmar una suscripción sin pago | Pago y suscripción se guardan juntos o no se guarda ninguno (RI-05) |
| Contratación y perfiles (PostgreSQL) | `pg_advisory_xact_lock(accountId)`, más la restricción `EXCLUDE` | Sin períodos superpuestos ni más perfiles que el máximo, aunque lleguen peticiones simultáneas |
| Mi lista (MongoDB) | Una sola operación sobre el documento del perfil: `$addToSet` con `upsert` para agregar, `$pull` para quitar | Sin duplicados. Reintentar es seguro: repetirla da el mismo resultado |
| Sesiones de reproducción (MongoDB) | `updateOne` condicionado a la `revision` anterior | Dos pestañas o dos peticiones no suman el mismo tramo dos veces. La segunda recibe "La sesión cambió en otra pestaña" |
| Contenidos (MongoDB) | `@Version` de Spring Data (bloqueo optimista) | Dos administradores no se pisan una edición. El segundo recibe un aviso y recarga |

Cada escritura en MongoDB afecta un solo documento, y en MongoDB eso es atómico. Por eso no se necesitan transacciones de varios documentos.

---

## 5. Matriz de fallos

### 5.1 Una base no está disponible

La aplicación detecta la caída en **5 segundos como máximo** por base:
- PostgreSQL: `spring.datasource.hikari.connection-timeout`, y también en `ReportingJdbc`.
- MongoDB: `DatabaseTimeoutsConfig`, y también en `ReportingMongo`.

Cuando la detecta, responde **503 "Servicio no disponible"** con la cabecera `Retry-After: 30`. Nunca muestra trazas ni mensajes internos. La página dice qué parte del servicio está afectada (`DatabaseUnavailableAdvice` y `templates/error.html`).

| Situación | Qué ve el usuario | Qué queda guardado | Qué sigue funcionando |
|---|---|---|---|
| **PostgreSQL caído**: el cliente navega o inicia sesión | 503 avisando que no se puede acceder a cuentas, perfiles ni suscripciones. Como V1 empieza en PostgreSQL, el catálogo, "Mi lista" y el reproductor también responden 503. | Nada nuevo. Ninguna escritura en MongoDB se hace sin pasar antes por V1. | — |
| **PostgreSQL caído**: el cliente contrata un plan o gestiona perfiles | 503 | Nada. La transacción no llega a abrirse o se revierte completa. | — |
| **PostgreSQL caído**: el administrador | Planes, ingresos y consumo responden 503. El de consumo, porque necesita los nombres de los planes. | Nada | Login del administrador y administración del catálogo (solo MongoDB) |
| **MongoDB caído**: el cliente | 503 avisando que no se puede acceder al catálogo ni a la actividad, y que la cuenta y la suscripción no se ven afectadas. En la página de inicio, las secciones que cargan por htmx muestran un aviso. | Nada nuevo | Registro, login, perfiles, planes, contratación e historial (solo PostgreSQL) |
| **MongoDB caído**: el administrador inicia sesión | 503 con la página de login y el aviso "No pudimos verificar tus credenciales: el servicio de administradores no responde". No dice "credenciales incorrectas". | Nada | — |
| **MongoDB caído**: el administrador ya tenía sesión | Catálogo y consumo responden 503 | Nada | Planes e informe de ingresos (solo PostgreSQL) |

El login del administrador necesita su propio manejador (`AdminLoginFailureHandler`). La autenticación ocurre en el filtro de Spring Security, antes de cualquier controlador, así que `DatabaseUnavailableAdvice` no llega a actuar.

### 5.2 Una operación se interrumpe

| Momento | Resultado | Por qué |
|---|---|---|
| Falla a mitad de la contratación (por ejemplo, entre la suscripción y el pago) | No queda ni la suscripción ni el pago | Es una sola transacción; el trigger diferido rechaza al confirmar una suscripción sin pago |
| Falla PostgreSQL después de V1 y antes de escribir en MongoDB | No queda nada | La escritura en MongoDB es el último paso |
| Falla la escritura en MongoDB (al iniciar una reproducción o agregar a "Mi lista") | No queda nada en ninguna base; el usuario ve 503 | En PostgreSQL esas operaciones solo leen; nada que revertir |
| La sesión se crea en MongoDB pero la respuesta no llega al navegador | Queda una sesión abierta sin actividad | `AbandonedPlaybackCleanup` la cierra (revisa cada 30 s y cierra tras 1 minuto sin actividad) con la última posición guardada; aparece en "Continuar viendo" si no llegó al 90 % |
| El usuario cierra la pestaña o pierde la conexión mientras ve algo | Igual: la sesión se cierra sola | El reproductor avisa cada 10 s. Se pierde, como mucho, el tiempo desde el último aviso |
| Se reintenta "Agregar a Mi lista" tras un error | El contenido queda una sola vez | `$addToSet` con `upsert`: repetirla no cambia el resultado |
| Dos pestañas controlan la misma sesión | Se aplica la primera; la segunda recibe un aviso y recarga | `updateOne` condicionado a `revision` |
| Dos contrataciones o dos creaciones de perfil simultáneas | Una se aplica y la otra se rechaza con un mensaje | `pg_advisory_xact_lock(accountId)`, con la restricción `EXCLUDE` como segunda barrera |

### 5.3 Cambia el estado de un dato referenciado

| Cambio | Efecto en la otra base | Comportamiento |
|---|---|---|
| Se desactiva un perfil | Sus sesiones y su "Mi lista" se conservan | V1 lo rechaza: no se puede elegir ni usar. Si se reactiva, recupera su lista y su historial |
| Vence la suscripción mientras se reproduce | La sesión queda abierta | El siguiente control o aviso se rechaza; la limpieza automática cierra la sesión con la última posición guardada. El catálogo lleva a los planes |
| Se desactiva o renombra un plan | Las sesiones conservan `planIdAtStart` | Los informes siguen agrupando por el plan que se tenía al iniciar cada sesión, con su nombre actual |
| Se retira un contenido | Siguen existiendo las entradas en "Mi lista" y las sesiones | Desaparece del catálogo y de "Continuar viendo" y no se puede reproducir. "Mi lista" lo muestra marcado como retirado. Los informes lo siguen contando |
| Se restaura un contenido retirado | — | Vuelve a estar disponible con las mismas referencias, porque su `contentId` no cambia |

---

## 6. Configuración

| Variable | Valor por defecto | Para qué |
|---|---|---|
| `POSTGRES_CONNECT_TIMEOUT_MS` | 5000 | Espera máxima por una conexión de PostgreSQL antes de responder 503 |
| `MONGODB_TIMEOUT_MS` | 5000 | Espera máxima por un servidor de MongoDB antes de responder 503 |
| `REPORT_POSTGRES_USER` / `REPORT_POSTGRES_PASSWORD` | `cine_report` / `cine_report_dev` | Conexión de solo lectura del informe de ingresos |
| `REPORT_MONGODB_USER` / `REPORT_MONGODB_PASSWORD` | `cineReport` / `cine_report_dev` | Conexión de solo lectura del informe de consumo |

---

## 7. Cómo demostrarlo

Con la aplicación corriendo y las bases levantadas con `docker compose up -d`:

**1. MongoDB caído**

```bash
docker compose stop mongo
```

- Como cliente, inicia sesión, entra a Perfiles y a Planes, e historial: todo funciona.
- Entra al catálogo: debe aparecer el 503 que avisa que el catálogo no está disponible y que la cuenta no se ve afectada.
- En otra ventana, intenta entrar al panel de administración: debe aparecer el 503 en el login, con el aviso.
- Vuelve a levantar la base con `docker compose start mongo`.

**2. PostgreSQL caído**

```bash
docker compose stop postgres
```

- Recarga cualquier página de cliente: 503 que avisa que no se puede acceder a cuentas ni suscripciones.
- El panel de administración sigue abriendo, y su catálogo funciona.
- Vuelve a levantar la base con `docker compose start postgres`.

**3. Una operación interrumpida**

La de la contratación se muestra con `db/demos/postgres/03_demo_tx_rollback.sql`.

**4. Sesión abandonada**

- Inicia una reproducción y cierra la pestaña.
- Después de un minuto, el título aparece en "Continuar viendo" con la posición donde quedó.

---

## 8. Evidencia automatizada

| Prueba | Qué comprueba |
|---|---|
| `DatabaseUnavailableWebMvcTest` | 503 con el mensaje correcto para PostgreSQL y para MongoDB, con o sin traducción de Spring |
| `AdminSecurityWebMvcTest`, `AdminLoginFailureHandlerTest` | Login del administrador: 503 si MongoDB cae; las credenciales incorrectas siguen siendo "credenciales incorrectas" |
| `BillingTransactionIT` | Fallo a mitad de la contratación, trigger de pago y contrataciones simultáneas |
| `ProfileRulesIT` | Creación simultánea de perfiles sin superar el máximo |
| `ViewingAccessTest`, `ViewingCurrentProfileWebMvcTest` | V1: perfil ajeno o inactivo, suscripción vencida, perfil tomado de la sesión |
| `PlaybackSessionValidatorIT` | Las sesiones cumplen el validador real de `playback_sessions` |
| `ReportingConnectionIT`, `ReportingMongoIT` | Los informes leen con `cine_report` y `cineReport`, que no pueden escribir |

---

## 9. Limitaciones conocidas

- **El informe de consumo depende de las dos bases.** Toma los nombres de los planes de PostgreSQL: si PostgreSQL cae, todo el informe responde 503, aunque las sesiones estén disponibles en MongoDB.
- **Los informes muestran el nombre actual del plan.** Si un plan se renombra, el consumo y los ingresos anteriores aparecen con el nombre nuevo, aunque se agrupan por el plan correcto. No existe un historial de nombres.
- **Las dos bases deben reiniciarse juntas.** Si se borra solo una (`docker volume rm` de un volumen), los identificadores guardados en MongoDB pueden apuntar a filas que ya no existen en PostgreSQL. Para empezar de cero, se usa `docker compose down -v`. Los scripts de `db/demos/` deben cargarse en orden: primero PostgreSQL y después MongoDB (`02_synthetic_sessions.js` usa los perfiles que exporta `07_export_viewers.sql`).
- **No hay reintentos automáticos ni una cola de escrituras pendientes** (patrón *outbox*). Con la regla de no escribir en las dos bases no hacen falta: ante una caída, el usuario recibe el 503 y vuelve a intentar.
