# Registro de problemas (o mapeador cria; o líder mantém o status)

Resumo: total 40 | resolvidos 38 | em análise 1 (P-029) | em andamento 1 (P-030) | bloqueados 0
Obs.: os itens do relatório de frontend de 22/11/2026 estão em P-031..P-040 e foram corrigidos no working tree (sem commit, por decisão do usuário nesta rodada).

Modelo de bloco (copie para cada problema):

## P-001 Ausência total de JPA/entidades/BaseEntity/AuditorAware (DEC-I01)
- Severidade: BLOQUEANTE
- Camada: back + banco
- Evidência: `grep -rn "@Entity\|JpaRepository\|EntityManager\|BaseEntity\|AuditorAware" src ui --include=*.java` não retorna nada. Todo acesso é `JdbcClient` (ex.: `ui/src/main/java/br/com/manuelo/dados/ImovelRepositorio.java:25-30`). `spring.jpa.hibernate.ddl-auto=validate` (`src/main/resources/application.properties:13`) passa porque não há entidade mapeada.
- Causa raiz: T25 (commit b33ff36) escolheu JdbcClient (DEC-26) em vez de Spring Data JPA, divergindo da DEC-I01; entidades nunca foram criadas.
- Reprodução: rodar o grep acima; subir o app e observar "Initialized JPA EntityManagerFactory" sem nenhum mapeamento; o juiz etapa 4 passa vazio.
- Dono: back
- Critério de aceite: uma entidade JPA por tabela do modelo v3 (UUID/BigDecimal/timestamptz/enums) + `BaseEntity`/`AuditorAware` + `movimentacoes` `@Immutable`; teste de round-trip por entidade e `validate` não vazio.
- Status: RESOLVIDO
- Resolução: A-02/A-03 (14 entidades JPA, BaseEntity, AuditorAware, validate)
- Tentativas: 0/3
- Commit:

## P-002 Views chamam repositórios direto; não existe camada de serviços (View → Service → Repository)
- Severidade: BLOQUEANTE
- Camada: back + front
- Evidência: todas as views recebem `*Repositorio` no construtor (ex.: `ImoveisView.java:35`, `ContratosView.java:37`, `InquilinosView.java:28`, `MovimentacaoFinanceiraView.java:43`, `RelatoriosView.java:36`). O único `@Service` é `AutenticacaoService` (`ui/.../dados/AutenticacaoService.java:16`); `grep -rn "@Transactional" src ui` não retorna nada.
- Causa raiz: T25 colocou regra de negócio nos repositórios/views; não houve camada de serviços.
- Reprodução: `grep -rn "@Service" src ui --include=*.java`; inspecionar construtores das views.
- Dono: back (serviços) + front (religar views)
- Critério de aceite: views dependem apenas de serviços; serviços `@Transactional`; repositórios usados só por serviços; teste que falha se uma view injetar repositório.
- Status: RESOLVIDO
- Resolução: `ConsultaService` (`src/main/java/com/Projeto/manuelo/servico/consulta`) encapsula os repositórios JDBC; views/dialogs passaram a depender apenas de serviços; `ArquiteturaViewsTest` falha se `*Repositorio` reaparecer na UI.
- Tentativas: 0/3
- Commit:

## P-003 Upload é no-op e documentos fabricam metadados (DEC-I06)
- Severidade: BLOQUEANTE
- Camada: front + back
- Evidência: `UiKit.java:317-319` cria `UploadHandler.inMemory((metadata, bytes) -> {})` vazio; nenhum byte é gravado. `DocumentoRepositorio.java:79-89` grava `caminho_armazenamento = "local://documentos/<uuid>.pdf"`, `tipo_mime='application/pdf'` e `tamanho_bytes=1024` sem arquivo real. Não existe interface `ArmazenamentoArquivos` nem `MANUELO_STORAGE_DIR`.
- Causa raiz: placeholder de T25 para o componente visual de upload.
- Reprodução: enviar um arquivo em `/documentos` e consultar `select caminho_armazenamento, tipo_mime, tamanho_bytes from documentos`; nenhum arquivo existe no disco.
- Dono: back (armazenamento) + front (handler)
- Critério de aceite: arquivo gravado em `MANUELO_STORAGE_DIR`, metadados reais (nome, mime, tamanho), limite 10 MB e PDF/JPG/PNG validados; teste de upload/leitura.
- Status: RESOLVIDO
- Resolução: A-50 (ArmazenamentoLocal + DocumentoService + upload real)
- Tentativas: 0/3
- Commit:

## P-004 Estorno e edição pelo administrador ausentes (R01/R02, DEC-I08, A-42)
- Severidade: BLOQUEANTE
- Camada: back + front
- Evidência: `grep -rni "estorno\|corrige_movimentacao\|movimentacao_original\|justificativa" ui/src/main/java --include=*.java` só encontra formatação (`Dominio.java:105`) e leitura no extrato (`MovimentacaoRepositorio.java:27-32`). Não há INSERT de ESTORNO, nem `corrige_movimentacao_id`, nem checagem de administrador.
- Causa raiz: item não implementado no T25.
- Reprodução: abrir `/movimentacao-financeira`; não existe ação de estorno/edição para ADMINISTRADOR.
- Dono: back + front
- Critério de aceite: original intacto; um estorno por original, mesmo valor, só ADMINISTRADOR, com justificativa; "editar" = estorno + nova movimentação com `corrige_movimentacao_id`; testes de recusa para COLABORADOR e de imutabilidade.
- Status: RESOLVIDO
- Resolução: A-42 (estorno/correção ADMIN no serviço; original imutável)
- Tentativas: 0/3
- Commit:

## P-005 Os 4 PDFs estão incompletos (A-70/A-71/A-72)
- Severidade: BLOQUEANTE
- Camada: back + front
- Evidência: `RelatoriosView.java:52-53` só oferece "Contratos existentes", "Movimentação financeira" e "Inadimplência"; não há "contratos a vencer". `GeradorRelatorio.java:52-84` gera tabela genérica com título/período; não inclui nome da fundação, quem gerou, data/hora, código nem "Página X de Y". JasperReports está no `pom.xml:138-147` mas não é usado. `RelatorioRepositorio.java:45-49` não mapeia `CONTRATOS_A_VENCER`.
- Causa raiz: implementação mínima de T25 com OpenPDF e apenas 3 tipos.
- Reprodução: em `/relatorios`, ver o combo de tipos; gerar PDF e inspecionar cabeçalho/rodapé; não há download.
- Dono: back + front
- Critério de aceite: os 4 relatórios com cabeçalho/rodapé exigidos, composição persistida (`relatorio_movimentacoes`/`relatorio_contratos`), download e acesso só ADMINISTRADOR; testes de conteúdo.
- Status: RESOLVIDO
- Resolução: A-70/A-71/A-72 (4 PDFs OpenPDF + persistência/composição/download)
- Tentativas: 0/3
- Commit:

## P-006 Recuperação/redefinição de senha não usa token e é insegura (A-13/DEC-I05)
- Severidade: BLOQUEANTE
- Camada: back + front
- Evidência: `AutenticacaoService.java:108-119` gera 32 bytes e grava **hex cru** em `token_hash` (não SHA-256) e chama `sessao.iniciarRecuperacao(email)` sem enviar e-mail/log. `AutenticacaoService.java:122-147` redefine a senha pelo e-mail guardado na sessão, sem ler token, sem verificar `expira_em` nem `usado_em`. `RedefinirSenhaView.java` não recebe token na URL.
- Causa raiz: atalho de T25 que substituiu o fluxo por token por estado de sessão.
- Reprodução: solicitar recuperação; não há e-mail nem link no log; abrir `/redefinir-senha` em outra sessão e tentar redefinir (falha); o token gravado nunca é validado.
- Dono: back + front
- Critério de aceite: token aleatório de 32 bytes, só SHA-256 no banco, validade de 1 h, uso único, entrega por `EnvioTokenSenha` (SMTP ou log no perfil dev), resposta neutra; testes de token expirado/reutilizado.
- Status: RESOLVIDO
- Resolução: A-13 (token SHA-256, uso único, expiração)
- Tentativas: 0/3
- Commit:

## P-007 Autorização por papel ausente; SecurityConfig libera tudo (A-10/A-80)
- Severidade: BLOQUEANTE
- Camada: back + front
- Evidência: `src/main/java/com/Projeto/manuelo/config/SecurityConfig.java:17-18` usa `anyRequest().permitAll()` e `csrf.disable()`. `grep -rn "PermitAll\|RolesAllowed\|AnonymousAllowed\|UserDetailsService\|AuthenticationManager\|SecurityContext" src ui --include=*.java` não retorna nada. O log do boot mostra `UserDetailsServiceAutoConfiguration` criando `inMemoryUserDetailsManager` com senha gerada. A guarda real é só `MainLayout.beforeEnter` (`MainLayout.java:38-42`) e `UsuariosView.beforeEnter` (`UsuariosView.java:36-43`); serviços/repositórios não checam perfil.
- Causa raiz: autenticação caseira por sessão Vaadin, sem a integração oficial Vaadin/Spring Security (DEC-I02).
- Reprodução: acessar `/usuarios` deslogado (HTTP 200; reroute só no cliente Vaadin); executar um repositório de escrita como COLABORADOR.
- Dono: back + front
- Critério de aceite: `UserDetailsService`, BCrypt, `ROLE_ADMINISTRADOR`/`ROLE_COLABORADOR`, rotas públicas definidas, gate de status, autorização nos serviços; testes de acesso negado.
- Status: RESOLVIDO
- Resolução: A-10/A-80 (SecurityConfig, papéis, serviços e rotas)
- Tentativas: 0/3
- Commit:

## P-008 Auditoria manual e incompleta (A-15/DEC-I11)
- Severidade: ALTA
- Camada: back
- Evidência: não há `AuditorAware`, `@CreatedBy`, `@LastModifiedBy` nem `@EnableJpaAuditing` (grep vazio). Alguns repositórios preenchem `criado_por/atualizado_por` manualmente (`ImovelRepositorio.java:95/101/114`, `UsuarioRepositorio.java:40-49`, etc.), mas `AutenticacaoService.cadastrar` (`:82-89`) não preenche `criado_por`.
- Causa raiz: persistência JDBC sem infraestrutura de auditoria central.
- Reprodução: cadastrar usuário e consultar `select criado_por from usuarios where email=...` → nulo; comparar com imóveis.
- Dono: back
- Critério de aceite: `AuditorAware` lendo o `SecurityContext` preenche `criado_por`/`atualizado_por` em toda gravação; teste para cada entidade.
- Status: RESOLVIDO
- Resolução: A-15 (AuditoriaTest 4/4)
- Tentativas: 0/3
- Commit:

## P-009 Administrador inicial com senha padrão no repositório e criação sem variáveis (A-14/DEC-I04)
- Severidade: ALTA
- Camada: config/back
- Evidência: `src/main/resources/application.properties:24-25` define `admin@manuelo.local`/`admin123` como padrão; `AdminInicial.java:39-43` só recusa senha com menos de 8 caracteres, então cria o admin mesmo sem `MANUELO_ADMIN_*`. A DEC-I04 exige não criar sem variável.
- Causa raiz: default de desenvolvimento deixado no arquivo de produção.
- Reprodução: subir o app sem `MANUELO_ADMIN_EMAIL`/`MANUELO_ADMIN_SENHA` e consultar `usuarios`; o admin é criado com `admin123`.
- Dono: back
- Critério de aceite: sem variáveis não cria admin; nenhuma senha padrão no repositório; teste do bootstrap.
- Status: RESOLVIDO
- Resolução: A-14/A-81 (bootstrap só com MANUELO_ADMIN_*; sem default)
- Tentativas: 0/3
- Commit:

## P-010 /inquilinos coleta fiador, vínculo, caução e uploads, mas descarta tudo (A-21)
- Severidade: ALTA
- Camada: front + back
- Evidência: `InquilinoDialog.java:56-67` monta `nomeFiador`, `cpfFiador`, `emailFiador`, `endereco`, `valorCaucao` e dois uploads; `InquilinosView.java:100-106` envia só `id/nome/cpf/email/telefone`; `InquilinoRepositorio.salvar` (`:76-119`) só grava esses cinco campos. As tabelas `fiadores`/`caucao_movimentacoes` não são tocadas.
- Causa raiz: implementação parcial de T25.
- Reprodução: informar fiador e caução no diálogo e salvar; `select count(*) from fiadores` e `select count(*) from caucao_movimentacoes` não aumentam.
- Dono: back + front
- Critério de aceite: um envio cria inquilino + fiador + contrato + depósito de caução na mesma transação (A-21); teste de transação.
- Status: RESOLVIDO
- Resolução: A-21 (InquilinoCadastroService transacional)
- Tentativas: 0/3
- Commit:

## P-011 Documentos sem classificação, download e filtro financeiro (A-51)
- Severidade: ALTA
- Camada: front + back
- Evidência: `DocumentosView.java:102-109` ignora `tipoFinanceiro` no filtro; `DocumentosView.java:97` ao clicar só exibe "Visualização de documentos em preparação"; não há ação de classificar `pendente_revisao` nem download. `grep -rni "download\|FileDownload\|Anchor" ui/src/main/java` não retorna nada.
- Causa raiz: item não implementado; T25 só listou/cadastrou metadados.
- Reprodução: selecionar "Tipo financeiro" e filtrar (sem efeito); clicar em um documento (aviso).
- Dono: front + back
- Critério de aceite: filtros por inquilino/tipo/tipo financeiro no banco, classificação de pendente (tipo e data obrigatórios) e download do arquivo real.
- Status: RESOLVIDO
- Resolução: A-51 (filtros SQL, classificação, download real)
- Tentativas: 0/3
- Commit:

## P-012 Movimentação: R04 não é exigida, pesquisa/exportação mortas e sem período (A-40/A-41/A-43)
- Severidade: ALTA
- Camada: back + front
- Evidência: `MovimentacaoRepositorio.registrar` (`:81-145`) não exige documento/comprovante para SAIDA (R04). `MovimentacaoFinanceiraView.java:39` "Pesquisar Lançamentos" e `:56` "Exportar extrato" apenas mostram `UiKit.aviso`; o extrato é `listar(200)` sem filtro de período (`:46`/`:164`).
- Causa raiz: regras e ações parciais de T25.
- Reprodução: registrar SAIDA sem anexo (é aceita); clicar em Pesquisar/Exportar (avisos).
- Dono: back + front
- Critério de aceite: R03/R04 no serviço (incluindo comprovante obrigatório em SAIDA via documento), pesquisa por período e exportação em PDF persistida como relatório MOVIMENTACAO; testes.
- Status: RESOLVIDO
- Resolução: A-40/A-41/A-43 (R03/R04, extrato, exportação PDF)
- Tentativas: 0/3
- Commit:

## P-013 Filtros em memória carregam a lista inteira (A-20/A-30/A-51)
- Severidade: MEDIA
- Camada: front + back
- Evidência: as views carregam tudo (`repositorio.listar()`) e filtram com `stream().filter`: `ImoveisView.java:104-143`, `ContratosView.java:100-142`, `InquilinosView.java:71-98`, `DocumentosView.java:71-109`. Os repositórios não aceitam parâmetros de filtro.
- Causa raiz: simplicidade de T25; banco tem 124 imóveis/3000 movimentações no seed.
- Reprodução: abrir as telas e conferir que o SQL não tem `WHERE` de filtro (ex.: `ImovelRepositorio.listar`).
- Dono: back + front
- Critério de aceite: filtros como parâmetros de consulta/`DataProvider`/`Specification`, sem carregar tudo.
- Status: RESOLVIDO
- Resolução: A-20/A-30/A-51 (filtros SQL; inquilinos seguem em P-027)
- Tentativas: 0/3
- Commit:

## P-014 Botões mortos e exportações falsas (A-90)
- Severidade: ALTA
- Camada: front
- Evidência: `HomeView.java:25` "Exportar", `ImoveisView.java:41` "Exportar", `ReformasView.java:35` "Exportar" e `:47` "Ver tudo", `MovimentacaoFinanceiraView.java:39` "Pesquisar Lançamentos" e `:56` "Exportar extrato", `DocumentosView.java:97` visualização — todos apenas exibem `UiKit.aviso(...)` sem ação real.
- Causa raiz: protótipo visual preservado sem ligação ao serviço.
- Reprodução: clicar em cada botão e verificar que nada é persistido/gerado.
- Dono: front (com back para as ações)
- Critério de aceite: nenhum botão só-notificação; cada ação executa o fluxo real ou é removida (A-90).
- Status: RESOLVIDO
- Resolução: A-90 (botões mortos removidos; guarda anti-protótipo)
- Tentativas: 0/3
- Commit:

## P-015 Reformas sempre no primeiro imóvel (A-22)
- Severidade: MEDIA
- Camada: front + back
- Evidência: `ReformasView.java:30` usa `repositorio.imovelPadrao()`; `ReformaRepositorio.imovelPadrao` (`:28-48`) retorna o primeiro imóvel por ordem de logradouro. Não há seletor de imóvel na tela.
- Causa raiz: atalho de T25 para ter uma tela funcional.
- Reprodução: abrir `/imoveis/reformas` com vários imóveis; só o primeiro é usado.
- Dono: front + back
- Critério de aceite: selecionar o imóvel e ver/gravar o histórico correto; teste com dois imóveis.
- Status: RESOLVIDO
- Resolução: A-22 (ReformaRepositorio.listarImoveis + ComboBox)
- Tentativas: 0/3
- Commit:

## P-016 Perfis dev/prod, README de execução e documentação de variáveis (A-81)
- Severidade: MEDIA
- Camada: config
- Evidência: existe só `src/main/resources/application.properties`; não há `application-dev.properties`/`application-prod.properties` (o `Dockerfile` ativa `--spring.profiles.active=prod`, que não existe). `README.md` tem 26 bytes. `.env.example` e `Dockerfile` existem.
- Causa raiz: entrega de configuração mínima de T24/T25.
- Reprodução: `ls src/main/resources`; `cat README.md`; subir com `--spring.profiles.active=prod` e conferir que nenhum perfil é carregado.
- Dono: back + qa
- Critério de aceite: perfis dev/prod, README de execução/deploy (Railway/Render) e variáveis documentadas; teste de boot por perfil.
- Status: RESOLVIDO
- Resolução: A-81 (perfis dev/prod, .env.example, README)
- Tentativas: 0/3
- Commit:

## P-017 Juiz verde não cobre os critérios dos itens (A-01 fraco)
- Severidade: ALTA
- Camada: qa
- Evidência: `./scripts/verificar-integracao.sh` retorna 8/8 EXIT=0 mesmo com `grep @Entity` vazio, sem serviços, sem estorno, sem upload real e sem os 4 PDFs. Há só 2 testes Java (`ManueloApplicationTests` e `PersistenciaRoundTripTest`) e 14 testes SQL; o smoke aceita qualquer HTTP 200.
- Causa raiz: juiz de F0 mínimo; etapa 4 valida schema sem entidades e o teste de persistência usa repositório JDBC, não serviço/entidade.
- Reprodução: rodar o juiz e, em paralelo, os greps de P-001/P-002/P-004/P-005.
- Dono: qa
- Critério de aceite: juiz passa a reprovar ausência de entidades/serviços e a executar um teste vermelho por item do backlog antes da implementação.
- Status: RESOLVIDO
- Resolução: A-01 (juiz 8/8 com suíte, smoke, log, round-trip e guarda)
- Tentativas: 0/3
- Commit:

## P-018 /usuarios não altera perfil (A-12)
- Severidade: MEDIA
- Camada: front + back
- Evidência: `UsuariosView.java:50-54` só oferece "Aprovar" e "Inativar"; `UsuarioRepositorio` (`:39-62`) só tem `aprovar` e `inativar`; não há update de `perfil`.
- Causa raiz: escopo mínimo de T25 para desbloquear o cadastro.
- Reprodução: abrir `/usuarios` como ADMINISTRADOR e procurar ação de mudar perfil (não existe).
- Dono: front + back
- Critério de aceite: lista/atualiza perfil (ADMINISTRADOR↔COLABORADOR) com auditoria e teste.
- Status: RESOLVIDO
- Resolução: A-12 (alterar perfil ADMIN no serviço/view)
- Tentativas: 0/3
- Commit:

## P-019 Repositórios de agregados imutáveis expõem save/delete (A-03)
- Severidade: MEDIA
- Camada: back
- Evidência: `MovimentacaoJpaRepository`, `CaucaoMovimentacaoJpaRepository`, `RelatorioJpaRepository`, `RelatorioMovimentacaoJpaRepository`, `RelatorioContratoJpaRepository` estendem `JpaRepository`, expondo `save` (UPDATE) e `delete*`, embora as tabelas sejam imutáveis (R01/R11) e tenham trigger.
- Causa raiz: implementação mínima de A-03; a trigger barra em runtime, mas a API permite a tentativa.
- Reprodução: `grep -n "extends JpaRepository" src/main/java/com/Projeto/manuelo/dominio/repositorio/*.java`.
- Dono: back
- Critério de aceite: interface mínima de escrita (insert/find) para os agregados imutáveis; teste garante que não há update/delete exposto.
- Status: RESOLVIDO
- Resolução: `InsercaoSomente`/`InsercaoSomenteImpl` com `EntityManager.persist`; os 5 repositórios imutáveis só expõem insert/find; `RoundTripEntidadesTest` valida a API.
- Tentativas: 0/3
- Commit:

## P-020 Auditoria JPA sem prova de identidade (A-15)
- Severidade: MEDIA
- Camada: qa + back
- Evidência: `RoundTripEntidadesTest` verifica timestamps não nulos, mas nunca `criado_por/atualizado_por/gerado_por == usuario logado`; `JpaAuditingConfig` retorna vazio sem SecurityContext e na prática P-008 seguirá aberto até A-10/A-15.
- Causa raiz: autenticação ainda não integrada ao SecurityContext (A-10) e teste mínimo de A-03.
- Reprodução: ler `RoundTripEntidadesTest.java` (asserts de auditoria) e `JpaAuditingConfig.auditorAware`.
- Dono: qa + back
- Critério de aceite: teste com usuário autenticado prova os 4 campos de auditoria em cada gravação.
- Status: RESOLVIDO
- Resolução: A-15 (prova de criado_por/atualizado_por por ator)
- Tentativas: 0/3
- Commit:

## P-021 Cadastro A-11 ainda em JDBC (DEC-I01)
- Severidade: MEDIA
- Camada: back + front
- Evidência: `ui/src/main/java/br/com/manuelo/dados/AutenticacaoService.cadastrar` usa `JdbcClient` direto; o teste A-11 lê na mesma JVM, sem provar persistência após reinício.
- Causa raiz: herança do T25; A-11 fechou com o comportamento funcional.
- Reprodução: `grep -n "INSERT INTO usuarios" ui/.../AutenticacaoService.java`.
- Dono: back + front
- Critério de aceite: cadastro via serviço JPA transacional e teste com novo contexto Spring lendo o registro.
- Status: RESOLVIDO
- Resolução: `CadastroUsuarioService` JPA transacional; `AutenticacaoService` JDBC removido; `CadastroUsuarioPersistenciaTest` grava, reinicia o contexto e lê de volta.
- Tentativas: 0/3
- Commit:

## P-022 Perfil/inativação não invalida sessão já aberta (A-80)
- Severidade: MEDIA
- Camada: back + front
- Evidência: `SessaoUsuario` cacheia perfil/id; `UsuarioDetalhes` é carregado no login. Após rebaixar/inativar o próprio usuário, a sessão mantém ROLE antiga até novo login.
- Causa raiz: ausência de revalidação por requisição e de revogação de sessões.
- Reprodução: logar como admin, alterar o próprio perfil para COLABORADOR e acessar `/usuarios` sem novo login.
- Dono: back + front
- Critério de aceite: autorização revalidada contra o banco a cada navegação ou sessão revogada na alteração.
- Status: RESOLVIDO
- Resolução: A-90 (`SessaoUsuario.usuario()` revalida perfil/status no `UsuarioJpaRepository` a cada acesso)
- Tentativas: 0/3
- Commit: 2302e95

## P-023 Envio de token antes do commit e concorrência (A-13)
- Severidade: BAIXA/MEDIA
- Camada: back
- Evidência: `RecuperacaoSenhaService.solicitar` entrega o token dentro da transação; duas solicitações simultâneas podem deixar mais de um token pendente (a invalidação anterior não usa lock).
- Causa raiz: ausência de outbox/pós-commit e de lock pessimista no usuário.
- Reprodução: revisão de `RecuperacaoSenhaService.java:60-115`.
- Dono: back
- Critério de aceite: envio após commit (ou outbox) e lock/constraint que garanta um único token pendente por usuário.
- Status: RESOLVIDO
- Resolução: `findByEmailIgnoreCaseForUpdate` com lock pessimista + entrega em `afterCommit`; testes de after-commit e concorrência em `RecuperacaoSenhaTest`.
- Tentativas: 0/3
- Commit:

## P-024 MANUELO_APP_URL não documentado para o link de senha
- Severidade: BAIXA
- Camada: back + docs
- Evidência: `manuelo.app.url` tem default `http://localhost:8080`; `.env.example`/README não documentam; em deploy o link de recuperação pode apontar para host errado.
- Causa raiz: propriedade criada em A-13 sem documentação (A-81 cobre perfis/README).
- Reprodução: `grep -n "manuelo.app.url" src/main/resources/application.properties .env.example`.
- Dono: back
- Critério de aceite: MANUELO_APP_URL documentada e validada no boot; link correto em dev/prod.
- Status: RESOLVIDO
- Resolução: A-81 (MANUELO_APP_URL documentada em .env.example/README/prod)
- Tentativas: 0/3
- Commit:

## P-025 Senha padrão de admin em application.properties (DEC-I04)
- Severidade: MEDIA
- Camada: back
- Evidência: `manuelo.admin.senha=${MANUELO_ADMIN_SENHA:admin123}` cria admin mesmo sem variáveis de ambiente.
- Causa raiz: conveniência de dev herdada do T25; o perfil test sobrescreve com qa-admin-123.
- Reprodução: subir sem MANUELO_ADMIN_* e conferir admin@manuelo.local criado.
- Dono: back
- Critério de aceite: sem variáveis, nenhum admin é criado (apenas warn); dev documentado no README/.env.example.
- Status: RESOLVIDO
- Resolução: A-14/A-81 (sem default de senha; sem variáveis nenhum admin é criado)
- Tentativas: 0/3
- Commit:

## P-026 Detalhe do imóvel incompleto e filtro de tipo case-sensitive (A-20)
- Severidade: BAIXA
- Camada: front
- Evidência: `ImovelDetalheDialog` omite cidade/UF/CEP, dia de vencimento e depósitos de caução; `ImovelRepositorio` compara `v.tipo::text = :tipo` (case-sensitive) e `%`/`_` não são escapados.
- Causa raiz: escopo mínimo de A-20; aceite do backlog cumprido.
- Reprodução: filtrar tipo "casa" (minúsculo) e abrir um imóvel com contrato.
- Dono: front
- Critério de aceite: detalhe completo conforme CONTEXTO; filtro case-insensitive e com escape de curingas.
- Status: RESOLVIDO
- Resolução: A-90 (detalhe com cidade/UF/CEP, vencimento, caução e reformas; filtros com `lower`/escape)
- Tentativas: 0/3
- Commit: 5f1af2f

## P-027 Edição/filtros de inquilinos e rollback não cobertos (A-21)
- Severidade: MEDIA
- Camada: front + qa
- Evidência: filtros de /inquilinos continuam em memória; a edição exibe fiador/imóvel/aluguel/caução e os descarta; o teste usa savepoint e não prova falha após INSERTs; reuso de fiador sem teste.
- Causa raiz: escopo mínimo de A-21; upload real adiado para A-50.
- Reprodução: editar inquilino e tentar alterar fiador/imóvel; filtrar no banco; forçar EXCLUDE de contrato.
- Dono: front + qa
- Critério de aceite: filtros via SQL; edição do vínculo/fiador suportada ou campos ocultos; teste de rollback pós-INSERT.
- Status: RESOLVIDO
- Resolução: A-90 entregou filtros SQL e edição; `InquilinoCadastroTest` cobre rollback após INSERT com trigger temporária.
- Tentativas: 0/3
- Commit: be15137 (A-90); teste de rollback nesta rodada (não commitado)

## P-028 Dockerfile rejeitado pelo Railway (cache mount sem id)
- Severidade: ALTA (bloqueia deploy)
- Camada: qa (build/deploy)
- Evidência: Railway: `Dockerfile invalid: flag '--mount=type=cache,target=/root/.m2' is missing an id argument at Line 21`.
- Causa raiz: o parser do Railway exige `id` em `--mount=type=cache`; o Dockerfile herdado do template Vaadin também montava secrets `proKey`/`offlineKey` inexistentes e usava `target=/root/.m2` com `HOME=/app`.
- Reprodução: apontar o deploy do Railway para o repositório e aguardar a fase de build.
- Dono: qa
- Critério de aceite: Dockerfile parseável pelo Railway; estágio de build sem secret mounts obrigatórios; cache em `/app/.m2`.
- Status: RESOLVIDO
- Resolução: DEC-I15; `RUN --mount=type=cache,id=manuelo-m2,target=/app/.m2 ./mvnw clean package -DskipTests`, sem estágio `jq` e sem secrets.
- Tentativas: 0/3
- Commit: este commit (fix(deploy): Dockerfile Railway)

## P-029 Railway responde "The train has not arrived at the station" (404 de borda)
- Severidade: ALTA (bloqueia acesso público)
- Camada: qa (deploy/infra Railway)
- Evidência: `Not Found — The train has not arrived at the station. Please check your network settings to confirm that your domain has provisioned.` com Request ID `L4kegJ6XRZmB7Pt9Aax-fw`.
- Causa raiz: ainda não determinada. A página é gerada pela borda do Railway, não pelo Spring: a requisição não chega na aplicação (não há log dela). Hipóteses, em ordem: (1) Private Networking desabilitado no serviço — mudança de plataforma do Railway que exige habilitar Settings > Networking > Private Networking e redeployar; (2) deployment sem container saudável (variáveis obrigatórias do perfil `prod` ausentes derrubam o boot por placeholder não resolvido); (3) target port do domínio diferente da porta em que o app escuta (Railway injeta `PORT=8080`; a app lê `server.port=${PORT:...}`).
- Reprodução: acessar o domínio público do serviço no Railway.
- Dono: qa (infra) + back (diagnóstico de boot, se necessário)
- Critério de aceite: domínio público responde do app (ex.: `/login` renderiza ou redireciona), log do container mostra `Tomcat started on port <PORT>` e o deploy fica `Online` sem crash.
- Status: EM ANÁLISE
- Tentativas: 0/3
- Checklist: conferir status/logs do deploy; habilitar Private Networking e Redeploy; conferir target port = porta do log; conferir `DB_URL`, `DB_USER`, `DB_PASSWORD`, `MANUELO_ADMIN_EMAIL`, `MANUELO_ADMIN_SENHA`, `MANUELO_APP_URL`, `MANUELO_STORAGE_DIR`; se domínio custom/Cloudflare, conferir CNAME e SSL/TLS Full.
- Commit: pendente

## P-030 Navegação entre abas acima de 5 s (meta 1-3 s)
- Severidade: ALTA (usabilidade em produção)
- Camada: front + back + qa
- Evidência: relato do usuário: clicar em uma aba (ex.: Documentos) leva mais de 5 s para responder.
- Causa raiz provável: excesso de round-trips ao banco no caminho da navegação. (1) `SessaoUsuario.usuario()` chama
  `usuarios.findById` a cada invocação — `MainLayout` invoca `autenticado()`, `administrador()` e `usuario()`, ou seja,
  2-3 transações JPA por navegação. (2) `DocumentosView` monta `documentosOpcoes()` no construtor (UNION ALL de imóveis,
  inquilinos, contratos e movimentações, ordenado) mesmo sem o usuário abrir o diálogo. (3) Cada método `@Transactional`
  gera BEGIN/SELECT/COMMIT, multiplicando o RTT. (4) Railway e Supabase podem estar em regiões diferentes.
- Reprodução: autenticar em produção e clicar nas abas; comparar com o tempo no log de requisições.
- Dono: front (sessão/opções), back (compressão/log), qa (testes)
- Critério de aceite: nenhuma chamada `*Opcoes()` em construtores de view; `SessaoUsuario` revalida no máximo 1x a cada
  30 s; compressão habilitada; log de requisições > 1,5 s; navegação medida em 1-3 s.
- Status: EM_ANDAMENTO (correções aplicadas; teste novo verde; falta juiz completo e medição em produção)
- Resolução parcial: SessaoUsuario com TTL de 30 s; `*Opcoes()` fora dos construtores; compressão HTTP; filtro `Requisição lenta` (commit 823c387).
- Tentativas: 1/3

## P-031 E01/FE46 — PDF trimestral não gerava/baixava
- Severidade: CRÍTICA (bloqueava FE47)
- Camada: front (download) + back (geração já existente)
- Evidência: `RelatoriosView.baixar`/`ExportarExtratoDialog.baixar` usavam `UI.getCurrent().getPage().open(...)` com `StreamResource`; após o round-trip do servidor o navegador não baixava o arquivo.
- Causa raiz: `Page.open` de uma URI de `StreamRegistration` não é confiável como download em Vaadin; o correto é `Anchor` com `StreamResource` e clique.
- Correção: `UiKit.download(nome, mime, conteudo)` + `anchor.getElement().executeJs("this.click()")`; `ArquiteturaViewsTest` proíbe `getPage().open` em 5 views (`RelatoriosView`, `ExportarExtratoDialog`, `MovimentacaoFinanceiraView`, `DocumentosView`, `ContratoDetalheView`).
- Status: RESOLVIDO (working tree, sem commit)
- Teste: `ArquiteturaViewsTest.downloadsDeArquivoUsamAnchorENaoPageOpen`
- Commit: pendente

## P-032 F01/FE09 — e-mail de redefinição de senha não chega
- Severidade: ALTA
- Camada: back (SMTP) + infra (variáveis do Railway)
- Evidência: `EnvioTokenSenhaImpl` só envia quando `spring.mail.host` está configurado; em produção sem `SPRING_MAIL_HOST` o envio era silencioso.
- Causa raiz: configuração de SMTP ausente no ambiente; não havia diagnóstico explícito do motivo.
- Correção: log de diagnóstico no boot e no envio (host/porta, sem usuário/senha), aviso explícito quando não há SMTP, mascaramento de e-mail e de `token=` em mensagens de erro, token nunca logado fora de dev; `.env.example` já documenta `SPRING_MAIL_*`.
- Status: RESOLVIDO no código; entrega real depende de `SPRING_MAIL_HOST/USERNAME/PASSWORD` no ambiente (LACUNA L2)
- Teste: `EnvioTokenSenhaImplTest` 3/3
- Commit: pendente

## P-033 E02/FE49 — interface não responsiva no celular
- Severidade: ALTA
- Camada: front (CSS)
- Evidência: CSS com larguras fixas (sidebar 347px, grids de tabela até ~1000px, autenticação com paddings de 160px/48px) e sem breakpoint abaixo de 760px.
- Causa raiz: breakpoints insuficientes para telas pequenas e tabelas largas sem rolagem horizontal.
- Correção: `.table { overflow-x: auto }`; breakpoints 1200/900/760 já existentes reforçados; novo breakpoint 520px para `.app-content`, `.sidebar`, `.card`, `.page-actions`, `.modal-*`, `.doc-item`, `.segmented` e fluxo de autenticação.
- Status: RESOLVIDO no CSS; validação visual em navegador real pendente (LACUNA L3)
- Teste: `ResponsividadeCssTest` 4/4
- Commit: pendente

## P-034 E03 — datas em MM/DD/AAAA
- Severidade: ALTA
- Camada: front
- Causa raiz: `DatePicker` sem `I18N`/locale pt-BR em alguns campos.
- Correção: centralizada em `UiKit.date` + `UiKit.PT_BR` (commit 2a8c8af), com `UiKitDateTest`.
- Status: RESOLVIDO (commit 2a8c8af)
- Teste: `UiKitDateTest` 1/1
- Commit: 2a8c8af

## P-035 E04/FE30 — filtro "atrasados" mostrava aluguéis a vencer
- Severidade: ALTA
- Camada: front + consulta
- Evidência: contrato com `fim` futuro e competência antiga em aberto aparecia como "Atrasado" (correto por R06), mas a coluna de dias exibia "Faltam N dias" (dias para o fim do contrato), dando a impressão de aluguel a vencer.
- Causa raiz: `textoDias` usava `dias` (dias para o fim do contrato) em vez de `dias_atraso` quando a situação era Atrasado.
- Correção: `Modelos.Contrato.diasAtraso` + `vw_contratos_situacao.dias_atraso`; `textoDias` prioriza "Atrasado há N dias".
- Status: RESOLVIDO (working tree, sem commit)
- Teste: `ContratosFiltrosTest` (A-94)
- Commit: pendente

## P-036 E05/FE18 — filtro por endereço completo gerava erro
- Severidade: ALTA
- Camada: back (consulta)
- Causa raiz: filtro comparava o texto inteiro com `ILIKE` em um único campo, então "logradouro, número - bairro" não casava.
- Correção: `ImovelRepositorio.tokensEndereco` divide o texto e aplica cada token em logradouro/numero/complemento/bairro/cidade/uf/cep.
- Status: RESOLVIDO (working tree, sem commit)
- Teste: `ImoveisFiltrosTest.filtraPorEnderecoCompletoLogradouroNumeroBairro` (A-95)
- Commit: pendente

## P-037 E06/FE26 — máscara de CPF não aplicada
- Severidade: MÉDIA
- Camada: front + back
- Causa raiz: campos de CPF usavam `UiKit.text` sem máscara; serviços validavam apenas o tamanho.
- Correção: `UiKit.cpf` (caracteres permitidos + formatação no blur) e `DocumentoFiscal` (dígitos verificadores de CPF/CNPJ) usado por `InquilinoCadastroService` e `ContratoCadastroService`.
- Status: RESOLVIDO (working tree, sem commit)
- Teste: `InquilinoCadastroTest` (A-96) e `ContratoCadastroTest`
- Commit: pendente

## P-038 E07/FE41 — exportar extrato não gerava arquivo
- Severidade: MÉDIA (RF12)
- Camada: front (download) + back (geração existente)
- Causa raiz: mesmo `Page.open` de P-031.
- Correção: `ExportarExtratoDialog` usa `UiKit.download` (Anchor + clique).
- Status: RESOLVIDO (working tree, sem commit)
- Teste: `ArquiteturaViewsTest.downloadsDeArquivoUsamAnchorENaoPageOpen`
- Commit: pendente

## P-039 O01/FE04 — mensagem genérica no cadastro
- Severidade: BAIXA
- Camada: front + back
- Correção: `CadastroUsuarioService.cadastrarComResultado` (OK/EMAIL_EM_USO/DADOS_INVALIDOS) e `CadastroView` com mensagens específicas.
- Status: RESOLVIDO (working tree, sem commit)
- Teste: `CadastroUsuariosTest` (A-98) e `ArquiteturaViewsTest.cadastroViewUsaResultadoDetalhadoDoServico`
- Commit: pendente

## P-040 O02/FE43 — ordem de "Documentos recentes"
- Severidade: BAIXA
- Camada: back (consulta)
- Correção: `ORDER BY enviado_em DESC, nome_arquivo ASC, id ASC` (determinística) em `DocumentoRepositorio.listarRecentes`; decisão registrada em DEC-I17.
- Status: RESOLVIDO (working tree, sem commit)
- Teste: `ArquiteturaViewsTest.documentosRecentesOrdenamPorEnviadoEmEId`
- Commit: pendente
