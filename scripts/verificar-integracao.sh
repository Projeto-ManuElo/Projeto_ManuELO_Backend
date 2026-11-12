#!/usr/bin/env bash
# Juiz do ManuElo (A-01). Executa as 8 etapas do AGENTS.md e só sai com 0 se todas passarem.
#
#   1. build limpo                     ./mvnw -B clean package -DskipTests
#   2. testes Java (unitários/persistência) contra Postgres real
#   3. Postgres limpo + migrations do zero (scripts/verificar-db.sh)
#   4. app sobe com spring.jpa.hibernate.ddl-auto=validate
#   5. smoke: rotas do mapa respondem 200/redirect ao login, sem stack trace
#   6. varredura do log: nenhum ERROR/Exception inesperado
#   7. prova de persistência com reinício do contexto Spring
#   8. guarda anti-protótipo: nenhuma lista em memória do DemoData como fonte de dados
#
# Runtime: usa DB_URL/DB_USER/DB_PASSWORD quando informados. Sem eles, sobe um
# Postgres embutido temporário via Node (plano C do AGENTS.md) em 127.0.0.1:55433
# quando a porta está livre (senão em porta aleatória). Assim o juiz não depende de
# um cluster compartilhado. O que o juiz sobe é encerrado por PID capturado no trap
# — nunca usamos pkill/pgrep -f com o nome do jar.
#
# Etapa 8: os records de DemoData são permitidos como DTO (repositórios os constroem
# a partir do banco). São proibidos os padrões de protótipo:
#   DemoData.imoveis( / contratos( / movimentacoes( / inquilinos( / documentos( / reformas(
#   List<DemoData.X> campo = DemoData...
set -euo pipefail

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT"

JAVA_HOME="${JAVA_HOME:-/home/joaomiguel/.cache/manuelo/jdk21}"
if [ ! -x "$JAVA_HOME/bin/java" ]; then
  if command -v java >/dev/null 2>&1; then
    JAVA_HOME=$(CDPATH= cd -- "$(dirname -- "$(readlink -f "$(command -v java)")")/.." && pwd)
  else
    echo "ERRO: JDK 21 não encontrado (JAVA_HOME nem PATH)."
    exit 1
  fi
fi
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

LOGDIR=$(mktemp -d "${TMPDIR:-/tmp}/manuelo-juiz-XXXXXX")
APP_PID=""
DB_PID=""
DB_PROPRIA=0
FALHOU=""

log() { printf '%s\n' "$*"; }
etapa() { printf '\n=== Etapa %s/8: %s ===\n' "$1" "$2"; }

encerrar_pid() {
  pid="$1"
  [ -n "$pid" ] || return 0
  kill -0 "$pid" 2>/dev/null || return 0
  kill "$pid" 2>/dev/null || true
  i=0
  while kill -0 "$pid" 2>/dev/null && [ "$i" -lt 15 ]; do
    sleep 1
    i=$((i + 1))
  done
  kill -9 "$pid" 2>/dev/null || true
  wait "$pid" 2>/dev/null || true
}

cleanup() {
  encerrar_pid "$APP_PID"
  encerrar_pid "$DB_PID"
  rm -rf "$LOGDIR"
}
trap cleanup EXIT
trap 'cleanup; exit 130' INT
trap 'cleanup; exit 143' TERM

falhar() {
  FALHOU="$1"
  log ""
  log "FALHOU — $1"
  exit 1
}

porta_livre() {
  node -e 'const n=require("net");const s=n.createServer();s.once("error",()=>process.exit(1));s.listen(Number(process.argv[1]),"127.0.0.1",()=>s.close(()=>process.exit(0)))' "$1" >/dev/null 2>&1
}

porta_aleatoria() {
  node -e 'const n=require("net");const s=n.createServer();s.listen(0,"127.0.0.1",()=>{console.log(s.address().port);s.close()})'
}

db_ping() {
  node -e '
    const { Client } = require(process.argv[1]);
    const c = new Client({
      host: process.argv[2], port: Number(process.argv[3]), user: process.argv[4],
      password: process.argv[5], database: process.argv[6], connectionTimeoutMillis: 3000
    });
    c.connect().then(() => c.end()).then(() => process.exit(0)).catch(() => process.exit(1));
  ' "$ROOT/scripts/node_modules/pg" "$DB_HOST" "$DB_PORT" "$DB_USER" "$DB_PASSWORD" "$DB_NAME" >/dev/null 2>&1
}

garantir_runtime_pg() {
  RUNTIME="${MANUELO_DB_RUNTIME:-${TMPDIR:-/tmp}/manuelo-db-runtime}"
  export MANUELO_DB_RUNTIME="$RUNTIME"
  if [ -f "$RUNTIME/node_modules/embedded-postgres/dist/index.js" ]; then
    return 0
  fi
  log "  embedded-postgres ausente; instalando em $RUNTIME (uma vez)..."
  mkdir -p "$RUNTIME"
  if [ ! -f "$RUNTIME/package.json" ]; then
    (cd "$RUNTIME" && npm init -y >/dev/null 2>&1) || return 1
  fi
  (cd "$RUNTIME" && npm install --no-audit --no-fund --silent embedded-postgres@18.6.0-beta.19) || return 1
}

iniciar_postgres_embutido() {
  garantir_runtime_pg || return 1
  PORTA="${PG_PORT:-55433}"
  if [ "$PORTA" -eq 0 ] || ! porta_livre "$PORTA"; then
    PORTA=$(porta_aleatoria)
  fi
  PG_PORT="$PORTA" PG_USER="$DB_USER" PG_PASSWORD="$DB_PASSWORD" PG_DATABASE="$DB_NAME" \
    node "$ROOT/scripts/pg-runtime.mjs" >"$LOGDIR/postgres.log" 2>&1 &
  DB_PID=$!
  i=0
  while [ "$i" -lt 180 ]; do
    if grep -q '^READY ' "$LOGDIR/postgres.log" 2>/dev/null; then
      DB_HOST=127.0.0.1
      DB_PORT="$PORTA"
      DB_URL="jdbc:postgresql://127.0.0.1:$PORTA/$DB_NAME"
      return 0
    fi
    if ! kill -0 "$DB_PID" 2>/dev/null; then
      log "  Postgres embutido encerrou durante a subida:"
      sed -n '1,80p' "$LOGDIR/postgres.log"
      DB_PID=""
      return 1
    fi
    sleep 1
    i=$((i + 1))
  done
  log "  timeout aguardando o Postgres embutido"
  return 1
}

# Garante que o banco responde antes das etapas que dependem dele. Se o banco foi
# iniciado pelo próprio juiz e caiu, reinicia uma vez em porta livre.
garantir_banco() {
  if db_ping; then
    return 0
  fi
  if [ "$DB_PROPRIA" -ne 1 ]; then
    log "  banco externo deixou de responder em $DB_HOST:$DB_PORT/$DB_NAME"
    return 1
  fi
  log "  banco embutido caiu; reiniciando em porta livre..."
  encerrar_pid "$DB_PID"
  DB_PID=""
  PG_PORT=0 iniciar_postgres_embutido || return 1
  DB_PROPRIA=1
  log "  Postgres embutido reiniciado em $DB_URL"
  i=0
  while [ "$i" -lt 30 ]; do
    if db_ping; then
      return 0
    fi
    sleep 1
    i=$((i + 1))
  done
  return 1
}

# --- Configuração do banco ---------------------------------------------------
DB_URL_INFORMADA="${DB_URL:-}"
DB_USER="${DB_USER:-postgres}"
DB_PASSWORD="${DB_PASSWORD:-postgres}"
DB_URL="${DB_URL:-jdbc:postgresql://127.0.0.1:55433/manuelo}"

URL_SEM_PREFIXO="${DB_URL#jdbc:postgresql://}"
URL_SEM_QUERY="${URL_SEM_PREFIXO%%\?*}"
AUTHORITY="${URL_SEM_QUERY%%/*}"
DB_HOST="${AUTHORITY%:*}"
DB_PORT="${AUTHORITY##*:}"
case "$AUTHORITY" in
  *:*) ;;
  *) DB_PORT=5432 ;;
esac
DB_NAME="${URL_SEM_QUERY#*/}"
[ "$DB_NAME" != "$URL_SEM_QUERY" ] || DB_NAME="postgres"
[ "$DB_NAME" != "" ] || DB_NAME="postgres"

log "Juiz do ManuElo — raiz: $ROOT"
log "Runtime Java: $JAVA_HOME"
log "Banco: $DB_HOST:$DB_PORT/$DB_NAME (usuário $DB_USER)"

if [ -n "$DB_URL_INFORMADA" ]; then
  if db_ping; then
    log "DB_URL informada acessível; usando o banco do ambiente."
  else
    falhar "DB_URL informada não está acessível ($DB_URL). Suba o banco ou remova DB_URL para usar o runtime local."
  fi
else
  # Sem DB_URL explícita o juiz usa um cluster próprio e limpo: 55433 se estiver
  # livre, senão uma porta aleatória. Isso evita depender/alterar um Postgres
  # compartilhado que outro processo possa estar usando.
  log "Sem DB_URL explícita; subindo Postgres embutido temporário."
  iniciar_postgres_embutido || falhar "não foi possível subir o Postgres embutido"
  DB_PROPRIA=1
  log "Postgres embutido pronto em $DB_URL."
fi

# --- Etapa 1: build limpo ----------------------------------------------------
etapa 1 "build limpo (mvn clean package -DskipTests)"
if ! "$ROOT/mvnw" -B clean package -DskipTests 2>&1 | tee "$LOGDIR/build.log"; then
  tail -n 80 "$LOGDIR/build.log" || true
  falhar "build limpo"
fi
log "Build OK."

# --- Etapa 2: testes Java contra Postgres -----------------------------------
etapa 2 "testes Java contra o Postgres real (mvn test)"
garantir_banco || falhar "banco indisponível antes dos testes Java"
if ! DB_URL="$DB_URL" DB_USER="$DB_USER" DB_PASSWORD="$DB_PASSWORD" \
  "$ROOT/mvnw" -B test 2>&1 | tee "$LOGDIR/testes.log"; then
  tail -n 120 "$LOGDIR/testes.log" || true
  falhar "testes Java"
fi
log "Testes OK."

# --- Etapa 3: Postgres limpo + migrations do zero ----------------------------
etapa 3 "Postgres limpo + migrations do zero (scripts/verificar-db.sh)"
if ! "$ROOT/scripts/verificar-db.sh" 2>&1 | tee "$LOGDIR/banco.log"; then
  tail -n 120 "$LOGDIR/banco.log" || true
  falhar "migrations/banco"
fi
log "Banco do zero OK."

# --- Etapa 4: app sobe com ddl-auto=validate --------------------------------
etapa 4 "app sobe com ddl-auto=validate"

# Guardas estáticas (P-001/P-002/P-008): sem modelo JPA e serviços reais o
# ddl-auto=validate passa vazio e o juiz dá falso verde. Estas checagens rodam
# antes de subir o app para falhar cedo e com mensagem rastreável.
DIRS_ESTATICOS=()
if [ -d src/main/java ]; then DIRS_ESTATICOS+=("src/main/java"); fi
if [ -d ui/src/main/java ]; then DIRS_ESTATICOS+=("ui/src/main/java"); fi

contar_java() {
  local padrao="$1"
  shift
  { grep -RlE --include='*.java' "$padrao" "$@" 2>/dev/null || true; } | wc -l | tr -d ' '
}

FALHAS_ESTATICAS=()
if [ "${#DIRS_ESTATICOS[@]}" -eq 0 ]; then
  FALHAS_ESTATICAS+=("P-001: nenhuma entidade JPA (DEC-I01)")
else
  ENTIDADES=$(contar_java '@Entity([^A-Za-z0-9_]|$)' "${DIRS_ESTATICOS[@]}")
  if [ "$ENTIDADES" -eq 0 ]; then
    FALHAS_ESTATICAS+=("P-001: nenhuma entidade JPA (DEC-I01)")
  elif [ "$ENTIDADES" -lt 13 ]; then
    FALHAS_ESTATICAS+=("P-001: apenas $ENTIDADES entidade(s) JPA; modelo v3 exige ao menos 13 (DEC-I01)")
  fi

  AUDITORIA_OK=1
  for TOKEN in EnableJpaAuditing AuditorAware BaseEntity; do
    if ! grep -RqE --include='*.java' "$TOKEN" "${DIRS_ESTATICOS[@]}" 2>/dev/null; then
      AUDITORIA_OK=0
    fi
  done
  if [ "$AUDITORIA_OK" -ne 1 ]; then
    FALHAS_ESTATICAS+=("P-008: AuditorAware/BaseEntity ausente (DEC-I11)")
  fi

  # DEC-I15: a camada de serviços pertence aos itens S2+ e não bloqueia a fundação
  # (A-02/A-03). O teste vermelho por item roda na etapa 2; aqui apenas informamos.
  SERVICOS=$(contar_java '@Service([^A-Za-z0-9_]|$)' "${DIRS_ESTATICOS[@]}")
  TRANSACIONAIS=$(contar_java '@Transactional([^A-Za-z0-9_]|$)' "${DIRS_ESTATICOS[@]}")
  if [ "$SERVICOS" -lt 2 ] || [ "$TRANSACIONAIS" -lt 1 ]; then
    log "  [aviso] P-002: camada de serviços ainda incompleta (@Service=$SERVICOS, @Transactional=$TRANSACIONAIS); não bloqueia a fundação (DEC-I15)."
  fi
fi

if [ "${#FALHAS_ESTATICAS[@]}" -gt 0 ]; then
  for FALHA_ESTATICA in "${FALHAS_ESTATICAS[@]}"; do
    log "  [estático] $FALHA_ESTATICA"
  done
  falhar "critérios estáticos: ${FALHAS_ESTATICAS[*]}"
fi

garantir_banco || falhar "banco indisponível antes de subir o app"
JAR=$(find target -maxdepth 1 -name 'manuelo-*.jar' ! -name '*.original' -print -quit)
[ -n "$JAR" ] || falhar "jar empacotado não encontrado em target/"
APP_PORT="${APP_PORT:-18081}"
if ! porta_livre "$APP_PORT"; then
  APP_PORT=$(porta_aleatoria)
fi
log "  jar: $JAR"
log "  porta: $APP_PORT"
(
  export DB_URL DB_USER DB_PASSWORD
  export MANUELO_ADMIN_EMAIL="${MANUELO_ADMIN_EMAIL:-qa@manuelo.local}"
  export MANUELO_ADMIN_SENHA="${MANUELO_ADMIN_SENHA:-qa-admin-123}"
  export VAADIN_LAUNCH_BROWSER=false
  exec "$JAVA_HOME/bin/java" -jar "$JAR" \
    --server.port="$APP_PORT" \
    --spring.jpa.hibernate.ddl-auto=validate
) >"$LOGDIR/app.log" 2>&1 &
APP_PID=$!

APP_OK=0
i=0
while [ "$i" -lt 240 ]; do
  if grep -q 'Started ManueloApplication' "$LOGDIR/app.log" 2>/dev/null; then
    APP_OK=1
    break
  fi
  if ! kill -0 "$APP_PID" 2>/dev/null; then
    break
  fi
  sleep 1
  i=$((i + 1))
done
if [ "$APP_OK" -ne 1 ]; then
  tail -n 120 "$LOGDIR/app.log" || true
  falhar "app não subiu"
fi
if grep -qE 'Schema-validation|SchemaManagementException|APPLICATION FAILED TO START' "$LOGDIR/app.log"; then
  grep -nE 'Schema-validation|SchemaManagementException|APPLICATION FAILED TO START' "$LOGDIR/app.log" || true
  falhar "validação do schema (ddl-auto=validate)"
fi
log "App iniciado com validate OK."

# --- Etapa 5: smoke das rotas ------------------------------------------------
etapa 5 "smoke das rotas"
command -v curl >/dev/null 2>&1 || falhar "curl não disponível para o smoke"
ROTAS="/ /cadastro /aprovacao /aguardando-aprovacao /recuperar-senha /email-enviado /redefinir-senha /home /imoveis /inquilinos /contratos /documentos /movimentacao-financeira /relatorios /imoveis/reformas /contratos/detalhe /usuarios"
FALHA_SMOKE=""
for ROTA in $ROTAS; do
  CORPO="$LOGDIR/corpo-smoke.html"
  CODIGO=$(curl -sS --max-time 30 -o "$CORPO" -w '%{http_code}' "http://127.0.0.1:$APP_PORT$ROTA" 2>/dev/null || echo "000")
  case "$CODIGO" in
    200|302|303) ;;
    *) FALHA_SMOKE="$ROTA -> HTTP $CODIGO"; break ;;
  esac
  if grep -qE 'Internal Server Error|Whitelabel Error Page|java\.lang\.[A-Za-z]+Exception|at com\.Projeto\.manuelo|at br\.com\.manuelo' "$CORPO"; then
    FALHA_SMOKE="$ROTA -> stack trace no corpo"
    break
  fi
  log "  [ok] $ROTA -> $CODIGO"
done
if [ -n "$FALHA_SMOKE" ]; then
  falhar "smoke: $FALHA_SMOKE"
fi

# --- Etapa 6: varredura do log ----------------------------------------------
etapa 6 "varredura do log (ERROR/Exception inesperados)"
# Nenhuma exceção de negócio é esperada neste estágio. Se surgir uma exceção
# esperada e documentada, adicione o padrão em ERROS_ESPERADOS (regex grep -E).
ERROS_ESPERADOS="${MANUELO_ERROS_ESPERADOS:-}"
if grep -nE '(^|[[:space:]])(ERROR|FATAL)([[:space:]]|$)' "$LOGDIR/app.log" >"$LOGDIR/erros.log" 2>/dev/null; then
  if [ -n "$ERROS_ESPERADOS" ]; then
    grep -vE "$ERROS_ESPERADOS" "$LOGDIR/erros.log" >"$LOGDIR/erros-filtrados.log" 2>/dev/null || true
  else
    cp "$LOGDIR/erros.log" "$LOGDIR/erros-filtrados.log"
  fi
  if [ -s "$LOGDIR/erros-filtrados.log" ]; then
    sed -n '1,60p' "$LOGDIR/erros-filtrados.log"
    falhar "log com ERROR/FATAL inesperado"
  fi
fi
if grep -nE 'Caused by:|[[:space:]]at [a-zA-Z0-9_.$]+\(|Exception:' "$LOGDIR/app.log" >"$LOGDIR/stack.log" 2>/dev/null; then
  if [ -n "$ERROS_ESPERADOS" ]; then
    grep -vE "$ERROS_ESPERADOS" "$LOGDIR/stack.log" >"$LOGDIR/stack-filtrado.log" 2>/dev/null || true
  else
    cp "$LOGDIR/stack.log" "$LOGDIR/stack-filtrado.log"
  fi
  if [ -s "$LOGDIR/stack-filtrado.log" ]; then
    sed -n '1,60p' "$LOGDIR/stack-filtrado.log"
    falhar "log com Exception/stack trace inesperado"
  fi
fi
log "Log limpo."

# --- Etapa 7: prova de persistência com reinício do contexto ----------------
etapa 7 "prova de persistência (grava, reinicia o contexto e lê de volta)"
garantir_banco || falhar "banco indisponível antes da prova de persistência"

# A prova vale com PersistenciaRoundTripTest (repositório) enquanto os serviços
# não existem. DEC-I15: não falhamos por ausência de serviço; o teste vermelho por
# item roda na etapa 2. Se houver serviços de negócio, prefira a prova por serviço
# (o teste passa a referenciar um ...Service).
DIRS_PERSISTENCIA=()
if [ -d src/main/java ]; then DIRS_PERSISTENCIA+=("src/main/java"); fi
if [ -d ui/src/main/java ]; then DIRS_PERSISTENCIA+=("ui/src/main/java"); fi
TESTE_PERSISTENCIA="src/test/java/com/Projeto/manuelo/PersistenciaRoundTripTest.java"
SERVICOS_NEGOCIO=""
if [ "${#DIRS_PERSISTENCIA[@]}" -gt 0 ]; then
  SERVICOS_NEGOCIO=$({ grep -RlE --include='*.java' '@Service([^A-Za-z0-9_]|$)' "${DIRS_PERSISTENCIA[@]}" 2>/dev/null || true; } | grep -v '/AutenticacaoService\.java$' || true)
fi
if [ ! -f "$TESTE_PERSISTENCIA" ]; then
  falhar "prova de persistência não encontrou $TESTE_PERSISTENCIA"
fi
if grep -qE '[A-Za-z0-9_]+Service([^A-Za-z0-9_]|$)' "$TESTE_PERSISTENCIA"; then
  if [ -n "$SERVICOS_NEGOCIO" ]; then
    log "  [ok] prova de persistência referencia serviço de negócio."
  else
    log "  [aviso] P-002: teste referencia ...Service, mas nenhum serviço de negócio foi encontrado; seguindo com a prova por repositório (DEC-I15)."
  fi
else
  if [ -n "$SERVICOS_NEGOCIO" ]; then
    log "  [aviso] P-002: há serviços de negócio; prefira migrar a prova para um ...Service (DEC-I15)."
  else
    log "  [aviso] P-002: prova por repositório (serviços pertencem a S2+); não bloqueia (DEC-I15)."
  fi
fi

if ! DB_URL="$DB_URL" DB_USER="$DB_USER" DB_PASSWORD="$DB_PASSWORD" \
  "$ROOT/mvnw" -B test -Dtest=PersistenciaRoundTripTest 2>&1 | tee "$LOGDIR/persistencia.log"; then
  tail -n 120 "$LOGDIR/persistencia.log" || true
  falhar "prova de persistência"
fi
if ! grep -q 'PersistenciaRoundTripTest' "$LOGDIR/persistencia.log"; then
  falhar "prova de persistência não executou (teste não encontrado)"
fi
log "Persistência comprovada."

# --- Etapa 8: guarda anti-protótipo ----------------------------------------
etapa 8 "guarda anti-protótipo (DemoData/lista em memória nas views)"
DIRS=()
if [ -d src/main/java ]; then DIRS+=("src/main/java"); fi
if [ -d ui/src/main/java ]; then DIRS+=("ui/src/main/java"); fi
if [ "${#DIRS[@]}" -gt 0 ]; then
  PADROES=(
    'DemoData\.(imoveis|contratos|movimentacoes|inquilinos|documentos|reformas)[[:space:]]*\('
    'List<DemoData\.[A-Za-z0-9_]+>[^;=]*=[[:space:]]*DemoData\.'
  )
  PROTOTIPO=0
  for PADRAO in "${PADROES[@]}"; do
    if grep -RInE --include='*.java' "$PADRAO" "${DIRS[@]}" >"$LOGDIR/guarda.log" 2>/dev/null; then
      log "  padrão proibido: $PADRAO"
      sed -n '1,40p' "$LOGDIR/guarda.log"
      PROTOTIPO=1
    fi
  done
  if [ "$PROTOTIPO" -ne 0 ]; then
    falhar "guarda anti-protótipo: ainda há protótipo em memória"
  fi
fi
log "Guarda anti-protótipo OK (records de DemoData como DTO são permitidos)."

log ""
log "=============================================="
log "VERIFICAÇÃO DE INTEGRAÇÃO OK (8/8 etapas)"
log "Runtime do banco: ${DB_HOST}:${DB_PORT}/${DB_NAME}"
log "=============================================="
exit 0
