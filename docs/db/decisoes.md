# Decisões novas tomadas pelo agente (append-only)
As DEC-01 a DEC-13 já estão em docs/CONTEXTO.md. Registre aqui só as novas, a partir de DEC-14.

Modelo de entrada:
## DEC-14 título curto
- Contexto: o que estava ambíguo e onde (arquivo/tela)
- Escolha: o que foi decidido
- Motivo: precedência aplicada ou critério de desempate
- Reverter: como desfazer (migration e arquivos afetados)

## DEC-14 ordem de migrations: documentos antes dos triggers de movimentação
- Contexto: T07 (V006) exige o trigger deferido de comprovante em SAIDA, que consulta a tabela
  `documentos`; T08 (V007) é quem cria `documentos`. A ordem original criaria uma dependência invertida.
- Escolha: V006 cria `documentos` e `reformas` (T08); V007 cria os triggers de integridade das
  movimentações, incluindo o comprovante deferido (T07). T07 continua dependendo de V005.
- Motivo: dependência física real; a precedência do CONTEXTO não define ordem de migrations.
- Reverter: se `documentos` voltar para V007, mover o trigger `trg_movimentacao_exige_comprovante_saida`
  para a migration seguinte e ajustar os testes.


## DEC-15 endereço completo com logradouro, número, bairro e cidade obrigatórios
- Contexto: o CONTEXTO não marca quais campos de endereço são opcionais; o schema-base deixava número e
  bairro nulos.
- Escolha: logradouro, numero, bairro e cidade NOT NULL; complemento e cep opcionais; uf com default 'SE'
  e CHECK de duas letras maiúsculas; cep no formato 00000-000 ou 00000000.
- Motivo: desempate do AGENTS a favor da integridade; endereço sem número/bairro não é utilizável.
- Reverter: nova migration com ALTER TABLE imoveis ALTER COLUMN ... DROP NOT NULL e drop dos CHECKs.

## DEC-16 documento e telefone normalizados em dígitos
- Contexto: CPF/CNPJ aparecem formatados no Figma; a busca por CPF precisa funcionar.
- Escolha: `documento` guarda só dígitos (11 para CPF, 14 para CNPJ) em inquilinos e fiadores;
  `telefone` guarda 10 ou 11 dígitos. A formatação fica na interface.
- Motivo: unicidade e busca determinísticas; CHECK mais restritivo e reversível.
- Reverter: nova migration ampliando o CHECK para aceitar pontuação e ajustando os seeds.

## DEC-17 backend permanece na raiz; frontend entra em `frontend/`
- Contexto: o pedido sugeria `backend/` + `frontend/`, mas o repositório já é o projeto Spring Boot na
  raiz e o `pom.xml` referencia `db/migrations` em caminho relativo; `scripts/verificar-db.sh` calcula
  `ROOT` a partir da própria pasta.
- Escolha: manter o backend na raiz (layout Spring Boot padrão) e copiar o frontend para `frontend/`,
  sem projeto Maven agregador e sem mover `db/`, `scripts/` ou `pom.xml`.
- Motivo: mover quebraria o build (recurso `db/migrations`) e o juiz do banco, contrariaria "não mover
  configuração essencial do Spring Boot" e o escopo do AGENTS.md; a estrutura do pedido é declarada
  "não uma imposição".
- Reverter: criar `backend/`, mover `pom.xml`, `src/`, `mvnw*`, `.mvn/` e `db/` para dentro dele e
  ajustar `ROOT`/caminhos do compose; nenhuma migration ou teste muda.

## DEC-18 copiar apenas arquivos versionados do frontend
- Contexto: o repositório de frontend tem `target/`, `src/main/frontend/generated/`, `.vscode/`,
  `.github/` e `.git` locais, além dos 33 arquivos rastreados.
- Escolha: copiar exatamente `git ls-files` via `git archive HEAD`, preservando bytes e hierarquia;
  excluir artefatos de build, caches, configuração local de IDE e o `.git` aninhado.
- Motivo: garante conteúdo idêntico verificável por SHA-256 e evita lixo/segredos de máquina; o
  Vaadin regenera `src/main/frontend/generated/` no primeiro build.
- Reverter: remover `frontend/` e repetir a cópia incluindo os caminhos ignorados, se algum for
  necessário.

## DEC-19 usar o clone CESAR do backend, não o clone em Projetos
- Contexto: o pedido aponta `C:\Users\ronald\Projetos\Projeto_ManuELO_Backend`, mas esse clone
  está em `main` com alterações locais não commitadas (inclui `db/migrations/V010`); o workspace da
  sessão é `C:\Users\ronald\CESAR\Projeto_ManuELO_Backend`, limpo e no branch
  `featute/intregracao_front`.
- Escolha: copiar e commitar no clone CESAR; não escrever nada no clone em Projetos.
- Motivo: evitar sobrescrever trabalho alheio e manter a tarefa no clone/branch corretos; o conteúdo
  rastreado do backend é o mesmo commit-base.
- Reverter: repetir a cópia (mesmo comando `git archive`) no outro clone e remover `frontend/` daqui.

## DEC-20 build Maven único na raiz sem mover fontes nem duplicar classes
- Contexto: T23 pede um único ponto de build na raiz; o pedido proíbe mover pacotes/renomear classes,
  mas o código Vaadin está em `frontend/src/main/java/br/com/manuelo`.
- Escolha: `build-helper-maven-plugin:3.6.1` (`add-source`) registra `frontend/src/main/java` como
  raiz de código; `frontend/src/main/resources` entra como `<resource>` **excluindo**
  `application.properties`. `com.vaadin:vaadin` foi incorporado (era declarado no frontend), e
  `spring-boot-starter-test`/`browserless-test-spring` **não** foram adicionados (não há testes em
  `frontend/src/test`; a raiz já tem os starters de teste granulares do Boot 4.1).
- Motivo: cumpre o build único preservando os arquivos byte a byte; a exclusão do
  `application.properties` evita que a configuração do frontend (sem datasource/Flyway) sobrescreva a
  do backend no classpath — a raiz é a fonte da verdade de `spring.datasource`/`spring.flyway`.
  Não adicionar repositórios `vaadin-directory`/`vaadin-prereleases` porque todos os artefatos
  resolvem do Central (verificado com o build).
- Reverter: remover o plugin `build-helper` e o `<resource>` de `frontend/src/main/resources` e
  voltar ao build do backend apenas; a dependência `com.vaadin:vaadin` pode sair se o starter
  `vaadin-spring-boot-starter` (que já traz `vaadin-core-internal`) bastar.

## DEC-21 pasta `frontend/` continua fonte das telas; `npmFolder` fica na raiz
- Contexto: o Vaadin 25.3.5 executa o Vite com diretório de trabalho = `${project.basedir}`. Ao
  apontar `npmFolder`/`projectBasedir` para `frontend/`, o build falha no Windows porque o
  `vite.generated.ts` gerado importa `./..\target/plugins/...` (a sequência `\t` vira tab e o módulo
  não resolve); mantendo o `npmFolder` na raiz, importa `./target/plugins/...` e o Vite build passa.
- Escolha: apenas `<frontendDirectory>${project.basedir}/frontend/src/main/frontend</frontendDirectory>`;
  `npmFolder` permanece o padrão (raiz). O Vaadin gera na raiz `package.json`, `package-lock.json`,
  `tsconfig.json`, `types.d.ts`, `vite.config.ts`, `vite.generated.ts`, `node_modules/` e
  `src/main/bundles/prod.bundle` — artefatos de build, não versionados nesta tarefa.
- Motivo: é a única combinação que compila as telas de `frontend/src/main/frontend` (inclusive
  `animacoes.css`, que entra no bundle) e empacota `META-INF/VAADIN/**` no JAR, sem tocar em Java
  nem mover arquivos. O `package.json` gerado tem o mesmo conteúdo do de `frontend/package.json`
  (difere apenas no fim de linha).
- Reverter: remover o `<frontendDirectory>` do `vaadin-maven-plugin` (volta a usar
  `src/main/frontend` da raiz) e apagar os artefatos gerados na raiz. Quando o plugin corrigir o
  separador de caminho no Windows, `npmFolder`/`projectBasedir` podem apontar para `frontend/`.

## DEC-22 `frontend/pom.xml` removido e `mainClass` explícita no `spring-boot-maven-plugin`
- Contexto: dois POMs independentes (raiz e `frontend/`) e duas classes `@SpringBootApplication`
  (`com.Projeto.manuelo.ManueloApplication` e `br.com.manuelo.Application`) no classpath consolidado.
- Escolha: apagar `frontend/pom.xml` (o `mvnw`/`.mvn` de `frontend/` ficam inertes, sem POM) e fixar
  `<mainClass>com.Projeto.manuelo.ManueloApplication</mainClass>`.
- Motivo: um único ponto de build (critério da T23) e `repackage` determinístico — sem `mainClass` o
  plugin falha com "Unable to find a single main class". Preserva a entrada atual do artefato
  `com.Projeto:manuelo`; as telas Vaadin são descobertas por varredura de classpath independente do
  pacote da classe de boot.
- Reverter: restaurar `frontend/pom.xml` com `git checkout -- frontend/pom.xml` (ou o backup em
  `%TEMP%\frontend-pom.xml.bak`) e remover o `<mainClass>` do POM raiz. A unificação da classe de boot
  (e do pacote das telas) é decisão de código da T24.

## DEC-23 raiz é o local oficial das configurações npm, Vite e TypeScript
- Contexto: a consolidação da T23 deixou duas cópias de cada configuração de frontend — uma na raiz,
  efetivamente usada pelo build (a DEC-21 manteve `npmFolder` na raiz), e outra em `frontend/`,
  herdada da cópia do repositório de frontend feita na T21. Nenhuma referência (POM, Java, scripts ou
  config) aponta para as cópias de `frontend/`; a cópia de `vite.generated.ts` estava inválida
  (`./..\target/...`).
- Escolha: a raiz é o local oficial de `package.json`, `package-lock.json`, `tsconfig.json`,
  `types.d.ts` e `vite.config.ts`, que passam a ser versionados. `vite.generated.ts`, `node_modules/`,
  `src/main/bundles/` e `src/main/frontend/generated/` continuam ignorados, por serem gerados a cada
  build. As cinco cópias de `frontend/` saem do índice com `git rm --cached`, preservadas no disco
  nesta tarefa. O build Maven continua único na raiz (T23), com
  `<frontendDirectory>frontend/src/main/frontend</frontendDirectory>` e `npmFolder` na raiz.
- Motivo: versionar a configuração efetiva torna o build reproduzível — sem `package-lock.json`, um
  clone limpo resolveria dependências de forma não determinística — e o próprio `tsconfig.json` /
  `types.d.ts` informa "recommended to commit". Arquivo gerado não entra no controle de versão:
  `vite.generated.ts` é sobrescrito a cada execução do `flow:prepare-frontend` e geraria diff eterno.
  Manter duas cópias versionadas convidava à divergência silenciosa entre a configuração lida pelo
  build e a configuração editada.
- Reverter: `git rm --cached` dos cinco arquivos da raiz e `git add` das cinco cópias de `frontend/`
  restaura o estado anterior (os arquivos físicos de `frontend/` nunca foram apagados). Se o plugin
  corrigir o separador de caminho no Windows e `npmFolder` voltar para `frontend/`, as cópias de
  `frontend/` passam a ser a fonte oficial e as da raiz deixam de ser versionadas.

## DEC-24 `frontend/` vira `ui/`; assets Vaadin vão para `src/main/frontend`
- Contexto: a T23 manteve as telas em `frontend/src/main/frontend` e o `pom.xml` apontava
  `<frontendDirectory>` para lá. O modo de desenvolvimento do Vaadin 25 trata qualquer pasta
  `frontend/` na raiz como **frontend legado** e ignora o `frontendDirectory`; o build de produção
  usa o `frontendDirectory`. Resultado: `@CssImport("./animacoes.css")` resolvia no build e falhava
  no dev (ou vice-versa), com `[UNRESOLVED_IMPORT]`. Além disso, o `Dockerfile` e os wrappers
  `ui/mvnw`/`ui/.mvn` ficaram sem `pom.xml` depois da T23.
- Escolha: renomear a pasta copiada de `frontend/` para `ui/`; manter em `ui/` o Java
  (`ui/src/main/java/br/com/manuelo`), os resources (`ui/src/main/resources`) e o bundle de
  desenvolvimento; mover o único asset de frontend (`animacoes.css`) para o diretório padrão do
  Vaadin, `src/main/frontend/`; apontar `<frontendDirectory>` para `${project.basedir}/src/main/frontend`;
  atualizar `tsconfig.json`; mover `Dockerfile`/`.dockerignore` para a raiz; remover os wrappers
  inertes `ui/mvnw`, `ui/mvnw.cmd` e `ui/.mvn`.
- Motivo: o diretório padrão `src/main/frontend` é usado tanto pelo dev server quanto pelo
  `build-frontend`, elimina o aviso de frontend legado e mantém uma única fonte para CSS/TS. O
  `Dockerfile` na raiz usa o `pom.xml`/`mvnw` corretos no contexto de build, e o `.dockerignore`
  passa a excluir `.env` (o antigo não excluía). As telas Java continuam em `br.com.manuelo`.
- Reverter: `git mv ui frontend`, mover `src/main/frontend/animacoes.css` de volta para
  `frontend/src/main/frontend/`, restaurar os wrappers com `git checkout -- frontend/mvnw
  frontend/mvnw.cmd frontend/.mvn`, devolver `Dockerfile`/`.dockerignore` para `frontend/` e
  restaurar os paths antigos em `pom.xml` e `tsconfig.json`.

## DEC-25 `@EnableVaadin("br.com.manuelo")` define a raiz de varredura das rotas
- Contexto: com o build único (T23/T24), o `@SpringBootApplication` está em
  `com.Projeto.manuelo`, mas as views Vaadin estão em `br.com.manuelo`. Em dev o app subia e
  `/` exibia `No views found`; o `RouteRegistry` registrava zero rotas. O log
  `FullDependenciesScanner: Visited N classes` não indicava o problema. Causa raiz confirmada
  por um `VaadinServiceInitListener` temporário: o `VaadinServletContextInitializer` usa como
  raiz de varredura as `AutoConfigurationPackages` do Spring Boot (o pacote da classe de boot)
  quando não há `VaadinScanPackages`; `vaadin.allowed-packages` apenas **restringe** a varredura,
  não acrescenta pacotes externos.
- Escolha: anotar `ManueloApplication` com `@EnableVaadin("br.com.manuelo")`; manter
  `scanBasePackages = { "com.Projeto.manuelo", "br.com.manuelo" }` para os beans Spring. Remover
  a implementação duplicada de `AppShellConfigurator`/`@StyleSheet` da classe de boot (o shell
  oficial é `br.com.manuelo.AppShell`). Remover o `HomeView` de exemplo criado em
  `com.Projeto.manuelo.views` (rota `""` concorrente, não versionado) e a cópia solta de
  `src/main/resources/META-INF/resources/styles.css`.
- Motivo: uma linha explícita resolve a descoberta de rotas sem mover/renomear as 13 classes do
  frontend nem duplicar pacotes; o Spring continua escaneando os dois pacotes para beans.
- Reverter: remover `@EnableVaadin` e mover as views para um subpacote de
  `com.Projeto.manuelo`, ou registrar `VaadinScanPackages` manualmente.   `vaadin.allowed-packages`
  deve continuar contendo `br.com.manuelo`.

## DEC-26 persistência explícita com JdbcClient, sem JPA
- Contexto: as telas usavam listas fixas de `DemoData`; nada era gravado. O schema é a fonte da
  verdade e tem triggers, enums e constraints que o Hibernate não representa.
- Escolha: repositórios em `br.com.manuelo.dados` com `JdbcClient` e SQL explícito; os records de
  `DemoData` viram DTOs de exibição preenchidos pelos repositórios. `ddl-auto=validate` continua.
- Motivo: SQL direto respeita enums/triggers/views existentes e evita mapeamento duplicado;
  as telas mudam pouco (trocam a lista fixa pela consulta).
- Reverter: criar entidades JPA e repositórios Spring Data, mantendo as mesmas consultas/regras;
  remover `dados/*Repositorio` e ajustar as views.

## DEC-27 autenticação de sessão Vaadin com HTTP liberado
- Contexto: `SecurityConfig` liberava tudo e o login só navegava para `/home`; não havia usuário.
- Escolha: `SessaoUsuario` com `@VaadinSessionScope` guarda o usuário autenticado; o login consulta
  `usuarios`, valida BCrypt e o status; `MainLayout` implementa `BeforeEnterObserver` e redireciona
  para `/` sem sessão. O HTTP permanece `permitAll` para os recursos estáticos do Vaadin.
- Motivo: o Vaadin precisa do bootstrap/estáticos sem autenticação HTTP; a fronteira de acesso fica
  na navegação das rotas com layout. Evita formulário Spring Security concorrente com o login Figma.
- Reverter: habilitar `formLogin`/`oauth2` no `SecurityConfig` e trocar o gate do `MainLayout` por
  `@PreAuthorize`/regras de request; remover `SessaoUsuario`.

## DEC-28 administrador inicial por variáveis de ambiente
- Contexto: o seed mantém hashes inválidos de propósito e não havia credencial de acesso real.
- Escolha: `AdminInicial` cria um usuário ATIVO/ADMINISTRADOR na primeira subida a partir de
  `MANUELO_ADMIN_EMAIL`/`MANUELO_ADMIN_SENHA` (padrão de dev `admin@manuelo.local` / `admin123`).
- Motivo: permite testar login/cadastro/aprovação sem colocar hash de senha no repositório; trocar
  as variáveis em produção é suficiente.
- Reverter: remover o bean `AdminInicial` e criar o administrador com `db/seed/primeiro_admin.sql`
  ou com um script operacional.

## DEC-29 tela /usuarios para aprovação (fora do Figma)
- Contexto: o cadastro cria `PENDENTE_APROVACAO`, mas não existia rota para o administrador aprovar.
- Escolha: adicionar `/usuarios` (visível só para ADMINISTRADOR) com aprovar/inativar. Reaproveita
  o `UiKit`/tabela existentes e mantém as telas do Figma intactas.
- Motivo: fecha o fluxo de cadastro ponta a ponta sem alterar as telas originais.
- Reverter: remover `UsuariosView` e o item de menu; aprovar via SQL (`UPDATE usuarios ...`).

## DEC-30 "Laudo" e relatórios
- Contexto: o formulário de documentos oferece "Laudo", mas `tipo_documento` não tem esse valor; o
  botão de relatório não gravava nada.
- Escolha: mapear "Laudo" para `DOCUMENTO_CONTABIL` (sem alterar o enum) e registrar o relatório em
  `relatorios`/`relatorio_movimentacoes`/`relatorio_contratos`, gerando PDF ou XLSX em
  `${java.io.tmpdir}/manuelo-relatorios`.
- Motivo: preserva o enum já testado e dá destino real ao botão, sem armazenar binários no banco.
- Reverter: adicionar `LAUDO` ao enum em nova migration (não transacional) e/ou remover a geração
  de arquivo, mantendo só o registro do relatório.
