# CineConecta — Índices y mediciones de rendimiento

Documento consolidado de experimentos de índices y planes de ejecución.

---

## 1. PostgreSQL 17 — Módulo `billing` (Persona 2)

Script reproducible: [`db/demos/postgres/04_index_experiment.sql`](../db/demos/postgres/04_index_experiment.sql)  
Entorno medido: contenedor `postgres:17` (`docker-compose.yaml`), esquema aislado `exp_billing` con **100.000 suscripciones** (20.000 cuentas × 5 períodos escalonados) y **100.000 pagos** tras ejecutar `ANALYZE`.

### 1.1 Tamaño de tablas e índices

| Tabla | Filas | Tamaño tabla | Índices sin secundarios (solo PK) | Total sin secundarios | Índices con secundarios | Total con secundarios |
|---|---:|---:|---:|---:|---:|---:|
| `plans` | 3 | 8.192 bytes | 16 kB | 24 kB | 16 kB | 24 kB |
| `subscriptions` | 100.000 | 5.888 kB | 3.080 kB | 8.992 kB | 6.880 kB | 12 MB |
| `payments` | 100.000 | 6.592 kB | 2.208 kB | 8.824 kB | 5.152 kB | 11 MB |

### 1.2 Experimento Q1 — Suscripción vigente e historial por cuenta (`ix_subscriptions_account_starts`)

Consulta medida:
```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT s.subscriptionid, s.planid, s.startsat, s.endsat
FROM exp_billing.subscriptions s
WHERE s.accountid = 15432
ORDER BY s.startsat DESC;
```

| Métrica | Antes (sin índice) | Después (`ix_exp_sub_account_starts`) | Mejora |
|---|---|---|---|
| Operador principal | `Seq Scan on subscriptions s` (99.995 descartadas) | `Bitmap Index Scan` + `Bitmap Heap Scan` | Elimina barrido completo |
| Buffers (`shared`) | `hit=736` | `hit=5 read=3` (8 bloques) | **~92× menos bloques** |
| Tiempo de ejecución | `5.127 ms` | `0.050 ms` | **~102× más rápido** |

### 1.3 Experimento Q2 — Ingresos por plan en ventana de 7 días (`ix_payments_paidat` + `ix_subscriptions_plan`)

Consulta medida (2.113 pagos coincidentes de 100.000):
```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT pl.planid, pl.name, COUNT(pay.paymentid), COALESCE(SUM(pay.amount), 0)
FROM exp_billing.plans pl
JOIN exp_billing.subscriptions sub ON sub.planid = pl.planid
JOIN exp_billing.payments pay ON pay.subscriptionid = sub.subscriptionid
WHERE pay.paidat >= TIMESTAMPTZ '2025-03-10 00:00:00+00'
  AND pay.paidat <  TIMESTAMPTZ '2025-03-17 00:00:00+00'
GROUP BY pl.planid, pl.name
ORDER BY pl.planid;
```

| Métrica | Antes (sin índices) | Después (`ix_exp_pay_paidat`) | Mejora |
|---|---|---|---|
| Acceso a `payments` | `Seq Scan on payments pay` (97.887 descartadas, `6.661 ms`, `hit=824`) | `Bitmap Index Scan` + `Bitmap Heap Scan` (`0.483 ms`, `hit=335 read=3`) | **~13,8× más rápido en filtro de pagos** |
| Buffers totales | `shared hit=1561` | `shared hit=1072 read=3` | **~31 % menos bloques totales** |
| Tiempo total de consulta | `20.651 ms` | `10.401 ms` | **~2× más rápido end-to-end** |

### 1.4 Evidencias transaccionales y de concurrencia (PostgreSQL 17)

- **Atomicidad y trigger diferido (`db/demos/postgres/03_demo_tx_rollback.sql`):**
  - Caso A (`ROLLBACK` antes de insertar en `payments`): conteo permanece 5 → 5.
  - Caso B (`COMMIT` sin fila en `payments`): el trigger `tg_subscription_requires_payment` (`fn_subscription_requires_payment()`) rechaza el `COMMIT` con `ERROR: La suscripción 8 no tiene un pago registrado` (`SQLSTATE 23514`), dejando 0 registros parciales (5 → 5).
- **Concurrencia (`BillingTransactionIT` y `db/demos/postgres/02_demo_concurrency.sql`):**
  - Dos hilos simultáneos sobre la misma cuenta: con `pg_advisory_xact_lock` uno confirma y el segundo recibe `ActiveSubscriptionAlreadyExistsException`; sin advisory lock la restricción `ex_subscriptions_no_overlap` (`EXCLUDE USING gist`) rechaza el segundo con `SQLSTATE 23P01` (`ConcurrentSubscriptionException`). En ambos casos queda exactamente 1 suscripción y 1 pago.

---

## 2. MongoDB — Catálogo (Persona 3)

*(Pendiente de completar por Persona 3 con `explain("executionStats")` sobre `contents`).*

---

## 3. MongoDB — Pipeline de consumo (Persona 4)

*(Pendiente de completar por Persona 4 con el pipeline de agregación sobre `playback_sessions`).*
