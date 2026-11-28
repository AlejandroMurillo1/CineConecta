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
  demos/postgres/    datos de demostración y demos de concurrencia, rollback e índices
  demos/mongo/       catálogo de demostración
docs/
  data-model.md                 diccionario de datos de ambas bases
  04_indices_y_mediciones.md    experimento de índices en PostgreSQL
```

El detalle de los scripts, las reglas que garantiza cada base y el despliegue en la nube está en [`db/README.md`](db/README.md).

## Cómo ejecutarlo en local

**1. Bases de datos**

```bash
docker compose up -d
```

La primera vez, cada contenedor ejecuta sus scripts de `db/`. Esos scripts **solo corren con los volúmenes vacíos**: si cambia algo en `db/`, reinicie con `docker compose down -v && docker compose up -d` (borra los datos de ambas bases).

Por defecto PostgreSQL queda en `localhost:5432` y MongoDB en `localhost:27017`. Si esos puertos están ocupados (por ejemplo, por un PostgreSQL o MongoDB instalado en Windows), cree un `.env` en la raíz:

```
POSTGRES_PORT=5434
MONGO_PORT=27018
```

**2. Datos de demostración**

```bash
docker compose exec -T postgres psql -U cine -d cineconecta -v ON_ERROR_STOP=1 < db/demos/postgres/01_synthetic_billing.sql
docker compose exec -T mongo mongosh -u cineOwner -p cine_owner_dev --authenticationDatabase cineconecta cineconecta < db/demos/mongo/01_demo_catalog.js
```

**3. Aplicación**

Si cambió los puertos, cree `config/application.properties` (está en `.gitignore`) con las conexiones de su máquina:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5434/cineconecta?currentSchema=cineconecta
spring.datasource.username=cine_app
spring.datasource.password=cine_app_dev
spring.mongodb.uri=mongodb://cineApp:cine_app_dev@localhost:27018/cineconecta?authSource=cineconecta
```

Luego arranque con el perfil `local`:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local      # Windows: mvnw.cmd ...
```

La aplicación queda en http://localhost:8080 y el panel de administración en http://localhost:8080/admin.

> No defina `POSTGRES_PASSWORD` en el `.env`: el compose la usa para el superusuario `cine`, y la aplicación la tomaría como contraseña de `cine_app`.

**Pruebas**

```bash
./mvnw test
```

Las pruebas `*IT` usan Testcontainers y necesitan Docker en ejecución.

## Credenciales (solo desarrollo)

Todas estas contraseñas son de desarrollo. En la nube se cambian (ver `db/README.md`) y nunca se suben al repositorio.

### Usuarios de las bases de datos

**PostgreSQL**: base `cineconecta`, esquema `cineconecta`.

| Usuario | Contraseña | Uso | Definido en |
|---|---|---|---|
| `cine` | `cine` | Superusuario del contenedor; ejecuta los scripts | `docker-compose.yaml` |
| `cine_owner` | `cine_owner_dev` | Dueño del esquema y sus objetos | `db/postgres/00_roles.sql` |
| `cine_app` | `cine_app_dev` | Aplicación: lectura e inserción; actualización solo en columnas permitidas; sin DELETE | `db/postgres/00_roles.sql` |
| `cine_report` | `cine_report_dev` | Solo lectura, para informes | `db/postgres/00_roles.sql` |

**MongoDB**: base `cineconecta`.

| Usuario | Contraseña | Base de autenticación | Uso | Definido en |
|---|---|---|---|---|
| `root` | `root` | `admin` | Superusuario del contenedor | `docker-compose.yaml` |
| `cineOwner` | `cine_owner_dev` | `cineconecta` | Dueño de la base (scripts de demostración) | `db/mongo/init/03_users.js` |
| `cineApp` | `cine_app_dev` | `cineconecta` | Aplicación; solo lectura sobre `admin_users` | `db/mongo/init/03_users.js` |
| `cineReport` | `cine_report_dev` | `cineconecta` | Solo lectura, para informes | `db/mongo/init/03_users.js` |

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
