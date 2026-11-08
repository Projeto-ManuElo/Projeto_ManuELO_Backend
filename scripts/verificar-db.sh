#!/usr/bin/env sh
# Juiz do ManuElo DB: sobe um Postgres limpo, aplica db/migrations em ordem,
# carrega o seed (quando existir) e roda db/tests/*.sql. Sai com 0 só se tudo passar.
#
# Ordem de runtime (AGENTS.md):
#   1. docker compose
#   2. Postgres local (initdb/psql) em pasta temporária
#   3. Postgres embutido via Node (plano C, equivalente ao PGlite)
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
MIGRATIONS="$ROOT/db/migrations"
TESTS="$ROOT/db/tests"
SEED="$ROOT/db/seed/dev_seed.sql"
COMPOSE_FILE="$ROOT/docker-compose.yml"

have() { command -v "$1" >/dev/null 2>&1; }

log() { printf '%s\n' "$*"; }

run_files_with_psql() {
  # $1 = função que executa psql lendo stdin
  runner="$1"

  for f in "$MIGRATIONS"/*.sql; do
    [ -e "$f" ] || continue
    log "migration: $(basename "$f")"
    "$runner" < "$f"
  done

  if [ -f "$SEED" ]; then
    log "seed: $(basename "$SEED")"
    "$runner" < "$SEED"
  fi

  for f in "$TESTS"/*.sql; do
    [ -e "$f" ] || continue
    log "teste: $(basename "$f")"
    "$runner" < "$f"
  done
}

use_docker() {
  if ! have docker; then return 1; fi
  if ! docker info >/dev/null 2>&1; then return 1; fi
  if ! docker compose version >/dev/null 2>&1; then return 1; fi

  compose() { docker compose -f "$COMPOSE_FILE" "$@"; }

  cleanup_docker() {
    compose down -v --remove-orphans >/dev/null 2>&1 || true
  }
  trap cleanup_docker EXIT INT TERM

  log "runtime: docker compose ($COMPOSE_FILE)"
  cleanup_docker
  compose up -d

  i=0
  until compose exec -T db pg_isready -U manuelo -d manuelo >/dev/null 2>&1; do
    i=$((i + 1))
    if [ "$i" -ge 90 ]; then
      log "timeout aguardando o Postgres do docker compose"
      return 1
    fi
    sleep 1
  done

  psql_docker() {
    compose exec -T db psql -v ON_ERROR_STOP=1 -U manuelo -d manuelo "$@"
  }

  run_files_with_psql psql_docker
  log "VERIFICAÇÃO OK (docker compose)"
}

pick_port() {
  if have node; then
    node -e "const n=require('net');const s=n.createServer();s.listen(0,'127.0.0.1',()=>{console.log(s.address().port);s.close()})"
  elif have python3; then
    python3 -c "import socket;s=socket.socket();s.bind(('127.0.0.1',0));print(s.getsockname()[1]);s.close()"
  else
    echo 55433
  fi
}

use_local() {
  if ! have initdb || ! have pg_ctl || ! have psql; then return 1; fi

  DATA=$(mktemp -d "${TMPDIR:-/tmp}/manuelo-pg-XXXXXX")
  SOCK="$DATA/sock"
  PORT=$(pick_port)
  mkdir -p "$SOCK"

  cleanup_local() {
    pg_ctl -D "$DATA/pg" -m immediate stop >/dev/null 2>&1 || true
    rm -rf "$DATA"
  }
  trap cleanup_local EXIT INT TERM

  log "runtime: postgres local (porta $PORT, dados em $DATA)"
  initdb -D "$DATA/pg" -U postgres --auth=trust --no-instructions >/dev/null
  pg_ctl -D "$DATA/pg" -o "-p $PORT -k $SOCK -h 127.0.0.1" -w start >/dev/null

  psql_local() {
    psql -v ON_ERROR_STOP=1 -h "$SOCK" -p "$PORT" -U postgres -d manuelo "$@"
  }

  psql -v ON_ERROR_STOP=1 -h "$SOCK" -p "$PORT" -U postgres -d postgres \
    -c 'CREATE DATABASE manuelo' >/dev/null

  run_files_with_psql psql_local
  log "VERIFICAÇÃO OK (postgres local)"
}

use_node() {
  if ! have node || ! have npm; then return 1; fi

  RUNTIME="${MANUELO_DB_RUNTIME:-${TMPDIR:-/tmp}/manuelo-db-runtime}"
  DRIVER="$RUNTIME/node_modules/embedded-postgres/dist/index.js"

  if [ ! -f "$DRIVER" ]; then
    log "runtime: instalando Postgres embutido em $RUNTIME"
    mkdir -p "$RUNTIME"
    if [ ! -f "$RUNTIME/package.json" ]; then
      (cd "$RUNTIME" && npm init -y >/dev/null 2>&1)
    fi
    (cd "$RUNTIME" && npm install --no-audit --no-fund --silent embedded-postgres@18.6.0-beta.19)
  fi

  MANUELO_DB_RUNTIME="$RUNTIME" node "$ROOT/scripts/pg-verify.mjs"
  log "VERIFICAÇÃO OK (postgres embutido via Node)"
}

if have docker && docker info >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
  use_docker
  exit 0
fi

if have initdb && have pg_ctl && have psql; then
  use_local
  exit 0
fi

if have node && have npm; then
  use_node
  exit 0
fi

log "ERRO: nenhum runtime disponível (docker compose, postgres local ou node/npm)."
exit 1
