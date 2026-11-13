# ManuElo

Sistema de gestão de imóveis, inquilinos, contratos, movimentação financeira e documentos da
Fundação Manoel Cruz. Aplicação Spring Boot + Vaadin Flow com PostgreSQL e migrations Flyway.

## Requisitos

- JDK 21 (`JAVA_HOME` apontando para ele)
- Node.js 20+ (build do frontend Vaadin e runtime auxiliar de testes)
- Docker + Docker Compose, **ou** PostgreSQL local (16+), **ou** Node/npm para o Postgres embutido do juiz

## Configuração

Copie o modelo de variáveis e preencha os valores locais:

```sh
cp .env.example .env
chmod 600 .env
```

Principais chaves:

| Variável | Uso |
|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | conexão JDBC com o PostgreSQL |
| `PORT` / `APP_PORT` | porta HTTP da aplicação (padrão 8080) |
| `MANUELO_ADMIN_EMAIL`, `MANUELO_ADMIN_SENHA` | administrador inicial; obrigatórios em produção |
| `MANUELO_APP_URL` | URL pública usada no link de redefinição de senha |
| `MANUELO_STORAGE_DIR` | diretório dos documentos enviados |

Nunca commite o `.env` nem segredos reais.

## Banco de dados

### Docker Compose (recomendado)

```sh
docker compose up -d
```

O banco sobe em `localhost:55432` (usuário `manuelo`, banco `manuelo`). As migrations em
`db/migrations` são aplicadas pelo Flyway na subida da aplicação. Para validar schema, seed e
testes SQL sem subir a aplicação:

```sh
./scripts/verificar-db.sh
```

### PostgreSQL já existente

Crie um banco vazio e aponte `DB_URL` para ele. O Flyway aplica as migrations do zero; o
Hibernate roda com `ddl-auto=validate` e não altera o schema.

## Executar em desenvolvimento

O perfil `dev` é o padrão. Defina `MANUELO_ADMIN_EMAIL` e `MANUELO_ADMIN_SENHA` no `.env`
para criar o primeiro administrador; sem essas variáveis nenhum admin é criado (DEC-I04).

```sh
set -a; . ./.env; set +a
./mvnw spring-boot:run
```

Ou gere o jar e execute:

```sh
./mvnw -B clean package -DskipTests
java -jar target/manuelo-0.0.1-SNAPSHOT.jar
```

Acesse `http://localhost:8080`. O primeiro acesso usa o administrador configurado.

## Testes e juiz de integração

Os testes usam PostgreSQL real (não Testcontainers) e o perfil `test`. Com um banco acessível:

```sh
JAVA_HOME=/caminho/para/jdk21 ./mvnw -B test
```

O juiz completo sobe um PostgreSQL limpo temporário, aplica migrations, roda a suíte, valida o
schema, sobe a aplicação, faz smoke das rotas, varre o log, prova persistência após reinício do
contexto e bloqueia resíduos de protótipo:

```sh
./scripts/verificar-integracao.sh
```

Saída esperada: `VERIFICAÇÃO DE INTEGRAÇÃO OK` e código de saída 0. O runtime usado (Docker,
Postgres local ou embutido) é escolhido automaticamente e registrado na saída.

## Produção

Ative o perfil `prod`:

```sh
java -jar application.jar --spring.profiles.active=prod
```

Em produção são obrigatórios `DB_URL`, `DB_USER`, `DB_PASSWORD`, `MANUELO_ADMIN_EMAIL` e
`MANUELO_ADMIN_SENHA`; não há senha padrão. Configure também `MANUELO_APP_URL` e
`MANUELO_STORAGE_DIR` (volume persistente para documentos). O `Dockerfile` já executa com
`--spring.profiles.active=prod`, usa usuário não-root e aceita chaves Vaadin via secrets de build.

## Segurança e perfis

- Login por e-mail/senha com BCrypt e sessão Spring Security.
- `ADMINISTRADOR` acessa usuários e relatórios; `COLABORADOR` opera o dia a dia.
- Rotas `/usuarios/**` e `/relatorios/**` são restritas a administradores.
- Regras de negócio (estorno, imutabilidade, caução, competência, relatórios) ficam nos serviços,
  nunca nas views.
- Redefinição de senha usa token de uso único com hash SHA-256 e validade de 1 hora.
- Sem SMTP configurado, o perfil `dev` registra o link no log; em produção defina `SPRING_MAIL_HOST`,
  `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME` e `SPRING_MAIL_PASSWORD` para enviar por e-mail.

## Relatórios

Os quatro PDFs (contratos existentes, atrasados, a vencer e movimentação financeira) são gerados
com OpenPDF, persistidos na tabela `relatorios` com a composição exata e baixados pela tela de
Relatórios. A exportação de extrato também gera PDF a partir da movimentação financeira.

## Estrutura

```
src/main/java/com/Projeto/manuelo/   entidades, repositórios, serviços, segurança, arquivos
ui/src/main/java/br/com/manuelo/     views e diálogos Vaadin Flow
db/migrations/                       schema versionado (Flyway)
db/tests/                            testes SQL do banco
scripts/                             juiz de integração e runtime de banco
docs/                                contexto, backlog, decisões e relatórios
```

## Problemas comuns

- **Connection refused**: o banco não está no ar ou `DB_URL` aponta para outra porta.
- **Flyway validate/drift**: use um banco limpo no juiz; migrations já commitadas não são editadas.
- **Porta ocupada**: defina `PORT` (ou `APP_PORT`) para outra porta.
- **Sem navegador no servidor**: mantenha `VAADIN_LAUNCH_BROWSER=false`.
