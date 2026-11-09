# Progresso (memória do loop; a conversa não é memória)

Iteração atual: 20 | Orçamento: 40 iterações | Máx. 3 tentativas por tarefa
Runtime usado: postgres embutido via Node (embedded-postgres 18.6.0-beta.19; docker e psql indisponíveis)
Última atualização: T20 concluída

## Entendimento inicial (8 linhas)
1. O ManuElo é um sistema financeiro/documental; o banco é PostgreSQL (Supabase) com migrations Flyway.
2. O modelo v3 tem usuários, imóveis, inquilinos, fiadores, contratos, caução, movimentações,
   documentos, reformas, relatórios e configurações, além de views/funções derivadas.
3. Movimentações e caução são imutáveis: correção só por ESTORNO total, com autoria e justificativa.
4. Contrato atrasado é dívida de competência de aluguel, não data de fim; próximo do vencimento é `fim`.
5. Toda tabela editável tem auditoria; nada é apagado fisicamente; FKs sempre `ON DELETE RESTRICT`.
6. A verificação é o script `scripts/verificar-db.sh` do zero, com teste positivo e negativo por regra.
7. Entrego migrations, triggers, views, seed fictício, testes, dicionário, schema.dbml e relatório final.
8. O escopo é só banco: não crio Java, telas, nem importo dados reais.

## Tarefas (uma por iteração; a ordem respeita as dependências)
- [x] T01 Ambiente e juiz: docker-compose.yml, scripts/verificar-db.sh, db/tests/00_roles_stub.sql
      (cria roles anon/authenticated só no teste). O script já deve rodar e passar com 0 migrations.
- [x] T02 V001 extensões (btree_gist, pg_trgm), enums, função de auditoria (atualizado_em).
- [x] T03 V002 usuarios (status, aprovação) e tokens_redefinicao_senha, com testes dos checks.
- [x] T04 V003 imoveis, inquilinos, fiadores, índices de busca (nome, CPF, endereço).
- [x] T05 V004 contratos (dia_vencimento, ação judicial, sem sobreposição) e caucao_movimentacoes.
- [x] T06 V005 movimentacoes: checks por tipo e categoria, competência, forma CAUCAO, índices.
- [x] T08 V006 documentos (checks de vínculo, tamanho, classificação) e reformas.
      (Antecipada: T07 depende de `documentos` para o comprovante de SAÍDA; DEC-14.)
- [x] T07 V007 regras de integridade das movimentações: imutabilidade, estorno (admin, valor igual,
      sem duplo estorno), imóvel coerente com o contrato, saldo de caução, comprovante em SAIDA (deferido).
- [x] T09 V008 relatorios, relatorio_movimentacoes, relatorio_contratos, configuracoes (+ seeds de config).
- [x] T10 V009 views e funções: extrato e resumo por período (entradas, saídas, saldo, estornos),
      situação de contratos (validade e atraso), situação de imóveis, indicadores da home, histórico de
      reformas, lista de documentos, saldo de caução.
- [x] T11 V010 segurança: RLS em todas as tabelas, revoke para anon/authenticated (protegido por
      "se a role existir"), role manuelo_app com privilégio mínimo.
- [x] T12 Seed fictício (db/seed/dev_seed.sql: 124 imóveis, contratos, movimentações com estornos e
      atrasos) e db/seed/primeiro_admin.sql (parâmetros por variável psql, sem senha no repositório).
- [x] T13 Teste de volume: as views respondem em menos de 2 s com 124 imóveis e 3000 movimentações.
- [x] T14 Entregáveis finais: docs/db/schema.dbml (para dbdiagram.io), docs/db/dicionario.md,
      docs/db/RELATORIO-FINAL.md (decisões, riscos, como aplicar em src/main/resources/db/migration).
- [x] T15 Handoff de integração: docs/db/integracao-equipe.md (RNF1, RNF2, autenticação, Storage de PDF,
      busca/filtros, edição segura de imóveis e E1.1 cadastrar imóveis).
- [x] T16 Publicação no Supabase: bundle SQL para o SQL Editor (db/supabase/aplicar_tudo.sql),
      gerador (scripts/gerar-bundle-supabase.sh), guia (db/supabase/README.md) e aplicador via
      psql/Docker (scripts/aplicar-supabase.sh). A criação do projeto continua manual (login).
- [x] T17 Automação da conexão: o script lê ~/.config/manuelo/supabase.env e aceita --url; guia
      atualizado para definir a URI uma vez fora do repositório.
- [x] T18 Guia seguro: troca do heredoc por editor (`nano`) para não gravar a URI/senha no histórico
      do shell; requisito de `psql`/Docker explicitado.
- [x] T19 Ambiente local: `.env.example` versionado, `.env` local ignorado, `.gitignore`, script lê o
      `.env` do projeto antes do config do usuário/CI, guia e integração atualizados.
- [x] T20 Aplicador Node (sem sudo/psql) + correção do seed: `scripts/aplicar-supabase.mjs` com
      `node-postgres`, dependência em `scripts/package.json`; seed passa a acompanhar 17 meses para
      não marcar contratos pagos como atrasados.

## Bloqueios (tarefa travada: diagnóstico e o que já tentou)
(nenhum)

## Log (uma entrada por iteração: tarefa, arquivos, resultado da verificação)
### T20 — aplicador Node e correção do seed
- Arquivos: scripts/aplicar-supabase.mjs; scripts/package.json; scripts/package-lock.json;
  scripts/aplicar-supabase.sh; .gitignore; db/seed/dev_seed.sql; db/tests/11_seed_ficticio.sql;
  db/supabase/README.md.
- O ambiente não tem `sudo`/`apt-get` (runtime Flatpak). O script agora cai para Node quando não há
  psql/Docker; `npm install --prefix scripts` trouxe `pg` 8.x. Testado contra Postgres embutido:
  124 imóveis, 3040 movimentações, admin criado, `vw_home_indicadores` OK.
- Correção: `acompanhar_pagamento_desde` do seed ficou em 17 meses, alinhado às 18 competências pagas
  (m=0..17); antes todos os 110 contratos apareciam atrasados. Teste 11 agora exige >= 10 atrasados e
  >= 80 contratos em dia.
- Verificação: `11 migration(ões), 13 teste(s), 0 falha(s)` → VERIFICAÇÃO OK.

### T19 — .env do projeto
- Arquivos: .env.example (versionado); .env (local, ignorado); .gitignore;
  scripts/aplicar-supabase.sh; db/supabase/README.md; docs/db/integracao-equipe.md.
- Ordem de precedência: `.env` do projeto → `~/.config/manuelo/supabase.env` → variável de
  ambiente/CI. `.env` está no `.gitignore`; o modelo `.env.example` é o único versionado.
- O Spring Boot não lê `.env` sozinho; o guia mostra `set -a; . ./.env; set +a` (ou `spring-dotenv`).
- Testes: `sh -n`, `--help`, rejeição de porta 6543 e confirmação de que `.env` é ignorado pelo Git.
- Verificação de banco inalterada: 11 migrations, 13 testes, 0 falhas.

### T18 — configuração da URI sem vazamento
- Arquivos: db/supabase/README.md.
- O guia não ensina mais `cat <<EOF` (que grava a senha no histórico); usa `nano` no arquivo
  `~/.config/manuelo/supabase.env` com `chmod 600` e explica que o script mascara a URL.
- Verificação de banco inalterada: 11 migrations, 13 testes, 0 falhas.

### T17 — configuração única da URI do Supabase
- Arquivos: scripts/aplicar-supabase.sh; db/supabase/README.md.
- O script agora carrega `~/.config/manuelo/supabase.env` (ou `$XDG_CONFIG_HOME`) quando nenhuma
  variável está definida e aceita `--url <URI>`. Continua recusando a porta 6543 e não grava segredo
  no repositório. Testado `sh -n`, `--help` e rejeição da porta 6543.
- Verificação de banco inalterada: 11 migrations, 13 testes, 0 falhas.

### T16 — bundle e aplicação no Supabase
- Arquivos: db/supabase/aplicar_tudo.sql (gerado); db/supabase/README.md;
  scripts/gerar-bundle-supabase.sh; scripts/aplicar-supabase.sh.
- O bundle concatena V001..V011 em uma transação com `search_path = public, extensions`; foi aplicado
  em Postgres limpo e passou nos 13 testes. O script usa psql ou Docker, recusa a porta 6543 e aplica
  seed/admin opcionais.
- Bloqueio real: criar o projeto Supabase exige login na conta do usuário; não há token/CLI no ambiente.
  O caminho manual está documentado; depois de criar, basta colar o bundle no SQL Editor.
- Verificação: bundle + seed + `13 teste(s), 0 falha(s)` → OK.

### T15 — handoff de integração com o backend
- Arquivos: docs/db/integracao-equipe.md.
- Cobre RNF1 (Maven/Spring Boot 3, Session pooler 5432, `ddl-auto=validate`, mapeamento JPA,
  transações com triggers deferidos), RNF2 (Flyway, seed, primeiro admin), autenticação/aprovação,
  upload em bucket privado do Supabase, buscas com trigram, edição segura de imóveis e o fluxo E1.1
  (imóvel + contrato + documento na mesma transação).
- Verificação: sem mudança de SQL; `11 migration(ões), 13 teste(s), 0 falha(s)`.

### T14 — entregáveis de documentação
- Arquivos: docs/db/schema.dbml; docs/db/dicionario.md; docs/db/RELATORIO-FINAL.md.
- O schema.dbml reflete as 13 tabelas, enums, FKs e índices reais (V001..V011); as views/funções estão
  listadas no fim. O dicionário cobre colunas, CHECKs, triggers, views, funções e consultas de exemplo.
- O relatório final traz como aplicar no Flyway, evidência da verificação, desempenho antes/depois da
  V011, riscos e checklist. Nenhum arquivo Java foi criado (escopo).
- Verificação: `11 migration(ões), 13 teste(s), 0 falha(s)` → VERIFICAÇÃO OK.

### T13 — volume e desempenho
- Arquivos: db/migrations/V011__desempenho_views.sql; db/tests/12_volume_desempenho.sql.
- `fn_contrato_atraso` passou a agregar pagamentos uma única vez por competência; `vw_home_indicadores`
  usa CTEs MATERIALIZED para não recalcular `vw_contratos_situacao` três vezes.
- Medido com o seed: home 39 ms (era 1163 ms), contratos 37 ms, atrasados 59 ms, a vencer 32 ms,
  extrato 18 meses 14 ms. Limite do teste: 2 s por consulta e média da home.
- Verificação: `11 migration(ões), 13 teste(s), 0 falha(s)` → VERIFICAÇÃO OK.

### T12 — seed fictício
- Arquivos: db/seed/dev_seed.sql; db/seed/primeiro_admin.sql; db/tests/11_seed_ficticio.sql.
- Volume: 124 imóveis, 120 inquilinos, 60 fiadores, 110 contratos e 3040 movimentações
  (2150 entradas, 860 saídas com comprovante, 30 estornos), 110 depósitos e 10 abatimentos de caução.
- O primeiro admin é criado via psql com variáveis `:nome`, `:email`, `:senha_hash` (hash fornecido pelo
  backend), sem segredo no repositório; entra PENDENTE_APROVACAO e é aprovado por si na sequência.
- Verificação: `10 migration(ões), 12 teste(s), 0 falha(s)` → VERIFICAÇÃO OK.

### T11 — segurança RLS
- Arquivos: db/migrations/V010__seguranca_rls.sql; db/tests/10_seguranca_rls.sql.
- Nota: não revoguei USAGE do schema public de PUBLIC (quebraria outras roles do Supabase); o acesso de
  anon/authenticated é negado no nível das tabelas.
- Verificação: `10 migration(ões), 11 teste(s), 0 falha(s)` → VERIFICAÇÃO OK.

### T10 — views e funções derivadas
- Arquivos: db/migrations/V009__views_funcoes.sql; db/tests/09_views_funcoes.sql.
- Verificação: `9 migration(ões), 10 teste(s), 0 falha(s)` → VERIFICAÇÃO OK.

### T09 — relatórios e configurações
- Arquivos: db/migrations/V008__relatorios_configuracoes.sql; db/tests/08_relatorios_configuracoes.sql.
- Verificação: `8 migration(ões), 9 teste(s), 0 falha(s)` → VERIFICAÇÃO OK.

### T07 — regras de integridade e imutabilidade
- Arquivos: db/migrations/V007__regras_movimentacoes_caucao.sql; db/tests/07_regras_movimentacoes_caucao.sql.
- Correção: o teste de duplo estorno agora espera `check_violation` (o trigger responde antes da unique).
- Verificação: `7 migration(ões), 8 teste(s), 0 falha(s)` → VERIFICAÇÃO OK.

### T08 — documentos e reformas (V006, antecipada)
- Arquivos: db/migrations/V006__documentos_reformas.sql; db/tests/06_documentos_reformas.sql.
- Verificação: `6 migration(ões), 7 teste(s), 0 falha(s)` → VERIFICAÇÃO OK.

### T06 — movimentações
- Arquivos: db/migrations/V005__movimentacoes.sql; db/tests/05_movimentacoes.sql.
- Correção: CHECK de tipo/categoria precisava de `categoria IS NOT NULL` para não passar com NULL.
- Verificação: `5 migration(ões), 6 teste(s), 0 falha(s)` → VERIFICAÇÃO OK.

### T05 — contratos e caução
- Arquivos: db/migrations/V004__contratos_caucao.sql; db/tests/04_contratos_caucao.sql.
- Verificação: `4 migration(ões), 5 teste(s), 0 falha(s)` → VERIFICAÇÃO OK.

### T04 — imóveis, inquilinos e fiadores
- Arquivos: db/migrations/V003__imoveis_inquilinos_fiadores.sql; db/tests/03_imoveis_inquilinos_fiadores.sql.
- Verificação: `3 migration(ões), 4 teste(s), 0 falha(s)` → VERIFICAÇÃO OK.

### T03 — usuários e tokens de senha
- Arquivos: db/migrations/V002__usuarios_tokens.sql; db/tests/02_usuarios_tokens.sql.
- Verificação: `2 migration(ões), 3 teste(s), 0 falha(s)` → VERIFICAÇÃO OK.

### T02 — extensões, enums e auditoria
- Arquivos: db/migrations/V001__extensoes_enums_auditoria.sql; db/tests/01_estrutura_base.sql.
- Verificação: `1 migration(ões), 2 teste(s), 0 falha(s)` → VERIFICAÇÃO OK.

### T01 — ambiente e juiz
- Arquivos: docker-compose.yml; scripts/verificar-db.sh; scripts/pg-verify.mjs; db/tests/00_roles_stub.sql.
- Runtime: sem docker/psql na máquina; usei Postgres embutido via Node (plano C).
- Verificação: `0 migration(ões), 1 teste(s), 0 falha(s)` → VERIFICAÇÃO OK.
