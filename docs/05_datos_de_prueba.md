# CineConecta — Datos de prueba (seed)

Este documento explica cómo se generan los datos de demostración y de volumen, qué reglas respetan y cómo cargarlos y comprobarlos. Responde al RNF-19: datos sintéticos con volumen para las mediciones, indicando el tamaño.

Los datos de prueba no son parte del esquema. Los scripts de `db/postgres/` y `db/mongo/init/` crean la estructura, los usuarios y el administrador inicial. Todo lo de este documento está en `db/demos/` y se carga aparte.

---

## 1. Dos tipos de datos

| Tipo | Para qué sirve | Ejemplos |
|---|---|---|
| **Demostración** | Casos con nombre propio para la sustentación: cada cuenta muestra una regla. | Ana, con plan vigente y un perfil infantil. Carlos, con la suscripción vencida. Lucía, que intenta bajar de plan con más perfiles de los que permite. |
| **Volumen** | Cantidad realista para que los informes tengan datos y las mediciones de índices sean significativas. | 20 000 cuentas, ≈ 95 000 suscripciones, 150 contenidos, ≈ 200 000 sesiones. |

Los dos tipos conviven en las mismas bases. Las cuentas de volumen tienen el correo `vol.NNNNNN@volumen.cineconecta.local`. Los contenidos, sesiones y listas generados llevan `synthetic: true`. Así se distinguen de los datos de demostración y de los creados desde la aplicación.

---

## 2. Scripts

### 2.1 PostgreSQL (`db/demos/postgres/`)

| Script | Tipo | Responsable | Qué genera |
|---|---|---|---|
| `01_synthetic_billing.sql` | Demostración | P2 | Ana, Carlos, Lucía y Mateo, con suscripciones vigentes, vencidas y sin plan, y sus pagos |
| `05_synthetic_accounts.sql` | Demostración | P1 | Casos de cuentas y perfiles: límite del plan alcanzado, clasificaciones de 0 a 18, perfiles inactivos, suscripción vencida, cuenta recién registrada y correo con mayúsculas (tabla al inicio del script) |
| `06_volume_accounts.sql` | Volumen | P1 | N cuentas (20 000 por defecto) con 1 a 5 perfiles cada una (≈ 60 000), registradas en los últimos 2 años; ≈ 15 % de perfiles inactivos |
| `08_volume_subscriptions.sql` | Volumen | P5 | El historial de suscripciones y pagos de esas cuentas (≈ 95 000 de cada uno con 20 000 cuentas) |
| `07_export_viewers.sql` | Puente | P4, P5 | No escribe nada: exporta a MongoDB los perfiles que van a tener sesiones y listas (sección 3) |
| `02_demo_concurrency.sql`, `03_demo_tx_rollback.sql`, `04_index_experiment.sql` | Demostraciones de la sustentación | P2 | Concurrencia, reversión de la transacción e índices. No cargan datos permanentes. |

### 2.2 MongoDB (`db/demos/mongo/`)

| Script | Tipo | Responsable | Qué genera |
|---|---|---|---|
| `01_demo_catalog.js` | Demostración | P5 | 5 contenidos con IDs fijos (película, serie, documental y un borrador), con afiche propio |
| `03_volume_catalog.js` | Volumen | P5 | N contenidos (150 por defecto) |
| `02_synthetic_sessions.js` | Volumen | P4 (ampliado por P5) | Sesiones de reproducción cerradas para los perfiles exportados (≈ 200 000) |
| `04_synthetic_my_lists.js` | Volumen | P5 | "Mi lista" para ≈ 60 % de los perfiles exportados |

`db/mongo/init/catalog-samples.js` (P3) también agrega 3 contenidos de ejemplo, pero lo hace al crear el contenedor y solo si `contents` está vacía.

### 2.3 Cargador (`db/demos/load_all.sh`)

Corre todo en el orden correcto y muestra el tamaño de cada parte (sección 4).

---

## 3. Orden y dependencias

```
PostgreSQL                                   MongoDB
──────────                                   ───────
01 cuentas demo + suscripciones + pagos
05 casos de cuentas y perfiles
06 cuentas y perfiles de volumen
08 suscripciones y pagos de volumen
                                             01 catálogo de demostración
                                             03 catálogo de volumen
07 exporta perfiles + historial ──────────▶ 02 sesiones de reproducción
   (const VIEWERS = [...])       ──────────▶ 04 Mi lista
```

- **`08` necesita `06`**, porque genera suscripciones solo para las cuentas de volumen. También necesita los planes Básico, Estándar y Premium de `db/postgres/03_plans.sql`.
- **`02` y `04` necesitan las dos bases a la vez.**
  - De PostgreSQL toman, a través de `07`, qué perfiles existen, su clasificación y qué plan tenía la cuenta en cada fecha.
  - De MongoDB toman qué contenidos están publicados.
  - La salida de `07` es una sola línea de JavaScript, `const VIEWERS = [...]`, que se pone antes del script de MongoDB.
- **`07` no exporta todos los perfiles de volumen.** Exporta los de demostración más una muestra de los primeros N perfiles de volumen con suscripciones (3 400 por defecto). Esa muestra es la que decide cuántas sesiones se generan.

Esto respeta el principio de integración de `docs/03_integracion_y_fallos.md`: ningún script escribe en las dos bases, y los identificadores que se guardan en MongoDB (`accountId`, `profileId`, `planIdAtStart`) salen de PostgreSQL.

---

## 4. Cómo cargarlos

### 4.1 Todo junto (recomendado)

Desde la raíz del proyecto, en bash o Git Bash (en Windows, PowerShell no sirve para este script):

```bash
docker compose down -v && docker compose up -d   # opcional: empezar con las bases vacías
./db/demos/load_all.sh
```

Con el tamaño medio tarda menos de un minuto. El script espera a que las dos bases respondan y se detiene en el primer error.

Usa `docker compose exec`, es decir, entra a los contenedores. Por eso funciona igual aunque hayas cambiado los puertos en `.env`.

### 4.2 Tamaño

| Variable | Por defecto (medio) | Grande | Qué controla |
|---|---|---|---|
| `CUENTAS` | 20 000 | 100 000 | Cuentas de volumen; sus perfiles, suscripciones y pagos crecen en proporción |
| `CONTENIDOS` | 150 | 500 | Contenidos generados en el catálogo |
| `PERFILES_VOLUMEN` | 3 400 | 17 000 | Perfiles de volumen con sesiones y listas (≈ 60 sesiones por perfil) |

```bash
CUENTAS=100000 CONTENIDOS=500 PERFILES_VOLUMEN=17000 ./db/demos/load_all.sh
```

Resultado con el tamaño medio, medido en una carga desde cero:

| Dato | Cantidad |
|---|---|
| Cuentas (volumen + demo) | 20 010 |
| Perfiles activos / inactivos de volumen | 51 000 / 9 000 |
| Cuentas de volumen con suscripciones (vigentes hoy) | 17 387 (11 956) |
| Suscripciones / pagos | 94 518 / 94 518 (19 MB y 17 MB con índices) |
| Contenidos | 158 (150 generados: 65 películas, 55 series con 876 episodios, 30 documentales) |
| Sesiones de reproducción | ≈ 203 000, de agosto de 2025 a hoy |
| Mi lista | 2 009 listas, 12 862 títulos guardados |

### 4.3 Un script suelto

Cada script trae su comando al inicio. Los que solo usan una base:

```bash
docker compose exec -T postgres psql -U cine -d cineconecta -v ON_ERROR_STOP=1 -v cuentas=20000 < db/demos/postgres/06_volume_accounts.sql
docker compose exec -T postgres psql -U cine -d cineconecta -v ON_ERROR_STOP=1 < db/demos/postgres/08_volume_subscriptions.sql
docker compose exec -T -e CONTENIDOS=150 mongo mongosh --quiet -u cineOwner -p cine_owner_dev --authenticationDatabase cineconecta cineconecta < db/demos/mongo/03_volume_catalog.js
```

Los que usan las dos bases, `02` y `04`, van precedidos por la exportación de `07`:

```bash
{ docker compose exec -T postgres psql -U cine -d cineconecta -qAt -v ON_ERROR_STOP=1 -v perfiles_volumen=3400 < db/demos/postgres/07_export_viewers.sql
  cat db/demos/mongo/02_synthetic_sessions.js
} | docker compose exec -T mongo mongosh --quiet -u cineOwner -p cine_owner_dev --authenticationDatabase cineconecta cineconecta
```

---

## 5. Cómo se generan

### 5.1 Suscripciones y pagos (`08`)

Cada cuenta de volumen recibe un historial que depende solo de su número:

- **Cuántos períodos:** ≈ 8 % nunca contrató; el resto tiene de 1 a 12 períodos de 30 días seguidos.
- **Estado hoy:** ≈ 65 % sigue vigente (el último período empezó hace menos de 29 días); el resto venció hace 1 a 120 días.
- **Pausas:** ≈ 30 % de las cuentas con 4 o más períodos deja de pagar entre 5 y 44 días y luego vuelve.
- **Cambios de plan:** ≈ 20 % de las cuentas con 3 o más períodos sube de plan a mitad del historial.
- **Plan:** el cliente elige Básico, Estándar o Premium (40 %, 40 % y 20 %), pero nunca uno con menos perfiles que los activos de su cuenta. Por eso hay más Premium que Básico: muchas cuentas de volumen tienen 4 o 5 perfiles activos.
- **Pago:** uno por suscripción, por el precio del plan, en el instante en que empieza, con la referencia `VOL-<subscriptionId>`.

### 5.2 Catálogo (`03`)

- **Tipos:** ≈ 50 % películas, 30 % series y 20 % documentales.
- **Estados:** ≈ 85 % publicados, 8 % borradores y 7 % retirados.
- **Películas:** de 80 a 150 minutos, con dirección y de 1 a 4 personas de reparto (la mayoría con personaje).
- **Series:** de 1 a 4 temporadas con 4 a 10 episodios de 22 a 58 minutos. Una serie en borrador puede tener temporadas vacías, como permite el validador.
- **Documentales:** de 45 a 110 minutos, con temas y, a veces, entrevistados y fuentes.
- **Metadatos:**
  - Años de 1975 a 2026, con más títulos recientes.
  - 1 a 3 géneros, de 12 de ficción y 6 de documental.
  - Clasificación coherente con el género: animación y familiar de 0 a 12, terror 16 o 18.
  - Países y algunos idiomas de audio y de subtítulos.
- **Afiche:** el del primer género (`/images/posters/genre-*.svg`, 18 afiches propios).
- **Identificadores estables:** `_id` y `episodeId` dependen solo de la posición del contenido. Se puede volver a cargar sin romper las sesiones ni las listas que apuntan a ellos.

### 5.3 Sesiones de reproducción (`02`)

Para cada perfil exportado, y dentro de cada una de sus suscripciones:

- **Cuántas:** entre 0,25 y 0,55 sesiones por día de suscripción, según el perfil, siempre de noche.
- **Qué contenido:** solo publicado y con clasificación permitida para el perfil.
  - Un peso fijo por título hace que unos pocos concentren muchas vistas: el 10 % más visto reúne ≈ 45 %.
  - En las series se elige un episodio.
- **Cómo termina:** ≈ 50 % se ve completa (`finished`), ≈ 30 % queda a medias (aparece en "Continuar viendo") y ≈ 20 % se abandona al poco tiempo.
- **Plan:** `planIdAtStart` es el plan de la suscripción vigente cuando empieza la sesión.
- **Orden:** las sesiones de un mismo perfil no se superponen y todas terminan antes de ahora.

### 5.4 Mi lista (`04`)

≈ 60 % de los perfiles exportados tiene lista, de 1 a 12 títulos distintos. Solo incluye contenidos publicados y permitidos, y los populares aparecen con más probabilidad. Las listas que un perfil armó desde la aplicación (sin `synthetic: true`) no se tocan.

---

## 6. Reglas que respetan

Los datos pasan por las mismas restricciones que la aplicación. Si un script intentara violar una, la base lo rechazaría y el script se detendría.

| Regla | Dónde se garantiza |
|---|---|
| Suscripción de 30 días exactos | `CHECK` de `subscriptions` |
| Sin períodos superpuestos por cuenta | `EXCLUDE` de `subscriptions` |
| Exactamente un pago por suscripción | `UNIQUE` + trigger diferido (pago y suscripción en la misma transacción) |
| Ningún plan con menos perfiles que los activos (RI-04) | Lógica de `08` |
| Ninguna suscripción antes del registro de la cuenta | Lógica de `08` (y corrección de las cuentas demo en `01`) |
| Documentos válidos por tipo y estado | Validador `$jsonSchema` de cada colección |
| Título + año únicos; `episodeId` único | Índices únicos de `contents` |
| Sesión dentro de una suscripción, con su plan, sobre contenido publicado y permitido | Lógica de `02`, con los datos exportados por `07` |
| Lista sin repetidos, con contenido publicado y permitido | Validador (`uniqueItems`) y lógica de `04` |

Antes de subirlos, los scripts se probaron en un PostgreSQL limpio con los scripts del repositorio. En MongoDB se probaron con un simulador que ejecuta los scripts y valida cada documento contra el validador real de `01_collections.js` y los índices únicos. Se revisaron 205 000 documentos y se encontraron 0 incumplimientos de las reglas de la tabla.

---

## 7. Determinismo e idempotencia

- **Deterministas:**
  - Los generadores usan una semilla por cuenta, por contenido o por perfil, no `Math.random()`.
  - Con el mismo tamaño, dos personas obtienen las mismas cuentas, el mismo catálogo y las mismas listas.
  - Las fechas son relativas al momento de la carga ("vigente hoy", "hace 30 días"), así que las sesiones cambian unos segundos entre una carga y otra.
- **Idempotentes:** se pueden volver a correr.
  - `06` y `08` no duplican: solo agregan lo que falta.
  - `01` y `03` reescriben los mismos documentos.
  - `02` y `04` borran y regeneran solo lo marcado con `synthetic: true`.
- **Nada de la aplicación se pierde:** las cuentas registradas, las sesiones reales y las listas armadas desde la aplicación no se tocan.

---

## 8. Cómo comprobar lo cargado

**PostgreSQL** (por ejemplo, desde pgAdmin, ver `docs/guia_pgadmin_compass.md`):

```sql
SET search_path = cineconecta;
SELECT COUNT(*) FROM accounts;                                   -- ≈ 20 010
SELECT COUNT(*) FROM subscriptions WHERE now() >= startsat AND now() < endsat;   -- vigentes hoy
SELECT pl.name, COUNT(*), SUM(p.amount)
FROM payments p JOIN subscriptions s USING (subscriptionid) JOIN plans pl USING (planid)
GROUP BY pl.name;                                                -- lo que muestra el informe de ingresos
```

**MongoDB** (Compass o `mongosh`):

```js
db.contents.countDocuments({ synthetic: true })                  // 150
db.playback_sessions.countDocuments({ synthetic: true })         // ≈ 200 000
db.my_lists.countDocuments({ synthetic: true })                  // ≈ 2 000
```

**En la aplicación:**
- Inicia sesión con `vol.000023@volumen.cineconecta.local` / `Demo123*`. Tiene Premium vigente, varios meses de historial y 5 perfiles, de 0 a 18 años; elige "Perfil 2" (18).
- El catálogo, "Continuar viendo", las marcas de visto y "Mi lista" ya tienen datos.
- Los informes de ingresos y de consumo del administrador muestran meses de historial (usa un rango amplio de fechas).

---

## 9. Mediciones de índices

Con este volumen se pueden hacer las mediciones antes y después de MongoDB que pide RNF-17 (`docs/04_indices_y_mediciones.md`, secciones 2 y 3).

- **`contents`:** búsqueda por estado, tipo, género, idioma y clasificación (`explain("executionStats")`).
- **`playback_sessions`:** "Continuar viendo" por perfil y la agregación del informe de consumo por fecha de inicio.

Para que el efecto de un índice se note más, se puede cargar el tamaño grande.

---

## 10. Cómo quitar los datos

Para volver a empezar, lo más simple es `docker compose down -v`, que borra las dos bases. Si solo quieres quitar el volumen:

```sql
-- PostgreSQL (como cine). Primero pagos y suscripciones, después perfiles y cuentas.
SET search_path = cineconecta;
DELETE FROM payments WHERE reference LIKE 'VOL-%';
DELETE FROM subscriptions s USING accounts a WHERE a.accountid = s.accountid AND LOWER(a.email) LIKE '%@volumen.cineconecta.local';
DELETE FROM profiles p USING accounts a WHERE a.accountid = p.accountid AND LOWER(a.email) LIKE '%@volumen.cineconecta.local';
DELETE FROM accounts WHERE LOWER(email) LIKE '%@volumen.cineconecta.local';
```

```js
// MongoDB (como cineOwner)
db.playback_sessions.deleteMany({ synthetic: true })
db.my_lists.deleteMany({ synthetic: true })
db.contents.deleteMany({ synthetic: true })
```

Borrar solo una de las dos bases deja identificadores huérfanos (ver la sección 9 de `docs/03_integracion_y_fallos.md`). Si quitas el volumen de PostgreSQL, quita también las sesiones y listas sintéticas de MongoDB.

La aplicación no puede borrar nada. Estos comandos solo se pueden ejecutar con los usuarios dueños: `cine` o `cine_owner` en PostgreSQL y `cineOwner` en MongoDB.

---

## 11. Limitaciones

- **Fechas relativas:** las fechas dependen del momento de la carga. Si pasan semanas, las suscripciones "vigentes" vencen, como pasaría en la realidad. Para volver a tenerlas vigentes, se recrean las bases con `docker compose down -v` y se vuelve a cargar.
- **Muestra de perfiles:** solo los perfiles de la muestra de `07` tienen sesiones y listas. El resto de cuentas de volumen tiene suscripciones, pero no actividad.
- **Contenidos sobrantes:** `03` nunca borra contenidos. Si se carga con un `CONTENIDOS` menor que antes, los de más se quedan, porque los contenidos no se borran.
- **Popularidad aproximada:** el peso de cada título sale de un número fijo derivado de su ID, no de un ranking definido a mano.
