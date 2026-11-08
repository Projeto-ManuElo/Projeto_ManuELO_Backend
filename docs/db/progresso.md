# Progresso (memória do loop; a conversa não é memória)

Iteração atual: 6 | Orçamento: 40 iterações | Máx. 3 tentativas por tarefa
Runtime usado: postgres embutido via Node (embedded-postgres 18.6.0-beta.19; docker e psql indisponíveis)
Última atualização: T06 concluída

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
- [ ] T08 V006 documentos (checks de vínculo, tamanho, classificação) e reformas.
      (Antecipada: T07 depende de `documentos` para o comprovante de SAÍDA; DEC-14.)
- [ ] T07 V007 regras de integridade das movimentações: imutabilidade, estorno (admin, valor igual,
      sem duplo estorno), imóvel coerente com o contrato, saldo de caução, comprovante em SAIDA (deferido).
- [ ] T09 V008 relatorios, relatorio_movimentacoes, relatorio_contratos, configuracoes (+ seeds de config).
- [ ] T10 V009 views e funções: extrato e resumo por período (entradas, saídas, saldo, estornos),
      situação de contratos (validade e atraso), situação de imóveis, indicadores da home, histórico de
      reformas, lista de documentos, saldo de caução.
- [ ] T11 V010 segurança: RLS em todas as tabelas, revoke para anon/authenticated (protegido por
      "se a role existir"), role manuelo_app com privilégio mínimo.
- [ ] T12 Seed fictício (db/seed/dev_seed.sql: 124 imóveis, contratos, movimentações com estornos e
      atrasos) e db/seed/primeiro_admin.sql (parâmetros por variável psql, sem senha no repositório).
- [ ] T13 Teste de volume: as views respondem em menos de 2 s com 124 imóveis e 3000 movimentações.
- [ ] T14 Entregáveis finais: docs/db/schema.dbml (para dbdiagram.io), docs/db/dicionario.md,
      docs/db/RELATORIO-FINAL.md (decisões, riscos, como aplicar em src/main/resources/db/migration).

## Bloqueios (tarefa travada: diagnóstico e o que já tentou)
(nenhum)

## Log (uma entrada por iteração: tarefa, arquivos, resultado da verificação)
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
