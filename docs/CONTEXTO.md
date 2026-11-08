# ManuElo: contexto do projeto (fonte da verdade)

Reutilizável: cole este arquivo (ou aponte para ele) em qualquer prompt do projeto.
Em conflito com a memória do modelo, este arquivo vence.

## 1. Projeto
ManuElo é o sistema de gestão financeira e documental da Fundação Manuel Cruz (entidade sem fins
lucrativos, 124 imóveis próprios alugados, presta contas ao Ministério Público de Sergipe).
Substitui planilhas e pastas físicas por uma base única. Entregue vazio, sem importar planilhas.
Projeto 2 GTI (CESAR School). MVP em 13 semanas, equipe de 7 pessoas.

## 2. Stack e arquitetura
Java 21, Spring Boot 3, Vaadin Flow (front 100% Java), Spring Data JPA/Hibernate, PostgreSQL no
Supabase (Session pooler, porta 5432; nunca o Transaction pooler). OpenPDF (recibos) e JasperReports
Community (relatórios), sem IA generativa na geração. Monólito modular: View -> Service -> Repository
-> Banco. Hospedagem: Railway ou Render. Banco: migrations Flyway-compatíveis.

## 3. Papéis
- ADMINISTRADOR: tudo, e com exclusividade: aprovar usuários, estornar, editar movimentação, gerar relatório.
- COLABORADOR: opera o dia a dia (cadastros, movimentações, documentos). Todos veem os mesmos dados.
- Não existe perfil de leitura. Cadastro público nasce COLABORADOR com status PENDENTE_APROVACAO.
- Toda ação grava quem fez (autoria).

## 4. Glossário
- Movimentação financeira = antes "lançamento". Tipos: ENTRADA, SAIDA, ESTORNO.
- Caução (no Figma aparece "calção", erro de digitação): garantia depositada pelo inquilino.
- Fiador: garantidor do contrato. Estorno: registro que anula uma movimentação sem apagá-la.
- Competência: mês de referência de um aluguel.

## 5. Telas (Figma) e o que cada uma exige dos dados
- /login, /cadastro (nome, e-mail, senha, confirmar) -> /aguardando-aprovacao, /recuperar-senha
  (e-mail) -> aviso de envio -> /redefinir-senha (nova senha, confirmar).
- /home "Visão geral": 4 cards: Imóveis alugados (subtexto: N em manutenção), Imóveis cadastrados
  (N vagos), Contratos próximos do vencimento (7 dias, configurável), Contratos atrasados (subtexto:
  N com mais de uma semana sem pagar). Lista "Movimentações recentes". Botões Exportar e + Nova Movimentação.
  Clique nos cards leva a /imoveis (filtro alugado ou sem filtro) e /contratos (filtro próximo do
  vencimento ou atrasado).
- /imoveis: filtros opcionais (endereço, tipo, status, inquilino vinculado). Lista: endereço, tipo,
  inquilino, status. Cadastro/edição: endereço, tipo, status, inquilino vinculado, upload do contrato.
  Detalhe mostra tudo, inclusive o contrato. Seção Reformas: histórico (data, descrição, valor,
  responsável do período) e + Novo Registro.
- /inquilinos: filtros (nome, CPF, e-mail); lista (nome, CPF, e-mail). Adicionar/editar: nome, CPF,
  e-mail, fiador (nome, CPF, e-mail), imóvel vinculado, upload do contrato, valor da caução, upload
  do recibo da caução.
- /contratos: filtros (nome, CPF, imóvel vinculado, validade, situação atrasado ou não). Lista: nome,
  validade + "faltam N dias", endereço, CPF, e-mail. Novo contrato: dados do inquilino, "tem ação
  judicial / qual", caução inicial e atual, fiador (nome, e-mail, telefone, CPF), upload do contrato
  (não há geração de contrato). "Pagar o aluguel": valor, forma de pagamento, caução inicial/atual,
  botões Pagar e Pagar com caução.
- /movimentacao-financeira: abas Entrada/Saída. Campos: categoria, forma de pagamento, valor, data,
  descrição, imóvel, comprovante (PDF/JPG/PNG até 10 MB). Ao confirmar: "só editável pelo administrador".
  Extrato do período (data, tipo, categoria, valor). Exportar extrato: confirma o período e gera PDF.
- /documentos: filtros (nome do inquilino, tipo de documento, tipo financeiro entrada/saída). Lista
  "Documentos recentes" (nome, enviado em, tamanho, etiqueta Contrato/Comprovante/Pendente).
  + Adicionar documento.
- /relatorios: período (trimestre é só atalho), tipo de relatório, formato PDF, totais
  (entradas, saídas, saldo), extrato, Gerar PDF.

## 6. Os 4 PDFs pedidos pelo cliente
Todos levam: nome da fundação, título, quem gerou, data e hora, período ou data de referência, código,
rodapé "Página X de Y".
1. Contratos existentes. 2. Contratos atrasados (dias de atraso, valor em atraso).
3. Contratos prestes a vencer (até 7 dias, configurável). 4. Movimentação (entradas e saídas) por
período: totais, subtotais por categoria, origem de cada entrada (inquilino e imóvel, ou descrição),
destino de cada saída, comprovante sim/não, estornos do período; filtro opcional Entrada ou Saída.

## 7. Modelo de dados v3 (tabelas e colunas-chave)
Auditoria padrão (criado_por/em, atualizado_por/em) em toda tabela editável.
- usuarios: nome, email uq, senha_hash, perfil{ADMINISTRADOR,COLABORADOR}, status{PENDENTE_APROVACAO,
  ATIVO,INATIVO}, aprovado_por, aprovado_em, ultimo_acesso_em.
- tokens_redefinicao_senha: usuario_id, token_hash uq, expira_em, usado_em.
- imoveis: logradouro, numero, complemento, bairro, cidade, uf, cep?, tipo{APARTAMENTO,CASA,COMERCIAL,
  TERRENO}, status{ALUGADO,VAGO,EM_MANUTENCAO}.
- inquilinos: nome, documento uq (CPF/CNPJ), email?.
- fiadores: nome, documento uq, email?, telefone?.
- contratos: imovel_id, inquilino_id, fiador_id?, inicio, fim?, valor_aluguel>0, dia_vencimento 1..31,
  acompanhar_pagamento_desde, possui_acao_judicial, acao_judicial_descricao (obrigatória se true).
  Sem sobreposição de períodos no mesmo imóvel.
- caucao_movimentacoes (imutável): contrato_id, tipo{DEPOSITO,ABATIMENTO_ALUGUEL,DEVOLUCAO}, valor>0,
  data, movimentacao_id?, documento_id? (recibo da caução). Saldo nunca negativo.
- movimentacoes (imutável): tipo, categoria{ALUGUEL,OUTRAS_ENTRADAS,MANUTENCAO,TRIBUTOS,ADMINISTRATIVO,
  OUTRAS_SAIDAS} (nula no ESTORNO), valor>0, data_movimentacao, descricao,
  forma_pagamento{PIX,BOLETO,TRANSFERENCIA,DINHEIRO,CAUCAO}, imovel_id?, responsavel_id, contrato_id?,
  competencia?, origem_descricao?, favorecido?, movimentacao_original_id? uq, justificativa?,
  corrige_movimentacao_id?, criado_em.
- documentos: nome_arquivo, tipo?, data_documento?, pendente_revisao, caminho_armazenamento, tipo_mime,
  tamanho_bytes<=10MB, vínculos opcionais (movimentacao_id, imovel_id, inquilino_id, contrato_id; ao
  menos um), enviado_por/em, atualizado_por/em.
- reformas: imovel_id, data_reforma, descricao, valor>0, movimentacao_id?.
- relatorios (insert-only): nome, tipo{CONTRATOS_EXISTENTES,CONTRATOS_ATRASADOS,CONTRATOS_A_VENCER,
  MOVIMENTACAO}, data_referencia, periodo_inicio?, periodo_fim?, filtro_tipo_movimentacao?, janela_dias?,
  total_entradas?, total_saidas?, saldo?, qtd_registros, caminho_arquivo, gerado_por, gerado_em.
- relatorio_movimentacoes (relatorio_id, movimentacao_id, ordem) e relatorio_contratos (relatorio_id,
  contrato_id, ordem, situacao, dias): guardam a composição exata do PDF.
- configuracoes: chave pk, valor, descricao. Seeds: dias_alerta_vencimento=7, dias_atraso_critico=7.
- Derivados (views/funções, sem tabela): extrato e saldo por período, situação de contratos
  (validade e pagamento), situação de imóveis, indicadores da home, histórico de reformas, lista de
  documentos, saldo de caução. Recibo e extrato não têm tabela.

## 8. Regras de negócio
R01 Movimentação nunca é editada nem apagada. Correção = ESTORNO total (um por original, só
    ADMINISTRADOR, mesmo valor, não se estorna estorno, exige justificativa).
R02 "Editar" feito pelo administrador = estorno do original + nova movimentação com corrige_movimentacao_id.
R03 ENTRADA: ALUGUEL exige contrato_id, competencia e imóvel igual ao do contrato; OUTRAS_ENTRADAS
    exige origem_descricao não vazia (descrição obrigatória). forma CAUCAO só em ALUGUEL.
R04 SAIDA exige forma de pagamento e pelo menos um documento (comprovante) até o fim da transação.
R05 Contrato atrasado = existe competência com vencimento passado cujo valor líquido pago (entradas de
    aluguel menos estornadas) é menor que valor_aluguel. Dias de atraso = hoje menos o vencimento
    mais antigo em aberto. Só conta a partir de acompanhar_pagamento_desde.
R06 Contrato próximo do vencimento = fim entre hoje e hoje + dias_alerta_vencimento. Vencido = fim passado.
R07 Caução: depósito não é receita. Abatimento vira ENTRADA de ALUGUEL com forma CAUCAO.
R08 Documento: ao menos um vínculo; PDF/JPG/PNG até 10 MB; pendente_revisao permite tipo e data
    vazios; classificar exige os dois.
R09 Relatório é insert-only e guarda a composição. Estorno conta no período da data do estorno.
R10 Usuário nunca é apagado (INATIVO). Token de senha: só hash, uso único, com prazo.
R11 Segurança Supabase: RLS ligado em todas as tabelas, sem acesso para anon/authenticated (a app usa
    JDBC direto); imutabilidade garantida por trigger, não só por permissão.

## 9. Decisões pré-tomadas
DEC-01 lançamentos virou movimentacoes (o dono do projeto disse "não mais lançamentos").
DEC-02 "Atrasado" é pagamento em atraso (Figma: "mais de uma semana sem pagar"), não data de fim.
DEC-03 Edição pelo administrador = estorno + nova movimentação (preserva "nada é apagado").
DEC-04 Aprovação de cadastro (Figma) troca ativo boolean por status + aprovado_por/em.
DEC-05 Caução em livro-razão próprio; depósito fora da receita; abatimento entra como ALUGUEL/CAUCAO.
DEC-06 Fiador em tabela própria ("Numero" do Figma = telefone). Ação judicial fica no contrato.
DEC-07 "Proprietário do período" das reformas = inquilino do contrato vigente na data (derivado);
       não existe entidade proprietário (os imóveis são da fundação).
DEC-08 Tipos de relatório = os 4 pedidos; trimestre é atalho de interface, sem coluna.
DEC-09 imoveis.status guarda a manutenção; alugado = tem contrato vigente; vago = sem contrato vigente;
       alugados + vagos = cadastrados; "em manutenção" é contagem informativa.
DEC-10 Comprovante obrigatório em SAIDA e opcional em ENTRADA (constraint trigger deferido).
DEC-11 Primeiro administrador criado por script fora das migrations (sem senha no repositório).
DEC-12 acompanhar_pagamento_desde = maior entre início do contrato e o mês do cadastro, para contratos
       antigos não nascerem "atrasados".
DEC-13 O banco suporta todos os filtros do Figma (nome, CPF, imóvel, validade, situação); a interface
       decide quais mostrar.

## 10. Fora de escopo
Geração de contratos, OCR/digitalização, trilha de auditoria por hash, importação de planilhas, site
público, login social, atas, planilhas diárias, fotos de imóveis, relatórios em Excel (saem só em PDF).
