# Integração backend × banco — ManuElo

Este documento é o handoff para a equipe Java/Vaadin. Ele cobre os RNFs pedidos (RNF1 e RNF2),
autenticação, armazenamento de PDFs no Supabase, busca/filtros, edição segura de imóveis e o caso de
uso **E1.1 — Cadastrar imóveis**. O banco está pronto e validado; **nenhum código Java foi criado**
(escopo do agente de banco).

## 0. Onde está cada coisa
| Item | Caminho |
|---|---|
| Migrations | `db/migrations/V001..V011` |
| Seed de desenvolvimento | `db/seed/dev_seed.sql` |
| Primeiro administrador | `db/seed/primeiro_admin.sql` |
| Testes do banco | `db/tests/*.sql` |
| Verificação | `scripts/verificar-db.sh` |
| Dicionário | `docs/db/dicionario.md` |
| Diagrama | `docs/db/schema.dbml` |
| Aplicação no Supabase | `db/supabase/README.md` e `db/supabase/aplicar_tudo.sql` |
| Regras/contexto | `docs/CONTEXTO.md` e `docs/db/decisoes.md` |

Ao criar o projeto Spring Boot, copie as migrations para `src/main/resources/db/migration/`
(mesmos nomes `V001__...`). O Flyway aplica em ordem e registra em `flyway_schema_history`.

Para ver o schema rápido no Supabase **sem** Flyway, use o SQL Editor e cole
`db/supabase/aplicar_tudo.sql` (instruções em `db/supabase/README.md`). Não misture esse caminho com
Flyway no mesmo banco: o Flyway encontraria tabelas sem `flyway_schema_history`. Há também o script
`scripts/aplicar-supabase.sh` para aplicar via `psql`/Docker.

## 1. RNF1 — ORM e conexão com o Supabase

### 1.1 Dependências (Maven)
Use o parent do Spring Boot 3 e Java 21. Dependências mínimas:

```xml
<parent>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-parent</artifactId>
  <version>3.3.x</version>
</parent>

<dependencies>
  <dependency>
    <groupId>com.vaadin</groupId>
    <artifactId>vaadin-spring-boot-starter</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
  </dependency>
  <dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
  </dependency>
  <dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
  </dependency>
  <dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
  </dependency>
  <dependency>
    <groupId>com.github.librepdf</groupId>
    <artifactId>openpdf</artifactId>
    <version>2.0.3</version>
  </dependency>
  <dependency>
    <groupId>net.sf.jasperreports</groupId>
    <artifactId>jasperreports</artifactId>
    <version>6.21.3</version>
  </dependency>
</dependencies>
```

### 1.2 `application.yml`
Nunca grave credenciais no repositório. Use variáveis de ambiente (Railway/Render/Supabase).

No desenvolvimento local, copie `.env.example` para `.env` (já ignorado pelo Git), preencha e carregue
antes de subir a aplicação:

```bash
cp .env.example .env
chmod 600 .env
set -a; . ./.env; set +a
./mvnw spring-boot:run
```

O Spring Boot não lê `.env` nativamente; use o `set -a` acima, a biblioteca `spring-dotenv` ou a
configuração de ambiente da IDE. Em produção, cadastre as chaves como secrets da plataforma.

```yaml
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USER}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 10
      minimum-idle: 1
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        jdbc:
          time_zone: UTC
        format_sql: false
    show-sql: false
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: false
    validate-on-migrate: true
server:
  port: ${PORT:8080}
```

`DB_URL` no Supabase (Session pooler, porta **5432**):

```
jdbc:postgresql://<host-do-session-pooler>:5432/postgres?sslmode=require
```

O usuário do Session pooler tem o formato `postgres.<project-ref>`. **Não use o Transaction pooler**
(porta 6543): ele não preserva recursos de sessão e pode quebrar transações com constraints deferidas.

### 1.3 Mapeamento das entidades
- `ddl-auto=validate`: o banco é dono do schema; o Hibernate só valida.
- `uuid` → `java.util.UUID`.
- `timestamptz` → `Instant` (ou `OffsetDateTime`); `date` → `LocalDate`.
- `numeric(14,2)` → `BigDecimal`.
- Enums → `@Enumerated(EnumType.STRING)` com os nomes exatos do dicionário.
- `smallint` → `Short`/`short`.
- `char(2)` → `String` (UF).
- Nomes de colunas em `snake_case`; se não usar a estratégia padrão do Spring, anote `@Column(name=...)`.
- **Tabelas insert-only**: `movimentacoes`, `caucao_movimentacoes`, `relatorios`,
  `relatorio_movimentacoes`, `relatorio_contratos`, `configuracoes` (configurações só INSERT/UPDATE).
  Não exponha `delete`/`save` de atualização para elas; o banco rejeita com SQLSTATE `55000`.
- `documentos` e `reformas` aceitam UPDATE; `usuarios`, `imoveis`, `inquilinos`, `fiadores`,
  `contratos` também, sempre preenchendo `atualizado_por`.

### 1.4 Transações e triggers deferidos
Três regras são verificadas no **fim da transação**:

1. `SAIDA` precisa de ao menos um `documentos.movimentacao_id`.
2. `forma_pagamento = CAUCAO` precisa do `caucao_movimentacoes.movimentacao_id` correspondente.
3. `corrige_movimentacao_id` precisa do ESTORNO da original já gravado.

Portanto, no service (anotado com `@Transactional`):

```java
// SAIDA + comprovante na mesma transação
Movimentacao saida = movimentacaoRepo.save(novaSaida(...));
documentoRepo.save(novoComprovante(saida.getId(), arquivo));
// commit dispara trg_movimentacao_comprovante_saida

// Pagamento com caução
Movimentacao entrada = movimentacaoRepo.save(novaEntradaCaucao(...));
caucaoRepo.save(novoAbatimento(entrada.getId(), contratoId, valor));

// Correção pelo administrador
Movimentacao estorno = movimentacaoRepo.save(novoEstorno(original, admin));
movimentacaoRepo.save(novaCorrigida(original.getId(), ...));
```

Se o upload no Storage falhar, a transação deve sofrer rollback (ou o arquivo deve ser removido). Faça o
upload antes do commit e compense em caso de erro.

## 2. RNF2 — Popular o banco

### 2.1 Schema e seeds
- **Schema**: Flyway no startup (ou `flyway migrate` no deploy).
- **Produção**: banco vazio; só configurações padrão (`dias_alerta_vencimento=7`,
  `dias_atraso_critico=7`) vêm nas migrations.
- **Desenvolvimento/homologação**: aplicar `db/seed/dev_seed.sql` depois das migrations. Ele cria
  124 imóveis, 110 contratos e 3040 movimentações (com atrasos, estornos, comprovantes e caução).
- **Primeiro administrador** (uma vez, em qualquer ambiente): rodar via psql com variáveis, sem senha
  no repositório:

```bash
psql "$DB_URL" \
  -v nome="Administrador" \
  -v email="admin@fundacao.org" \
  -v senha_hash="$BCRYPT_HASH" \
  -f db/seed/primeiro_admin.sql
```

O script insere como `PENDENTE_APROVACAO` e aprova por si na sequência (DEC-11). Gere o hash no backend
com `BCryptPasswordEncoder`. Não use texto puro.

### 2.2 Ordem de aplicação
1. Migrations `V001..V011`.
2. `configuracoes` já vem com os dois valores.
3. `primeiro_admin.sql` (produção) ou `dev_seed.sql` + admin (desenvolvimento).
4. Subir a aplicação.

### 2.3 Verificação antes de integrar
```bash
./scripts/verificar-db.sh
# esperado: 11 migration(ões), 13 teste(s), 0 falha(s) → VERIFICAÇÃO OK
```

## 3. Autenticação e aprovação de usuários

Fluxo:
1. `/cadastro` grava `usuarios` com `perfil='COLABORADOR'`, `status='PENDENTE_APROVACAO'`,
   `aprovado_por/em` nulos e `senha_hash` BCrypt.
2. Administrador aprova: `UPDATE usuarios SET status='ATIVO', aprovado_por=:admin, aprovado_em=now()`.
3. Login só permite `status='ATIVO'`. `INATIVO` e `PENDENTE_APROVACAO` recebem mensagem genérica.
4. Recuperação: gere um token aleatório, guarde **somente** o hash em `tokens_redefinicao_senha`
   (`token_hash`, `expira_em`), envie o token por e-mail; na redefinição, localize pelo hash, valide
   `expira_em > now()` e `usado_em IS NULL`, atualize a senha e marque `usado_em`. O trigger impede
   reutilização/alteração.
5. Toda ação grava o usuário em `responsavel_id`, `criado_por` ou `enviado_por`.

Consultas úteis:

```sql
-- login (e-mail sem diferenciar maiúsculas)
SELECT id, nome, senha_hash, perfil, status
  FROM usuarios
 WHERE lower(email) = lower(:email);

-- aprovar
UPDATE usuarios
   SET status = 'ATIVO', aprovado_por = :admin_id, aprovado_em = now(), atualizado_por = :admin_id
 WHERE id = :usuario_id AND status = 'PENDENTE_APROVACAO';
```

Autorização: `ADMINISTRADOR` para aprovar usuários, estornar, corrigir movimentação e gerar relatório;
`COLABORADOR` para o dia a dia. Todos veem os mesmos dados.

## 4. Upload de PDF/imagem no Supabase Storage

- Bucket privado (sugestão: `documentos`).
- O backend usa a **service role key** apenas no servidor (nunca no Vaadin/client). O front baixa por
  URL assinada de curta duração.
- Caminho sugerido: `movimentacoes/{movimentacaoId}/{uuid}-{nome}`, `contratos/{contratoId}/...`,
  `imoveis/{imovelId}/...`.
- Valide no backend e no banco: `tipo_mime` ∈ {`application/pdf`, `image/jpeg`, `image/png`} e
  `tamanho_bytes` ≤ 10 MiB.
- Grave em `documentos`: `nome_arquivo`, `caminho_armazenamento`, `tipo_mime`, `tamanho_bytes`,
  vínculo, `enviado_por`, `pendente_revisao`.
- Para comprovante de SAIDA, o documento deve estar gravado **na mesma transação** da movimentação.
- Para documentos ainda não classificados, `pendente_revisao=true` permite `tipo`/`data_documento`
  nulos; ao classificar, preencha os dois e mude para `false`.

## 5. Busca e filtros

O banco tem índices GIN trigram; use `ILIKE '%termo%'` sem medo para nome/endereço/documento.

### Imóveis (`/imoveis`)
```sql
SELECT *
  FROM vw_imoveis_situacao
 WHERE (:endereco IS NULL OR logradouro ILIKE '%' || :endereco || '%'
                         OR bairro ILIKE '%' || :endereco || '%'
                         OR cidade ILIKE '%' || :endereco || '%')
   AND (:tipo IS NULL OR tipo = :tipo)
   AND (:status IS NULL OR status_exibicao = :status)
   AND (:inquilino_id IS NULL OR inquilino_atual_id = :inquilino_id)
 ORDER BY logradouro, numero;
```
`status_exibicao` já resolve `EM_MANUTENCAO` com precedência (DEC-09).

### Inquilinos (`/inquilinos`)
```sql
SELECT * FROM inquilinos
 WHERE (:nome IS NULL OR nome ILIKE '%' || :nome || '%')
   AND (:cpf IS NULL OR documento = regexp_replace(:cpf, '\D', '', 'g'))
   AND (:email IS NULL OR email ILIKE '%' || :email || '%')
 ORDER BY nome;
```

### Contratos (`/contratos`)
```sql
SELECT *
  FROM vw_contratos_situacao
 WHERE (:nome IS NULL OR inquilino_nome ILIKE '%' || :nome || '%')
   AND (:cpf IS NULL OR inquilino_documento = regexp_replace(:cpf, '\D', '', 'g'))
   AND (:imovel_id IS NULL OR imovel_id = :imovel_id)
   AND (:validade IS NULL OR situacao_validade = :validade)  -- VIGENTE/A_VENCER/VENCIDO
   AND (:atrasado IS NULL OR atrasado = :atrasado)
 ORDER BY fim NULLS LAST, inicio DESC;
```

### Documentos (`/documentos`)
```sql
SELECT *
  FROM vw_documentos_lista
 WHERE (:inquilino IS NULL OR inquilino_nome ILIKE '%' || :inquilino || '%')
   AND (:tipo IS NULL OR tipo = :tipo)
   AND (:tipo_financeiro IS NULL OR tipo_financeiro = :tipo_financeiro)
 ORDER BY enviado_em DESC;
```

### Movimentações (`/movimentacao-financeira`)
```sql
SELECT * FROM fn_extrato_periodo(:inicio, :fim, :filtro);  -- :filtro = ENTRADA/SAIDA/NULL
SELECT * FROM fn_resumo_periodo(:inicio, :fim);
```

Sempre pagine no banco (`LIMIT/OFFSET` ou `Pageable`) e evite carregar todos os registros na memória.

## 6. Edição segura de imóveis

O imóvel é uma entidade editável, mas algumas mudanças precisam de cuidado:

1. **Nunca apague** um imóvel com contrato: a FK é `ON DELETE RESTRICT`. A interface não deve oferecer
   exclusão; no máximo inativar a manutenção/status.
2. **Endereço/tipo/status** podem ser alterados. Preencha `atualizado_por`; o trigger carimba
   `atualizado_em`.
3. **Inquilino vinculado** não é coluna de `imoveis`: é o contrato vigente. Para “vincular” um
   inquilino, crie um contrato; a view `vw_imoveis_situacao` passa a mostrar `ALUGADO`.
4. **Status**: `VAGO` é o default. `ALUGADO` só deve ser gravado quando existir contrato vigente (a
   exibição deriva de qualquer forma). `EM_MANUTENCAO` tem precedência na exibição.
5. **Alteração de contrato**: o banco impede sobreposição de períodos no mesmo imóvel. Faça a mudança
   em uma transação e trate a violação como erro de negócio.
6. **Movimentação não é editável**. “Editar” = estorno + nova movimentação (R02). Nunca faça UPDATE.
7. **Concorrência**: a tabela não tem coluna de versão. Para evitar lost update, use uma das opções:
   - `@Version` exige nova coluna; crie `V012__versao_imoveis.sql` se for necessário.
   - Ou faça `UPDATE ... WHERE id=:id AND atualizado_em=:lido` e verifique linhas afetadas.
   - Recomendado para o MVP: atualização por campo com `atualizado_em` + mensagem de conflito.

Exemplo de atualização segura:

```sql
UPDATE imoveis
   SET logradouro = :logradouro,
       numero = :numero,
       complemento = :complemento,
       bairro = :bairro,
       cidade = :cidade,
       uf = :uf,
       cep = :cep,
       tipo = :tipo,
       status = :status,
       atualizado_por = :usuario_id,
       atualizado_em = now()
 WHERE id = :id;
```

## 7. E1.1 — Cadastrar imóveis (passo a passo)

Tela do Figma: cadastro/edição com endereço, tipo, status, inquilino vinculado e upload do contrato.
No modelo, “inquilino vinculado” e “contrato” são um **contrato** e um **documento**, não colunas do
imóvel.

### 7.1 Campos e validação
| Campo da tela | Destino | Obrigatório | Regra |
|---|---|---|---|
| Logradouro | `imoveis.logradouro` | sim | não vazio |
| Número | `imoveis.numero` | sim | não vazio |
| Complemento | `imoveis.complemento` | não | ≤ 100 |
| Bairro | `imoveis.bairro` | sim | não vazio |
| Cidade | `imoveis.cidade` | sim | não vazio |
| UF | `imoveis.uf` | sim | 2 letras maiúsculas, default `SE` |
| CEP | `imoveis.cep` | não | `00000-000` ou `00000000` |
| Tipo | `imoveis.tipo` | sim | enum |
| Status | `imoveis.status` | sim | `VAGO` por padrão; `EM_MANUTENCAO` permitido |
| Inquilino vinculado | `contratos.inquilino_id` | não | se informado, criar contrato |
| Dados do contrato | `contratos.*` | se vincular | `inicio`, `valor_aluguel`, `dia_vencimento`, `acompanhar_pagamento_desde` |
| Upload do contrato | `documentos` | não | vínculo `imovel_id` e/ou `contrato_id`; PDF/JPG/PNG ≤ 10 MB |

### 7.2 Fluxo transacional
```java
@Transactional
public UUID cadastrarImovel(CadastroImovelForm form, Usuario autor) {
    Imovel imovel = imovelRepo.save(form.toImovel(autor));       // status VAGO/EM_MANUTENCAO

    if (form.temContrato()) {
        Contrato contrato = contratoRepo.save(form.toContrato(imovel, autor));
        if (form.temArquivoContrato()) {
            String caminho = storage.upload(form.arquivo(), "contratos/" + contrato.getId());
            documentoRepo.save(Documento.deContrato(contrato, imovel, caminho, autor));
        }
        // se o formulário permitir, o status pode ir para ALUGADO
        imovelRepo.marcarAlugado(imovel.getId(), autor);
    } else if (form.temArquivoContrato()) {
        String caminho = storage.upload(form.arquivo(), "imoveis/" + imovel.getId());
        documentoRepo.save(Documento.deImovel(imovel, caminho, autor)); // pendente_revisao=true
    }
    return imovel.getId();
}
```

Regras:
- O imóvel nasce `VAGO` (ou `EM_MANUTENCAO` se o usuário marcar).
- Só marque `ALUGADO` se o contrato for criado na mesma transação.
- O documento do contrato pode ficar `pendente_revisao=true` até a classificação.
- Se o upload falhar, faça rollback do imóvel/contrato.
- Não permita número/CEP inválidos: valide na tela e deixe o banco como última barreira.

### 7.3 Consulta após cadastro
```sql
SELECT *
  FROM vw_imoveis_situacao
 WHERE id = :imovel_id;
```

A view devolve `situacao_contrato`, `em_manutencao`, `status_exibicao`, `contrato_atual_id` e
`inquilino_atual_nome` para montar a lista/detalhe sem consultas extras.

## 8. Variáveis de ambiente
| Variável | Exemplo | Observação |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://...pooler.supabase.com:5432/postgres?sslmode=require` | Session pooler |
| `DB_USER` | `postgres.<project-ref>` | Session pooler |
| `DB_PASSWORD` | segredo | fora do repositório |
| `SUPABASE_URL` | `https://<ref>.supabase.co` | Storage |
| `SUPABASE_SERVICE_ROLE_KEY` | segredo | só no servidor |
| `SUPABASE_BUCKET` | `documentos` | bucket privado |
| `APP_TIMEZONE` | `America/Maceio` | relatórios e data de referência |

## 9. Armadilhas comuns
- Usar Transaction pooler (6543) → erros de sessão/deferimento. Use 5432.
- `ddl-auto=update` → não use; o schema é das migrations.
- Tentar `UPDATE`/`DELETE` em movimentação → SQLSTATE `55000`.
- Inserir SAIDA sem comprovante na mesma transação → erro no commit.
- Inserir pagamento com `forma_pagamento='CAUCAO'` sem `caucao_movimentacoes` → erro no commit.
- Passar CPF/CNPJ formatado para `documento` → CHECK rejeita; normalize para dígitos.
- Passar `LocalDateTime` em coluna `timestamptz` sem timezone → use `Instant`.
- Esquecer `atualizado_por` em updates → auditoria incompleta.
- Deletar imóvel/usuário → FK `RESTRICT` e regra de negócio; não ofereça exclusão.
- Aplicar `dev_seed.sql` em produção → dados fictícios; use só em dev/homologação.

## 10. Checklist de integração
- [ ] Projeto Spring Boot 3/Java 21 criado com as dependências acima.
- [ ] Migrations copiadas para `src/main/resources/db/migration`.
- [ ] `DB_URL` do Session pooler e credenciais em variáveis de ambiente.
- [ ] Flyway aplicou V001..V011 e `ddl-auto=validate` subiu sem erros.
- [ ] Primeiro administrador criado via script (hash BCrypt).
- [ ] Seed aplicado só em desenvolvimento.
- [ ] Login bloqueia usuários não `ATIVO`.
- [ ] SAIDA + comprovante e CAUCAO + abatimento na mesma transação.
- [ ] Upload no bucket privado e `documentos.caminho_armazenamento` preenchido.
- [ ] Buscas usando os índices trigram e views derivadas.
- [ ] Edição de imóvel sem exclusão e com auditoria.
- [ ] E1.1 criando imóvel + contrato + documento em uma transação.
- [ ] `./scripts/verificar-db.sh` continua verde antes de cada entrega.
