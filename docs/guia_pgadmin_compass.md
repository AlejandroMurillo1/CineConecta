# Guía: ver las bases con pgAdmin y MongoDB Compass

Sirve para mirar los datos mientras se usa la aplicación: se hace algo en la app, se refresca la herramienta y se ve qué cambió en cada base. Útil para la sustentación.

Requisitos: las bases levantadas con `docker compose up -d --wait`, [pgAdmin 4](https://www.pgadmin.org/download/) y [MongoDB Compass](https://www.mongodb.com/try/download/compass).

## 1. Saber qué puertos usa tu PC

Cada integrante puede tener puertos distintos: si el puerto por defecto está ocupado, se cambia en el `.env`. **Hay que conectarse al puerto del contenedor, no al de otro PostgreSQL o MongoDB instalado en el equipo.**

**a. Preguntarle a Docker** (la forma segura), desde la carpeta del proyecto:

```bash
docker compose port postgres 5432    # ej. 127.0.0.1:15432 → el puerto es 15432
docker compose port mongo 27017      # ej. 127.0.0.1:37017 → el puerto es 37017
```

Si responde `no container found`, las bases no están levantadas.

**b. O mirar el `.env`** de la raíz del proyecto:

| Variable | Si no está en el `.env` |
|---|---|
| `POSTGRES_PORT` | `5432` |
| `MONGO_PORT` | `27017` |

### Si el puerto por defecto está ocupado

`docker compose up` falla con `ports are not available` o `bind: An attempt was made to access a socket...` cuando otro programa (p. ej. un PostgreSQL instalado con pgAdmin) ya usa el puerto. Para revisarlo en Windows (PowerShell o CMD):

```powershell
netstat -ano | findstr :5432                                  # si sale LISTENING, está ocupado
netsh interface ipv4 show excludedportrange protocol=tcp      # rangos que Windows reserva
```

Elija un puerto libre que **no** esté dentro de los rangos reservados (por ejemplo `15432` y `37017`) y póngalo en el `.env`:

```
POSTGRES_PORT=15432
MONGO_PORT=37017
```

Después: `docker compose up -d --wait`, y use los mismos puertos en `config/application.properties` (ver el README) para que la aplicación también se conecte.

## 2. pgAdmin (PostgreSQL)

1. Clic derecho en **Servers → Register → Server…**
2. Pestaña **General**: Name `CineConecta (Docker)`.
3. Pestaña **Connection**:

   | Campo | Valor |
   |---|---|
   | Host name/address | `localhost` |
   | Port | el de `POSTGRES_PORT` (paso 1) |
   | Maintenance database | `cineconecta` |
   | Username / Password | `cine` / `cine` (marcar *Save password*) |

4. Las tablas están en **Databases → cineconecta → Schemas → `cineconecta`** (no en `public`) **→ Tables**.

Para ver los cambios: **Tools → Query Tool**, escribir una consulta y presionar **F5** después de cada acción en la app. Ejemplos:

```sql
SELECT * FROM cineconecta.plans ORDER BY planid;

-- Contrataciones con su pago (se escriben en una sola transacción)
SELECT s.subscriptionid, a.email, p.name AS plan, s.startsat, s.endsat, pay.amount, pay.reference
FROM cineconecta.subscriptions s
JOIN cineconecta.accounts a ON a.accountid = s.accountid
JOIN cineconecta.plans p ON p.planid = s.planid
LEFT JOIN cineconecta.payments pay ON pay.subscriptionid = s.subscriptionid
ORDER BY s.subscriptionid DESC LIMIT 10;

SELECT * FROM cineconecta.profiles ORDER BY profileid DESC;
```

## 3. MongoDB Compass

1. **New connection**, pegar la URI (cambiando `37017` por su `MONGO_PORT`):

   ```
   mongodb://cineOwner:cine_owner_dev@localhost:37017/cineconecta?authSource=cineconecta
   ```

2. Abrir la base **`cineconecta`**: colecciones `contents`, `my_lists`, `playback_sessions` y `admin_users`.
3. En una colección, en la barra de **Documents**: **Filter** (ej. `{ status: "DRAFT" }`) y en *Options* **Sort** `{ updatedAt: -1 }` para ver primero lo más reciente.
4. Después de cada acción en la app, botón **↻ Refresh**.

## 4. Qué mirar según lo que se hace en la app

| Acción en la app | URL | Base | Dónde mirar |
|---|---|---|---|
| Crear, editar, publicar o retirar un contenido | `/admin/contents` | MongoDB | `contents`, sort `{ updatedAt: -1 }` |
| Crear, renombrar, desactivar un plan | `/admin/plans` | PostgreSQL | `plans` |
| Contratar un plan | `/billing/plans` | PostgreSQL | `subscriptions` y `payments` |
| Registrarse / crear o desactivar un perfil | `/register`, `/profiles` | PostgreSQL | `accounts`, `profiles` |
| Agregar o quitar de Mi lista | detalle del catálogo, `/viewing/my-list` | MongoDB | `my_lists`, filtro `{ _id: <profileId> }` |
| Reproducir, pausar, finalizar | reproductor | MongoDB | `playback_sessions`, sort `{ startedAt: -1 }` |

Administrador: `admin@cineconecta.local` / `Admin123*` (en `/admin/login`). Clientes de demostración: ver el README.

## 5. Problemas comunes

| Síntoma | Causa y solución |
|---|---|
| pgAdmin conecta, pero no aparecen las tablas ni los cambios | Está conectado al PostgreSQL local y no al de Docker: revisar el puerto (paso 1). |
| Las tablas no aparecen en `public` | Están en el esquema `cineconecta`. |
| Compass: `Authentication failed` | Falta `?authSource=cineconecta` en la URI, o el usuario/clave no coinciden. |
| `connection refused` | Las bases no están corriendo: `docker compose up -d --wait`. |
| Hice un cambio y no lo veo | Ninguna de las dos herramientas se actualiza sola: F5 en pgAdmin, ↻ Refresh en Compass. |
