# ManuElo — mapa técnico para transformar um relatório de bugs em prompt de correção

Versão: 19/11/2026. Branch atual: `fix/problemas-abertos`.
Objetivo deste documento: ser colado no Claude junto com o relatório de bugs. O Claude deve usar
estas informações para gerar UM prompt de execução para um agente autônomo que tem acesso ao
repositório. O agente segue o protocolo do `AGENTS.md` e não pergunta nada ao usuário.

> Regra de ouro para o Claude: não invente arquivos, rotas ou tabelas. Use apenas os caminhos e
> nomes abaixo. Se o relatório não tiver reprodução suficiente, escreva `LACUNA:` com a pergunta
> objetiva que o humano precisa responder.

---

## 1. Visão geral

ManuElo é o sistema de gestão financeira e documental da Fundação Manoel Cruz (Sergipe):
imóveis, inquilinos, fiadores, contratos, caução, movimentações financeiras imutáveis, documentos,
reformas e 4 relatórios em PDF. Substitui planilhas/pastas físicas. Não importa planilhas.
Perfis: **ADMINISTRADOR** (tudo + aprovar usuários, estornar, editar movimentação, gerar relatório)
e **COLABORADOR** (operação do dia a dia). Cadastro público nasce COLABORADOR/PENDENTE_APROVACAO.

Fonte da verdade funcional: `docs/CONTEXTO.md` (regras R01–R11, modelo v3, telas, DEC-01–DEC-13).
Decisões de implementação: `docs/integracao/decisoes.md` (DEC-I01–DEC-I16).
Estado do trabalho: `docs/integracao/backlog.md`, `progresso.md`, `problemas.md`.
Em conflito: CONTEXTO > DEC-I > Figma > código.

---

## 2. Stack, build e execução

- Java **21** (`maven.compiler.release=21`).
- Spring Boot **4.1.2** (pom atual; o CONTEXTO fala “Spring Boot 3”), Spring Framework 7.
- Vaadin Flow **25.3.5** (front 100% Java, sem React/Angular).
- Spring Data JPA/Hibernate (`ddl-auto=validate`, sem DDL automático), Flyway, Spring Security, BCrypt.
- PostgreSQL (Supabase Session pooler, porta 5432; nunca Transaction pooler em produção).
- OpenPDF para os 4 PDFs. JasperReports está no pom, mas o renderer real é `RelatorioPdfRenderer`.
- Apache POI para XLSX (exportação de extrato).
- Maven Wrapper (`./mvnw`), módulo único `com.Projeto:manuelo`.
- `ui/src/main/java` entra como source via `build-helper-maven-plugin`; assets em `src/main/frontend`.
- Boot: `com.Projeto.manuelo.ManueloApplication` (`scanBasePackages = com.Projeto.manuelo, br.com.manuelo`).

Comandos (ambiente desta máquina):
```sh
export JAVA_HOME=/home/joaomiguel/.jdks/openjdk-26.0.2.1   # compila release 21; ideal JDK 21
export PATH="$JAVA_HOME/bin:$PATH"

npm run dev                 # Postgres embutido + app em http://localhost:8080 (admin/admin123 em dev)
./mvnw -B test              # testes Java (precisam de Postgres; perfil test)
./mvnw -B clean package -DskipTests
java -jar target/manuelo-0.0.1-SNAPSHOT.jar
./scripts/verificar-db.sh           # migrations + testes SQL
./scripts/verificar-integracao.sh   # juiz completo (8 etapas; Postgres embutido via Node)
```

Variáveis: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `PORT`/`APP_PORT`, `MANUELO_ADMIN_EMAIL`,
`MANUELO_ADMIN_SENHA`, `MANUELO_APP_URL`, `MANUELO_STORAGE_DIR`, SMTP (`SPRING_MAIL_*`).
Em `prod` as 4 `MANUELO_*` e as 3 `DB_*` são obrigatórias (placeholder sem default derruba o boot).

---

## 3. Estrutura de diretórios e responsabilidades

```
Dockerfile                  build multi-stage; runtime eclipse-temurin:21-jre-alpine; perfil prod
docker-compose.yml          Postgres local (55432)
pom.xml                     Java 21, Boot 4.1.2, Vaadin 25.3.5, JPA, Flyway, Security, OpenPDF, POI
README.md                   execução, banco, testes, deploy, latência
AGENTS.md                   protocolo da equipe de agentes (brief/relato, fronteiras, juiz)
db/migrations/V001..V012    schema, enums, triggers, RLS, views e funções (fonte da verdade do banco)
db/tests/*.sql              testes SQL das regras
db/seed/dev_seed.sql        seed de desenvolvimento
scripts/                    juiz, verificação de banco, Postgres embutido, deploy Supabase
src/main/java/com/Projeto/manuelo/
  ManueloApplication.java   boot
  dominio/                  @Entity (Usuario, Imovel, Inquilino, Fiador, Contrato, CaucaoMovimentacao,
                            Movimentacao, Documento, Reforma, Relatorio*, TokenRedefinicaoSenha,
                            Configuracao, BaseEntity)
  dominio/repositorio/      Spring Data JPA (UsuarioJpaRepository etc.) + InsercaoSomente
  servico/                  regras de negócio transacionais (ver seção 7)
  seguranca/                login, cadastro, aprovação, recuperação de senha, UserDetails
  arquivos/                 ArmazenamentoArquivos + ArmazenamentoLocal (filesystem)
  config/                   SecurityConfig, AdminInicial, JpaAuditingConfig, ErrosGlobalConfig,
                            RequisicoesLentasFilter
ui/src/main/java/br/com/manuelo/
  *View.java, *Dialog.java  telas e diálogos Vaadin (18 rotas)
  MainLayout.java           layout autenticado + guarda de sessão
  AuthLayout.java           layout das telas públicas
  UiKit.java                componentes visuais, upload, notificações
  dados/                    repositórios de leitura/escrita com JdbcClient (SQL nativo) +
                            SessaoUsuario + Dominio (enums/rótulos) + GeradorRelatorio (legado)
  Modelos.java              DTOs record usados pelas telas (NÃO é dado em memória)
src/main/resources/         application.properties / -dev / -prod; relatorios (templates/logo)
src/test/java/              19 classes de teste (ver seção 10)
```

Regra de camadas: **View → Service → Repository → Banco**. Views/dialogs não podem referenciar
`*Repositorio` (há teste de arquitetura). Repositórios `ui/.../dados` são SQL nativo de leitura/escrita;
repositórios JPA em `src/main/java/.../dominio/repositorio` cobrem o modelo e a auditoria.

---

## 4. Rotas reais (Vaadin) e o que cada tela usa

| Rota | View | Layout | Serviços/repos principais | Tabelas/views |
|---|---|---|---|---|
| `/` e `/login` | `LoginView` | `AuthLayout` | `LoginService`, `UsuarioDetalhesService` | `usuarios` |
| `/cadastro` | `CadastroView` | `AuthLayout` | `CadastroUsuarioService` | `usuarios` |
| `/aprovacao`, `/aguardando-aprovacao` | `AprovacaoView` | `AuthLayout` | estática | — |
| `/recuperar-senha` | `RecuperarSenhaView` | `AuthLayout` | `RecuperacaoSenhaService`, `EnvioTokenSenhaImpl` | `usuarios`, `tokens_redefinicao_senha` |
| `/email-enviado` | `EmailEnviadoView` | `AuthLayout` | estática | — |
| `/redefinir-senha` | `RedefinirSenhaView` | `AuthLayout` | `RecuperacaoSenhaService` | `tokens_redefinicao_senha`, `usuarios` |
| `/home` | `HomeView` | `MainLayout` | `ConsultaService`, `HomeRepositorio` | `vw_home_indicadores`, `movimentacoes` |
| `/imoveis` | `ImoveisView` + `ImovelDialog`, `ImovelDetalheDialog` | `MainLayout` | `ConsultaService`, `DocumentoService` | `vw_imoveis_situacao`, `imoveis`, `contratos`, `inquilinos` |
| `/imoveis/reformas` | `ReformasView` + `ReformaDialog` | `MainLayout` | `ConsultaService` | `reformas`, `vw_reformas_historico`, `imoveis`, `contratos` |
| `/inquilinos` | `InquilinosView` + `InquilinoDialog` | `MainLayout` | `ConsultaService`, `InquilinoCadastroService`, `DocumentoService` | `inquilinos`, `contratos`, `fiadores`, `imoveis`, `caucao_movimentacoes`, `documentos` |
| `/contratos` | `ContratosView` + `ContratoDialog` | `MainLayout` | `ConsultaService`, `ContratoCadastroService`, `DocumentoService` | `vw_contratos_situacao`, `contratos`, `fiadores`, `caucao_movimentacoes` |
| `/contratos/detalhe` | `ContratoDetalheView` + `PagarAluguelDialog` | `MainLayout` | `ConsultaService`, `ContratoCadastroService`, `PagamentoAluguelService`, `CaucaoService`, `DocumentoService` | `vw_contratos_situacao`, `vw_saldo_caucao`, `movimentacoes`, `caucao_movimentacoes`, `documentos` |
| `/movimentacao-financeira` | `MovimentacaoFinanceiraView` + `EditarMovimentacaoDialog`, `EstornoDialog`, `ExportarExtratoDialog` | `MainLayout` | `MovimentacaoFinanceiraService`, `ConsultaService`, `RelatorioService`, `DocumentoService` | `movimentacoes`, `fn_extrato_periodo`, `fn_resumo_periodo`, `documentos`, `relatorios` |
| `/documentos` | `DocumentosView` + `DocumentoDialog` | `MainLayout` | `DocumentoService`, `ConsultaService` | `documentos`, `inquilinos`, `contratos`, `imoveis`, `movimentacoes` |
| `/relatorios` | `RelatoriosView` + `ExportarExtratoDialog` | `MainLayout` | `RelatorioService`, `MovimentacaoFinanceiraService` | `relatorios`, `relatorio_*`, views/funções |
| `/usuarios` | `UsuariosView` | `MainLayout` | `UsuarioAdminService` | `usuarios` |

- `/usuarios/**` e `/relatorios/**` exigem ROLE_ADMINISTRADOR no `SecurityConfig` e nos serviços.
- Rotas públicas: `/`, `/login`, `/cadastro`, `/aprovacao`, `/aguardando-aprovacao`,
  `/recuperar-senha`, `/redefinir-senha`, `/email-enviado`, `/VAADIN/**`, `/frontend/**`,
  `/images/**`, `/favicon.ico`, `/error`, `/styles.css`, `/aura/**`, `/icons/**`, `/sw.js`,
  `/manifest.webmanifest`, `/offline.html`, `/favicon.svg`.
- `MainLayout.beforeEnter` também barra sessão não autenticada.

---

## 5. Serviços e regras (onde a lógica mora)

`src/main/java/com/Projeto/manuelo/servico/`:

| Serviço | Responsabilidade | Regras |
|---|---|---|
| `MovimentacaoFinanceiraService` | registrar entrada/saída, estornar, corrigir (estorno + nova), extrato e resumo | R01, R02, R03, R04, R07, R09; só ADMIN estorna/corrige |
| `PagamentoAluguelService` | pagar aluguel e pagar com caução | R03, R05, R07 |
| `CaucaoService` | depósito e devolução de caução | R07, R05 (saldo nunca negativo) |
| `ContratoCadastroService` | criar contrato (com sobreposição/EXCLUDE) e ação judicial | R05, R06, DEC-07 |
| `InquilinoCadastroService` | salvar inquilino + vínculo/contrato/caução em transação | R05, R07 |
| `DocumentoService` | upload real, vínculo, classificação, download | R08, DEC-I06 |
| `RelatorioService` | gerar/persistir/baixar os 4 PDFs | R09; ADMIN |
| `consulta/ConsultaService` | fachada de leitura das telas (Home, listas, detalhes, opções) | somente leitura |

`src/main/java/com/Projeto/manuelo/seguranca/`:

| Classe | Responsabilidade |
|---|---|
| `LoginService` | autenticar via AuthenticationManager, atualizar último acesso, logout |
| `CadastroUsuarioService` | cadastro público PENDENTE_APROVACAO/COLABORADOR, e-mail único, BCrypt |
| `UsuarioAdminService` | listar/aprovar/inativar/alterar perfil (exige ADMIN) |
| `RecuperacaoSenhaService` | token aleatório, só SHA-256 no banco, 1 h, uso único, resposta neutra |
| `UsuarioDetalhesService` / `UsuarioDetalhes` | UserDetails e ROLE_* |

Regras de negócio completas (não violar): `docs/CONTEXTO.md` seção 8, R01–R11.
Resumo: movimentação/caução/relatório são **insert-only** (nunca UPDATE/DELETE; trigger no banco);
correção = ESTORNO; SAIDA exige comprovante na mesma transação; caução tem livro próprio; contrato
atrasado é competência em aberto (não data de fim); relatório guarda a composição; usuário só inativa.

---

## 6. Banco de dados

- 12 migrations `db/migrations/V001..V012` empacotadas em `classpath:db/migration`.
  **Migration commitada não pode ser editada**; correções de schema = `V013+`.
- Tabelas: `usuarios`, `tokens_redefinicao_senha`, `imoveis`, `inquilinos`, `fiadores`, `contratos`,
  `caucao_movimentacoes`, `movimentacoes`, `documentos`, `reformas`, `relatorios`,
  `relatorio_movimentacoes`, `relatorio_contratos`, `configuracoes`, `flyway_schema_history`.
- Views: `vw_contratos_situacao`, `vw_contratos_atrasados`, `vw_contratos_a_vencer`,
  `vw_imoveis_situacao`, `vw_home_indicadores`, `vw_reformas_historico`, `vw_documentos_lista`,
  `vw_saldo_caucao`, `vw_movimentacoes_recentes`.
- Funções: `fn_contrato_atraso`, `fn_contrato_situacao`, `fn_extrato_periodo`, `fn_resumo_periodo`,
  triggers de imutabilidade/regras (`fn_bloquear_alteracao`, `fn_movimentacao_regras`,
  `fn_movimentacao_correcao`, `fn_movimentacao_comprovante_saida`, `fn_caucao_regras`,
  `fn_movimentacao_caucao_vinculada`, `fn_token_redefinicao_uso_unico`, auditoria/`atualizado_em`).
- Enums nativos: `tipo_movimentacao`, `categoria_movimentacao`, `forma_pagamento`, `tipo_imovel`,
  `status_imovel`, `status_usuario`, `perfil_usuario`, `tipo_documento`, `tipo_caucao`,
  `tipo_relatorio`, `filtro_tipo_movimentacao`.
- `movimentacoes`, `caucao_movimentacoes`, `relatorios` e composições são `@Immutable` no JPA e
  protegidos por trigger. Inserts usam `InsercaoSomente`.
- `relatorios.caminho_arquivo` e `documentos.caminho_armazenamento` guardam caminho relativo no
  `MANUELO_STORAGE_DIR` (implementação local).

---

## 7. Fluxos críticos (para mapear bugs)

- **Login/logout**: `LoginView` → `LoginService` → `AuthenticationManager` → `UsuarioDetalhesService`;
  `SessaoUsuario` (sessão Vaadin, proxy) sincroniza com SecurityContext e revalida status no banco no
  máximo 1x/30 s (DEC-I16). `MainLayout` faz a guarda.
- **Cadastro/aprovação**: `CadastroView` → `CadastroUsuarioService` (PENDENTE_APROVACAO) →
  `/aprovacao`; `UsuariosView` → `UsuarioAdminService` (aprovar/inativar/perfil, só ADMIN).
- **Senha**: `RecuperarSenhaView` → `RecuperacaoSenhaService.solicitar` (token 32 bytes, SHA-256,
  1 h, uso único, e-mail ou log em dev) → `RedefinirSenhaView` → `redefinir(token, novaSenha)`.
- **Imóveis**: `ConsultaService.listarImoveis` (SQL com filtros), `salvarImovel`, `ImovelDialog`,
  `ImovelDetalheDialog` (contrato vigente, caução, reformas).
- **Inquilinos**: `ConsultaService.listarInquilinos`/`detalheInquilino`;
  `InquilinoCadastroService.salvar` (inquilino + fiador + contrato + caução + documentos).
- **Contratos**: `ConsultaService.listarContratos`/`buscarContrato`; `ContratoCadastroService.criar`
  (valida sobreposição, cria inquilino/fiador/contrato/caução/documento); `ContratoDetalheView` +
  `PagarAluguelDialog` → `PagamentoAluguelService`.
- **Movimentação**: `MovimentacaoFinanceiraView` → `MovimentacaoFinanceiraService`; extrato/resumo
  via `fn_extrato_periodo`/`fn_resumo_periodo`; estorno/edição em `EstornoDialog`/`EditarMovimentacaoDialog`.
- **Documentos**: `DocumentoDialog` → `DocumentoService.salvar` (arquivo primeiro, depois registro);
  `classificar` (tipo+data); `baixar` (stream); `DocumentosView` monta a lista com `ConsultaService`.
- **Relatórios**: `RelatoriosView` → `RelatorioService.gerar` (4 tipos) → `RelatorioPdfRenderer`
  (OpenPDF) + persistência `relatorios`/composições + `ArmazenamentoLocal`; download por `baixar`.
- **Reformas**: `ReformasView` → `ConsultaService.reformasImoveis/listarReformas/registrarReforma`
  (comprovante opcional; proprietário do período derivado do contrato vigente).

---

## 8. Segurança, configuração e deploy

- Spring Security com sessão HTTP (`IF_REQUIRED`), CSRF desabilitado (decisão do projeto), login por
  e-mail, BCrypt, ROLE_ADMINISTRADOR/ROLE_COLABORADOR; só ATIVO entra.
- Autorização também nos serviços (não só na view) para estorno, edição de movimentação, relatórios e usuários.
- `AdminInicial` cria o primeiro ADMIN se `usuarios` estiver vazia, usando `MANUELO_ADMIN_EMAIL`/`SENHA`
  (sem variáveis não cria; sem senha padrão no repositório).
- `ArmazenamentoLocal` grava/ler arquivos em `MANUELO_STORAGE_DIR`
  (sem a variável, usa `${java.io.tmpdir}/manuelo-documentos`).
  Em produção o diretório precisa ser gravável e persistente (volume).
- Perfis: `dev` (default), `prod` (Dockerfile), `test` (src/test/resources).
- Dockerfile: build Maven (cache `id=manuelo-m2` em `/app/.m2`), runtime `21-jre-alpine`, não-root,
  `--spring.profiles.active=prod`. Railway injeta `PORT`; app lê `server.port=${PORT:...}`.
- Railway: se o domínio mostrar “The train has not arrived…”, é borda/rede (private networking,
  target port, deploy sem container saudável), não erro do Spring (P-029).
- Latência: manter Railway e Supabase na mesma região; procurar `Requisição lenta` no log (>1,5 s)
  (DEC-I16/P-030).

---

## 9. Testes e juiz

Testes em `src/test/java/com/Projeto/manuelo/` (JUnit 5 + Spring Boot Test + Mockito + Postgres real):

| Teste | Cobre |
|---|---|
| `SegurancaAutenticacaoTest` | login, status, rotas, papéis, logout |
| `CadastroUsuariosTest`, `CadastroUsuarioPersistenciaTest` | cadastro/aprovação/usuários |
| `BootstrapAdminTest` | primeiro administrador por env |
| `RecuperacaoSenhaTest` | token, hash, expiração, uso único, SMTP/dev |
| `ImoveisFiltrosTest` | filtros SQL de imóveis e detalhe |
| `InquilinoCadastroTest` | transação de inquilino/fiador/contrato/caução |
| `ContratoCadastroTest`, `ContratosFiltrosTest` | criação/sobreposição/filtros de contrato |
| `PagamentoAluguelTest` | pagar aluguel, caução, atraso |
| `MovimentacaoFinanceiraTest` | R01–R04, estorno, extrato/resumo |
| `UploadDocumentoTest` | upload, classificação, download, vínculo |
| `RelatorioServiceTest` | 4 PDFs, persistência, composição, download |
| `ReformasHistoricoTest` | reformas e proprietário derivado |
| `AuditoriaTest` | criado_por/atualizado_por |
| `RoundTripEntidadesTest`, `PersistenciaRoundTripTest` | JPA/round-trip |
| `ArquiteturaViewsTest` | view/dialog não usa repositório |
| `SessaoUsuarioCacheTest` | cache de revalidação de sessão (A-91) |
| `ManueloApplicationTests` | contexto |

Juiz (`./scripts/verificar-integracao.sh`): build limpo → testes → Postgres limpo + migrations do
zero → app com `ddl-auto=validate` → smoke das rotas → varredura de log → prova de persistência
(reinício do contexto) → guarda anti-protótipo (sem `DemoData`/lista em memória). Só é “pronto” com
o juiz EXIT 0. Há também `./scripts/verificar-db.sh` (migrations + testes SQL) e runtime Postgres
embutido via Node.

---

## 10. Estado atual e problemas conhecidos

- Backlog A-01..A-90 **CONCLUIDO**; A-91 (latência) **EM_ANDAMENTO**.
- `problemas.md`: P-001..P-028 **RESOLVIDO**; **P-029** (404 de borda do Railway) **EM ANÁLISE**;
  **P-030** (navegação >5 s) **EM_ANDAMENTO** — cache de sessão 30 s, opções de diálogo sob demanda,
  compressão e log de requisições lentas já aplicados (commit `823c387`), falta medir em produção.
- `GeradorRelatorio` (ui/dados) é legado não referenciado; o PDF real é
  `RelatorioService` + `RelatorioPdfRenderer`.
- `JasperReports` está no pom, mas não é usado.
- CSRF desabilitado é decisão existente; só trate como bug se o relatório pedir explicitamente.
- Não há `DemoData` como fonte de dados; `Modelos.java` é apenas DTO.

Armadilhas que não podem ser “consertadas” removendo a trava:
- Imutabilidade de movimentações/caução/relatórios (trigger + `@Immutable`).
- `ddl-auto=validate` (nunca trocar para `update` em produção).
- Migrations commitadas (nunca editar; criar `V013+`).
- Regra de negócio no serviço, nunca na view.
- Dinheiro em `BigDecimal`; datas em `LocalDate`; timestamptz em `OffsetDateTime`/`Instant` com UTC.
- UUID como `UUID` no Java e `uuid` no Postgres.
- Enums nativos: em SQL, `CAST(:valor AS tipo_enum)`.
- `SessaoUsuario` é proxy de sessão Vaadin; não cachear usuário em campo estático.

---

## 11. Checklist para o relatório de bugs (o que o Claude precisa exigir)

Para cada bug, o prompt gerado deve conseguir responder:

1. **ID/título** e severidade (bloqueante/alta/média/baixa).
2. **Ambiente**: local, produção (Railway), versão/commit; navegador/dispositivo.
3. **Perfil** usado (ADMINISTRADOR/COLABORADOR) e usuário (sem expor senha).
4. **Rota/tela** exata (ex.: `/movimentacao-financeira`) e ação (botão/aba/filtro/diálogo).
5. **Passos para reproduzir**, em ordem.
6. **Resultado atual** x **resultado esperado** (mensagem, valor, comportamento).
7. **Evidências**: print, texto do erro, stack trace, log (`Requisição lenta`, `ERROR`), resposta HTTP,
   Request ID, e se possível o SQL/registro afetado.
8. **Frequência** (sempre/intermitente) e se é **regressão** (funcionava antes).
9. **Dados de teste** (IDs/descrições fictícias) e se pode reproduzir no seed local.
10. Se envolve dinheiro/caução/relatório: valores, datas/competências e contrato/imóvel usados.

Sem isso, o Claude deve marcar `LACUNA:` em vez de inventar a causa.

---

## 12. Formato do prompt que o Claude deve produzir

O prompt final deve ser um único texto em português, pronto para o agente, contendo:

```text
CONTEXTO
- Repositório: ManuElo (Spring Boot 4.1.2 + Vaadin 25.3.5 + PostgreSQL/Flyway).
- Fontes: docs/CONTEXTO.md (R01-R11), docs/integracao/{decisoes,backlog,mapa,problemas}.md, AGENTS.md.
- Estado: branch fix/problemas-abertos; A-91 em andamento; P-029/P-030 abertos.
- Regra: não perguntar nada; decidir e registrar em docs/integracao/decisoes.md.

ITENS (um BRIEF por bug)
### B-01 <título> (severidade)
- Sintoma:
- Rota/tela/arquivo provável:
- Causa provável (se conhecida) e o que NÃO assumir:
- Correção mínima esperada (camada: banco/back/front):
- Critério de aceite testável:
- Teste vermelho primeiro (classe/arquivo e comando):
- Riscos/regressões (regras R0x/DEC-Ixx afetadas):
- Evidência do relatório (passos, log, print):

ORDEM E EXECUÇÃO
1. Para cada item: QA escreve teste vermelho → banco (se schema) → back → front → QA roda o juiz.
2. Revisor independente aprova o diff; líder confere escopo e commita "A-NN: título" ou "P-NNN: título".
3. Critério de pronto: ./scripts/verificar-integracao.sh EXIT 0 + teste do item verde.
4. Se 3 tentativas falharem, registrar P-NNN em problemas.md e seguir.

RESTRIÇÕES
- Mudança mínima e reversível; preservar layout do Figma.
- Não editar migration commitada; não remover imutabilidade/estorno; não usar lista em memória.
- Segredos só em variáveis de ambiente; não commitar .env.
- Rodar comandos de verdade e relatar saída; nunca “deve funcionar”.

SAÍDA ESPERADA
- Plano por item, arquivos a tocar, testes a criar, comandos de verificação e commits.
```

O Claude deve preencher isso usando o relatório; se um bug for de infraestrutura (ex.: 404 de borda do
Railway), classificar como `infra/deploy` e não como bug de código.

---

## 13. Prompt-mestre para colar no Claude (junto com este mapa e o relatório)

```text
Você é um engenheiro de software sênior. Vou te passar (1) o mapa técnico do projeto ManuElo e
(2) um relatório de bugs. Sua tarefa NÃO é corrigir: é gerar um prompt de execução para um agente
autônomo que tem acesso ao repositório e seguirá o AGENTS.md.

Use exclusivamente os caminhos, rotas, serviços, tabelas e convenções do mapa. Para cada bug do
relatório:
1. Classifique em front/back/banco/segurança/dados/infra e diga a rota/tela.
2. Aponte os arquivos prováveis (caminho completo a partir da raiz do repositório).
3. Descreva a correção mínima e o critério de aceite testável.
4. Defina o teste vermelho primeiro (classe/arquivo e comando Maven/juiz).
5. Liste riscos/regressões e regras R01-R11/DEC-Ixx afetadas.
6. Se faltar reprodução, escreva "LACUNA:" com a pergunta objetiva ao humano.

Agrupe bugs duplicados, ordene por dependência (banco → back → front → qa) e produza UM prompt final
no formato da seção 12 do mapa, com BRIEFs numerados (B-01, B-02...), restrições, critério de pronto
(./scripts/verificar-integracao.sh EXIT 0) e instrução de commits.

Não escreva código, não invente arquivos e não peça ao agente para perguntar ao usuário. Responda em
português do Brasil.
```
