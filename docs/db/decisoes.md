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
