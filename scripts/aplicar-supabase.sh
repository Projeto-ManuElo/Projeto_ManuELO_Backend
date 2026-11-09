#!/usr/bin/env sh
# Aplica as migrations do ManuElo em um banco Supabase.
# Requer psql ou Docker. Nao cria o projeto Supabase (isso exige login na conta).
#
# Uso:
#   SUPABASE_DB_URL='postgresql://postgres.<ref>:<senha>@<host>:5432/postgres?sslmode=require' \
#     ./scripts/aplicar-supabase.sh
#
# Opcoes:
#   --seed        aplica db/seed/dev_seed.sql (somente dev/homologacao)
#   --admin       aplica db/seed/primeiro_admin.sql com ADMIN_NOME, ADMIN_EMAIL e ADMIN_HASH
#   --url <URI>   informa a URI direto (sobrepoe SUPABASE_DB_URL)
#
# A URL tambem pode ser definida no .env do projeto (copie de .env.example) ou,
# uma unica vez, em ~/.config/manuelo/supabase.env:
#   SUPABASE_DB_URL='postgresql://...'
#
# Aviso: este caminho NAO grava flyway_schema_history. Se a aplicacao for usar Flyway,
# aplique em um banco limpo ou recrie o schema antes de subir o Spring Boot.
set -eu

root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
project_env="$root/.env"
env_file="${XDG_CONFIG_HOME:-$HOME/.config}/manuelo/supabase.env"

# 1) .env do projeto (dev local); 2) configuracao do usuario; 3) variavel de ambiente/CI.
if [ -z "${SUPABASE_DB_URL:-}" ] && [ -z "${DATABASE_URL:-}" ] && [ -f "$project_env" ]; then
  set -a
  # shellcheck disable=SC1090
  . "$project_env"
  set +a
fi

if [ -z "${SUPABASE_DB_URL:-}" ] && [ -z "${DATABASE_URL:-}" ] && [ -f "$env_file" ]; then
  # shellcheck disable=SC1090
  . "$env_file"
fi

url="${SUPABASE_DB_URL:-${DATABASE_URL:-}}"
with_seed=0
with_admin=0

while [ "$#" -gt 0 ]; do
  case "$1" in
    --seed) with_seed=1 ;;
    --admin) with_admin=1 ;;
    --url)
      shift
      if [ "$#" -eq 0 ]; then
        printf '--url exige uma URI\n' >&2
        exit 2
      fi
      url=$1
      ;;
    -h|--help)
      sed -n '2,25p' "$0"
      exit 0
      ;;
    *)
      printf 'opcao desconhecida: %s\n' "$1" >&2
      exit 2
      ;;
  esac
  shift
done

if [ -z "$url" ]; then
  printf 'Defina SUPABASE_DB_URL (Session pooler, porta 5432).\n' >&2
  printf 'Ex.: postgresql://postgres.<ref>:<senha>@<host>:5432/postgres?sslmode=require\n' >&2
  exit 1
fi

case "$url" in
  *:6543/*|*:6543)
    printf 'Use o Session pooler na porta 5432; a porta 6543 e o Transaction pooler.\n' >&2
    exit 1
    ;;
esac

if command -v psql >/dev/null 2>&1; then
  runner=psql
elif command -v docker >/dev/null 2>&1; then
  runner=docker
else
  printf 'Instale psql (postgresql-client) ou Docker para aplicar as migrations.\n' >&2
  exit 1
fi

run_file() {
  file=$1
  if [ "$runner" = psql ]; then
    psql "$url" -v ON_ERROR_STOP=1 -1 -f "$file"
  else
    docker run --rm -i -e PGURL="$url" postgres:16 \
      sh -c 'psql "$PGURL" -v ON_ERROR_STOP=1 -1 -f -' < "$file"
  fi
}

run_admin() {
  if [ -z "${ADMIN_NOME:-}" ] || [ -z "${ADMIN_EMAIL:-}" ] || [ -z "${ADMIN_HASH:-}" ]; then
    printf 'Para --admin defina ADMIN_NOME, ADMIN_EMAIL e ADMIN_HASH (bcrypt).\n' >&2
    exit 1
  fi
  file="$root/db/seed/primeiro_admin.sql"
  if [ "$runner" = psql ]; then
    psql "$url" -v ON_ERROR_STOP=1 \
      -v nome="$ADMIN_NOME" -v email="$ADMIN_EMAIL" -v senha_hash="$ADMIN_HASH" \
      -f "$file"
  else
    docker run --rm -i -e PGURL="$url" -e ANOME="$ADMIN_NOME" \
      -e AEMAIL="$ADMIN_EMAIL" -e AHASH="$ADMIN_HASH" postgres:16 \
      sh -c 'psql "$PGURL" -v ON_ERROR_STOP=1 -v nome="$ANOME" -v email="$AEMAIL" -v senha_hash="$AHASH" -f -' \
      < "$file"
  fi
}

printf 'Aplicando migrations em %s\n' "$(printf '%s' "$url" | sed 's#//[^@]*@#//***@#')"
for f in "$root"/db/migrations/V*.sql; do
  printf '  - %s\n' "$(basename "$f")"
  run_file "$f"
done

if [ "$with_seed" = 1 ]; then
  printf 'Aplicando seed ficticio (dev/homologacao)...\n'
  run_file "$root/db/seed/dev_seed.sql"
fi

if [ "$with_admin" = 1 ]; then
  printf 'Criando primeiro administrador...\n'
  run_admin
fi

printf '\nOK. Confira no Supabase: Table Editor ou SQL Editor.\n'
printf '  select count(*) from imoveis;\n'
printf '  select * from vw_home_indicadores;\n'
