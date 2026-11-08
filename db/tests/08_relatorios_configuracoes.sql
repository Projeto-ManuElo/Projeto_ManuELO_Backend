-- T09: relatórios insert-only, composição e configurações.

INSERT INTO usuarios (id, nome, email, senha_hash, perfil, status, aprovado_por, aprovado_em)
VALUES ('00000000-0000-4000-8000-000000000901', 'T09 Admin', 't09.admin@example.test',
        '$2a$10$abcdefghijklmnopqrstuvABCDEFGHIJKLMNOPQRSTUVWXYZ012345', 'ADMINISTRADOR', 'ATIVO',
        '00000000-0000-4000-8000-000000000901', now())
ON CONFLICT DO NOTHING;

INSERT INTO imoveis (id, logradouro, numero, bairro, cidade, tipo)
VALUES ('00000000-0000-4000-8000-000000000902', 'Rua do Relatório', '90', 'Centro', 'Aracaju', 'CASA')
ON CONFLICT DO NOTHING;

INSERT INTO inquilinos (id, nome, documento)
VALUES ('00000000-0000-4000-8000-000000000903', 'T09 Inquilino', '90000000010')
ON CONFLICT DO NOTHING;

INSERT INTO contratos (id, imovel_id, inquilino_id, inicio, fim, valor_aluguel, dia_vencimento,
                       acompanhar_pagamento_desde)
VALUES ('00000000-0000-4000-8000-000000000904', '00000000-0000-4000-8000-000000000902',
        '00000000-0000-4000-8000-000000000903', '2025-01-01', '2025-12-31', 1000.00, 10,
        '2025-01-01')
ON CONFLICT DO NOTHING;

INSERT INTO movimentacoes (id, tipo, categoria, valor, data_movimentacao, descricao,
                           forma_pagamento, imovel_id, responsavel_id, contrato_id, competencia)
VALUES ('00000000-0000-4000-8000-000000000905', 'ENTRADA', 'ALUGUEL', 1000.00, '2025-03-10',
        'Aluguel março', 'PIX', '00000000-0000-4000-8000-000000000902',
        '00000000-0000-4000-8000-000000000901', '00000000-0000-4000-8000-000000000904',
        '2025-03-01')
ON CONFLICT DO NOTHING;

-- Positivo: relatório de movimentação com composição.
INSERT INTO relatorios (id, nome, tipo, data_referencia, periodo_inicio, periodo_fim,
                        total_entradas, total_saidas, saldo, qtd_registros, caminho_arquivo,
                        gerado_por)
VALUES ('00000000-0000-4000-8000-000000000911', 'Movimentação março/2025', 'MOVIMENTACAO',
        '2025-03-31', '2025-03-01', '2025-03-31', 1000.00, 0.00, 1000.00, 1,
        'supabase://relatorios/mov-2025-03.pdf', '00000000-0000-4000-8000-000000000901')
ON CONFLICT DO NOTHING;

INSERT INTO relatorio_movimentacoes (relatorio_id, movimentacao_id, ordem)
VALUES ('00000000-0000-4000-8000-000000000911', '00000000-0000-4000-8000-000000000905', 1)
ON CONFLICT DO NOTHING;

-- Positivo: relatório de contratos a vencer com composição.
INSERT INTO relatorios (id, nome, tipo, data_referencia, janela_dias, qtd_registros,
                        caminho_arquivo, gerado_por)
VALUES ('00000000-0000-4000-8000-000000000912', 'Contratos a vencer', 'CONTRATOS_A_VENCER',
        '2025-12-20', 7, 1, 'supabase://relatorios/vencer.pdf',
        '00000000-0000-4000-8000-000000000901')
ON CONFLICT DO NOTHING;

INSERT INTO relatorio_contratos (relatorio_id, contrato_id, ordem, situacao, dias)
VALUES ('00000000-0000-4000-8000-000000000912', '00000000-0000-4000-8000-000000000904', 1,
        'A_VENCER', 11)
ON CONFLICT DO NOTHING;

-- Negativos de relatório.
DO $$
BEGIN
  BEGIN
    INSERT INTO relatorios (nome, tipo, data_referencia, qtd_registros, caminho_arquivo, gerado_por)
    VALUES ('Sem período', 'MOVIMENTACAO', '2025-03-31', 0, 'x.pdf',
            '00000000-0000-4000-8000-000000000901');
    RAISE EXCEPTION 'aceitou relatório de movimentação sem período/totais';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO relatorios (nome, tipo, data_referencia, periodo_inicio, periodo_fim,
                            total_entradas, total_saidas, saldo, qtd_registros, caminho_arquivo,
                            gerado_por)
    VALUES ('Período invertido', 'MOVIMENTACAO', '2025-03-31', '2025-03-31', '2025-03-01',
            0, 0, 0, 0, 'x.pdf', '00000000-0000-4000-8000-000000000901');
    RAISE EXCEPTION 'aceitou período invertido';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO relatorios (nome, tipo, data_referencia, periodo_inicio, periodo_fim,
                            total_entradas, total_saidas, saldo, qtd_registros, caminho_arquivo,
                            gerado_por)
    VALUES ('Totais em contrato', 'CONTRATOS_EXISTENTES', '2025-03-31', '2025-03-01', '2025-03-31',
            0, 0, 0, 0, 'x.pdf', '00000000-0000-4000-8000-000000000901');
    RAISE EXCEPTION 'aceitou totais em relatório de contratos';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO relatorios (nome, tipo, data_referencia, qtd_registros, caminho_arquivo, gerado_por)
    VALUES ('A vencer sem janela', 'CONTRATOS_A_VENCER', '2025-12-20', 0, 'x.pdf',
            '00000000-0000-4000-8000-000000000901');
    RAISE EXCEPTION 'aceitou contratos a vencer sem janela';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO relatorios (nome, tipo, data_referencia, janela_dias, qtd_registros,
                            caminho_arquivo, gerado_por)
    VALUES ('A vencer janela zero', 'CONTRATOS_A_VENCER', '2025-12-20', 0, 0, 'x.pdf',
            '00000000-0000-4000-8000-000000000901');
    RAISE EXCEPTION 'aceitou janela de zero dias';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO relatorios (nome, tipo, data_referencia, periodo_inicio, periodo_fim,
                            total_entradas, total_saidas, saldo, filtro_tipo_movimentacao,
                            qtd_registros, caminho_arquivo, gerado_por)
    VALUES ('Filtro em contrato', 'CONTRATOS_ATRASADOS', '2025-03-31', '2025-03-01', '2025-03-31',
            0, 0, 0, 'ENTRADA', 0, 'x.pdf', '00000000-0000-4000-8000-000000000901');
    RAISE EXCEPTION 'aceitou filtro de tipo em relatório de contratos';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO relatorio_movimentacoes (relatorio_id, movimentacao_id, ordem)
    VALUES ('00000000-0000-4000-8000-000000000911', '00000000-0000-4000-8000-000000000905', 2);
    RAISE EXCEPTION 'aceitou movimentação repetida na composição';
  EXCEPTION WHEN unique_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO relatorio_contratos (relatorio_id, contrato_id, ordem, situacao, dias)
    VALUES ('00000000-0000-4000-8000-000000000912', '00000000-0000-4000-8000-000000000904', 2,
            'ATRASADO', NULL);
    RAISE EXCEPTION 'aceitou atrasado sem dias';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    UPDATE relatorios SET nome = 'alterado'
     WHERE id = '00000000-0000-4000-8000-000000000911';
    RAISE EXCEPTION 'aceitou UPDATE em relatório';
  EXCEPTION WHEN SQLSTATE '55000' THEN NULL;
  END;

  BEGIN
    DELETE FROM relatorios WHERE id = '00000000-0000-4000-8000-000000000911';
    RAISE EXCEPTION 'aceitou DELETE em relatório';
  EXCEPTION WHEN SQLSTATE '55000' THEN NULL;
  END;

  BEGIN
    UPDATE relatorio_movimentacoes SET ordem = 2
     WHERE relatorio_id = '00000000-0000-4000-8000-000000000911';
    RAISE EXCEPTION 'aceitou UPDATE na composição do relatório';
  EXCEPTION WHEN SQLSTATE '55000' THEN NULL;
  END;
END $$;

-- Configurações: seeds e auditoria.
DO $$
BEGIN
  IF (SELECT valor FROM configuracoes WHERE chave = 'dias_alerta_vencimento') <> '7' THEN
    RAISE EXCEPTION 'seed dias_alerta_vencimento ausente ou incorreto';
  END IF;
  IF (SELECT valor FROM configuracoes WHERE chave = 'dias_atraso_critico') <> '7' THEN
    RAISE EXCEPTION 'seed dias_atraso_critico ausente ou incorreto';
  END IF;
END $$;

UPDATE configuracoes SET valor = '10' WHERE chave = 'dias_alerta_vencimento';
DO $$
BEGIN
  IF (SELECT valor FROM configuracoes WHERE chave = 'dias_alerta_vencimento') <> '10' THEN
    RAISE EXCEPTION 'update de configuração não persistiu';
  END IF;
  IF (SELECT atualizado_em FROM configuracoes WHERE chave = 'dias_alerta_vencimento') IS NULL THEN
    RAISE EXCEPTION 'configuração sem atualizado_em';
  END IF;
  BEGIN
    UPDATE configuracoes SET valor = '   ' WHERE chave = 'dias_atraso_critico';
    RAISE EXCEPTION 'aceitou valor de configuração vazio';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
END $$;

-- Restaura o valor para não influenciar os testes de views.
UPDATE configuracoes SET valor = '7' WHERE chave = 'dias_alerta_vencimento';
