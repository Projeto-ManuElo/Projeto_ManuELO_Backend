# Registro de problemas (o mapeador cria; o líder mantém o status)

Resumo: total 18 | abertos 18 | em correção 0 | em revisão 0 | resolvidos 0 | bloqueados 0

Modelo de bloco (copie para cada problema):

## P-001 Ausência total de JPA/entidades/BaseEntity/AuditorAware (DEC-I01)
- Severidade: BLOQUEANTE
- Camada: back + banco
- Evidência: `grep -rn "@Entity\|JpaRepository\|EntityManager\|BaseEntity\|AuditorAware" src ui --include=*.java` não retorna nada. Todo acesso é `JdbcClient` (ex.: `ui/src/main/java/br/com/manuelo/dados/ImovelRepositorio.java:25-30`). `spring.jpa.hibernate.ddl-auto=validate` (`src/main/resources/application.properties:13`) passa porque não há entidade mapeada.
- Causa raiz: T25 (commit b33ff36) escolheu JdbcClient (DEC-26) em vez de Spring Data JPA, divergindo da DEC-I01; entidades nunca foram criadas.
- Reprodução: rodar o grep acima; subir o app e observar "Initialized JPA EntityManagerFactory" sem nenhum mapeamento; o juiz etapa 4 passa vazio.
- Dono: back
- Critério de aceite: uma entidade JPA por tabela do modelo v3 (UUID/BigDecimal/timestamptz/enums) + `BaseEntity`/`AuditorAware` + `movimentacoes` `@Immutable`; teste de round-trip por entidade e `validate` não vazio.
- Status: ABERTO
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
- Status: ABERTO
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
- Status: ABERTO
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
- Status: ABERTO
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
- Status: ABERTO
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
- Status: ABERTO
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
- Status: ABERTO
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
- Status: ABERTO
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
- Status: ABERTO
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
- Status: ABERTO
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
- Status: ABERTO
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
- Status: ABERTO
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
- Status: ABERTO
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
- Status: ABERTO
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
- Status: ABERTO
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
- Status: ABERTO
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
- Status: ABERTO
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
- Status: ABERTO
- Tentativas: 0/3
- Commit:
