# Armadilhas conhecidas (confira cada uma com evidência antes de implementar ou diagnosticar)

## Nomes e schema x entidades
- Renomeação lancamentos -> movimentacoes (DEC-01): entidade, @Table, queries nativas e JPQL ainda usam o nome antigo?
- Enums nativos do Postgres x @Enumerated(STRING): erro "column is of type X but expression is of type character varying".
  Em Hibernate 6 avalie @JdbcTypeCode(SqlTypes.NAMED_ENUM) ou mapeamento equivalente (confirme a versão antes).
- UUID, numeric(14,2) -> BigDecimal, timestamptz -> Instant/OffsetDateTime: tipos e precisão batem?
- Herança SINGLE_TABLE de movimentacoes com discriminador "tipo" e enum.
- Colunas novas do modelo v3 (status do usuário, caução, fiador, competência, dia_vencimento) existem na entidade?
- ddl-auto deveria ser validate ou none; create/update escondem o erro e brigam com o Flyway.

## Banco e conexão (Supabase)
- Transaction pooler (6543) com Hibernate quebra prepared statements; usar Session pooler (5432) ou prepareThreshold=0.
- Flyway: pasta (src/main/resources/db/migration), ordem, checksum, conexão direta/session para DDL.
- Triggers de imutabilidade: save() do JPA gera UPDATE em movimentacoes? Entidade deve ser @Immutable, sem setters.
- Estorno só ADMINISTRADOR e "edição" = estorno + nova movimentação: o service respeita isso?

## Back
- @Transactional ausente ou no lugar errado; LazyInitializationException ao a view tocar relação lazy (open-in-view).
- View chamando Repository direto (a regra é View -> Service -> Repository).
- Spring Security: login, rotas permitidas, perfil x papéis (ROLE_ADMINISTRADOR), usuário PENDENTE_APROVACAO não entra.
- Propriedades por perfil, segredos em variáveis de ambiente, fuso horário e locale pt-BR.

## Front (Vaadin Flow)
- Rotas reais x rotas do Figma (/movimentacao-financeira etc.); @Route, layout, @PermitAll/@RolesAllowed.
- Binder: validação do bean x CHECKs do banco; conversores de BigDecimal e datas em pt-BR.
- Grid com filtros (nome, CPF, imóvel, validade, situação) usando DataProvider/Specification, sem carregar tudo.
- Upload: limite de 10 MB (Vaadin e spring.servlet.multipart), tipos PDF/JPG/PNG, caminho de armazenamento.
- Botões que chamam serviço com assinatura antiga; exceções do serviço sem tratamento na tela (notificação).

## Build e ambiente
- Versão do Java (21), do Spring Boot e do Vaadin compatíveis; modo dev do Vaadin exige Node; build de produção.
- Dependências faltando ou duplicadas; perfis do Maven/Gradle; variáveis de ambiente ausentes.

## Específicas de "protótipo para sistema real"
- Views Vaadin são criadas a cada navegação: estado só sobrevive no serviço e no banco, nunca em campo da view.
- Cadastro, login e aprovação são um único fluxo: testar a sequência inteira, não cada tela isolada.
- Spring Security com Vaadin: usar a configuração oficial do Vaadin; permitAll global esconde falhas de autorização.
- Upload no Vaadin e no Spring têm limites separados (10 MB nos dois).
- Entidade imutável + trigger no banco: repositório sem save() de entidade existente; testes devem provar a recusa.
