#!/usr/bin/env sh
# Gera db/supabase/aplicar_tudo.sql concatenando as migrations em ordem.
# Uso: ./scripts/gerar-bundle-supabase.sh
set -eu

root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
out="$root/db/supabase/aplicar_tudo.sql"
mkdir -p "$root/db/supabase"

{
  printf -- '-- ARQUIVO GERADO por scripts/gerar-bundle-supabase.sh. Nao edite a mao.\n'
  printf -- '-- Cole no SQL Editor do Supabase (Database > SQL Editor) e execute.\n'
  printf -- '-- Schema v3: 13 tabelas, 9 views e 12 funcoes (V001..V011).\n'
  printf -- '-- Se for usar Flyway depois, aplique em um banco limpo (ver db/supabase/README.md).\n\n'
  printf -- 'BEGIN;\n'
  printf -- 'CREATE SCHEMA IF NOT EXISTS extensions;\n'
  printf -- 'SET search_path = public, extensions;\n'
  for f in "$root"/db/migrations/V*.sql; do
    printf -- '\n-- ===== %s =====\n' "$(basename "$f")"
    cat "$f"
    printf '\n'
  done
  printf -- '\nCOMMIT;\n'
} > "$out"

printf 'gerado: %s\n' "$out"
