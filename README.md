# CineConecta

Plataforma web de streaming con **persistencia políglota**: PostgreSQL para cuentas, perfiles, planes, suscripciones y pagos, y MongoDB para el catálogo, la reproducción, "Mi lista" y los administradores.

Proyecto final de Sistemas Intensivos en Datos II, Universidad Icesi.

| Integrante | Módulo |
|---|---|
| Isabella Candado | `accounts` (registro, sesión, perfiles) y `shared` |
| Juan Felipe Correa | `billing` (planes, suscripciones, pagos) |
| Alejandro Murillo | `catalog` (catálogo y su administración) |
| Juan Pablo Cubillos | `viewing` (reproducción, continuar viendo, Mi lista) |
| Jhostin Wiesner | `backoffice`, `reporting`, scripts de bases de datos e integración |

## Stack

- Java 25, Spring Boot 4.1, Spring Modulith, Spring Security 7, Thymeleaf + htmx
- PostgreSQL 17 (Spring Data JPA) y MongoDB 8 (Spring Data MongoDB)
- Docker Compose para las bases en local

## Estructura

```
src/main/java/com/cineconecta/platform/
  accounts/  billing/  catalog/  viewing/  backoffice/  reporting/  shared/
db/
  postgres/          scripts de creación (se ejecutan al crear el contenedor)
  mongo/init/        colecciones, validadores, índices, usuarios y admin inicial
  demos/load_all.sh  carga todos los datos de prueba en orden
  demos/postgres/    datos de demostración y de volumen; demos de concurrencia, rollback e índices
  demos/mongo/       catálogo, sesiones y Mi lista de demostración y de volumen
docs/
  00_requisitos_RF_RNF.md       requerimientos y su estado
  02_catalogo_objetos_postgres.md  objetos de PostgreSQL
  03_integracion_y_fallos.md    integración entre las bases y matriz de fallos
  04_indices_y_mediciones.md    experimentos de índices
  05_datos_de_prueba.md         seed: qué genera, reglas, tamaño y cómo cargarlo
  data-model.md                 diccionario de datos de ambas bases
  guia_pgadmin_compass.md       ver las bases con pgAdmin y MongoDB Compass
```

El detalle de los scripts, las reglas que garantiza cada base y el despliegue en la nube está en [`db/README.md`](db/README.md).

## Cómo ejecutarlo en local

### Requisitos

- **JDK 25.** No hace falta instalar Maven: se usa `./mvnw` (en Windows, `mvnw.cmd`).
- **Docker Desktop**, que debe estar abierto, para las dos bases.
- **bash.** En Windows, Git Bash, para el script que carga los datos de prueba. PowerShell no sirve para ese script.

### Paso a paso (con todos los datos)

**1. Puertos (solo si 5432 o 27017 ya están ocupados).** Por ejemplo, por un PostgreSQL o MongoDB instalado en Windows. En ese caso, crea un `.env` en la raíz:

```
POSTGRES_PORT=5434
MONGO_PORT=27018
```

Y un `config/application.properties` (está en `.gitignore`) con las mismas conexiones:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5434/cineconecta?currentSchema=cineconecta
spring.datasource.username=cine_app
spring.datasource.password=cine_app_dev
spring.mongodb.uri=mongodb://cineApp:cine_app_dev@localhost:27018/cineconecta?authSource=cineconecta
```

> No definas `POSTGRES_PASSWORD` en el `.env`: el compose la usa para el superusuario `cine`, y la aplicación la tomaría como contraseña de `cine_app`.

**2. Bases de datos, empezando de cero**

```bash
docker compose down -v      # borra los datos anteriores de las dos bases (omítelo la primera vez)
docker compose up -d
```

Al crearse, cada contenedor ejecuta los scripts de `db/` (esquema, usuarios, validadores, índices, planes y administrador). **Esos scripts solo corren con los volúmenes vacíos**: si alguien cambia algo en `db/`, repite este paso.

**3. Datos de prueba** (bash o Git Bash, desde la raíz)

```bash
./db/demos/load_all.sh
```

Carga, en orden:
- las cuentas de demostración;
- 20 000 cuentas de volumen con su historial de suscripciones y pagos;
- un catálogo de 150 contenidos generados;
- ≈ 200 000 sesiones de reproducción y "Mi lista".

Tarda menos de un minuto y se puede repetir. El tamaño se cambia con variables, y el detalle está en [`docs/05_datos_de_prueba.md`](docs/05_datos_de_prueba.md).

**4. Aplicación**

```bash
./mvnw spring-boot:run      # Windows: mvnw.cmd spring-boot:run
```

Abre http://localhost:8080 para los clientes y http://localhost:8080/admin para la administración. Las credenciales están más abajo.

| Pantalla | Ruta |
|---|---|
| Registro e inicio de sesión de clientes | `/register`, `/login` |
| Inicio, con "Continuar viendo" y novedades del catálogo | `/` |
| Perfiles (crear, editar, elegir) | `/profiles` |
| Catálogo (filtrado por la clasificación del perfil en uso) y detalle, con Reproducir y Mi lista | `/catalog`, `/catalog/{id}` |
| Planes, contratación e historial | `/billing/plans`, `/billing/history` |
| Reproductor, continuar viendo y Mi lista | `/viewing/player/...`, `/viewing/continue-watching`, `/viewing/my-list` |
| Administración: catálogo y planes | `/admin/contents`, `/admin/plans` |
| Administración: informes de ingresos y de consumo por plan | `/admin/reports/revenue`, `/admin/reports/consumption` |

### Problemas frecuentes

| Síntoma | Causa y solución |
|---|---|
| La aplicación no arranca y menciona `url` o `dialect` | No encuentra PostgreSQL. Revisa que Docker esté abierto y que los puertos de `config/application.properties` coincidan con el `.env`. |
| Página 503, "Servicio no disponible" | Una de las bases está detenida. Revisa con `docker compose ps` y levántala con `docker compose up -d`. |
| `load_all.sh` falla con un error de validación de MongoDB | Las bases se crearon con scripts viejos de `db/`. Repite el paso 2 (`down -v` y `up -d`). |
| Las suscripciones de prueba aparecen vencidas | Las fechas se calculan al cargar los datos. Repite los pasos 2 y 3. |

### Pruebas

```bash
./mvnw test      # unitarias y de controladores
./mvnw verify    # además, las de integración (*IT), con PostgreSQL y MongoDB reales en Testcontainers
```

`verify` necesita Docker abierto.

## Ver las bases con pgAdmin y Compass

Para mirar los datos mientras se usa la aplicación (y cómo saber qué puertos usa cada PC): [`docs/guia_pgadmin_compass.md`](docs/guia_pgadmin_compass.md).

## Credenciales (solo desarrollo)

Todas estas contraseñas son de desarrollo. En la nube se cambian (ver `db/README.md`) y nunca se suben al repositorio.

### Usuarios de las bases de datos

**PostgreSQL**: base `cineconecta`, esquema `cineconecta`.

| Usuario | Contraseña | Uso | Definido en |
|---|---|---|---|
| `cine` | `cine` | Superusuario del contenedor; ejecuta los scripts | `docker-compose.yaml` |
| `cine_owner` | `cine_owner_dev` | Dueño del esquema y sus objetos | `db/postgres/00_roles.sql` |
| `cine_app` | `cine_app_dev` | Aplicación: lectura e inserción; actualización solo en columnas permitidas; sin DELETE | `db/postgres/00_roles.sql` |
| `cine_report` | `cine_report_dev` | Solo lectura; la aplicación lo usa para el informe de ingresos (`REPORT_POSTGRES_USER` / `REPORT_POSTGRES_PASSWORD`) | `db/postgres/00_roles.sql` |

**MongoDB**: base `cineconecta`.

| Usuario | Contraseña | Base de autenticación | Uso | Definido en |
|---|---|---|---|---|
| `root` | `root` | `admin` | Superusuario del contenedor | `docker-compose.yaml` |
| `cineOwner` | `cine_owner_dev` | `cineconecta` | Dueño de la base (scripts de demostración) | `db/mongo/init/03_users.js` |
| `cineApp` | `cine_app_dev` | `cineconecta` | Aplicación; solo lectura sobre `admin_users` | `db/mongo/init/03_users.js` |
| `cineReport` | `cine_report_dev` | `cineconecta` | Solo lectura; la aplicación lo usa para el informe de consumo (`REPORT_MONGODB_USER` / `REPORT_MONGODB_PASSWORD`) | `db/mongo/init/03_users.js` |

### Usuarios de la aplicación

**Administrador** (http://localhost:8080/admin/login), creado por `db/mongo/init/04_admin.js`:

| Correo | Contraseña |
|---|---|
| `admin@cineconecta.local` | `Admin123*` |

**Clientes de demostración** (http://localhost:8080/login), creados por `db/demos/postgres/01_synthetic_billing.sql`. Todos tienen la contraseña `Demo123*`.

| Correo | Situación | Perfiles |
|---|---|---|
| `ana.vigente@cineconecta.local` | Plan Estándar vigente | Ana Principal (18), Ana Kids (7) |
| `carlos.vencida@cineconecta.local` | Solo suscripciones vencidas | Carlos (18) |
| `lucia.downgrade@cineconecta.local` | Premium vencido, con más perfiles activos de los que permite un plan menor | Lucía (18), Invitado 1 (13), Invitado 2 (13); Antiguo inactivo |
| `mateo.sinplan@cineconecta.local` | Nunca ha tenido plan | Mateo (18) |

**Cuentas de volumen**, creadas por `db/demos/load_all.sh`: `vol.000001@volumen.cineconecta.local` a `vol.020000@…`, también con `Demo123*`. Por ejemplo, `vol.000023@volumen.cineconecta.local` tiene Premium vigente, varios meses de historial, sesiones y 5 perfiles. Más casos (límite de perfiles, correo con mayúsculas, cuenta recién creada) en `db/demos/postgres/05_synthetic_accounts.sql`.
