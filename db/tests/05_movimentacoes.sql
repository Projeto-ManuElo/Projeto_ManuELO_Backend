-- T06: movimentações, checks por tipo/categoria, competência e forma CAUCAO.

INSERT INTO usuarios (id, nome, email, senha_hash, perfil, status, aprovado_por, aprovado_em)
VALUES ('00000000-0000-4000-8000-000000000601', 'T06 Operador', 't06.operador@example.test',
        '$2a$10$abcdefghijklmnopqrstuvABCDEFGHIJKLMNOPQRSTUVWXYZ012345', 'ADMINISTRADOR', 'ATIVO',
        '00000000-0000-4000-8000-000000000601', now())
ON CONFLICT DO NOTHING;

INSERT INTO imoveis (id, logradouro, numero, bairro, cidade, tipo)
VALUES ('00000000-0000-4000-8000-000000000602', 'Rua da Movimentação', '60', 'Centro', 'Aracaju', 'CASA')
ON CONFLICT DO NOTHING;

INSERT INTO inquilinos (id, nome, documento)
VALUES ('00000000-0000-4000-8000-000000000603', 'T06 Inquilino', '90000000007')
ON CONFLICT DO NOTHING;

INSERT INTO contratos (id, imovel_id, inquilino_id, inicio, valor_aluguel, dia_vencimento,
                       acompanhar_pagamento_desde)
VALUES ('00000000-0000-4000-8000-000000000604', '00000000-0000-4000-8000-000000000602',
        '00000000-0000-4000-8000-000000000603', '2025-01-01', 1000.00, 10, '2025-01-01')
ON CONFLICT DO NOTHING;

-- Positivos.
INSERT INTO movimentacoes (id, tipo, categoria, valor, data_movimentacao, descricao,
                           forma_pagamento, imovel_id, responsavel_id, contrato_id, competencia)
VALUES ('00000000-0000-4000-8000-000000000611', 'ENTRADA', 'ALUGUEL', 1000.00, '2025-02-10',
        'Aluguel fevereiro', 'PIX', '00000000-0000-4000-8000-000000000602',
        '00000000-0000-4000-8000-000000000601', '00000000-0000-4000-8000-000000000604',
        '2025-02-01')
ON CONFLICT DO NOTHING;

INSERT INTO movimentacoes (id, tipo, categoria, valor, data_movimentacao, descricao,
                           forma_pagamento, responsavel_id, origem_descricao)
VALUES ('00000000-0000-4000-8000-000000000612', 'ENTRADA', 'OUTRAS_ENTRADAS', 150.00,
        '2025-02-15', 'Doação', 'DINHEIRO', '00000000-0000-4000-8000-000000000601',
        'Doação de terceiro')
ON CONFLICT DO NOTHING;

INSERT INTO movimentacoes (id, tipo, categoria, valor, data_movimentacao, descricao,
                           forma_pagamento, responsavel_id, favorecido)
VALUES ('00000000-0000-4000-8000-000000000613', 'SAIDA', 'MANUTENCAO', 300.00,
        '2025-02-20', 'Troca de bomba', 'PIX', '00000000-0000-4000-8000-000000000601',
        'Encanador Fulano')
ON CONFLICT DO NOTHING;

-- Negativos.
DO $$
BEGIN
  BEGIN
    INSERT INTO movimentacoes (tipo, categoria, valor, data_movimentacao, descricao,
                               forma_pagamento, responsavel_id)
    VALUES ('ENTRADA', 'MANUTENCAO', 10, '2025-02-01', 'X', 'PIX',
            '00000000-0000-4000-8000-000000000601');
    RAISE EXCEPTION 'aceitou SAIDA-categoria em ENTRADA';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO movimentacoes (tipo, categoria, valor, data_movimentacao, descricao,
                               forma_pagamento, responsavel_id)
    VALUES ('SAIDA', 'ALUGUEL', 10, '2025-02-01', 'X', 'PIX',
            '00000000-0000-4000-8000-000000000601');
    RAISE EXCEPTION 'aceitou categoria de entrada em SAIDA';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO movimentacoes (tipo, categoria, valor, data_movimentacao, descricao,
                               forma_pagamento, responsavel_id)
    VALUES ('ESTORNO', 'ALUGUEL', 10, '2025-02-01', 'X', 'PIX',
            '00000000-0000-4000-8000-000000000601');
    RAISE EXCEPTION 'aceitou ESTORNO com categoria';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO movimentacoes (tipo, categoria, valor, data_movimentacao, descricao,
                               forma_pagamento, responsavel_id)
    VALUES ('ENTRADA', NULL, 10, '2025-02-01', 'X', 'PIX',
            '00000000-0000-4000-8000-000000000601');
    RAISE EXCEPTION 'aceitou ENTRADA sem categoria';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO movimentacoes (tipo, categoria, valor, data_movimentacao, descricao,
                               forma_pagamento, responsavel_id, competencia)
    VALUES ('ENTRADA', 'ALUGUEL', 10, '2025-02-01', 'X', 'PIX',
            '00000000-0000-4000-8000-000000000601', '2025-02-01');
    RAISE EXCEPTION 'aceitou ALUGUEL sem contrato/imóvel';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO movimentacoes (tipo, categoria, valor, data_movimentacao, descricao,
                               forma_pagamento, responsavel_id, imovel_id, contrato_id)
    VALUES ('ENTRADA', 'ALUGUEL', 10, '2025-02-01', 'X', 'PIX',
            '00000000-0000-4000-8000-000000000601', '00000000-0000-4000-8000-000000000602',
            '00000000-0000-4000-8000-000000000604');
    RAISE EXCEPTION 'aceitou ALUGUEL sem competência';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO movimentacoes (tipo, categoria, valor, data_movimentacao, descricao,
                               forma_pagamento, responsavel_id, imovel_id, contrato_id, competencia)
    VALUES ('ENTRADA', 'ALUGUEL', 10, '2025-02-01', 'X', 'PIX',
            '00000000-0000-4000-8000-000000000601', '00000000-0000-4000-8000-000000000602',
            '00000000-0000-4000-8000-000000000604', '2025-02-15');
    RAISE EXCEPTION 'aceitou competência que não é o primeiro dia do mês';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO movimentacoes (tipo, categoria, valor, data_movimentacao, descricao,
                               forma_pagamento, responsavel_id)
    VALUES ('ENTRADA', 'OUTRAS_ENTRADAS', 10, '2025-02-01', 'X', 'PIX',
            '00000000-0000-4000-8000-000000000601');
    RAISE EXCEPTION 'aceitou OUTRAS_ENTRADAS sem origem';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO movimentacoes (tipo, categoria, valor, data_movimentacao, descricao,
                               forma_pagamento, responsavel_id, origem_descricao)
    VALUES ('ENTRADA', 'OUTRAS_ENTRADAS', 10, '2025-02-01', 'X', 'PIX',
            '00000000-0000-4000-8000-000000000601', '   ');
    RAISE EXCEPTION 'aceitou OUTRAS_ENTRADAS com origem em branco';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO movimentacoes (tipo, categoria, valor, data_movimentacao, descricao,
                               forma_pagamento, responsavel_id, origem_descricao)
    VALUES ('ENTRADA', 'OUTRAS_ENTRADAS', 10, '2025-02-01', 'X', 'CAUCAO',
            '00000000-0000-4000-8000-000000000601', 'Origem');
    RAISE EXCEPTION 'aceitou forma CAUCAO fora de ALUGUEL';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO movimentacoes (tipo, categoria, valor, data_movimentacao, descricao,
                               forma_pagamento, responsavel_id)
    VALUES ('ENTRADA', 'OUTRAS_ENTRADAS', 0, '2025-02-01', 'X', 'PIX',
            '00000000-0000-4000-8000-000000000601');
    RAISE EXCEPTION 'aceitou valor zero';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO movimentacoes (tipo, categoria, valor, data_movimentacao, descricao,
                               forma_pagamento, responsavel_id)
    VALUES ('SAIDA', 'OUTRAS_SAIDAS', 10, '2025-02-01', '   ', 'PIX',
            '00000000-0000-4000-8000-000000000601');
    RAISE EXCEPTION 'aceitou descrição vazia';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO movimentacoes (tipo, categoria, valor, data_movimentacao, descricao,
                               responsavel_id)
    VALUES ('SAIDA', 'OUTRAS_SAIDAS', 10, '2025-02-01', 'X',
            '00000000-0000-4000-8000-000000000601');
    RAISE EXCEPTION 'aceitou SAIDA sem forma de pagamento';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO movimentacoes (tipo, categoria, valor, data_movimentacao, descricao,
                               forma_pagamento, responsavel_id, movimentacao_original_id)
    VALUES ('ENTRADA', 'OUTRAS_ENTRADAS', 10, '2025-02-01', 'X', 'PIX',
            '00000000-0000-4000-8000-000000000601', '00000000-0000-4000-8000-000000000611');
    RAISE EXCEPTION 'aceitou original_id em movimentação normal';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO movimentacoes (tipo, categoria, valor, data_movimentacao, descricao,
                               forma_pagamento, responsavel_id, favorecido)
    VALUES ('ENTRADA', 'OUTRAS_ENTRADAS', 10, '2025-02-01', 'X', 'PIX',
            '00000000-0000-4000-8000-000000000601', 'Fulano');
    RAISE EXCEPTION 'aceitou favorecido em ENTRADA';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
END $$;

-- Estorno básico (regras de admin/valor ficam para a V007): unicidade do original.
INSERT INTO movimentacoes (id, tipo, valor, data_movimentacao, descricao, responsavel_id,
                           movimentacao_original_id, justificativa)
VALUES ('00000000-0000-4000-8000-000000000614', 'ESTORNO', 1000.00, '2025-02-25',
        'Estorno aluguel fevereiro', '00000000-0000-4000-8000-000000000601',
        '00000000-0000-4000-8000-000000000611', 'Pagamento duplicado')
ON CONFLICT DO NOTHING;

DO $$
BEGIN
  BEGIN
    INSERT INTO movimentacoes (tipo, valor, data_movimentacao, descricao, responsavel_id,
                               movimentacao_original_id, justificativa)
    VALUES ('ESTORNO', 1000.00, '2025-02-26', 'Segundo estorno',
            '00000000-0000-4000-8000-000000000601',
            '00000000-0000-4000-8000-000000000611', 'Duplicado');
    RAISE EXCEPTION 'aceitou dois estornos para o mesmo original';
  EXCEPTION WHEN unique_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO movimentacoes (tipo, valor, data_movimentacao, descricao, responsavel_id)
    VALUES ('ESTORNO', 10, '2025-02-26', 'sem original e sem justificativa',
            '00000000-0000-4000-8000-000000000601');
    RAISE EXCEPTION 'aceitou ESTORNO sem original/justificativa';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
END $$;
