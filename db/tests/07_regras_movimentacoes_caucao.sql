-- T07: imutabilidade, estorno, correção, imóvel do aluguel, comprovante e caução.

INSERT INTO usuarios (id, nome, email, senha_hash, perfil, status, aprovado_por, aprovado_em)
VALUES
  ('00000000-0000-4000-8000-000000000801', 'T07 Admin', 't07.admin@example.test',
   '$2a$10$abcdefghijklmnopqrstuvABCDEFGHIJKLMNOPQRSTUVWXYZ012345', 'ADMINISTRADOR', 'ATIVO',
   '00000000-0000-4000-8000-000000000801', now()),
  ('00000000-0000-4000-8000-000000000802', 'T07 Colaborador', 't07.colaborador@example.test',
   '$2a$10$abcdefghijklmnopqrstuvABCDEFGHIJKLMNOPQRSTUVWXYZ012345', 'COLABORADOR', 'ATIVO',
   '00000000-0000-4000-8000-000000000801', now())
ON CONFLICT DO NOTHING;

INSERT INTO imoveis (id, logradouro, numero, bairro, cidade, tipo)
VALUES
  ('00000000-0000-4000-8000-000000000803', 'Rua A do Teste', '80', 'Centro', 'Aracaju', 'CASA'),
  ('00000000-0000-4000-8000-000000000804', 'Rua B do Teste', '81', 'Centro', 'Aracaju', 'CASA')
ON CONFLICT DO NOTHING;

INSERT INTO inquilinos (id, nome, documento)
VALUES ('00000000-0000-4000-8000-000000000805', 'T07 Inquilino', '90000000009')
ON CONFLICT DO NOTHING;

INSERT INTO contratos (id, imovel_id, inquilino_id, inicio, valor_aluguel, dia_vencimento,
                       acompanhar_pagamento_desde)
VALUES
  ('00000000-0000-4000-8000-000000000806', '00000000-0000-4000-8000-000000000803',
   '00000000-0000-4000-8000-000000000805', '2025-01-01', 1000.00, 10, '2025-01-01'),
  ('00000000-0000-4000-8000-000000000807', '00000000-0000-4000-8000-000000000804',
   '00000000-0000-4000-8000-000000000805', '2025-01-01', 1000.00, 10, '2025-01-01')
ON CONFLICT DO NOTHING;

-- Positivos: entrada, saída com comprovante, estorno, correção e caução.
INSERT INTO movimentacoes (id, tipo, categoria, valor, data_movimentacao, descricao,
                           forma_pagamento, imovel_id, responsavel_id, contrato_id, competencia)
VALUES ('00000000-0000-4000-8000-000000000811', 'ENTRADA', 'ALUGUEL', 1000.00, '2025-03-10',
        'Aluguel março', 'PIX', '00000000-0000-4000-8000-000000000803',
        '00000000-0000-4000-8000-000000000801', '00000000-0000-4000-8000-000000000806',
        '2025-03-01')
ON CONFLICT DO NOTHING;

INSERT INTO movimentacoes (id, tipo, categoria, valor, data_movimentacao, descricao,
                           forma_pagamento, responsavel_id, favorecido)
VALUES ('00000000-0000-4000-8000-000000000812', 'SAIDA', 'OUTRAS_SAIDAS', 200.00, '2025-03-11',
        'Material de limpeza', 'PIX', '00000000-0000-4000-8000-000000000801', 'Fornecedor')
ON CONFLICT DO NOTHING;

INSERT INTO documentos (id, nome_arquivo, caminho_armazenamento, tipo_mime, tamanho_bytes,
                        movimentacao_id, enviado_por)
VALUES ('00000000-0000-4000-8000-000000000813', 'nota.jpg', 'supabase://docs/nota.jpg',
        'image/jpeg', 500, '00000000-0000-4000-8000-000000000812',
        '00000000-0000-4000-8000-000000000801')
ON CONFLICT DO NOTHING;

INSERT INTO caucao_movimentacoes (id, contrato_id, tipo, valor, data, criado_por)
VALUES ('00000000-0000-4000-8000-000000000814', '00000000-0000-4000-8000-000000000806',
        'DEPOSITO', 1000.00, '2025-01-01', '00000000-0000-4000-8000-000000000801')
ON CONFLICT DO NOTHING;

INSERT INTO movimentacoes (id, tipo, valor, data_movimentacao, descricao, responsavel_id,
                           movimentacao_original_id, justificativa)
VALUES ('00000000-0000-4000-8000-000000000815', 'ESTORNO', 1000.00, '2025-03-20',
        'Estorno aluguel março', '00000000-0000-4000-8000-000000000801',
        '00000000-0000-4000-8000-000000000811', 'Pagamento em duplicidade')
ON CONFLICT DO NOTHING;

INSERT INTO movimentacoes (id, tipo, categoria, valor, data_movimentacao, descricao,
                           forma_pagamento, imovel_id, responsavel_id, contrato_id, competencia,
                           corrige_movimentacao_id)
VALUES ('00000000-0000-4000-8000-000000000816', 'ENTRADA', 'ALUGUEL', 1000.00, '2025-03-21',
        'Aluguel março corrigido', 'PIX', '00000000-0000-4000-8000-000000000803',
        '00000000-0000-4000-8000-000000000801', '00000000-0000-4000-8000-000000000806',
        '2025-03-01', '00000000-0000-4000-8000-000000000811')
ON CONFLICT DO NOTHING;

INSERT INTO movimentacoes (id, tipo, categoria, valor, data_movimentacao, descricao,
                           forma_pagamento, imovel_id, responsavel_id, contrato_id, competencia)
VALUES ('00000000-0000-4000-8000-000000000817', 'ENTRADA', 'ALUGUEL', 500.00, '2025-04-10',
        'Aluguel abril pago com caução', 'CAUCAO', '00000000-0000-4000-8000-000000000803',
        '00000000-0000-4000-8000-000000000801', '00000000-0000-4000-8000-000000000806',
        '2025-04-01')
ON CONFLICT DO NOTHING;

INSERT INTO caucao_movimentacoes (id, contrato_id, tipo, valor, data, movimentacao_id, criado_por)
VALUES ('00000000-0000-4000-8000-000000000818', '00000000-0000-4000-8000-000000000806',
        'ABATIMENTO_ALUGUEL', 500.00, '2025-04-10', '00000000-0000-4000-8000-000000000817',
        '00000000-0000-4000-8000-000000000801')
ON CONFLICT DO NOTHING;

-- Negativos.
DO $$
BEGIN
  -- Estorno por colaborador.
  BEGIN
    INSERT INTO movimentacoes (tipo, valor, data_movimentacao, descricao, responsavel_id,
                               movimentacao_original_id, justificativa)
    VALUES ('ESTORNO', 1000.00, '2025-03-22', 'Estorno indevido',
            '00000000-0000-4000-8000-000000000802',
            '00000000-0000-4000-8000-000000000811', 'Tentativa');
    RAISE EXCEPTION 'aceitou estorno por colaborador';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  -- Estorno com valor diferente.
  BEGIN
    INSERT INTO movimentacoes (tipo, valor, data_movimentacao, descricao, responsavel_id,
                               movimentacao_original_id, justificativa)
    VALUES ('ESTORNO', 199.00, '2025-03-22', 'Estorno valor errado',
            '00000000-0000-4000-8000-000000000801',
            '00000000-0000-4000-8000-000000000812', 'Tentativa');
    RAISE EXCEPTION 'aceitou estorno com valor diferente';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  -- Estorno de estorno.
  BEGIN
    INSERT INTO movimentacoes (tipo, valor, data_movimentacao, descricao, responsavel_id,
                               movimentacao_original_id, justificativa)
    VALUES ('ESTORNO', 1000.00, '2025-03-22', 'Estorno de estorno',
            '00000000-0000-4000-8000-000000000801',
            '00000000-0000-4000-8000-000000000815', 'Tentativa');
    RAISE EXCEPTION 'aceitou estorno de estorno';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  -- Duplo estorno da mesma original.
  BEGIN
    INSERT INTO movimentacoes (tipo, valor, data_movimentacao, descricao, responsavel_id,
                               movimentacao_original_id, justificativa)
    VALUES ('ESTORNO', 1000.00, '2025-03-23', 'Segundo estorno',
            '00000000-0000-4000-8000-000000000801',
            '00000000-0000-4000-8000-000000000811', 'Tentativa');
    RAISE EXCEPTION 'aceitou duplo estorno';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  -- Aluguel com imóvel diferente do contrato.
  BEGIN
    INSERT INTO movimentacoes (tipo, categoria, valor, data_movimentacao, descricao,
                               forma_pagamento, imovel_id, responsavel_id, contrato_id, competencia)
    VALUES ('ENTRADA', 'ALUGUEL', 1000.00, '2025-05-10', 'Aluguel imóvel errado', 'PIX',
            '00000000-0000-4000-8000-000000000804', '00000000-0000-4000-8000-000000000801',
            '00000000-0000-4000-8000-000000000806', '2025-05-01');
    RAISE EXCEPTION 'aceitou aluguel com imóvel diferente do contrato';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  -- Correção por colaborador.
  BEGIN
    INSERT INTO movimentacoes (tipo, categoria, valor, data_movimentacao, descricao,
                               forma_pagamento, imovel_id, responsavel_id, contrato_id, competencia,
                               corrige_movimentacao_id)
    VALUES ('ENTRADA', 'ALUGUEL', 1000.00, '2025-05-11', 'Correção indevida', 'PIX',
            '00000000-0000-4000-8000-000000000803', '00000000-0000-4000-8000-000000000802',
            '00000000-0000-4000-8000-000000000806', '2025-05-01',
            '00000000-0000-4000-8000-000000000811');
    RAISE EXCEPTION 'aceitou correção por colaborador';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  -- Imutabilidade.
  BEGIN
    UPDATE movimentacoes SET descricao = 'alterada'
     WHERE id = '00000000-0000-4000-8000-000000000811';
    RAISE EXCEPTION 'aceitou UPDATE em movimentação';
  EXCEPTION WHEN SQLSTATE '55000' THEN NULL;
  END;

  BEGIN
    DELETE FROM movimentacoes WHERE id = '00000000-0000-4000-8000-000000000811';
    RAISE EXCEPTION 'aceitou DELETE em movimentação';
  EXCEPTION WHEN SQLSTATE '55000' THEN NULL;
  END;

  BEGIN
    UPDATE caucao_movimentacoes SET valor = 1
     WHERE id = '00000000-0000-4000-8000-000000000814';
    RAISE EXCEPTION 'aceitou UPDATE em caução';
  EXCEPTION WHEN SQLSTATE '55000' THEN NULL;
  END;

  BEGIN
    DELETE FROM caucao_movimentacoes WHERE id = '00000000-0000-4000-8000-000000000814';
    RAISE EXCEPTION 'aceitou DELETE em caução';
  EXCEPTION WHEN SQLSTATE '55000' THEN NULL;
  END;

  -- SAÍDA sem comprovante (trigger deferido forçado com SET CONSTRAINTS).
  BEGIN
    INSERT INTO movimentacoes (id, tipo, categoria, valor, data_movimentacao, descricao,
                               forma_pagamento, responsavel_id, favorecido)
    VALUES ('00000000-0000-4000-8000-000000000821', 'SAIDA', 'OUTRAS_SAIDAS', 10.00,
            '2025-06-01', 'Saída sem nota', 'PIX', '00000000-0000-4000-8000-000000000801',
            'Fornecedor');
    SET CONSTRAINTS trg_movimentacao_comprovante_saida IMMEDIATE;
    RAISE EXCEPTION 'aceitou SAÍDA sem comprovante';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  -- Correção sem estorno prévio.
  BEGIN
    INSERT INTO movimentacoes (id, tipo, categoria, valor, data_movimentacao, descricao,
                               forma_pagamento, imovel_id, responsavel_id, contrato_id, competencia,
                               corrige_movimentacao_id)
    VALUES ('00000000-0000-4000-8000-000000000822', 'ENTRADA', 'ALUGUEL', 1000.00,
            '2025-06-02', 'Correção sem estorno', 'PIX', '00000000-0000-4000-8000-000000000803',
            '00000000-0000-4000-8000-000000000801', '00000000-0000-4000-8000-000000000806',
            '2025-06-01', '00000000-0000-4000-8000-000000000812');
    SET CONSTRAINTS trg_movimentacao_correcao IMMEDIATE;
    RAISE EXCEPTION 'aceitou correção sem estorno prévio';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  -- Abatimento de caução com valor divergente.
  BEGIN
    INSERT INTO movimentacoes (id, tipo, categoria, valor, data_movimentacao, descricao,
                               forma_pagamento, imovel_id, responsavel_id, contrato_id, competencia)
    VALUES ('00000000-0000-4000-8000-000000000823', 'ENTRADA', 'ALUGUEL', 300.00,
            '2025-06-10', 'Aluguel com caução divergente', 'CAUCAO',
            '00000000-0000-4000-8000-000000000803', '00000000-0000-4000-8000-000000000801',
            '00000000-0000-4000-8000-000000000806', '2025-06-01');
    INSERT INTO caucao_movimentacoes (contrato_id, tipo, valor, data, movimentacao_id, criado_por)
    VALUES ('00000000-0000-4000-8000-000000000806', 'ABATIMENTO_ALUGUEL', 200.00, '2025-06-10',
            '00000000-0000-4000-8000-000000000823', '00000000-0000-4000-8000-000000000801');
    RAISE EXCEPTION 'aceitou abatimento com valor divergente';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  -- Saldo de caução negativo.
  BEGIN
    INSERT INTO caucao_movimentacoes (contrato_id, tipo, valor, data, criado_por)
    VALUES ('00000000-0000-4000-8000-000000000806', 'DEVOLUCAO', 600.00, '2025-07-01',
            '00000000-0000-4000-8000-000000000801');
    RAISE EXCEPTION 'aceitou saldo de caução negativo';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  -- Forma CAUCAO sem registro no livro de caução.
  BEGIN
    INSERT INTO movimentacoes (id, tipo, categoria, valor, data_movimentacao, descricao,
                               forma_pagamento, imovel_id, responsavel_id, contrato_id, competencia)
    VALUES ('00000000-0000-4000-8000-000000000824', 'ENTRADA', 'ALUGUEL', 100.00,
            '2025-07-10', 'Caução sem registro', 'CAUCAO',
            '00000000-0000-4000-8000-000000000803', '00000000-0000-4000-8000-000000000801',
            '00000000-0000-4000-8000-000000000806', '2025-07-01');
    SET CONSTRAINTS trg_movimentacao_caucao_vinculada IMMEDIATE;
    RAISE EXCEPTION 'aceitou forma CAUCAO sem livro de caução';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
END $$;
