# Backlog (o líder atualiza o status; cada item só fecha com o critério de aceite provado por teste)
Status: PENDENTE | EM_ANDAMENTO | CONCLUIDO | BLOQUEADO. Dono: ordem banco -> back -> front.

## S0 Fundação
- A-01 Juiz: scripts/verificar-integracao.sh com as 8 etapas do AGENTS.md. Aceite: roda e reprova o estado atual (DemoData).
  **CONCLUIDO** (juiz endurecido em 12/11: reprova P-001/P-002/P-008, código 1; etapas 1-3 verdes).
- A-02 Persistência base: DataSource por variáveis de ambiente (Session pooler 5432), JPA com ddl-auto=validate, Flyway
  existente, mapeamento de UUID, BigDecimal, timestamptz e enums nativos, BaseEntity com auditoria e AuditorAware.
  Aceite: app sobe contra o banco migrado com validate. **CONCLUIDO** (commit 371eda3; 14 entidades, validate OK, juiz verde).
- A-03 Entidades e repositórios de todo o modelo v3 (usuarios ... configuracoes, views como projeções de leitura).
  Aceite: round-trip (insert + select) por entidade; movimentacoes com @Immutable e sem UPDATE.
  **CONCLUIDO** (14 repositórios; RoundTripEntidadesTest 2/2; juiz EXIT 0; ressalvas P-019/P-020).

## S1 Autenticação e usuários (telas /login, /cadastro, /aguardando-aprovacao, /recuperar-senha, /redefinir-senha, /usuarios)
- A-10 Spring Security real: UserDetailsService, BCrypt, login/logout, rotas protegidas (públicas: login, cadastro,
  aguardando-aprovacao, recuperar-senha, redefinir-senha), papéis ADMINISTRADOR/COLABORADOR, status gate.
  Aceite: login ATIVO entra; PENDENTE e INATIVO não; senha errada falha; rota protegida redireciona ao login.
  **CONCLUIDO** (UserDetailsService/AuthenticationManager reais; `SegurancaAutenticacaoTest` 10/0/1; juiz 8/8).
- A-11 Cadastro real: grava usuarios com status PENDENTE_APROVACAO e perfil COLABORADOR; e-mail único; redireciona
  a /aguardando-aprovacao. Aceite: registro existe no banco após reiniciar o contexto.
  **CONCLUIDO** (CadastroUsuariosTest; e-mail único case-insensitive; hash BCrypt; ressalva JDBC em P-021).
- A-12 Tela /usuarios (só ADMINISTRADOR): lista pendentes e ativos, aprova (aprovado_por/em), reprova/inativa, altera perfil.
  Aceite: COLABORADOR é barrado; aprovação libera o login.
  **CONCLUIDO** (UsuarioAdminService + UsuariosView; 403 para COLABORADOR; auditoria; juiz 8/8).
- A-13 Recuperar e redefinir senha: token aleatório, só o hash no banco, uso único, expiração, entrega via interface
  (DEC-I05). Aceite: token expirado ou reutilizado falha; senha nova funciona no login.
  **CONCLUIDO** (RecuperacaoSenhaService + EnvioTokenSenha; RecuperacaoSenhaTest 10/10; juiz 8/8; ressalvas P-023).
- A-14 Bootstrap do primeiro administrador por variáveis de ambiente (DEC-I04). Aceite: banco vazio + variáveis = admin ATIVO.
  **CONCLUIDO** (BootstrapAdminTest 3/3; ressalva: default de dev em P-025).
- A-15 Auditoria: criado_por/atualizado_por vêm do usuário logado em toda gravação.

## S2 Imóveis, inquilinos, fiadores e reformas
- A-20 /imoveis: lista, filtros opcionais (endereço, tipo, status, inquilino vinculado), cadastro, edição, detalhe com
  contrato vigente. Aceite: criar e editar persistem; filtros retornam o subconjunto certo.
- A-21 /inquilinos: lista, filtros (nome, CPF, e-mail), adicionar/editar com fiador, imóvel vinculado, valor da caução
  e upload do recibo da caução. Aceite: um envio cria inquilino, fiador, contrato e depósito de caução na mesma transação.
- A-22 Reformas do imóvel: histórico (data, descrição, valor, responsável do período derivado) e + Novo Registro.

## S3 Contratos e caução
- A-30 /contratos: lista e filtros (nome, CPF, imóvel, validade, situação), "faltam N dias", dados pela view de situação.
- A-31 Novo contrato e detalhe: inquilino, fiador, ação judicial (descrição obrigatória se sim), caução, dia de
  vencimento, upload do contrato (documento). Aceite: sobreposição de períodos no mesmo imóvel é recusada com mensagem clara.
- A-32 "Pagar o aluguel": gera movimentação ENTRADA/ALUGUEL com competência; "Pagar com caução" gera abatimento
  e entrada com forma CAUCAO (R07). Aceite: saldo de caução nunca fica negativo; contrato sai de atrasado ao quitar.

## S4 Movimentação financeira
- A-40 Entrada e saída com as regras R03 e R04, comprovante, modal de confirmação, mensagens de erro do serviço.
- A-41 Extrato do período, pesquisa e totais (entradas, saídas, saldo, estornos conforme R09).
- A-42 Estorno e edição pelo administrador (R01 e R02); COLABORADOR não vê nem executa. Aceite: original intacto,
  um estorno por original, valor igual, nova movimentação ligada por corrige_movimentacao_id.
- A-43 Exportar extrato: modal de período e PDF persistido como relatório do tipo MOVIMENTACAO.

## S5 Documentos
- A-50 Upload (PDF/JPG/PNG até 10 MB) com armazenamento por interface (DEC-I06), documento pendente_revisao, vínculo.
- A-51 /documentos: filtros (nome do inquilino, tipo, tipo financeiro), "Documentos recentes", classificar pendente
  (tipo e data obrigatórios), baixar arquivo.

## S6 Home
- A-60 Indicadores reais (alugados, cadastrados, vagos, em manutenção, próximos do vencimento, atrasados, atrasados
  há mais de uma semana), movimentações recentes e cliques que abrem /imoveis e /contratos já filtrados.

## S7 Relatórios
- A-70 Os 4 PDFs (contratos existentes, atrasados, a vencer, movimentação por período com filtro Entrada/Saída):
  cabeçalho, gerado por, data e hora, período ou data de referência, código, rodapé "Página X de Y".
- A-71 Persistir relatorios e a composição (relatorio_movimentacoes, relatorio_contratos); download; só ADMINISTRADOR.
- A-72 /relatorios funcional: tipo, período (trimestre é atalho), cards, extrato, Gerar PDF, PDF personalizado.

## S8 Fecho
- A-80 Autorização por papel também nos serviços (não só na tela), tratamento global de erros e notificações, pt-BR,
  moeda e datas, fuso America/Sao_Paulo.
- A-81 Perfis dev e prod, variáveis de ambiente documentadas, Dockerfile, README de execução e deploy (Railway/Render).
- A-90 Auditoria final: DemoData removido, nenhum botão morto, todas as rotas do Figma funcionais, juiz verde,
  RELATORIO-FINAL.md.
