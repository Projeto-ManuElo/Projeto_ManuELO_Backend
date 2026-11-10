# Integração e organização da estrutura do projeto (T21)

Data: 10/11/2026. Escopo desta entrega: **apenas analisar, copiar e organizar arquivos**. Nenhuma
linha de Java, HTML, CSS, TypeScript, pom, migration ou configuração de runtime foi alterada, criada
ou removida. O frontend original permaneceu intacto (ver §6).

## 1. Fontes consultadas e precedência

1. `docs/CONTEXTO.md` (fonte da verdade).
2. `docs/referencias/figma/*.pdf` (telas).
3. `docs/referencias/schema-base.dbml`.
4. PDFs de modelagem em `docs/referencias/`:
   - `ManuElo_Modelagem_Parte1_Contexto_e_Modelagem_Conceitual.pdf`
   - `ManuElo_Modelagem_Parte3_Fluxos_Rastreabilidade_e_Validacao.pdf`
   - `ManuElo_Diagrama_Casos_de_Uso_docx__2_.pdf` e `DiagramaBD.pdf`

Trechos que decidem a arquitetura da integração (Parte 1, Tabela 9):

| ID | Requisito |
|---|---|
| RNF01 | 100% Java 21; Spring Boot 3; **Vaadin Flow no mesmo projeto** |
| RNF03 | Monólito modular em camadas; módulos se comunicam pela camada Service |
| RNF07 | Nada é apagado; correção por registro vinculado ao original |
| RNF12 | Upload de PDF e imagem (MIME e tamanho registrados); relatórios em PDF; exportação XLSX/CSV |
| RNF16 | Evolução para novos módulos; **REST possível no futuro** |

Consequência: o "frontend" deste projeto **não é uma SPA React/HTML**. É uma aplicação Vaadin Flow
(UI 100% Java, renderizada no servidor). A integração prevista pelo RNF01 é a UI Vaadin **no mesmo
processo Spring Boot**, chamando a camada Service — não obrigatoriamente REST. REST/CORS só serão
necessários se as duas aplicações continuarem separadas.

## 2. O que o frontend realmente é

- Spring Boot 4.1.2 + Vaadin 25.3.5, Java 21, Maven (wrapper próprio), grupo `br.com.manuelo`,
  artefato `frontend`.
- UI escrita em Java (`src/main/java/br/com/manuelo`). Não há HTML/CSS/TS autoral: `package.json`,
  `vite.config.ts`, `tsconfig.json` e `src/main/frontend/generated/**` são gerados/gerenciados pelo
  `vaadin-maven-plugin`.
- Telas existentes (rotas Vaadin):

| Arquivo | Rota | Conteúdo |
|---|---|---|
| `LoginView.java` | `` (raiz) | e-mail, senha, links "Esqueci a senha" e "Cadastre-se" |
| `CadastroView.java` | `/cadastro` | nome, e-mail, senha, confirmar senha |
| `AprovacaoView.java` | `/aprovacao` | aviso de aguardando aprovação |
| `EmailEnviadoView.java` | `/email-enviado` | aviso de e-mail de recuperação enviado |
| `RecuperarSenhaView.java` | `/recuperar-senha` | e-mail |
| `RedefinirSenhaView.java` | `/redefinir-senha` | nova senha, confirmar |
| `HomeView.java` | `/home` | 4 cards de indicadores + grid de movimentações recentes |
| `ImoveisView.java` | `/imoveis` | filtros (endereço, tipo, status, inquilino) + grid + diálogo |
| `ImovelDialog.java` | — | formulário de imóvel (endereço, tipo, status, inquilino) |
| `MainLayout.java` / `AppShell.java` | — | menu lateral, tema Aura, Push |
| `Application.java` | — | `@SpringBootApplication` |

- **Estado atual dos dados: tudo local/vazio.** `HomeView.atualizarMovimentacoes(new ArrayList<>())`,
  `ImoveisView.atualizarImoveis(new ArrayList<>())`, `record` internos com valores formatados
  (`String valor = "+ R$ 1.850"`), botões apenas navegam. Não há `RestClient`, `WebClient`, JPA,
  Security, login real nem chamada HTTP. Ou seja, hoje o frontend **não depende** do backend para
  compilar ou rodar.

## 3. O que o backend realmente é

- Spring Boot 4.1.2 + Java 21 + Vaadin 25.3.5 (o `pom.xml` da raiz já traz `vaadin-spring-boot-starter`,
  `spring-boot-starter-data-jpa`, Flyway, Security, Validation, WebMVC, OpenPDF, JasperReports, POI,
  Google Drive API, Lombok e PostgreSQL).
- Código Java existente: `src/main/java/com/Projeto/manuelo/ManueloApplication.java` (e testes em
  `src/test/java/...`). `src/main/resources/static`, `templates` e `db/migration` ainda vazios.
- O `pom.xml` **copia `db/migrations` para `db/migration`** no classpath (bloco `<resources>`), isto é,
  o build do Spring depende do caminho relativo `db/migrations`.
- Banco completo e validado em `db/` (V001..V011, testes, seed, bundle Supabase), documentação em
  `docs/db/` e juiz em `scripts/verificar-db.sh` (que calcula `ROOT` a partir da própria pasta).
- Nenhuma classe de domínio, repositório, service, controller, DTO ou configuração de datasource
  existe ainda. Não há endpoints REST.

## 4. Organização adotada

O backend já ocupa a raiz do repositório no layout padrão Spring Boot (`pom.xml` + `src/`). Mover tudo
para `backend/` quebraria, sem qualquer ganho funcional:

1. o `<resource><directory>db/migrations</directory>` do `pom.xml` (a não ser movendo `db/` junto);
2. o `ROOT` calculado por `scripts/verificar-db.sh` e os caminhos de `docker-compose.yml`;
3. a regra "não mover configurações essenciais do Spring Boot sem justificativa" e o escopo do
   AGENTS.md (db/, docs/db/, scripts/, docker-compose.yml).

A estrutura de referência do pedido é explicitamente "não uma imposição". Portanto: **backend na
raiz, frontend em `frontend/`**, ambos no mesmo repositório, sem módulo Maven agregador (nenhum
`<modules>` foi criado). Isso mantém as duas partes executáveis de forma independente e deixa a fusão
para a etapa de integração.

```
Projeto_ManuELO_Backend/
├── pom.xml                      # backend (Spring Boot + Vaadin + JPA/Flyway)
├── mvnw, mvnw.cmd, .mvn/
├── src/main/java/com/Projeto/manuelo/ManueloApplication.java
├── src/main/resources/          # application.properties, static, templates, db/migration
├── db/                          # migrations, tests, seed, supabase
├── scripts/                     # verificar-db.sh, pg-verify.mjs, aplicar-supabase.*
├── docs/
│   ├── CONTEXTO.md
│   ├── db/                      # progresso, decisoes, dicionario, schema.dbml, handoff, este doc
│   └── referencias/             # PDFs do Figma e da modelagem
├── docker-compose.yml
├── frontend/                    # cópia fiel do repositório de frontend
│   ├── pom.xml                  # aplicação Vaadin (Spring Boot 4.1.2)
│   ├── package.json, package-lock.json, vite.config.ts, tsconfig.json, types.d.ts
│   ├── mvnw, mvnw.cmd, .mvn/, Dockerfile, .dockerignore
│   ├── .gitignore, .mcp.json, LICENSE.md, README.md
│   └── src/main/
│       ├── java/br/com/manuelo/*.java
│       ├── resources/application.properties
│       ├── resources/META-INF/resources/{styles.css,images/logo.jpeg}
│       ├── frontend/animacoes.css
│       └── bundles/{README.md,dev.bundle}
└── README.md, AGENTS.md, LEIA-ME.md, PROMPT-INICIAL.md, opencode.jsonc
```

## 5. Arquivos transferidos

Método: `git -C <frontend> archive HEAD | tar -x -C frontend`, ou seja, **somente arquivos
versionados** (33), preservando nomes, hierarquia e bytes. Cópia, nunca movimentação.

```
.dockerignore
.gitignore
.mcp.json
.mvn/wrapper/maven-wrapper.properties
Dockerfile
LICENSE.md
README.md
mvnw
mvnw.cmd
package-lock.json
package.json
pom.xml
tsconfig.json
types.d.ts
vite.config.ts
src/main/bundles/README.md
src/main/bundles/dev.bundle
src/main/frontend/animacoes.css
src/main/java/br/com/manuelo/Application.java
src/main/java/br/com/manuelo/AppShell.java
src/main/java/br/com/manuelo/AprovacaoView.java
src/main/java/br/com/manuelo/CadastroView.java
src/main/java/br/com/manuelo/EmailEnviadoView.java
src/main/java/br/com/manuelo/HomeView.java
src/main/java/br/com/manuelo/ImoveisView.java
src/main/java/br/com/manuelo/ImovelDialog.java
src/main/java/br/com/manuelo/LoginView.java
src/main/java/br/com/manuelo/MainLayout.java
src/main/java/br/com/manuelo/RecuperarSenhaView.java
src/main/java/br/com/manuelo/RedefinirSenhaView.java
src/main/resources/META-INF/resources/images/logo.jpeg
src/main/resources/META-INF/resources/styles.css
src/main/resources/application.properties
```

**Não copiados (propositalmente):**

| Caminho | Motivo |
|---|---|
| `.git/` | repositório próprio; criaria submódulo/repo aninhado |
| `target/` | artefato de build |
| `node_modules/` | dependências instaladas |
| `src/main/frontend/generated/` | gerado pelo `vaadin-maven-plugin` (ignorado pelo `.gitignore` do frontend) |
| `.vscode/` | configuração local de IDE, ignorada pelo Git |
| `.github/` | hooks locais de ferramenta, não versionados |
| `.vaadin*` | lock/estado local de task |

## 6. Preservação e verificação de integridade

| Verificação | Resultado |
|---|---|
| Arquivos rastreados na origem | 33 |
| Arquivos no destino | 33 |
| SHA-256 divergente/faltante | 0 |
| Arquivos extras no destino | 0 |
| `git status` do frontend original | vazio (antes e depois) |
| `HEAD` do frontend original | `a33502693e2d310b5c3b4ea66e7888126c642d3e` (inalterado) |
| Arquivos rastreados do backend alterados | 0 (`git diff` vazio; só `?? frontend/`) |
| Essenciais do Spring Boot | `pom.xml`, `ManueloApplication.java`, `application.properties`, `docker-compose.yml`, `db/migrations`, `scripts/verificar-db.sh` presentes |
| Artefatos no destino | sem `target/`, `node_modules/`, `.git/`, `generated/` |

## 7. Conflitos e decisões

Não houve sobrescrita: `frontend/` não existia. Os nomes que colidiriam se o conteúdo fosse copiado
para a raiz foram isolados pela pasta `frontend/`:

| Nome | Backend (raiz) | Frontend |
|---|---|---|
| `pom.xml` | Spring Boot + domínio | aplicação Vaadin |
| `README.md` | 1 linha | README do template Vaadin |
| `src/main/resources/application.properties` | configuração do backend | porta/push/Vaadin |
| `src/main/frontend/` | só `generated/` (build) | `animacoes.css` + `generated/` |
| `.mvn/`, `mvnw`, `mvnw.cmd`, `.gitignore`, `Dockerfile` | já existem | mantidos só em `frontend/` |

Outros pontos de atenção (não são conflitos de arquivo):

- Pacotes diferentes: `com.Projeto.manuelo` (backend) × `br.com.manuelo` (frontend).
- Duas classes `@SpringBootApplication` (`ManueloApplication` e `Application`) e dois `AppShell`.
- Duas rotas `@Route("")` (login) se as views forem fundidas sem ajuste.
- O README do frontend ainda é o do template ("My Application").

## 8. Pendências para a integração (não implementadas)

1. **Forma da integração.** O RNF01 pede Vaadin Flow no mesmo projeto e o RNF03 pede monólito modular.
   Caminho recomendado: uma única aplicação Spring Boot com as views em
   `src/main/java/br/com/manuelo` (ou pacote unificado), chamando Services; alternativa futura
   (RNF16): manter aplicações separadas e expor REST.
2. **Build.** Se houver fusão: mover as views para o módulo do backend, unificar pacote, remover o
   `Application`/`AppShell` duplicado e o `pom.xml` do frontend; ou transformar a raiz em projeto
   Maven multi-módulo (`backend/` + `frontend/`). Se as partes ficarem separadas: nada a fazer no
   build atual (não há `<modules>`).
3. **Datasource/Flyway.** `application.properties` do backend não tem `spring.datasource.*`,
   `spring.flyway.*` nem `ddl-auto=validate`; a URI do Supabase (Session pooler 5432) deve vir de
   variável de ambiente/`.env` (o Spring não lê `.env` sozinho — ver DEC/T19).
4. **Autenticação/autorização.** Implementar Spring Security + sessão, hash de senha, perfis
   ADMINISTRADOR/COLABORADOR, status PENDENTE_APROVACAO/ATIVO/INATIVO, token de redefinição e
   proteção de rotas Vaadin (`BeforeEnter`). RLS está bloqueada para `anon`/`authenticated`; a
   autorização precisa existir na aplicação.
5. **CORS.** Só será necessário se o frontend rodar em origem separada (ex.: porta 8081 chamando
   REST na 8080). Em Vaadin no mesmo processo, não há CORS.
6. **Contratos divergentes entre tela e banco** (ajustar na integração, sem mexer agora):
   - `ImoveisView.Imovel(String endereco, ...)` usa um endereço único; o banco tem `logradouro`,
     `numero`, `complemento`, `bairro`, `cidade`, `uf`, `cep`.
   - Status da tela: "Alugado"/"Vago"/"Em manutenção"; enum do banco: `ALUGADO`/`VAGO`/`EM_MANUTENCAO`.
   - `HomeView.Movimentacao(String data, String descricao, String categoria, String valor)` usa texto
     formatado; o banco tem `timestamptz`, `numeric(14,2)` e enum `categoria_movimentacao`.
   - `ImovelDialog` tem 4 campos; faltam endereço completo e upload do contrato (`documentos`).
   - Telas ainda inexistentes no frontend: inquilinos, contratos, movimentação financeira,
     documentos e relatórios.
7. **Upload de documentos.** PDF/JPG/PNG até 10 MB, vínculo obrigatório, bucket privado do Supabase —
   exige configuração de Storage e limites no Spring (`spring.servlet.multipart.*`).
8. **Formatação.** Moeda/data devem ser formatadas na UI (`numeric`/`timestamptz` no banco).
9. **Risco de build do Vaadin.** A cópia contém `src/main/bundles/dev.bundle` (versionado no
   frontend) e **não** contém `src/main/frontend/generated/`; o primeiro build do frontend vai
   regenerar `generated/` e pode recompilar o bundle. Nada foi alterado por causa disso.

## 9. Validação executada

```
# integridade da cópia
33 arquivos rastreados na origem / 33 no destino / 0 hash divergente / 0 extra
origem: git status vazio, HEAD a33502693e2d310b5c3b4ea66e7888126c642d3e

# backend
git status: ?? frontend/   (nenhum arquivo rastreado modificado)

# banco (scripts/verificar-db.sh)
resumo: 11 migration(ões), 13 teste(s), 0 falha(s)
VERIFICAÇÃO OK (postgres embutido via Node)
```

Runtime do banco: **Postgres embutido via Node (plano C)**. O Docker está instalado, mas a rota
`docker compose` falhou duas vezes seguidas com `FATAL: the database system is shutting down`:
o `compose exec pg_isready` responde durante o servidor temporário do `initdb` e o `psql` seguinte
encontra o servidor desligando. O script não foi alterado nesta tarefa; a falha está registrada em
`docs/db/progresso.md` para correção futura (aguardar o healthcheck do container em vez de
`pg_isready`).

## 10. Observação sobre o caminho informado

O pedido cita `C:\Users\ronald\Projetos\Projeto_ManuELO_Backend`. Esse diretório existe, mas é outro
clone: está em `main` (`ed2f9b9`) e tem alterações locais não commitadas (inclusive em
`db/migrations/V010__seguranca_rls.sql`). O clone em uso é
`C:\Users\ronald\CESAR\Projeto_ManuELO_Backend`, que é o workspace da sessão, está limpo e no branch
`featute/intregracao_front` (`72b0e5a`). Para não escrever em uma árvore de trabalho alheia e suja,
a cópia foi feita no clone CESAR. Nada foi escrito em `C:\Users\ronald\Projetos\...`.
