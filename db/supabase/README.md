# Aplicar o banco no Supabase

O projeto Supabase **precisa ser criado por você** (exige login na sua conta). Depois disso, escolha
**um** dos caminhos abaixo. Não aplique dois caminhos no mesmo banco sem recriar o schema.

## 1. Criar o projeto (manual, ~2 minutos)
1. Entre em `https://supabase.com/dashboard` e clique em **New project**.
2. Escolha organização, nome, senha do banco e região (ex.: `South America (São Paulo)`).
3. Espere o provisionamento terminar.
4. Vá em **Project Settings → Database → Connection string → URI** e copie a URI do
   **Session pooler (porta 5432)**. Ela tem o formato:
   `postgresql://postgres.<ref>:<senha>@<host>:5432/postgres?sslmode=require`
5. Guarde essa URI em `SUPABASE_DB_URL` (nunca commite a senha).

## 2. Caminho A — SQL Editor (mais rápido, sem instalar nada)
1. Abra **Database → SQL Editor**.
2. Abra `db/supabase/aplicar_tudo.sql`, copie todo o conteúdo e cole no editor.
3. Clique em **Run**. O arquivo já tem `BEGIN/COMMIT`, cria as extensões, as 13 tabelas, as 9 views,
   as 12 funções, os triggers e as políticas RLS.
4. Atualize **Table Editor** para ver as tabelas.
5. (Opcional, só dev) cole também `db/seed/dev_seed.sql` e rode para ter dados fictícios.

Conferência no SQL Editor:

```sql
select count(*) from imoveis;          -- 124 se aplicou o seed
select * from vw_home_indicadores;
select table_name from information_schema.tables
 where table_schema = 'public' order by 1;
```

> Se depois for usar Flyway no mesmo banco, **recrie o schema** (projeto novo):
> `drop schema public cascade; create schema public;` e então deixe o Flyway aplicar.
> Caso contrário o Flyway encontrará tabelas sem `flyway_schema_history` e falhará.

## 3. Caminho B — Flyway (recomendado para a aplicação)
1. Copie `db/migrations/V001..V011` para `src/main/resources/db/migration/`.
2. Configure `DB_URL` com a URI do Session pooler (5432) e suba a aplicação; o Flyway aplica em ordem.
   O Spring Boot **não lê `.env` sozinho**: carregue no terminal antes de rodar
   (`set -a; . ./.env; set +a`) ou use a biblioteca `spring-dotenv`; em produção, use os secrets da
   plataforma.
3. Rode `db/seed/primeiro_admin.sql` uma vez (ver comando abaixo).
4. Não use o Caminho A no mesmo banco.

## 4. Caminho C — script `psql`/Docker/Node
O script usa `psql`, Docker ou Node (node-postgres), o que estiver disponível. Se não houver `psql`
nem Docker, instale a dependência Node uma vez:

```bash
npm install --prefix scripts
```

**Opção 1 (recomendada) — `.env` do projeto.** O script carrega esse arquivo automaticamente:

```bash
cp .env.example .env
chmod 600 .env
nano .env
```

Preencha `SUPABASE_DB_URL` (e `DB_URL`/`DB_USER`/`DB_PASSWORD` se for subir o Spring Boot). O `.env`
já está no `.gitignore`; nunca commite.

**Opção 2 — configuração do usuário** (fora do repositório):

```bash
mkdir -p ~/.config/manuelo
nano ~/.config/manuelo/supabase.env
```

No editor, cole e salve (Ctrl+O, Enter, Ctrl+X):

```sh
SUPABASE_DB_URL='postgresql://postgres.<ref>:<senha>@<host>:5432/postgres?sslmode=require'
```

```bash
chmod 600 ~/.config/manuelo/supabase.env
```

Não use `cat <<EOF` nem `export` no terminal para isso: o comando inteiro (com a senha) ficaria no
histórico do shell. O script mascara a URL no output e não grava nada no repositório.

Ordem de leitura: `.env` do projeto → `~/.config/manuelo/supabase.env` → variável de ambiente/CI.
Depois:

```bash
# schema
./scripts/aplicar-supabase.sh

# schema + seed fictício (dev/homologação)
./scripts/aplicar-supabase.sh --seed

# schema + primeiro admin (hash bcrypt gerado pelo backend)
ADMIN_NOME='Administrador' \
ADMIN_EMAIL='admin@fundacao.org' \
ADMIN_HASH='$2a$10$...' \
./scripts/aplicar-supabase.sh --admin
```

Também dá para passar a URI direto, sem arquivo e sem `export`:

```bash
./scripts/aplicar-supabase.sh --url 'postgresql://...' --seed
```

O script recusa a porta 6543 (Transaction pooler) e não grava `flyway_schema_history`. Se a aplicação
usar Flyway, prefira o Caminho B.

## 5. Primeiro administrador (qualquer caminho)
```bash
psql "$SUPABASE_DB_URL" \
  -v nome="Administrador" \
  -v email="admin@fundacao.org" \
  -v senha_hash="$BCRYPT_HASH" \
  -f db/seed/primeiro_admin.sql
```

O hash deve vir do `BCryptPasswordEncoder` do backend. O script insere como `PENDENTE_APROVACAO` e
aprova por si na sequência; não há senha no repositório.

## 6. Onde ver o banco no dashboard
- **Table Editor**: tabelas e dados.
- **SQL Editor**: consultas e `select * from vw_home_indicadores;`.
- **Database**: tabelas, enums, funções, triggers, extensions e Schema Visualizer.
- **Storage**: crie o bucket privado `documentos` para os PDFs (ver `docs/db/integracao-equipe.md`).

## 7. Observações
- Extensões usadas: `pgcrypto`, `btree_gist`, `pg_trgm`. O Supabase as suporta; o bundle tenta
  `CREATE EXTENSION IF NOT EXISTS`.
- A V010 revoga o acesso de `anon`/`authenticated` se essas roles existirem. A aplicação usa JDBC
  direto com a role do projeto; as políticas RLS já estão criadas para `manuelo_app`.
- Não aplique `db/seed/dev_seed.sql` em produção.
- Migrations já commitadas não devem ser editadas; mudanças futuras viram `V012__...sql`.
