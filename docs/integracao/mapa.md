# Mapa do projeto ManuElo (auditoria do mapeador — F0/F1)

Última auditoria: 12/11/2026. Fontes: código, `db/`, `pom.xml`, execução real do app e do juiz.
Runtime usado na auditoria: Postgres embutido via Node (`embedded-postgres 18.6.0-beta.19`) em
`127.0.0.1:55434` (auditoria) e porta efêmera no juiz; app em `127.0.0.1:8081`.

## Estado inicial informado pelo agente de banco (CONFIRMADO/REFUTADO com evidência)

| # | Afirmação | Veredito | Evidência |
|---|---|---|---|
| 1 | As views criam a lista em memória (`DemoData.imoveis()` etc.) em ImoveisView, ContratosView, InquilinosView, DocumentosView e outras | **REFUTADO** | As views guardam um campo `List`, mas ele é carregado do banco: `ImoveisView.java:37` `this.imoveis = repositorio.listar();`; `ContratosView.java:39`; `InquilinosView.java:30`; `DocumentosView.java:34`; `MovimentacaoFinanceiraView.java:46`; `HomeView.java:52`. `DemoData.java` só tem `record`s (linhas 11-44), não há fábrica `DemoData.imoveis()`. Os filtros ainda são aplicados em memória (`ImoveisView.java:104-143`). |
| 2 | Os diálogos só alteram a lista da instância da view; ao navegar/recarregar volta ao DemoData | **REFUTADO** | Os diálogos chamam repositórios que gravam no Postgres: `ImoveisView.java:146-153` → `ImovelRepositorio.salvar` (`ImovelRepositorio.java:86-141`); `InquilinosView.java:100-106`; `ContratosView.java:148-154` → `ContratoRepositorio.criar` (`ContratoRepositorio.java:121-165`); `DocumentosView.java:111-116`; `MovimentacaoFinanceiraView.java:145-158`; `ReformasView.java:77-86`. |
| 3 | Não existe camada de persistência: nenhum `@Entity`, `JpaRepository`, `JdbcTemplate` ou `EntityManager` | **REFUTADO (em parte)** | `@Entity`, `JpaRepository`, `EntityManager` continuam ausentes (`grep` vazio), mas existe persistência via `JdbcClient`/`JdbcTemplate` em 11 classes: `AdminInicial` e `ui/.../dados/*Repositorio.java` + `AutenticacaoService` (ex.: `ImovelRepositorio.java:25-30`, `AutenticacaoService.java:27-35`). |
| 4 | CadastroView só chama `UiKit.ir("aprovacao")`: sem INSERT em usuarios nem serviço de criação/consulta | **REFUTADO** | `CadastroView.java:40` chama `autenticacao.cadastrar(...)`; `AutenticacaoService.java:76-94` executa `INSERT INTO usuarios (...) 'COLABORADOR','PENDENTE_APROVACAO'`. |
| 5 | SecurityConfig usa `anyRequest().permitAll()` e nenhuma senha é validada | **PARCIAL** — `permitAll` CONFIRMADO; "senha não validada" REFUTADO | `SecurityConfig.java:17` `auth.anyRequest().permitAll()` e `:18` `csrf.disable()`. Porém `AutenticacaoService.java:57` usa `encoder.matches(senha, senhaHash)` com `BCryptPasswordEncoder` (`SecurityConfig.java:22-25`). |
| 6 | O banco está de pé (schema/seed via Flyway), mas o app só o toca no boot; nenhuma tela consulta | **REFUTADO** | O schema é criado pelo Flyway no boot, mas as telas consultam: `HomeRepositorio.java:20-30` (`vw_home_indicadores`), `ImovelRepositorio.java:33-73` (`vw_imoveis_situacao`), `ContratoRepositorio.java:41-61` (`vw_contratos_situacao`), etc. Auditoria: 18 rotas responderam HTTP 200 e o banco tinha dados (ver "Verificação executada"). |

Observação: o commit T25 (`b33ff36`) já religou as telas ao banco com `JdbcClient`; isso **não** atende à
DEC-I01 (Spring Data JPA/Hibernate/entidades/BaseEntity/AuditorAware). A árvore atual também contém
trabalho não commitado do QA: `scripts/verificar-integracao.sh`, `scripts/pg-runtime.mjs`,
`src/test/java/.../PersistenciaRoundTripTest.java`, `src/test/resources/application-test.properties`.

## Arquitetura real

- **Build**: Maven Wrapper (`./mvnw`), módulo único na raiz (`com.Projeto:manuelo:0.0.1-SNAPSHOT`).
  O código do frontend em `ui/src/main/java` entra como source via `build-helper-maven-plugin`
  (`pom.xml:208-226`); os assets ficam em `src/main/frontend` (`pom.xml:227-243`); as migrations de
  `db/migrations` são empacotadas em `classpath:db/migration` (`pom.xml:185-192`).
- **Versões**: Java 21 (`pom.xml:30`); Spring Boot **4.1.2** (`pom.xml:8`; jar empacotado
  `Spring-Boot-Version: 4.1.2`); Spring Framework 7.0.10; Vaadin BOM **25.3.5** (`pom.xml:31`);
  Hibernate ORM **7.4.13.Final** (jar em `BOOT-INF/lib`); Flyway 12.4.0; driver PostgreSQL 42.7.14;
  OpenPDF 1.3.43; JasperReports 7.0.8 (declarado, não usado no código); Apache POI 5.4.1.
- **Boot**: `com.Projeto.manuelo.ManueloApplication` (`ManueloApplication.java:8-14`) com
  `scanBasePackages={"com.Projeto.manuelo","br.com.manuelo"}` e `@EnableVaadin("br.com.manuelo")`.
- **Persistência atual**: `JdbcClient` sobre `JdbcTemplate`; não há `@Entity`, `JpaRepository`,
  `EntityManager`, `@Transactional`, `@Immutable`, `BaseEntity` nem `AuditorAware`. O
  `spring.jpa.hibernate.ddl-auto=validate` (`application.properties:13`) passa de forma vazia, pois não
  existem entidades mapeadas.
- **Autenticação**: sessão Vaadin (`SessaoUsuario`, escopo de sessão) + `AutenticacaoService` com
  BCrypt. `SecurityConfig` libera tudo e desabilita CSRF; as telas protegidas são guardadas por
  `MainLayout.beforeEnter` (redireciona para `""`). Não há `UserDetailsService`, `ROLE_*`,
  `@RolesAllowed`/`@PermitAll` nem autorização nos repositórios/serviços.
- **Banco**: 12 migrations `V001..V012` + seed fictício (`db/seed/dev_seed.sql`) + 14 testes SQL
  (`db/tests/*.sql`). Views/funções derivadas em `V009`/`V011` (`vw_contratos_situacao`,
  `vw_imoveis_situacao`, `vw_home_indicadores`, `vw_saldo_caucao`, `fn_resumo_periodo`, etc.).
- **Upload**: o componente `Upload` existe em `UiKit.java:317-335`, mas o `UploadHandler.inMemory`
  está **vazio** (`:318-319`); nenhum arquivo é gravado. `DocumentoRepositorio.salvar` fabrica
  `caminho_armazenamento = local://documentos/<uuid>.pdf`, `tipo_mime=application/pdf` e
  `tamanho_bytes=1024` (`DocumentoRepositorio.java:79-89`).
- **PDFs**: `GeradorRelatorio` usa OpenPDF e gera tabela genérica com título/período
  (`GeradorRelatorio.java:52-84`); não usa Jasper; não há cabeçalho da fundação, autor, data/hora,
  código nem "Página X de Y"; o XLSX é gerado com POI (`:86-116`).

## Camadas e caminhos

| Camada | Caminho real | Observação |
|---|---|---|
| Views/dialogs Vaadin | `ui/src/main/java/br/com/manuelo/*.java` | 19 rotas; `AuthLayout` para telas públicas |
| "Serviços" | `ui/src/main/java/br/com/manuelo/dados/AutenticacaoService.java` | Único `@Service`; não há serviços de negócio (imóvel, contrato, movimentação, etc.) |
| Repositórios | `ui/src/main/java/br/com/manuelo/dados/*Repositorio.java` | 9 repositórios `JdbcClient` (não Spring Data) + `AdminInicial`/`AutenticacaoService` também usam `JdbcClient` |
| Entidades | **inexistente** | `grep` por `@Entity`/`JpaRepository`/`EntityManager` = vazio |
| DTOs de tela | `ui/src/main/java/br/com/manuelo/DemoData.java` | Apenas `record`s; nome histórico é confundido com "DemoData" |
| Config/segurança | `src/main/java/com/Projeto/manuelo/config/` | `SecurityConfig.java`, `AdminInicial.java` |
| Migrations | `db/migrations/V001..V012` → `src/main/resources/db/migration` | Flyway `baseline-on-migrate=true`, `baseline-version=11` (`application.properties:16-19`) |
| Testes | `src/test/java/com/Projeto/manuelo/` e `src/test/resources/application-test.properties` | `ManueloApplicationTests`, `PersistenciaRoundTripTest`; 14 testes SQL em `db/tests` |
| Scripts | `scripts/` | `verificar-db.sh`, `verificar-integracao.sh`, `pg-runtime.mjs`, `pg-verify.mjs`, `aplicar-supabase.*` |
| UI kit | `ui/src/main/java/br/com/manuelo/UiKit.java` | Componentes visuais; `upload` é placeholder |
| Tema/assets | `ui/src/main/resources/`, `src/main/frontend/` | `styles.css`, imagens, bundle Vaadin |

## Rotas reais x Figma

| Rota no código | Rota no Figma | Situação |
|---|---|---|
| `""` (`LoginView`) | `/login` | Login real por e-mail/BCrypt; `/login` não é alias de rota (só `""`) |
| `cadastro` | `/cadastro` | Real (INSERT PENDENTE_APROVACAO) |
| `aprovacao` + alias `aguardando-aprovacao` | `/aguardando-aprovacao` | Tela estática; alias presente |
| `recuperar-senha` | `/recuperar-senha` | Gera token no banco, mas não entrega/valida token |
| `email-enviado` | (aviso do Figma) | Tela estática de aviso |
| `redefinir-senha` | `/redefinir-senha` | Redefine pela sessão de recuperação, não pelo token |
| `home` | `/home` | Indicadores e movimentações reais; Exportar é aviso |
| `imoveis` | `/imoveis` | Lista/CRUD reais; filtros em memória; upload de contrato é no-op |
| `imoveis/reformas` | seção Reformas de `/imoveis` | Lista/novo reais, mas sempre no primeiro imóvel |
| `inquilinos` | `/inquilinos` | Lista/CRUD básico; fiador/vínculo/caução/uploads descartados |
| `contratos` | `/contratos` | Lista/filtros/novo reais; fiador/ação judicial/caução/uploads ausentes |
| `contratos/detalhe` | detalhe do contrato | Leitura real + "Pagar o aluguel" real; upload no-op |
| `movimentacao-financeira` | `/movimentacao-financeira` | Registro real; sem período/comprovante/estorno/exportação |
| `documentos` | `/documentos` | Lista/metadados reais; sem arquivo, classificação, download |
| `relatorios` | `/relatorios` | 3 tipos, trimestre, KPIs, extrato e geração PDF/XLSX; faltam 4º tipo e download |
| `usuarios` (DEC-I03) | não existe no Figma | Lista/aprova/inativa; não altera perfil |

## Auditoria por tela (demo x real)

| Tela | View | Fonte dos dados hoje | Ações que funcionam | Ações mortas/parciais | Itens do backlog |
|---|---|---|---|---|---|
| Login | `LoginView` | `AutenticacaoService.login` → `usuarios` | Login BCrypt, status PENDENTE/INATIVO, último acesso | Não usa Spring Security/`ROLE_*` | A-10, A-14 |
| Cadastro | `CadastroView` | `AutenticacaoService.cadastrar` → `usuarios` | INSERT PENDENTE_APROVACAO, e-mail único | — | A-11 |
| Aprovação | `AprovacaoView` | estática | Aviso e retorno ao login | Sem polling/estado real | A-11 |
| Recuperar senha | `RecuperarSenhaView` | `AutenticacaoService.solicitarRecuperacao` → `tokens_redefinicao_senha` | Cria token (hex cru) no banco | Sem envio (SMTP/dev), sem link, token não é hash SHA-256 | A-13 |
| Redefinir senha | `RedefinirSenhaView` | `AutenticacaoService.redefinirSenha` → `usuarios` | Troca a senha da sessão de recuperação | Não lê token/expiração/uso único | A-13 |
| Home | `HomeView` | `HomeRepositorio` (`vw_home_indicadores`) + `MovimentacaoRepositorio` | 4 KPIs reais, últimas 10 movimentações, cliques filtram | "Exportar" só mostra aviso | A-60, A-43 |
| Imóveis | `ImoveisView` + `ImovelDialog` | `ImovelRepositorio.listar` (`vw_imoveis_situacao` + contratos) | Lista, filtros em memória, criar/editar persistem | Exportar falso; upload de contrato no-op; sem detalhe com contrato | A-20, A-50 |
| Reformas | `ReformasView` + `ReformaDialog` | `ReformaRepositorio` (`reformas` + `vw_reformas_historico`) | Histórico e novo registro | Sem escolha de imóvel (sempre o primeiro); Exportar/Ver tudo falsos | A-22 |
| Inquilinos | `InquilinosView` + `InquilinoDialog` | `InquilinoRepositorio.listar` | Lista, filtros em memória, nome/CPF/e-mail/telefone persistem | Fiador, imóvel, caução, recibo e contrato coletados e descartados | A-21 |
| Contratos | `ContratosView` + `ContratoDialog` | `ContratoRepositorio.listar` (`vw_contratos_situacao`) | Lista, filtros em memória, novo contrato com validação de sobreposição | Fiador, ação judicial, caução, dia de vencimento explícito e upload ausentes | A-30, A-31 |
| Detalhe do contrato | `ContratoDetalheView` + `PagarAluguelDialog` | `ContratoRepositorio.buscar` (views + `fiadores` + `vw_saldo_caucao`) | Detalhe, caução, fiador, pagar aluguel/com caução | Upload do contrato no-op; sem editar contrato | A-31, A-32 |
| Movimentação | `MovimentacaoFinanceiraView` | `MovimentacaoRepositorio` (`movimentacoes` + `fn_resumo_periodo`) | Registrar Entrada/Saída, confirmar, extrato recente | Pesquisar e Exportar falsos; sem período, comprovante obrigatório, estorno/edição | A-40, A-41, A-42, A-43 |
| Documentos | `DocumentosView` + `DocumentoDialog` | `DocumentoRepositorio.listar` (`documentos`) | Lista e cadastro de metadados | Sem arquivo real, classificação pendente, download; filtro financeiro ignorado | A-50, A-51 |
| Relatórios | `RelatoriosView` + `GeradorRelatorio` | `MovimentacaoRepositorio` + `RelatorioRepositorio` | 3 tipos, trimestre, KPIs, extrato, gera PDF/XLSX e persiste relatório | Sem "a vencer", cabeçalho/rodapé exigidos, PDF personalizado, download e restrição admin | A-70, A-71, A-72 |
| Usuários | `UsuariosView` | `UsuarioRepositorio` (`usuarios`) | Lista, aprova, inativa (admin) | Não altera perfil; proteção só no `beforeEnter` | A-12 |
| MainLayout | `MainLayout` | `SessaoUsuario` | Guarda de sessão, menu admin, logout | Sem Spring Security/`ROLE_*` | A-10, A-80 |

## Auditoria item a item do backlog

Legenda: FEITO = critério comprovado; PARCIAL = parte funciona; AUSENTE = não implementado.

### S0 Fundação
- **A-01 Juiz — FEITO**. `scripts/verificar-integracao.sh` existe e foi executado: **8/8 etapas, EXIT=0**.
  Ressalva: o juiz é fraco (validação JPA vazia, smoke só HTTP 200) e não cobre os critérios dos itens.
- **A-02 Persistência base — PARCIAL**. DataSource por env e `ddl-auto=validate` existem
  (`application.properties:6-14`), Flyway roda 12 migrations. **Faltam** entidades JPA, mapeamento
  UUID/BigDecimal/timestamptz/enums, `BaseEntity` e `AuditorAware` (grep vazio). O `validate` passa
  porque não há entidade.
- **A-03 Entidades e repositórios do modelo v3 — AUSENTE**. Não há `@Entity`, `JpaRepository`,
  `@Immutable`. Existem apenas repositórios JDBC que devolvem `record`s de `DemoData`.

### S1 Autenticação e usuários
- **A-10 Spring Security real — PARCIAL**. Login/logout por sessão Vaadin, BCrypt e gate de status
  funcionam; `SecurityConfig` é `permitAll`, sem `UserDetailsService`/`ROLE_*`/rotas protegidas no
  HTTP (log do boot cria `inMemoryUserDetailsManager` com senha gerada).
- **A-11 Cadastro real — FEITO**. `AutenticacaoService.cadastrar` grava PENDENTE_APROVACAO/COLABORADOR;
  e-mail único; redireciona a `/aprovacao`.
- **A-12 /usuarios — PARCIAL**. Lista, aprova (`aprovado_por/em`) e inativa; **não** altera perfil.
- **A-13 Recuperar/redefinir senha — PARCIAL (inseguro)**. Token de 32 bytes é gerado, mas gravado como
  hex cru (não SHA-256), sem entrega (SMTP/log), sem link, e a redefinição usa `sessao.emailRecuperacao`
  em vez do token; não verifica expiração/uso único.
- **A-14 Bootstrap do admin — PARCIAL**. `AdminInicial` cria admin por env, mas há senha padrão
  `admin123` em `application.properties:25`, então cria admin mesmo sem variáveis (contraria DEC-I04).
- **A-15 Auditoria — PARCIAL**. `criado_por/atualizado_por` são preenchidos manualmente em vários
  repositórios (`ImovelRepositorio.java:95/101/114`, etc.), mas o cadastro de usuário não preenche
  (`AutenticacaoService.java:82-89`), não há `AuditorAware` e a view/relatório não têm autoria completa.

### S2 Imóveis, inquilinos, fiadores e reformas
- **A-20 /imoveis — PARCIAL**. Lista, criar e editar persistem; filtros funcionam, mas em memória; sem
  detalhe com contrato vigente e upload é no-op.
- **A-21 /inquilinos — PARCIAL**. Cadastro básico persiste; fiador, imóvel vinculado, caução e uploads
  do recibo são coletados na tela e descartados no salvamento (`InquilinosView.java:100-106`).
- **A-22 Reformas — PARCIAL**. Histórico e novo registro persistem, mas sempre no primeiro imóvel
  (`ReformasView.java:30` → `ReformaRepositorio.imovelPadrao`), sem seletor.

### S3 Contratos e caução
- **A-30 /contratos — FEITO (funcional)**. Lista/filtros em memória, "faltam N dias" e dados da
  `vw_contratos_situacao`.
- **A-31 Novo contrato/detalhe — PARCIAL**. Cria contrato e rejeita sobreposição; **faltam** fiador,
  ação judicial, caução inicial, dia de vencimento e upload do contrato.
- **A-32 Pagar o aluguel — PARCIAL**. Gera ENTRADA/ALUGUEL com competência e "pagar com caução" gera
  abatimento + ENTRADA forma CAUCAO com checagem de saldo; falta o fluxo de depósito de caução e prova
  de quitação/atraso.

### S4 Movimentação financeira
- **A-40 Entrada/saída com R03/R04 — PARCIAL**. R03 é aplicada em parte; R04 (comprovante obrigatório
  em SAIDA) **não** é exigida no serviço/repositório (`MovimentacaoRepositorio.registrar:81-145`).
- **A-41 Extrato/pesquisa/totais — PARCIAL**. Extrato e totais por função existem; "Pesquisar
  Lançamentos" é botão morto e não há filtro de período na tela.
- **A-42 Estorno/edição admin — AUSENTE**. Nenhum código Java cria ESTORNO, justificativa,
  `movimentacao_original_id` ou `corrige_movimentacao_id`; não há checagem de administrador.
- **A-43 Exportar extrato — AUSENTE**. O botão só exibe "Extrato exportado com sucesso"
  (`MovimentacaoFinanceiraView.java:56`); não gera/persiste PDF.

### S5 Documentos
- **A-50 Upload real — AUSENTE**. Handler vazio (`UiKit.java:318-319`) e metadados fabricados
  (`DocumentoRepositorio.java:79-89`); não há `ArmazenamentoArquivos`/`MANUELO_STORAGE_DIR`.
- **A-51 /documentos — PARCIAL**. Lista e adiciona metadados; filtro financeiro é ignorado
  (`DocumentosView.java:102-109`); classificar pendente e baixar arquivo não existem.

### S6 Home
- **A-60 Home — FEITO**. Indicadores da `vw_home_indicadores`, movimentações recentes e cliques com
  filtros reais.

### S7 Relatórios
- **A-70 Os 4 PDFs — PARCIAL/AUSENTE**. Gera PDF/XLSX genérico (OpenPDF), mas só há 3 tipos na tela
  (`RelatoriosView.java:52-53`), sem "contratos a vencer"; sem os elementos obrigatórios de cabeçalho e
  rodapé ("Página X de Y"), sem Jasper e sem código do relatório.
- **A-71 Persistir composição/download/admin — PARCIAL**. Persiste `relatorios` e composições; não há
  download e a tela não é restrita a ADMINISTRADOR.
- **A-72 /relatorios funcional — PARCIAL**. Tipos, trimestre, KPIs, extrato e geração funcionam; faltam
  período livre, PDF personalizado e o 4º tipo.

### S8 Fecho
- **A-80 Autorização nos serviços/erros/pt-BR — AUSENTE/PARCIAL**. Sem autorização nos serviços, sem
  tratamento global de erros; formatação pt-BR/moeda/data existe em `UiKit`/`Dominio`.
- **A-81 Perfis dev/prod/README — PARCIAL**. Só `application.properties`; sem `application-dev/prod`;
  `README.md` tem 26 bytes; `.env.example` e `Dockerfile` existem.
- **A-90 Auditoria final — AUSENTE**. Há botões mortos, itens acima pendentes e `DemoData` ainda é o
  nome dos DTOs de tela.

## Verificação executada nesta auditoria

- `git status --short`: alterações em `AGENTS.md`, `PROMPT-INICIAL.md`, `opencode.jsonc`,
  `ManueloApplicationTests.java`; novos `docs/integracao/`, `scripts/verificar-integracao.sh`,
  `scripts/pg-runtime.mjs`, `PersistenciaRoundTripTest.java`, `src/test/resources/`.
- Build: `JAVA_HOME=... ./mvnw -B -q clean package -DskipTests` → **EXIT=0**; jar
  `target/manuelo-0.0.1-SNAPSHOT.jar`.
- Banco: `./scripts/verificar-db.sh` → **12 migrations, 14 testes, 0 falhas**.
- Juiz: `./scripts/verificar-integracao.sh` → **8/8 etapas, EXIT=0**.
- App: subiu com `ddl-auto=validate` em `127.0.0.1:8081`; **18 rotas responderam HTTP 200** (lista
  completa nas rotas acima) e **0 ERROR** no log.
- SQL (após seed): `usuarios=3`, `imoveis=124`, `inquilinos=120`, `fiadores=60`, `contratos=110`,
  `movimentacoes=3040`, `caucao_movimentacoes=120`, `documentos=860`, `reformas=0`, `relatorios=0`;
  `flyway` na versão `012`. Usuários: 2 ADMINISTRADOR/ATIVO + 1 COLABORADOR/ATIVO.
