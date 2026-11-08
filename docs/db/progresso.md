# Progresso (memória do loop; a conversa não é memória)

Iteração atual: 0 | Orçamento: 40 iterações | Máx. 3 tentativas por tarefa
Runtime usado: (docker | postgres local | pglite)
Última atualização: (preencher a cada iteração)

## Tarefas (uma por iteração; a ordem respeita as dependências)
- [ ] T01 Ambiente e juiz: docker-compose.yml, scripts/verificar-db.sh, db/tests/00_roles_stub.sql
      (cria roles anon/authenticated só no teste). O script já deve rodar e passar com 0 migrations.
- [ ] T02 V001 extensões (btree_gist, pg_trgm), enums, função de auditoria (atualizado_em).
- [ ] T03 V002 usuarios (status, aprovação) e tokens_redefinicao_senha, com testes dos checks.
- [ ] T04 V003 imoveis, inquilinos, fiadores, índices de busca (nome, CPF, endereço).
- [ ] T05 V004 contratos (dia_vencimento, ação judicial, sem sobreposição) e caucao_movimentacoes.
- [ ] T06 V005 movimentacoes: checks por tipo e categoria, competência, forma CAUCAO, índices.
- [ ] T07 V006 regras de integridade das movimentações: imutabilidade, estorno (admin, valor igual,
      sem duplo estorno), imóvel coerente com o contrato, saldo de caução, comprovante em SAIDA (deferido).
- [ ] T08 V007 documentos (checks de vínculo, tamanho, classificação) e reformas.
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
