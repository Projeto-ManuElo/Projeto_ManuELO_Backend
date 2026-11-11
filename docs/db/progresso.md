# Progresso (memória do loop; a conversa não é memória)

Iteração atual: 23 | Orçamento: 40 iterações | Máx. 3 tentativas por tarefa
Runtime usado: postgres embutido via Node (plano C; docker presente, mas `use_docker` perde a corrida do initdb)
Última atualização: T24 concluída (integração Vaadin: `ui/` + `src/main/frontend`)

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
- [x] T21 Organização frontend/backend: cópia fiel dos 33 arquivos versionados do repositório de
      frontend para `frontend/` (original intacto, backend intacto) e análise de integração em
      `docs/db/integracao-estrutura.md`. Nenhuma integração implementada (DEC-17..DEC-19).
- [x] T23 Consolidação do Maven: um único build na raiz compila `frontend/src/main/java`, copia os
      recursos do frontend, empacota as migrations em `db/migration` e gera o JAR executável Vaadin
      + Spring Boot. `frontend/pom.xml` removido (DEC-20..DEC-22). Sem alteração de Java, SQL ou
      regras; build validado com `mvnw package`.
- [x] T24 Integração Vaadin: renomeia `frontend/` para `ui/`, move assets para `src/main/frontend/`,
      corrige `pom.xml`/`tsconfig.json`, move Dockerfile para a raiz, remove a classe de boot
      duplicada, adiciona `scanBasePackages`, `SecurityConfig` temporário e baseline do Flyway;
      valida dev (`/` 200) e `clean package -DskipTests` (DEC-24).

## Bloqueios (tarefa travada: diagnóstico e o que já tentou)
(nenhum)

## Log (uma entrada por iteração: tarefa, arquivos, resultado da verificação)
### T24 — integração Vaadin e execução local
- Arquivos: `pom.xml`; `tsconfig.json`; `src/main/resources/application.properties`; `.gitignore`;
  `.dockerignore`; `Dockerfile` (movido de `ui/`); `ui/**` (renomeado de `frontend/`);
  `src/main/frontend/animacoes.css`; remoção de `ui/src/main/java/br/com/manuelo/Application.java`,
  `ui/mvnw`, `ui/mvnw.cmd` e `ui/.mvn`; `src/main/java/com/Projeto/manuelo/config/SecurityConfig.java`;
  `ManueloApplication.java`; `docs/db/decisoes.md` (DEC-24); `docs/db/integracao-estrutura.md`.
- Causa raiz: o Vaadin trata `frontend/` como frontend legado no dev server e ignora
  `<frontendDirectory>`; o build de produção usa o `frontendDirectory`. O CSS ficava em um caminho
  para cada modo, gerando `[UNRESOLVED_IMPORT]` no Vite. O projeto também tinha duas classes
  `@SpringBootApplication`, Spring Security bloqueando `/` e Flyway sem histórico no schema criado
  pelo script SQL.
- Correções: assets em `src/main/frontend/`; Java/resources em `ui/`; `scanBasePackages` inclui
  `br.com.manuelo`; `SecurityConfig` libera todas as rotas (temporário, sem auth real);
  `baseline-on-migrate`/`baseline-version=11`; `.env` local derivado de `SUPABASE_DB_URL`
  (`DB_URL`, `DB_USER`, `DB_PASSWORD` preenchidos; segredo não versionado).
- Verificação: `JAVA_HOME=/home/joaomiguel/.cache/manuelo/jdk21 ./mvnw -B clean package -DskipTests`
  → BUILD SUCCESS (Vite 5,5 s; 13 fontes; JAR repackage). `spring-boot:run` contra Postgres embutido
  (`127.0.0.1:39237/manuelo`) → `Started ManueloApplication`, `/` HTTP 200, sem erro do Vite;
  `/tmp/app-ui2.log`. `mvnw test` falha apenas por ausência de Docker/Testcontainers.
- P4/P7 resolvidos (wrappers inertes removidos; Dockerfile/.dockerignore na raiz); P5 resolvido
  (`src/main/frontend/` agora é a fonte real); P6 mantido (`ui/src/main/bundles/` é bundle versionado
  conforme o README do Vaadin).
- Correção pós-validação: `/` exibia `No views found` porque o `RouteRegistry` não via
  `br.com.manuelo`; `vaadin.allowed-packages` não acrescenta raízes de varredura. Adicionado
  `@EnableVaadin("br.com.manuelo")`, removida a implementação duplicada de `AppShellConfigurator`
  da classe de boot e o `HomeView` de exemplo em `com.Projeto.manuelo.views`. Confirmado por
  listener temporário: 8 rotas registradas, incluindo `"" -> br.com.manuelo.LoginView` e
  `home -> br.com.manuelo.HomeView` (DEC-25).
- Pendentes: validar visualmente as telas em navegador; decidir auth real; trocar o baseline fixo
  quando houver V012; `ui/README.md` e `ui/.mcp.json` continuam cópias históricas.
- Nenhum commit/push nesta tarefa; working tree contém as mudanças.

### T23.4 — configurações Vaadin/Vite da raiz (P1, P2 e P8)
- Arquivos: `package.json`, `package-lock.json`, `tsconfig.json`, `types.d.ts` e `vite.config.ts` da
  raiz (P1, adicionados ao índice); as cinco cópias correspondentes em `frontend/` (P2, retiradas do
  índice com `git rm --cached`, preservadas no disco); `docs/db/decisoes.md` (DEC-23);
  `docs/db/progresso.md`. Nenhum `.java`, `.sql`, `pom.xml`, `application.properties` ou arquivo de
  `db/` foi alterado.
- Contexto: a T23.3 (commit `341d389`) já havia ignorado os artefatos gerados e desrastreado 460
  arquivos de `src/main/frontend/generated/`; a auditoria da T23.4, somente leitura, classificou os
  arquivos de configuração e propôs as ações P1..P8.
- P1 executada: os cinco arquivos de configuração da raiz passam a ser versionados. São a fonte
  efetiva do build (`npmFolder` = raiz desde a DEC-21) e o próprio `tsconfig.json`/`types.d.ts` traz
  "recommended to commit". `vite.generated.ts` continua ignorado por ser sobrescrito a cada execução
  do `flow:prepare-frontend`.
- P2 executada: as cinco cópias de `frontend/` saíram do controle de versão sem serem apagadas do
  disco (mesmo SHA-256 antes e depois). Nada as referencia — nem `pom.xml`, nem Java, nem scripts — e
  a cópia de `vite.generated.ts` estava inválida (`./..\target/...`).
- P8 executada: DEC-23 registra a raiz como local oficial das configurações npm, Vite e TypeScript, a
  origem das cópias antigas de `frontend/` e a distinção entre arquivo de configuração-fonte e arquivo
  gerado pelo Vaadin/Vite.
- Nenhum commit e nenhum push foram feitos; as mudanças ficaram no índice e no working tree.
- Pendentes: P4 (remover do disco as cópias de `frontend/`, `frontend/target/` e
  `frontend/node_modules/`), P5 (`src/main/frontend/` estale), P6
  (`frontend/src/main/bundles/README.md` e `dev.bundle`) e P7 (resíduo Maven/Docker de `frontend/`;
  o `Dockerfile` chama `./mvnw` sem `frontend/pom.xml`).
- Pendente de validação: build `mvnw -B -DskipTests package` depois das mudanças (não executado nesta
  tarefa). Banco não afetado: nenhuma migration, seed ou teste alterado.

### T23 — consolidação do Maven na raiz
- Arquivos: `pom.xml` (raiz); `frontend/pom.xml` (removido); `docs/db/decisoes.md` (DEC-20..DEC-22);
  `docs/db/progresso.md`. Nenhum `.java`, `.sql`, `application.properties` ou `db/` alterado.
- `pom.xml`: `build-helper-maven-plugin:3.6.1` (add-source de `frontend/src/main/java`); `<resource>`
  de `frontend/src/main/resources` com `application.properties` excluído; dependência
  `com.vaadin:vaadin` (como no frontend); `vaadin-maven-plugin` com
  `<frontendDirectory>frontend/src/main/frontend</frontendDirectory>`;
  `spring-boot-maven-plugin` com `<mainClass>com.Projeto.manuelo.ManueloApplication</mainClass>`.
- Build: `mvnw -B -DskipTests package` → BUILD SUCCESS em 4min39 (Vite 27 s; 13 fontes compiladas;
  11 migrations + 2 recursos do frontend em `target/classes`; JAR repackage com
  `Start-Class: com.Projeto.manuelo.ManueloApplication`). Repetido com
  `mvnw -B -DskipTests clean package` → BUILD SUCCESS em 4min27 (JAR de 106 MB, 15 `.class` de
  `br.com.manuelo`, 11 migrations, `META-INF/VAADIN` em modo produção).
- JAR executado sem `DB_URL`: Spring Boot 4.1.2 sobe o Tomcat e o contexto e falha apenas em
  `dataSource`/`flyway` (`'url' must start with "jdbc"`), sem tocar em banco. Inicialização real
  com banco não foi validada (sem DB nesta tarefa).
- `mvnw -B -DskipTests test-compile` → BUILD SUCCESS. `dependency:tree` confirma `com.vaadin:vaadin:25.3.5`.
- Verificação de banco (plano C, `node scripts/pg-verify.mjs`): `11 migration(ões), 13 teste(s),
  0 falha(s)` → OK; `scripts/verificar-db.sh` não roda no bash do WSL por CRLF no working tree
  (arquivo intocado).
- Efeitos colaterais de build (não versionados): `package.json`, `package-lock.json`, `tsconfig.json`,
  `types.d.ts`, `vite.config.ts`, `vite.generated.ts`, `node_modules/`, `src/main/bundles/prod.bundle`
  na raiz, além de `frontend/target/` e `frontend/src/main/frontend/generated/`. O
  `.vaadin-node-tasks.lock` preexistente foi apagado pelo próprio build (arquivo de trava efêmero);
  não foi recriado para não forjar PID.

### T21 — organização frontend/backend e cópia do frontend
- Arquivos: `frontend/**` (33 arquivos versionados copiados de
  `C:\Users\ronald\CESAR\Projeto_ManuELO_Frontend`); `docs/db/integracao-estrutura.md`;
  `docs/db/decisoes.md` (DEC-17..DEC-19); `docs/db/progresso.md`.
- Nada em `db/`, `src/`, `pom.xml`, `scripts/` ou `docker-compose.yml` foi alterado.
- Cópia por `git archive HEAD | tar -x` (só versionados); fora `.git`, `target/`, `node_modules/`,
  `src/main/frontend/generated/`, `.vscode/`, `.github/`. 33/33 com SHA-256 idêntico; origem com
  `git status` limpo e HEAD `a335026`.
- Conflitos: nenhum no destino (`frontend/` não existia); `pom.xml`, `README.md`, `.mvn/`,
  `application.properties` e `mvnw` do frontend ficaram isolados sob `frontend/`.
- Verificação: `11 migration(ões), 13 teste(s), 0 falha(s)` → VERIFICAÇÃO OK (plano C). O Docker está
  disponível, mas `use_docker` falha com `FATAL: the database system is shutting down` porque
  `pg_isready` responde no servidor temporário do `initdb`; corrigir em tarefa futura (aguardar o
  healthcheck do container).

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
