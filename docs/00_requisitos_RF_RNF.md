# CineConecta — Requerimientos funcionales y no funcionales

> Fuentes: el enunciado del proyecto y el documento de requerimientos acordado por el equipo (RF y RI).
> Los RNF se derivan del enunciado ("Requisitos de implementación", "Evidencias y entrega").
>
> **Estado verificado contra el código de `develop` el 27 de noviembre de 2026.** Cada integrante actualiza la columna *Estado* de sus requerimientos en el mismo PR en que los implementa.
>
> Estados: ✅ implementado · 🟡 parcial · ⏳ pendiente

## 0. Actores y responsables

| Actor | Descripción |
|---|---|
| **Titular** | Dueño de la cuenta de cliente. Se registra, contrata suscripciones y administra perfiles. |
| **Perfil** | Quien ve contenido dentro de una cuenta, con una clasificación máxima. |
| **Administrador** | Usuario interno (en MongoDB) que gestiona el catálogo y los planes y consulta los informes. No tiene suscripción ni perfiles. |
| **Sistema** | Procesos automáticos, por ejemplo el cierre de sesiones de reproducción abandonadas. |

| Integrante | Módulo de la aplicación |
|---|---|
| P1 | `accounts` + `shared` (seguridad base, layout) |
| P2 | `billing` |
| P3 | `catalog` |
| P4 | `viewing` |
| P5 | `backoffice` + `reporting` |

---

## 1. Requerimientos funcionales

### 1.1 Cuentas y perfiles

| RF | Requerimiento | Actor | Ruta | RI | Resp. | Estado | Notas |
|---|---|---|---|---|---|---|---|
| RF-01 | Registrar cuenta | Titular | `GET/POST /register` | 01 | P1 | ✅ | Correo único sin distinguir mayúsculas; contraseña con BCrypt (8 a 72 caracteres). Tras registrarse inicia sesión y va a los planes. |
| RF-02 | Iniciar sesión (cliente) | Titular | `GET/POST /login` | 01 | P1 | ✅ | Emite un JWT en una cookie `HttpOnly`. El mensaje de error no revela si el correo existe. Vuelve a la página pedida (solo rutas locales). |
| RF-03 | Cerrar sesión (cliente) | Titular | `POST /logout` | — | P1 | ✅ | Borra la cookie. |
| RF-04 | Consultar historial de suscripciones y pagos | Titular | `GET /billing/history` | 03, 04, 05 | P2 | ✅ | |
| RF-05 | Crear perfil (requiere suscripción vigente y cupo en el plan) | Titular | `POST /profiles` | 02, 03, 04 | P1 | ✅ | Bloqueo por cuenta: no se supera el máximo del plan con peticiones simultáneas. |
| RF-06 | Editar nombre y clasificación | Titular | `GET /profiles/{id}/edit`, `POST /profiles/{id}` | 02 | P1 | ✅ | Nombre único en la cuenta, sin distinguir mayúsculas; clasificación de 0 a 18. |
| RF-07 | Desactivar o reactivar perfil | Titular | `POST /profiles/{id}/deactivate`, `.../reactivate` | 02, 03, 04 | P1 | ✅ | Desactivar conserva el historial. Reactivar vuelve a exigir suscripción y cupo. |
| RF-08 | Listar perfiles de la cuenta | Titular | `GET /profiles` | 02 | P1 | ✅ | Muestra activos, inactivos y el cupo del plan vigente. |
| RF-09 | Seleccionar perfil activo | Perfil | `POST /profiles/{id}/select` | 02 | P1 | ✅ | Solo perfiles activos de la propia cuenta. En cada petición se vuelve a verificar en PostgreSQL. |

### 1.2 Suscripciones y planes

| RF | Requerimiento | Actor | Ruta | RI | Resp. | Estado | Notas |
|---|---|---|---|---|---|---|---|
| RF-10 | Listar planes activos | Titular | `GET /billing/plans` | 03 | P2 | ✅ | Pública. |
| RF-11 | Contratar suscripción con pago simulado (una transacción; solo planes activos) | Titular | `GET /billing/checkout/{planId}`, `POST /billing/checkout` | 01–05 | P2 | ✅ | Pago aprobado o rechazado. Pago y suscripción en una única transacción; contrataciones simultáneas serializadas por cuenta. |
| RF-12.1 | Listar planes con su estado y número de suscripciones vigentes | Admin | `GET /admin/plans` | 03, 04 | P2 | ✅ | `PlanApi.listPlansForAdmin`; pantalla en billing, protegida por la cadena de administradores. |
| RF-12.2 | Crear plan (nombre, precio y máximo de perfiles) | Admin | `POST /admin/plans` | 03 | P2 | ✅ | Nombre único sin distinguir mayúsculas, hasta 60 caracteres; precio > 0; perfiles entre 1 y 10. |
| RF-12.3 | Desactivar o reactivar plan | Admin | `POST /admin/plans/{id}/deactivate`, `.../activate` | 03 | P2 | ✅ | Un plan inactivo deja de ofrecerse; las suscripciones vigentes siguen hasta vencer. |
| RF-12.4 | Editar el nombre de un plan | Admin | `POST /admin/plans/{id}/rename` | 03 | P2 | ✅ | Precio y perfiles no se editan (`cine_app` solo tiene `UPDATE (name, isActive)`). |

### 1.3 Catálogo

| RF | Requerimiento | Actor | Ruta | RI | Resp. | Estado | Notas |
|---|---|---|---|---|---|---|---|
| RF-13 | Registrar contenido | Admin | `GET /admin/contents/new`, `POST /admin/contents/{movies,series,documentaries}` | 06 | P3 | ✅ | Película, serie o documental; queda en borrador. |
| RF-14 | Actualizar contenido (si no está retirado) | Admin | `GET /admin/contents/{id}/edit`, `POST /admin/contents/{id}/{tipo}` | 06 | P3 | ✅ | |
| RF-15 | Gestionar temporadas (añadir y actualizar) | Admin | `/admin/series/{id}/seasons...` | 07 | P3 | ✅ | |
| RF-16 | Gestionar episodios (añadir y actualizar) | Admin | `/admin/series/{id}/seasons/{n}/episodes...` | 07 | P3 | ✅ | |
| RF-17 | Publicar contenido | Admin | `POST /admin/contents/{id}/publish` | 06, 07 | P3 | ✅ | Valida los datos obligatorios del tipo. |
| RF-18 | Retirar contenido | Admin | `POST /admin/contents/{id}/retire` | 06 | P3 | ✅ | No se borra. También existe `.../restore`. |
| RF-19 | Listar contenidos con filtros | Admin | `GET /admin/contents?status=&type=` | 06 | P3 | ✅ | Filtros por estado y tipo; 20 por página. |
| RF-20 | Buscar y filtrar | Perfil | `GET /catalog` | 02, 04, 06 | P3 | ✅ | Título, tipo, género, idioma y año; paginado. Solo contenido publicado con clasificación permitida. Exige perfil activo y suscripción vigente. |
| RF-21 | Ver detalle de un contenido | Perfil | `GET /catalog/{id}` | 02, 04, 06 | P3 | ✅ | |
| RF-22 | Ver temporadas y episodios | Perfil | `GET /catalog/{id}` | 02, 04, 06, 07 | P3 | ✅ | En el detalle de una serie. |

### 1.4 Mi lista y reproducción

| RF | Requerimiento | Actor | Ruta | RI | Resp. | Estado | Notas |
|---|---|---|---|---|---|---|---|
| RF-23 | Agregar a Mi lista | Perfil | `POST /viewing/my-list` | 02, 04, 06, 08 | P4 | 🟡 | Funciona sin duplicados, pero se agrega escribiendo el id del contenido. **Falta el botón "Agregar a Mi lista" en el detalle del catálogo.** |
| RF-24 | Quitar de Mi lista | Perfil | `POST /viewing/my-list/remove` | 08 | P4 | ✅ | |
| RF-25 | Ver Mi lista | Perfil | `GET /viewing/my-list` | 06, 08 | P4 | ✅ | Marca los contenidos retirados. |
| RF-26 | Iniciar reproducción | Perfil | `POST /viewing/player` | 02, 04, 06, 07, 09, 10 | P4 | 🟡 | Verifica cuenta, perfil activo, suscripción, contenido publicado y clasificación (ambas bases). **Falta el botón "Reproducir" en el detalle del catálogo**; hoy se escribe el id del contenido o del episodio. |
| RF-27 | Pausar, reanudar y avanzar (no priorizar) | Perfil | `POST /viewing/player/{sesión}` | 09, 10 | P4 | ✅ | Acciones `PAUSE`, `RESUME`, `SEEK` y `CHECKPOINT`. El tiempo visto excluye los saltos. |
| RF-28 | Finalizar reproducción | Perfil | `POST /viewing/player/{sesión}` (`FINISH`) | 09, 10 | P4 | ✅ | Completada al finalizar o al alcanzar el 90 %. |
| RF-29 | Ver "Continuar viendo" | Perfil | `GET /viewing/continue-watching` | 06, 10 | P4 | ✅ | Ordenado por actividad reciente; excluye los retirados. |
| RF-30 | Cerrar sesiones abandonadas (proceso del sistema) | Sistema | Tarea programada cada 30 s | 09 | P4 | ✅ | `AbandonedPlaybackCleanup`. |

### 1.5 Administración e informes

| RF | Requerimiento | Actor | Ruta | RI | Resp. | Estado | Notas |
|---|---|---|---|---|---|---|---|
| RF-31 | Iniciar sesión (admin) | Admin | `GET/POST /admin/login` | 11 | P5 | ✅ | Usuarios en MongoDB (`admin_users`) con BCrypt. |
| RF-32 | Cerrar sesión (admin) | Admin | `POST /admin/logout` | — | P5 | ✅ | |
| RF-33 | Ingresos por plan | Admin | `GET /admin/reports/revenue` | 12 | P5 | ✅ | Filtro de fechas. Lee PostgreSQL con `cine_report`. |
| RF-34 | Consumo por plan y tipo | Admin | `GET /admin/reports/consumption` | 13 | P5 | ✅ | Combina MongoDB (sesiones) y PostgreSQL (planes); plan vigente al iniciar cada sesión. |
| RF-35 | Rendimiento del catálogo por género, tipo y período | Admin | — | 14 | P5 | ⏳ | |
| RF-36 | Finalización y abandono de contenidos | Admin | — | 15 | P5 | ⏳ | |
| RF-37 | Contenidos de bajo desempeño | Admin | — | 16 | P5 | ⏳ | |
| RF-38 | Consumo por audiencia (clasificación y plan) | Admin | — | 17 | P5 | ⏳ | |
| RF-39 | Exportar informes y analítica a CSV | Admin | `.../revenue.csv`, `.../consumption.csv` | 12–17 | P5 | 🟡 | Hecho para RF-33 y RF-34 (UTF-8 con BOM, para que Excel muestre bien las tildes). Faltan los de RF-35 a RF-38. |

### 1.6 Resumen

| Estado | RF |
|---|---|
| ✅ Implementado | 35: RF-01 a RF-22, RF-24, RF-25, RF-27 a RF-34 |
| 🟡 Parcial | 3: RF-23 y RF-26 (falta botón en el catálogo), RF-39 |
| ⏳ Pendiente | 4: RF-35 a RF-38 |

**Lo que exige la sustentación ya funciona:** el flujo completo de contratación y visualización (RF-01, 02, 05, 09, 11, 20, 26, 28), los dos informes obligatorios (RF-33 y RF-34) y los rechazos por suscripción vencida y por clasificación no permitida. Para que el flujo de visualización no dependa de escribir ids a mano, lo más urgente es el punto pendiente de RF-23 y RF-26.

---

## 2. Requerimientos de información (dónde vive cada dato)

El detalle de cada RI (datos, origen y restricciones) está en el documento de requerimientos del equipo. Aquí se resume en qué base persiste cada uno, porque esa es la decisión de persistencia políglota.

| RI | Información | Base | Tabla / colección | Restricciones clave y dónde se garantizan |
|---|---|---|---|---|
| RI-01 | Cuentas | PostgreSQL | `accounts` | Correo único sin distinguir mayúsculas (`ux_accounts_email_ci`); solo se guarda el hash. |
| RI-02 | Perfiles | PostgreSQL | `profiles` | Nombre único por cuenta (`ux_profiles_name_ci`); clasificación 0–18 (`CHECK`); se desactivan, no se borran. |
| RI-03 | Planes | PostgreSQL | `plans` | Nombre único; precio y máximo de perfiles no se modifican (privilegios); `isActive` añadido. |
| RI-04 | Suscripciones | PostgreSQL | `subscriptions` | 30 días (`CHECK`); sin períodos superpuestos (`EXCLUDE`); vigencia `[inicio, fin)` calculada. |
| RI-05 | Pagos | PostgreSQL | `payments` | Exactamente uno por suscripción (`UNIQUE` + trigger diferido); referencia única. |
| RI-06 | Contenidos | MongoDB | `contents` | Documento polimórfico por tipo, validado con `$jsonSchema`; `schemaVersion` para evolucionar; se retiran, no se borran. |
| RI-07 | Temporadas y episodios | MongoDB | `contents` (embebidos en la serie) | Números únicos dentro de la serie o la temporada; id de episodio estable. |
| RI-08 | Mi lista | MongoDB | `my_lists` | Un documento por perfil (`_id = profileId`); sin duplicados. |
| RI-09 | Sesiones de reproducción | MongoDB | `playback_sessions` | Historial inmutable al cerrarse; guarda `planIdAtStart`; el tiempo visto lo calcula el servidor. |
| RI-10 | Progreso actual | MongoDB | `playback_sessions` | Calculado a partir de las sesiones. |
| RI-11 | Administradores | MongoDB | `admin_users` | Correo único; la aplicación solo puede leerlos (se crean en la carga inicial). |
| RI-12 a RI-17 | Informes | — (no se almacenan) | Calculados | RI-12 desde PostgreSQL; RI-13 y siguientes combinan ambas bases. |

Los identificadores que cruzan bases (`accountId`, `profileId`, `planIdAtStart`, `contentId`, `episodeId`) y cómo se verifican están en `docs/data-model.md`, sección 3.

---

## 3. Requerimientos no funcionales

### 3.1 Persistencia e integridad de datos

| RNF | Requerimiento | Cómo se cumple | Evidencia | Estado |
|---|---|---|---|---|
| RNF-01 | **Persistencia políglota con una fuente de verdad por dato.** Cuentas, perfiles, planes, suscripciones y pagos en la base relacional; catálogo, listas, actividad y administradores en MongoDB. | PostgreSQL 17 (esquema `cineconecta`) y MongoDB 8. Cada módulo es dueño de sus datos y los demás solo usan su API pública. | `db/postgres/`, `db/mongo/init/`, `docs/data-model.md` | ✅ |
| RNF-02 | **Se conserva el modelo relacional suministrado**; los cambios estructurales se justifican. | Tablas, columnas, longitudes, claves e índices únicos del anexo. Único cambio estructural: `plans.isActive`. | `docs/02_catalogo_objetos_postgres.md` §2 | ✅ |
| RNF-03 | **Pago y suscripción en una única transacción**: si una operación falla, ninguna queda registrada. | Transacción única en billing + trigger diferido que rechaza, al confirmar, una suscripción sin pago. | `BillingTransactionIT` (fallo a mitad de la transacción; trigger) | ✅ |
| RNF-04 | **Sin períodos superpuestos, ni siquiera con solicitudes simultáneas**, y sin superar el máximo de perfiles. | `pg_advisory_xact_lock(accountId)`, compartido por contratación y perfiles, + restricción `EXCLUDE` como segunda barrera. | `BillingTransactionIT` (dos contrataciones simultáneas), `ProfileRulesIT` (dos creaciones simultáneas) | ✅ |
| RNF-05 | **Nada se borra y el historial no se modifica.** Perfiles, planes y contenidos se desactivan o retiran; las sesiones cerradas no cambian; los ids no se reutilizan. | Privilegios: `cine_app` sin `DELETE` y con solo `INSERT` en el historial. Los contenidos se retiran por estado. Secuencias IDENTITY. | `db/postgres/02_grants.sql`, `db/mongo/init/03_users.js` | ✅ |
| RNF-06 | **Referencias entre bases verificadas por la aplicación**, sin suponer que una transacción relacional confirma o revierte escrituras en MongoDB. | Ids enteros de PostgreSQL copiados en MongoDB y verificados antes de escribir (perfil activo y de la cuenta, suscripción vigente, contenido publicado y clasificación). | `ViewingAccess`, `ProfileViewerContext`, `docs/data-model.md` §3 | 🟡 Falta el documento de integración con la matriz de fallos (`docs/03_integracion_y_fallos.md`, P5). |
| RNF-07 | **Comportamiento definido si una base no está disponible o una operación se interrumpe.** | Hoy se muestra una página de error genérica (500) sin detalles internos, y la transacción relacional se revierte. | `templates/error.html` | 🟡 Falta responder 503 ("servicio no disponible") y documentar cada caso. |
| RNF-08 | **El catálogo admite nuevas características sin afectar los contenidos existentes** y valida los datos obligatorios de cada tipo. | Documento polimórfico con validador `$jsonSchema` por tipo y `schemaVersion`. | `db/mongo/init/01_collections.js`, `catalog-schema.js` | ✅ |
| RNF-09 | **Fechas consistentes entre bases.** | Instantes en UTC (`TIMESTAMPTZ` en PostgreSQL, `Date` en MongoDB); la interfaz muestra la hora de Colombia. Vigencia `[inicio, fin)` evaluada con un único reloj (el de la aplicación). | `db/README.md` §1, `Clock` en `SharedConfig` | ✅ |

### 3.2 Seguridad

| RNF | Requerimiento | Cómo se cumple | Evidencia | Estado |
|---|---|---|---|---|
| RNF-10 | **Contraseñas con hash seguro**, para clientes y administradores. | BCrypt. Se rechazan contraseñas de más de 72 bytes en lugar de truncarlas. | `AccountServiceTest`, `AdminUserDetailsServiceTest` | ✅ |
| RNF-11 | **Sesión de cliente segura.** | JWT HS256 en una cookie `HttpOnly` y `SameSite=Lax` (`Secure` con HTTPS), con vencimiento de 60 minutos. CSRF activo en formularios y en htmx. Sin sesión HTTP en el servidor. | `JwtTokenServiceTest`, `AuthCookiesTest`, `CustomerSecurityWebMvcTest` | ✅ |
| RNF-12 | **Cada usuario solo accede a los perfiles y la actividad de su propia cuenta.** | La cuenta sale siempre del token, nunca de un parámetro. El perfil en uso se verifica en PostgreSQL en cada petición (activo y de la cuenta). Un perfil ajeno responde 404. | `ProfileWebMvcTest`, `ProfileRulesIT`, `ViewingCurrentProfileWebMvcTest` | ✅ |
| RNF-13 | **Autorización de administrador verificada en el servidor** para cada operación administrativa; administradores independientes de los clientes. | Cadena de seguridad propia en `/admin/**` (rol `ADMIN`), separada de la de clientes. Un token de cliente no abre el panel, y uno de administrador no sirve como cliente. | `AdminSecurityWebMvcTest`, `SecurityChainsCoexistenceWebMvcTest` | ✅ |
| RNF-14 | **Mínimo privilegio en las bases.** | PostgreSQL: `cine_owner` (DDL), `cine_app` (sin `DELETE`, `UPDATE` por columna), `cine_report` (solo lectura, usado por el informe de ingresos). MongoDB: rol `cineAppRole` por colección; `admin_users` de solo lectura para la aplicación. | `02_grants.sql`, `03_users.js`, `ReportingConnectionIT` | ✅ |
| RNF-15 | **Sin credenciales en el repositorio** fuera de las de desarrollo. | Conexiones y secreto JWT por variables de entorno, con valores por defecto solo para desarrollo. `.env` y `config/` en `.gitignore`. | `application.properties`, `.gitignore` | ✅ |

### 3.3 Rendimiento

| RNF | Requerimiento | Cómo se cumple | Evidencia | Estado |
|---|---|---|---|---|
| RNF-16 | **Índices seleccionados y justificados para las consultas principales de ambos motores.** | PostgreSQL: índices de login, perfiles, vigencia, historial e ingresos. MongoDB: índices de búsqueda y filtros del catálogo y de "Continuar viendo". | `docs/02_catalogo_objetos_postgres.md` §4, `db/mongo/init/02_indexes.js` | ✅ |
| RNF-17 | **Medición antes y después de optimizar una consulta en cada motor** (datos de prueba, plan de ejecución, tiempos). | PostgreSQL: experimentos con `EXPLAIN (ANALYZE, BUFFERS)` y volumen declarado. | `docs/04_indices_y_mediciones.md` §1, `db/demos/postgres/04_index_experiment.sql` | 🟡 Faltan las mediciones de MongoDB (§2 y §3, P3 y P4). |
| RNF-18 | **Resultados de búsqueda paginados.** | Catálogo de clientes con tamaño de página acotado; lista de administración con 20 por página. | `CatalogSearchService`, `ContentAdminController` | ✅ |
| RNF-19 | **Datos sintéticos con volumen para las mediciones**, indicando el tamaño. | Scripts deterministas con N configurable que informan el tamaño resultante. | `db/demos/postgres/05_synthetic_accounts.sql`, `06_volume_accounts.sql` | ✅ PostgreSQL · 🟡 el seed de MongoDB depende de P5 |

### 3.4 Usabilidad

| RNF | Requerimiento | Cómo se cumple | Evidencia | Estado |
|---|---|---|---|---|
| RNF-20 | **Interfaz en español y navegación coherente.** | Layout compartido (cabecera, barra y pie) en cuentas, perfiles, billing, catálogo y errores. | `templates/shared/layout.html` | 🟡 Las pantallas de viewing (`/viewing/*`) aún usan su propia cabecera. |
| RNF-21 | **Interacciones parciales sin recargar la página.** | htmx 2 (servido desde el propio servidor) en la búsqueda y los listados; animaciones CSS que respetan "reducir movimiento". | `catalog/search.html`, `static/js/app.js` | ✅ |
| RNF-22 | **Mensajes de error claros y sin detalles internos.** | Validaciones por campo; mensajes de reglas de negocio pensados para el usuario; página de error sin trazas. | `SharedLayoutWebMvcTest` | ✅ |

### 3.5 Mantenibilidad, pruebas y despliegue

| RNF | Requerimiento | Cómo se cumple | Evidencia | Estado |
|---|---|---|---|---|
| RNF-23 | **Arquitectura modular verificada.** | Spring Modulith: cada módulo expone solo su API pública y no hay ciclos. Una prueba hace fallar el build si alguien rompe una frontera. | `PlatformApplicationTests` (`ApplicationModules.verify()`) | ✅ |
| RNF-24 | **Pruebas automatizadas de las reglas críticas**, incluidas las que se demuestran en la sustentación. | Pruebas unitarias y de controladores, y pruebas de integración con Testcontainers (PostgreSQL y MongoDB reales). | `src/test` (`*Test` y `*IT`) | ✅ |
| RNF-25 | **Integración continua.** | Planificado: GitHub Actions con pruebas unitarias en cada PR y de integración. | — | ⏳ No existe `.github/workflows` (P2). |
| RNF-26 | **Ejecución local reproducible.** | Java 25, Spring Boot 4.1, Docker Compose con las dos bases, scripts versionados y datos de demostración. | `docker-compose.yaml`, `README.md`, `db/README.md` | ✅ |
| RNF-27 | **Control de versiones con flujo acordado.** | Ramas cortas desde `develop`, PR con revisión y mensajes Conventional Commits. | Historial del repositorio | ✅ |

### 3.6 Resumen de RNF

| Estado | RNF |
|---|---|
| ✅ Implementado | 21 |
| 🟡 Parcial | 5: RNF-06, 07, 17, 19 (MongoDB), 20 |
| ⏳ Pendiente | 1: RNF-25 |

---

## 4. Pendientes priorizados

| # | Pendiente | Requerimientos | Resp. |
|---|---|---|---|
| 1 | Botones "Agregar a Mi lista" y "Reproducir" en el detalle del catálogo (con el episodio, en las series) | RF-23, RF-26 | P3 + P4 |
| 2 | ~~Integrar la gestión de planes del administrador, con pantalla~~ ✅ | RF-12.1 a RF-12.4 | P2 |
| 3 | Documento de integración y matriz de fallos; respuesta 503 si una base no está disponible | RNF-06, RNF-07 | P5 (+ P1 para la página 503) |
| 4 | Mediciones de índices en MongoDB (antes y después) | RNF-17 | P3, P4 |
| 5 | Pantallas de viewing con el layout compartido | RNF-20 | P4 |
| 6 | Informes de analítica y sus CSV | RF-35 a RF-38, RF-39 | P5 |
| 7 | GitHub Actions | RNF-25 | P2 |
