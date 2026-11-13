# Decisões da equipe (append-only). DEC-I01 a DEC-I14 já estão tomadas: não reabrir, não perguntar.
Novas decisões: DEC-I15 em diante, no modelo ao final.

DEC-I01 Persistência: Spring Data JPA + Hibernate sobre o schema Flyway existente; ddl-auto=validate; entidades seguem o
        modelo v3 do CONTEXTO; PK UUID; BigDecimal; Instant ou OffsetDateTime para timestamptz; enums nativos mapeados
        de forma compatível com a versão do Hibernate em uso (confirmar antes).
DEC-I02 Autenticação: Spring Security com a integração oficial do Vaadin Flow, BCrypt, login por e-mail, papéis
        ROLE_ADMINISTRADOR e ROLE_COLABORADOR; só status ATIVO entra.
DEC-I03 Aprovação de usuários não tem tela no Figma: criar /usuarios mínima, só ADMINISTRADOR (lista, aprovar, inativar,
        mudar perfil), no mesmo estilo visual das demais telas. Sem isso o fluxo de cadastro fica inutilizável.
DEC-I04 Primeiro administrador: se a tabela usuarios estiver vazia, criar ADMINISTRADOR ATIVO a partir das variáveis de
        ambiente MANUELO_ADMIN_EMAIL e MANUELO_ADMIN_SENHA (senha guardada só como hash BCrypt). Sem variável, não cria.
DEC-I05 Recuperar senha: token aleatório de 32 bytes, só o SHA-256 no banco, validade de 1 hora, uso único. Entrega por
        interface EnvioTokenSenha: SMTP (Spring Mail) se spring.mail.host estiver configurado; senão, no perfil dev,
        registra o link no log. Resposta neutra para e-mail inexistente. Nunca logar token fora do perfil dev.
DEC-I06 Arquivos: interface ArmazenamentoArquivos com implementação em sistema de arquivos (diretório em
        MANUELO_STORAGE_DIR, padrão ./storage); o caminho relativo vai em documentos.caminho_armazenamento. Google Drive
        ou S3 ficam como evolução futura atrás da mesma interface. Gravar o arquivo primeiro, depois o registro.
DEC-I07 PDFs: JasperReports Community para os relatórios e OpenPDF para recibos e extrato, como na stack. Se o Jasper
        bloquear em 3 tentativas, use OpenPDF para tudo e registre a decisão.
DEC-I08 Transações nos serviços (@Transactional). Movimentação é @Immutable; "editar" do administrador = estorno + nova
        movimentação (R02). Nenhum UPDATE ou DELETE em movimentacoes, caucao_movimentacoes ou relatorios.
DEC-I09 Testes: JUnit 5 e Spring Boot Test contra o Postgres do docker-compose (perfil test, variáveis de ambiente).
        Para views, avaliar Karibu Testing; se inviável, testar o serviço e fazer smoke HTTP das rotas.
DEC-I10 DemoData e listas em memória são removidos (item a item, e por completo em A-90). O juiz reprova se sobrarem.
DEC-I11 Auditoria: AuditorAware lê o usuário do SecurityContext para criado_por e atualizado_por.
DEC-I12 Interface: preservar o layout do Figma já implementado; só ligar dados, filtros, diálogos e erros. Sem redesenho.
DEC-I13 Localização: pt-BR, moeda BRL, datas dd/MM/yyyy, fuso America/Sao_Paulo; conversores do Binder em pt-BR.
DEC-I14 Configuração por variáveis de ambiente (DB_URL, DB_USER, DB_PASSWORD, MANUELO_*); Session pooler (5432)
        no Supabase; perfis dev e prod; nenhum segredo no repositório.

Modelo para novas decisões:
## DEC-I15 título
- Contexto:
- Escolha:
- Motivo:
- Reverter:
