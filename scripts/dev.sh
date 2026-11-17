#!/usr/bin/env bash
set -euo pipefail

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT"

JAVA_HOME="${JAVA_HOME:-$HOME/.cache/manuelo/jdk21}"
if [ ! -x "$JAVA_HOME/bin/java" ]; then
  if command -v java >/dev/null 2>&1; then
    JAVA_HOME=$(CDPATH= cd -- "$(dirname -- "$(readlink -f "$(command -v java)")")/.." && pwd)
  else
    echo "ERRO: JDK 21 nao encontrado. Defina JAVA_HOME."
    exit 1
  fi
fi
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

DB_PID=""
cleanup() {
  if [ -n "$DB_PID" ] && kill -0 "$DB_PID" 2>/dev/null; then
    kill "$DB_PID" 2>/dev/null || true
    wait "$DB_PID" 2>/dev/null || true
  fi
}
trap cleanup EXIT
trap 'cleanup; exit 130' INT
trap 'cleanup; exit 143' TERM

porta_livre() {
  node -e 'const n=require("net");const s=n.createServer();s.once("error",()=>process.exit(1));s.listen(Number(process.argv[1]),"127.0.0.1",()=>s.close(()=>process.exit(0)))' "$1" >/dev/null 2>&1
}

porta_aleatoria() {
  node -e 'const n=require("net");const s=n.createServer();s.listen(0,"127.0.0.1",()=>{console.log(s.address().port);s.close()})'
}

export DB_USER="${DB_USER:-postgres}"
export DB_PASSWORD="${DB_PASSWORD:-postgres}"

if [ -z "${DB_URL:-}" ]; then
  RUNTIME="${MANUELO_DB_RUNTIME:-$HOME/.cache/manuelo-db-runtime}"
  if [ ! -f "$RUNTIME/node_modules/embedded-postgres/dist/index.js" ]; then
    echo "Instalando PostgreSQL embutido em $RUNTIME (uma vez)..."
    mkdir -p "$RUNTIME"
    if [ ! -f "$RUNTIME/package.json" ]; then
      (cd "$RUNTIME" && npm init -y >/dev/null 2>&1)
    fi
    (cd "$RUNTIME" && npm install --no-audit --no-fund --silent embedded-postgres@18.6.0-beta.19)
  fi

  if [ -z "${PG_PORT:-}" ]; then
    if porta_livre 55433; then
      PG_PORT=55433
    else
      PG_PORT=$(porta_aleatoria)
    fi
  fi

  DB_LOG="${TMPDIR:-/tmp}/manuelo-dev-db.log"
  echo "Subindo PostgreSQL embutido em 127.0.0.1:$PG_PORT/manuelo..."
  MANUELO_DB_RUNTIME="$RUNTIME" PG_PORT="$PG_PORT" PG_DATABASE=manuelo \
    PG_USER="${DB_USER:-postgres}" PG_PASSWORD="${DB_PASSWORD:-postgres}" \
    node scripts/pg-runtime.mjs >"$DB_LOG" 2>&1 &
  DB_PID=$!

  pronto=0
  for _ in $(seq 1 60); do
    if grep -q READY "$DB_LOG" 2>/dev/null; then
      pronto=1
      break
    fi
    if ! kill -0 "$DB_PID" 2>/dev/null; then
      break
    fi
    sleep 1
  done
  if [ "$pronto" -ne 1 ]; then
    echo "ERRO: PostgreSQL nao subiu. Log: $DB_LOG"
    cat "$DB_LOG" 2>/dev/null || true
    exit 1
  fi

  export DB_URL="jdbc:postgresql://127.0.0.1:$PG_PORT/manuelo"
  export DB_USER="${DB_USER:-postgres}"
  export DB_PASSWORD="${DB_PASSWORD:-postgres}"
fi

if [ -z "${PORT:-}" ]; then
  PORT="${APP_PORT:-}"
fi
if [ -z "$PORT" ]; then
  if porta_livre 8080; then
    PORT=8080
  elif porta_livre 8081; then
    PORT=8081
  else
    PORT=$(porta_aleatoria)
  fi
fi
export PORT
export APP_TIMEZONE="${APP_TIMEZONE:-America/Sao_Paulo}"
export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-dev}"
export MANUELO_ADMIN_EMAIL="${MANUELO_ADMIN_EMAIL:-admin@manuelo.local}"
export MANUELO_ADMIN_SENHA="${MANUELO_ADMIN_SENHA:-admin123}"
export MANUELO_APP_URL="${MANUELO_APP_URL:-http://localhost:$PORT}"

echo "Aplicacao: http://localhost:$PORT"
echo "Login: $MANUELO_ADMIN_EMAIL / $MANUELO_ADMIN_SENHA"
echo "Banco: $DB_URL"
echo "Ctrl+C encerra a aplicacao e o banco local."
echo

./mvnw -B -DskipTests spring-boot:run
