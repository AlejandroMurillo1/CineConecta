#!/usr/bin/env bash
# =============================================================================
# CineConecta · db/demos/load_all.sh
# Carga todos los datos de demostración y de volumen, en el orden correcto, en las
# bases de docker compose. Ejecutar desde la raíz del proyecto (bash o Git Bash):
#
#   docker compose down -v && docker compose up -d     # opcional: empezar de cero
#   ./db/demos/load_all.sh
#
# Tamaño (tamaño medio por defecto; todos los valores se pueden cambiar):
#   CUENTAS=20000           cuentas de volumen (≈ 3 perfiles cada una) y su historial
#                           de suscripciones y pagos (≈ 95 000 de cada uno)
#   CONTENIDOS=150          contenidos generados en el catálogo (además de los de demo)
#   PERFILES_VOLUMEN=3400   perfiles de volumen con sesiones y Mi lista (≈ 200 000 sesiones)
#
#   CUENTAS=100000 CONTENIDOS=500 PERFILES_VOLUMEN=17000 ./db/demos/load_all.sh   # grande
#
# Todo es idempotente: se puede volver a correr. Las cuentas y contenidos que ya existen
# se conservan; las sesiones y listas sintéticas se regeneran.
# =============================================================================
set -euo pipefail

# Git Bash (Windows) convierte los argumentos que parecen rutas Unix (/tmp/...) en rutas de
# Windows antes de pasárselos a docker; dentro del contenedor esas rutas no existen.
export MSYS_NO_PATHCONV=1
export MSYS2_ARG_CONV_EXCL='*'

CUENTAS="${CUENTAS:-20000}"
CONTENIDOS="${CONTENIDOS:-150}"
PERFILES_VOLUMEN="${PERFILES_VOLUMEN:-3400}"

DEMOS="$(cd "$(dirname "$0")" && pwd)"
PSQL=(docker compose exec -T postgres psql -U cine -d cineconecta -v ON_ERROR_STOP=1)

# Ejecuta un script en MongoDB con mongosh --file. No se le pasa por la entrada estándar:
# ahí mongosh lo trata como texto escrito en la consola, línea por línea, y con scripts
# grandes se vuelve muy lento (y llena la salida de "|").
#   mongo_run <archivo> [VARIABLE=valor ...]
mongo_run() {
  local file=$1
  shift
  local envs=()
  local kv
  for kv in "$@"; do envs+=(-e "$kv"); done
  docker compose exec -T mongo sh -c 'cat > /tmp/cineconecta_seed.js' < "$file"
  docker compose exec -T ${envs[@]+"${envs[@]}"} mongo mongosh --quiet -u cineOwner -p cine_owner_dev \
    --authenticationDatabase cineconecta cineconecta --file /tmp/cineconecta_seed.js
}

step() { printf '\n== %s\n' "$1"; }
started=$SECONDS

step "Esperando a que las bases respondan"
for _ in $(seq 1 60); do
  if docker compose exec -T postgres pg_isready -U cine -d cineconecta >/dev/null 2>&1 \
     && docker compose exec -T mongo mongosh --quiet --eval 'db.adminCommand("ping").ok' >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

step "PostgreSQL: cuentas demo con suscripciones y pagos (01)"
"${PSQL[@]}" -q < "$DEMOS/postgres/01_synthetic_billing.sql"
step "PostgreSQL: casos de cuentas y perfiles (05)"
"${PSQL[@]}" -q < "$DEMOS/postgres/05_synthetic_accounts.sql" >/dev/null
step "PostgreSQL: $CUENTAS cuentas de volumen y sus perfiles (06)"
"${PSQL[@]}" -q -v cuentas="$CUENTAS" < "$DEMOS/postgres/06_volume_accounts.sql"
step "PostgreSQL: suscripciones y pagos de las cuentas de volumen (08)"
"${PSQL[@]}" -q < "$DEMOS/postgres/08_volume_subscriptions.sql"

step "MongoDB: catálogo de demostración (01)"
mongo_run "$DEMOS/mongo/01_demo_catalog.js" | tail -1
step "MongoDB: catálogo de volumen, $CONTENIDOS contenidos (03)"
mongo_run "$DEMOS/mongo/03_volume_catalog.js" CONTENIDOS="$CONTENIDOS"

step "PostgreSQL → MongoDB: perfiles para sesiones y listas (07, muestra de $PERFILES_VOLUMEN perfiles de volumen)"
viewers="$(mktemp)"
script="$(mktemp)"
trap 'rm -f "$viewers" "$script"' EXIT
"${PSQL[@]}" -qAt -v perfiles_volumen="$PERFILES_VOLUMEN" < "$DEMOS/postgres/07_export_viewers.sql" > "$viewers"
echo "  exportados: $(wc -c < "$viewers" | tr -d ' ') bytes"

step "MongoDB: sesiones de reproducción (02)"
cat "$viewers" "$DEMOS/mongo/02_synthetic_sessions.js" > "$script"
mongo_run "$script"
step "MongoDB: Mi lista (04)"
cat "$viewers" "$DEMOS/mongo/04_synthetic_my_lists.js" > "$script"
mongo_run "$script"

step "Listo en $((SECONDS - started)) s"
echo "  Clientes: contraseña Demo123* (ana.vigente@cineconecta.local, vol.000001@volumen.cineconecta.local, ...)"
echo "  Administrador: admin@cineconecta.local / Admin123*"
