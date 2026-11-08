-- T10: funções e views derivadas.

INSERT INTO usuarios (id, nome, email, senha_hash, perfil, status, aprovado_por, aprovado_em)
VALUES ('00000000-0000-4000-8000-000000001001', 'T10 Admin', 't10.admin@example.test',
        '$2a$10$abcdefghijklmnopqrstuvABCDEFGHIJKLMNOPQRSTUVWXYZ012345', 'ADMINISTRADOR', 'ATIVO',
        '00000000-0000-4000-8000-000000001001', now());

INSERT INTO imoveis (id, logradouro, numero, bairro, cidade, tipo, status)
VALUES
  ('00000000-0000-4000-8000-000000001002', 'Rua View A', '1002', 'Centro', 'Aracaju', 'CASA', 'VAGO'),
  ('00000000-0000-4000-8000-000000001003', 'Rua View Manutenção', '1003', 'Centro', 'Aracaju', 'CASA',
   'EM_MANUTENCAO'),
  ('00000000-0000-4000-8000-000000001008', 'Rua View Atrasada', '1008', 'Centro', 'Aracaju', 'CASA',
   'VAGO');

INSERT INTO inquilinos (id, nome, documento)
VALUES ('00000000-0000-4000-8000-000000001004', 'T10 Inquilino', '90000000011');

INSERT INTO fiadores (id, nome, documento)
VALUES ('00000000-0000-4000-8000-000000001005', 'T10 Fiador', '90000000012');

INSERT INTO contratos (id, imovel_id, inquilino_id, fiador_id, inicio, fim, valor_aluguel,
                       dia_vencimento, acompanhar_pagamento_desde)
VALUES
  ('00000000-0000-4000-8000-000000001006', '00000000-0000-4000-8000-000000001002',
   '00000000-0000-4000-8000-000000001004', '00000000-0000-4000-8000-000000001005',
   (current_date - interval '3 months')::date, (current_date + interval '3 days')::date,
   1000.00, 10, (current_date - interval '3 months')::date),
  ('00000000-0000-4000-8000-000000001009', '00000000-0000-4000-8000-000000001008',
   '00000000-0000-4000-8000-000000001004', NULL,
   (current_date - interval '2 months')::date, NULL, 1000.00, 10,
   (current_date - interval '2 months')::date);

-- Movimentações em período isolado (ano 2000) para o resumo exato.
INSERT INTO movimentacoes (id, tipo, categoria, valor, data_movimentacao, descricao,
                           forma_pagamento, responsavel_id, origem_descricao)
VALUES ('00000000-0000-4000-8000-000000001011', 'ENTRADA', 'OUTRAS_ENTRADAS', 1000.00,
        '2000-01-05', 'Entrada de teste', 'PIX', '00000000-0000-4000-8000-000000001001',
        'Origem de teste');

INSERT INTO movimentacoes (id, tipo, categoria, valor, data_movimentacao, descricao,
                           forma_pagamento, responsavel_id, favorecido)
VALUES ('00000000-0000-4000-8000-000000001012', 'SAIDA', 'OUTRAS_SAIDAS', 200.00,
        '2000-01-06', 'Saída de teste', 'PIX', '00000000-0000-4000-8000-000000001001',
        'Fornecedor de teste');

INSERT INTO documentos (id, nome_arquivo, caminho_armazenamento, tipo_mime, tamanho_bytes,
                        movimentacao_id, enviado_por)
VALUES ('00000000-0000-4000-8000-000000001013', 'saida-teste.jpg',
        'supabase://docs/saida-teste.jpg', 'image/jpeg', 100,
        '00000000-0000-4000-8000-000000001012', '00000000-0000-4000-8000-000000001001');

INSERT INTO movimentacoes (id, tipo, valor, data_movimentacao, descricao, responsavel_id,
                           movimentacao_original_id, justificativa)
VALUES ('00000000-0000-4000-8000-000000001014', 'ESTORNO', 1000.00, '2000-01-07',
        'Estorno da entrada de teste', '00000000-0000-4000-8000-000000001001',
        '00000000-0000-4000-8000-000000001011', 'Teste de estorno');

-- Caução, reforma e documento classificado.
INSERT INTO caucao_movimentacoes (id, contrato_id, tipo, valor, data, criado_por)
VALUES ('00000000-0000-4000-8000-000000001015', '00000000-0000-4000-8000-000000001006',
        'DEPOSITO', 500.00, current_date, '00000000-0000-4000-8000-000000001001');

INSERT INTO reformas (id, imovel_id, data_reforma, descricao, valor, criado_por)
VALUES ('00000000-0000-4000-8000-000000001016', '00000000-0000-4000-8000-000000001002',
        (current_date - interval '30 days')::date, 'Reforma de teste', 100.00,
        '00000000-0000-4000-8000-000000001001');

INSERT INTO documentos (id, nome_arquivo, tipo, data_documento, pendente_revisao,
                        caminho_armazenamento, tipo_mime, tamanho_bytes, contrato_id, enviado_por)
VALUES ('00000000-0000-4000-8000-000000001017', 'contrato-teste.pdf', 'CONTRATO', current_date,
        false, 'supabase://docs/contrato-teste.pdf', 'application/pdf', 200,
        '00000000-0000-4000-8000-000000001006', '00000000-0000-4000-8000-000000001001');

-- Resumo exato do período isolado.
DO $$
DECLARE
  r record;
BEGIN
  SELECT * INTO r FROM fn_resumo_periodo('2000-01-01', '2000-01-31');
  IF r.total_entradas <> 0 OR r.total_saidas <> 200 OR r.saldo <> -200
     OR r.total_estornos <> 1000 OR r.qtd_registros <> 3 THEN
    RAISE EXCEPTION 'resumo inesperado: %', row_to_json(r);
  END IF;
END $$;

-- Extrato e filtros.
DO $$
DECLARE
  n integer;
BEGIN
  SELECT count(*) INTO n FROM fn_extrato_periodo('2000-01-01', '2000-01-31');
  IF n <> 3 THEN RAISE EXCEPTION 'extrato completo deveria ter 3 linhas, veio %', n; END IF;

  SELECT count(*) INTO n FROM fn_extrato_periodo('2000-01-01', '2000-01-31', 'ENTRADA');
  IF n <> 2 THEN RAISE EXCEPTION 'extrato de entrada deveria ter 2 linhas, veio %', n; END IF;

  SELECT count(*) INTO n FROM fn_extrato_periodo('2000-01-01', '2000-01-31', 'SAIDA');
  IF n <> 1 THEN RAISE EXCEPTION 'extrato de saída deveria ter 1 linha, veio %', n; END IF;
END $$;

-- Negativos de período.
DO $$
BEGIN
  BEGIN
    PERFORM * FROM fn_resumo_periodo('2000-02-01', '2000-01-01');
    RAISE EXCEPTION 'resumo aceitou período invertido';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    PERFORM * FROM fn_extrato_periodo(NULL, '2000-01-01');
    RAISE EXCEPTION 'extrato aceitou período nulo';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    PERFORM * FROM fn_contrato_situacao('00000000-0000-4000-8000-000000001006', NULL);
    RAISE EXCEPTION 'situação aceitou referência nula';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
END $$;

-- Situação dos contratos.
DO $$
DECLARE
  r record;
BEGIN
  SELECT * INTO r FROM fn_contrato_situacao('00000000-0000-4000-8000-000000001006', current_date);
  IF r.situacao_validade <> 'A_VENCER' OR r.dias_para_vencer <> 3 THEN
    RAISE EXCEPTION 'situação do contrato A_VENCER inesperada: %', row_to_json(r);
  END IF;
  IF NOT r.atrasado OR r.dias_atraso <= 0 OR r.valor_em_atraso <= 0 THEN
    RAISE EXCEPTION 'contrato deveria estar atrasado: %', row_to_json(r);
  END IF;

  SELECT * INTO r FROM fn_contrato_situacao('00000000-0000-4000-8000-000000001009', current_date);
  IF r.situacao_validade <> 'VIGENTE' OR NOT r.atrasado OR r.dias_atraso <= 0 THEN
    RAISE EXCEPTION 'situação do contrato em aberto inesperada: %', row_to_json(r);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM vw_contratos_a_vencer
                  WHERE contrato_id = '00000000-0000-4000-8000-000000001006') THEN
    RAISE EXCEPTION 'vw_contratos_a_vencer não listou o contrato';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM vw_contratos_atrasados
                  WHERE contrato_id = '00000000-0000-4000-8000-000000001009') THEN
    RAISE EXCEPTION 'vw_contratos_atrasados não listou o contrato';
  END IF;
END $$;

-- Situação de imóveis (DEC-09).
DO $$
DECLARE
  r record;
  home record;
BEGIN
  SELECT * INTO r FROM vw_imoveis_situacao WHERE id = '00000000-0000-4000-8000-000000001002';
  IF r.situacao_contrato <> 'ALUGADO' OR r.em_manutencao THEN
    RAISE EXCEPTION 'imóvel com contrato vigente deveria estar ALUGADO: %', row_to_json(r);
  END IF;

  SELECT * INTO r FROM vw_imoveis_situacao WHERE id = '00000000-0000-4000-8000-000000001003';
  IF r.situacao_contrato <> 'VAGO' OR NOT r.em_manutencao
     OR r.status_exibicao <> 'EM_MANUTENCAO' THEN
    RAISE EXCEPTION 'imóvel em manutenção inesperado: %', row_to_json(r);
  END IF;

  SELECT * INTO home FROM vw_home_indicadores;
  IF home.imoveis_alugados + home.imoveis_vagos <> home.imoveis_cadastrados THEN
    RAISE EXCEPTION 'alugados + vagos deveria igualar cadastrados: %', row_to_json(home);
  END IF;
  IF home.imoveis_em_manutencao < 1 THEN
    RAISE EXCEPTION 'home deveria contar imóvel em manutenção: %', row_to_json(home);
  END IF;
  IF home.contratos_atrasados < 2 OR home.contratos_proximos_vencimento < 1 THEN
    RAISE EXCEPTION 'indicadores de contratos inesperados: %', row_to_json(home);
  END IF;
END $$;

-- Reformas: responsável do período derivado do contrato vigente.
DO $$
DECLARE
  v_nome text;
BEGIN
  SELECT responsavel_periodo INTO v_nome
    FROM vw_reformas_historico WHERE id = '00000000-0000-4000-8000-000000001016';
  IF v_nome IS DISTINCT FROM 'T10 Inquilino' THEN
    RAISE EXCEPTION 'responsável do período inesperado: %', v_nome;
  END IF;
END $$;

-- Documentos: etiqueta e tipo financeiro.
DO $$
DECLARE
  r record;
BEGIN
  SELECT * INTO r FROM vw_documentos_lista WHERE id = '00000000-0000-4000-8000-000000001013';
  IF r.etiqueta <> 'PENDENTE' OR r.tipo_financeiro <> 'SAIDA' THEN
    RAISE EXCEPTION 'documento pendente inesperado: %', row_to_json(r);
  END IF;

  SELECT * INTO r FROM vw_documentos_lista WHERE id = '00000000-0000-4000-8000-000000001017';
  IF r.etiqueta <> 'CONTRATO' OR r.tipo_financeiro IS NOT NULL THEN
    RAISE EXCEPTION 'documento de contrato inesperado: %', row_to_json(r);
  END IF;
END $$;

-- Saldo de caução.
DO $$
DECLARE
  v_saldo numeric;
BEGIN
  SELECT saldo INTO v_saldo FROM vw_saldo_caucao
   WHERE contrato_id = '00000000-0000-4000-8000-000000001006';
  IF v_saldo <> 500 THEN
    RAISE EXCEPTION 'saldo de caução inesperado: %', v_saldo;
  END IF;
END $$;

-- Movimentações recentes (limite de 20 e presença do estorno mais novo).
DO $$
DECLARE
  n integer;
BEGIN
  SELECT count(*) INTO n FROM vw_movimentacoes_recentes;
  IF n > 20 THEN RAISE EXCEPTION 'vw_movimentacoes_recentes devolveu % linhas', n; END IF;
  IF NOT EXISTS (SELECT 1 FROM vw_movimentacoes_recentes
                  WHERE id = '00000000-0000-4000-8000-000000001014') THEN
    RAISE EXCEPTION 'vw_movimentacoes_recentes não trouxe o estorno recente';
  END IF;
END $$;
