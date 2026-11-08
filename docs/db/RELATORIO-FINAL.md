# Relatório final — banco ManuElo (v3)

## 1. O que foi entregue
- 11 migrations Flyway-compatíveis em `db/migrations/V001..V011`.
- 13 arquivos de teste em `db/tests`, com casos positivos e negativos para cada CHECK, trigger,
  constraint e view.
- Seed fictício (`db/seed/dev_seed.sql`) e script do primeiro administrador
  (`db/seed/primeiro_admin.sql`).
- Runtime de verificação (`scripts/verificar-db.sh` e `scripts/pg-verify.mjs`).
- Bundle para o SQL Editor do Supabase (`db/supabase/aplicar_tudo.sql`), guia
  (`db/supabase/README.md`) e script `scripts/aplicar-supabase.sh`.
- `docker-compose.yml` para Postgres local.
- Documentação: `docs/db/dicionario.md`, `docs/db/schema.dbml`, `docs/db/progresso.md`,
  `docs/db/decisoes.md` e `docs/db/integracao-equipe.md`.
- 124 imóveis, 120 inquilinos, 60 fiadores, 110 contratos e 3040 movimentações no seed, com atrasos,
  estornos, comprovantes e caução.

Escopo respeitado: nenhum arquivo Java, tela ou recurso de front foi criado. As orientações para o
Spring Boot/Vaadin estão em `docs/db/integracao-equipe.md`.

## 2. Verificação (definição de pronto)
Última execução de `./scripts/verificar-db.sh`:

```
[ok  ] migration: V001 ... V011
[ok  ] seed: dev_seed.sql
[ok  ] test: 00_roles_stub.sql ... 12_volume_desempenho.sql
resumo: 11 migration(ões), 13 teste(s), 0 falha(s)
VERIFICAÇÃO OK (postgres embutido via Node)
```

Runtime usado: Postgres embutido via Node (`embedded-postgres 18.6.0-beta.19`), porque docker e
psql/initdb não estavam disponíveis no ambiente. O script tenta docker, depois Postgres local e, por
fim, o runtime Node. O banco é derrubado e recriado do zero a cada execução.

## 3. Como aplicar no Spring Boot (Flyway)
1. Copie `db/migrations/V001..V011` para `src/main/resources/db/migration/`.
2. Configure o Flyway para rodar no startup (`spring.flyway.enabled=true`) e use a conexão JDBC do
   Supabase no **Session pooler, porta 5432** (nunca o Transaction pooler).
3. As migrations instalam as extensões `pgcrypto`, `btree_gist` e `pg_trgm`. No Supabase, rode com um
   usuário que possa criar extensões (o owner do projeto) ou habilite-as antes no painel.
4. Rode `db/seed/primeiro_admin.sql` uma única vez, via psql, com `-v nome=... -v email=...
   -v senha_hash=...`. O hash deve ser gerado pelo backend com BCrypt. O script não contém segredo.
5. O seed de desenvolvimento (`dev_seed.sql`) não deve ser aplicado em produção.
6. Depois das migrations, a role de aplicação `manuelo_app` existe, mas é `NOLOGIN`. No Supabase,
   conceda-a ao usuário JDBC ou use a role padrão do projeto; as políticas RLS já estão criadas.
7. Não edite migrations já aplicadas. Crie `V012__...sql` para qualquer ajuste.

Observação: o Supabase já possui as roles `anon` e `authenticated`; a V010 só revoga o acesso delas se
existirem. O schema `public` mantém `USAGE` para `PUBLIC` de propósito (evita quebrar outras roles do
Supabase); o bloqueio é por tabela/função e por RLS.

## 4. Modelo e regras cobertas
- 13 tabelas: `usuarios`, `tokens_redefinicao_senha`, `imoveis`, `inquilinos`, `fiadores`, `contratos`,
  `caucao_movimentacoes`, `movimentacoes`, `documentos`, `reformas`, `relatorios`,
  `relatorio_movimentacoes`, `relatorio_contratos` e `configuracoes`.
- 9 views e 12 funções derivadas (extrato, resumo, situação de contratos/imóveis, home, reformas,
  documentos, saldo de caução, movimentações recentes).
- Regras R01–R11 implementadas e testadas:
  - R01 imutabilidade de movimentações/caução + estorno total, único, por administrador, mesmo valor.
  - R02 correção = estorno prévio + nova movimentação com `corrige_movimentacao_id` (deferido).
  - R03 categoria × tipo, competência no 1º dia do mês, imóvel do contrato, origem em outras entradas,
    `CAUCAO` só em aluguel.
  - R04 comprovante obrigatório em SAIDA (constraint trigger deferido).
  - R05 atraso por competência vencida e líquida de estornos, a partir de
    `acompanhar_pagamento_desde`.
  - R06 próximo do vencimento e vencido por `fim`.
  - R07 caução em livro próprio, abatimento coerente, saldo nunca negativo.
  - R08 vínculo mínimo, MIME e tamanho de documento, classificação.
  - R09 relatório insert-only com composição exata.
  - R10 usuário nunca apagado e token de uso único.
  - R11 RLS e permissões mínimas.
- Decisões registradas: DEC-01 a DEC-13 no `CONTEXTO.md`; DEC-14 a DEC-16 em `docs/db/decisoes.md`
  (ordem de migrations, endereço obrigatório, documento/telefone só dígitos).

## 5. Desempenho
Com o seed (124 imóveis e 3040 movimentações), antes e depois da V011:

| Consulta | Antes | Depois |
|---|---:|---:|
| `vw_home_indicadores` | 1163 ms | 39 ms |
| `vw_contratos_situacao` | 381 ms | 37 ms |
| `vw_contratos_atrasados` | 380 ms | 59 ms |
| `vw_contratos_a_vencer` | 369 ms | 32 ms |
| `fn_extrato_periodo` (18 meses) | 16 ms | 14 ms |

A V011 agregou os pagamentos uma única vez por competência em `fn_contrato_atraso` e fez a home usar
CTEs `MATERIALIZED`, evitando recalcular `vw_contratos_situacao` três vezes. O teste
`12_volume_desempenho.sql` falha se qualquer consulta passar de 2 s.

## 6. Riscos e mitigação
| Risco | Mitigação |
|---|---|
| Extensões indisponíveis no Supabase | Habilitar `pgcrypto`/`pg_trgm` no painel ou rodar as migrations com o owner; `btree_gist` é necessária para a constraint de sobreposição. |
| Transaction pooler do Supabase | Usar Session pooler (5432); o Transaction pooler não mantém recursos de sessão e pode quebrar deferimentos/transações. |
| JPA tentar atualizar `movimentacoes`/`caucao_movimentacoes` | Mapear como insert-only e não expor `save`/`delete`; o banco rejeita UPDATE/DELETE com `55000`. |
| `LocalDateTime` × `timestamptz` | Mapear `timestamptz` para `Instant`/`OffsetDateTime`; `date` para `LocalDate`. |
| `numeric(14,2)` em `double` | Mapear para `BigDecimal`. |
| Enums | `@Enumerated(EnumType.STRING)` com os nomes exatos; não usar ordinal. |
| RLS + JDBC | Se o usuário JDBC não for `manuelo_app`, ajuste as políticas/concessões na V012; não desligue RLS. |
| Dados de seed em produção | Aplicar somente em desenvolvimento/homologação; o primeiro admin usa script separado. |
| Timezone | Cálculos de atraso usam `current_date`; o servidor deve operar em `America/Maceio` ou o relatório deve fixar a data de referência. |

## 7. O que o backend ainda precisa fazer (fora do escopo do banco)
- Autenticação/autorização (Spring Security, BCrypt, aprovação de cadastro, recuperação de senha).
- Upload para o Supabase Storage e gravação de `documentos.caminho_armazenamento`.
- Serviços de aplicação para movimentações, estornos, contratos, imóveis e relatórios.
- Geração dos 4 PDFs (OpenPDF/JasperReports) e gravação da composição em `relatorio_*`.
- Telas Vaadin e filtros. O banco já suporta todos os filtros do Figma (DEC-13).
- Esqueleto Spring Boot (Java 21, Spring Boot 3, Vaadin, JPA): instruções detalhadas em
  `docs/db/integracao-equipe.md`, incluindo RNF1 (conexão/ORM) e RNF2 (popular banco).

## 8. Checklist de aceite
- [x] `./scripts/verificar-db.sh` sai com código 0.
- [x] Banco recriado do zero a cada execução.
- [x] Teste positivo e negativo para cada CHECK, trigger, constraint e view.
- [x] Seed fictício com volume e casos de atraso/estorno/caução.
- [x] Nenhuma senha ou dado real no repositório.
- [x] `schema.dbml`, dicionário e este relatório atualizados.
- [x] Migrations não são editadas depois de commitadas (V011 é a evolução da V009).
