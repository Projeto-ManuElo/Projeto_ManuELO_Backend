-- T05: contratos (checks e exclusão de sobreposição) e caução.

INSERT INTO usuarios (id, nome, email, senha_hash, perfil, status, aprovado_por, aprovado_em)
VALUES ('00000000-0000-4000-8000-000000000501', 'T05 Operador', 't05.operador@example.test',
        '$2a$10$abcdefghijklmnopqrstuvABCDEFGHIJKLMNOPQRSTUVWXYZ012345', 'ADMINISTRADOR', 'ATIVO',
        '00000000-0000-4000-8000-000000000501', now())
ON CONFLICT DO NOTHING;

INSERT INTO imoveis (id, logradouro, numero, bairro, cidade, tipo)
VALUES ('00000000-0000-4000-8000-000000000502', 'Rua do Contrato', '50', 'Centro', 'Aracaju', 'CASA')
ON CONFLICT DO NOTHING;

INSERT INTO inquilinos (id, nome, documento)
VALUES ('00000000-0000-4000-8000-000000000503', 'T05 Inquilino', '90000000005')
ON CONFLICT DO NOTHING;

INSERT INTO fiadores (id, nome, documento)
VALUES ('00000000-0000-4000-8000-000000000504', 'T05 Fiador', '90000000006')
ON CONFLICT DO NOTHING;

INSERT INTO contratos (id, imovel_id, inquilino_id, fiador_id, inicio, fim, valor_aluguel,
                       dia_vencimento, acompanhar_pagamento_desde)
VALUES ('00000000-0000-4000-8000-000000000505', '00000000-0000-4000-8000-000000000502',
        '00000000-0000-4000-8000-000000000503', '00000000-0000-4000-8000-000000000504',
        '2025-01-01', '2025-12-31', 1000.00, 10, '2025-01-01')
ON CONFLICT DO NOTHING;

-- Positivo: contrato seguinte sem sobreposição é aceito.
INSERT INTO contratos (id, imovel_id, inquilino_id, inicio, valor_aluguel, dia_vencimento,
                       acompanhar_pagamento_desde)
VALUES ('00000000-0000-4000-8000-000000000506', '00000000-0000-4000-8000-000000000502',
        '00000000-0000-4000-8000-000000000503', '2026-01-01', 1200.00, 5, '2026-01-01')
ON CONFLICT DO NOTHING;

-- Positivo: caução depositada.
INSERT INTO caucao_movimentacoes (id, contrato_id, tipo, valor, data, criado_por)
VALUES ('00000000-0000-4000-8000-000000000507', '00000000-0000-4000-8000-000000000505',
        'DEPOSITO', 2000.00, '2025-01-01', '00000000-0000-4000-8000-000000000501')
ON CONFLICT DO NOTHING;

-- Negativos de contrato.
DO $$
BEGIN
  BEGIN
    INSERT INTO contratos (imovel_id, inquilino_id, inicio, valor_aluguel, dia_vencimento,
                           acompanhar_pagamento_desde)
    VALUES ('00000000-0000-4000-8000-000000000502', '00000000-0000-4000-8000-000000000503',
            '2025-06-01', 900.00, 10, '2025-06-01');
    RAISE EXCEPTION 'aceitou contrato sobreposto no mesmo imóvel';
  EXCEPTION WHEN exclusion_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO contratos (imovel_id, inquilino_id, inicio, valor_aluguel, dia_vencimento,
                           acompanhar_pagamento_desde)
    VALUES ('00000000-0000-4000-8000-000000000502', '00000000-0000-4000-8000-000000000503',
            '2027-01-01', 0, 10, '2027-01-01');
    RAISE EXCEPTION 'aceitou valor de aluguel zero';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO contratos (imovel_id, inquilino_id, inicio, valor_aluguel, dia_vencimento,
                           acompanhar_pagamento_desde)
    VALUES ('00000000-0000-4000-8000-000000000502', '00000000-0000-4000-8000-000000000503',
            '2027-01-01', 900.00, 0, '2027-01-01');
    RAISE EXCEPTION 'aceitou dia de vencimento 0';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO contratos (imovel_id, inquilino_id, inicio, valor_aluguel, dia_vencimento,
                           acompanhar_pagamento_desde)
    VALUES ('00000000-0000-4000-8000-000000000502', '00000000-0000-4000-8000-000000000503',
            '2027-01-01', 900.00, 32, '2027-01-01');
    RAISE EXCEPTION 'aceitou dia de vencimento 32';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO contratos (imovel_id, inquilino_id, inicio, fim, valor_aluguel, dia_vencimento,
                           acompanhar_pagamento_desde)
    VALUES ('00000000-0000-4000-8000-000000000502', '00000000-0000-4000-8000-000000000503',
            '2027-01-10', '2027-01-01', 900.00, 10, '2027-01-10');
    RAISE EXCEPTION 'aceitou fim anterior ao início';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO contratos (imovel_id, inquilino_id, inicio, valor_aluguel, dia_vencimento,
                           acompanhar_pagamento_desde)
    VALUES ('00000000-0000-4000-8000-000000000502', '00000000-0000-4000-8000-000000000503',
            '2027-01-01', 900.00, 10, '2026-12-01');
    RAISE EXCEPTION 'aceitou acompanhar_pagamento_desde anterior ao início';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO contratos (imovel_id, inquilino_id, inicio, valor_aluguel, dia_vencimento,
                           acompanhar_pagamento_desde, possui_acao_judicial)
    VALUES ('00000000-0000-4000-8000-000000000502', '00000000-0000-4000-8000-000000000503',
            '2027-01-01', 900.00, 10, '2027-01-01', true);
    RAISE EXCEPTION 'aceitou ação judicial sem descrição';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO caucao_movimentacoes (contrato_id, tipo, valor, data, criado_por)
    VALUES ('00000000-0000-4000-8000-000000000505', 'DEPOSITO', 0, '2025-01-01',
            '00000000-0000-4000-8000-000000000501');
    RAISE EXCEPTION 'aceitou caução com valor zero';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
END $$;
