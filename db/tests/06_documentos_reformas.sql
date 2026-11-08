-- T08: documentos (vínculos, tamanho, MIME, classificação) e reformas.

INSERT INTO usuarios (id, nome, email, senha_hash, perfil, status, aprovado_por, aprovado_em)
VALUES ('00000000-0000-4000-8000-000000000701', 'T08 Operador', 't08.operador@example.test',
        '$2a$10$abcdefghijklmnopqrstuvABCDEFGHIJKLMNOPQRSTUVWXYZ012345', 'ADMINISTRADOR', 'ATIVO',
        '00000000-0000-4000-8000-000000000701', now())
ON CONFLICT DO NOTHING;

INSERT INTO imoveis (id, logradouro, numero, bairro, cidade, tipo)
VALUES ('00000000-0000-4000-8000-000000000702', 'Rua dos Documentos', '70', 'Centro', 'Aracaju', 'CASA')
ON CONFLICT DO NOTHING;

INSERT INTO inquilinos (id, nome, documento)
VALUES ('00000000-0000-4000-8000-000000000703', 'T08 Inquilino', '90000000008')
ON CONFLICT DO NOTHING;

INSERT INTO contratos (id, imovel_id, inquilino_id, inicio, valor_aluguel, dia_vencimento,
                       acompanhar_pagamento_desde)
VALUES ('00000000-0000-4000-8000-000000000704', '00000000-0000-4000-8000-000000000702',
        '00000000-0000-4000-8000-000000000703', '2025-01-01', 1000.00, 10, '2025-01-01')
ON CONFLICT DO NOTHING;

INSERT INTO movimentacoes (id, tipo, categoria, valor, data_movimentacao, descricao,
                           forma_pagamento, responsavel_id, origem_descricao)
VALUES ('00000000-0000-4000-8000-000000000705', 'ENTRADA', 'OUTRAS_ENTRADAS', 50.00,
        '2025-03-01', 'Entrada avulsa', 'PIX', '00000000-0000-4000-8000-000000000701',
        'Origem avulsa')
ON CONFLICT DO NOTHING;

-- Positivo: documento pendente, sem tipo/data, vinculado a imóvel.
INSERT INTO documentos (id, nome_arquivo, pendente_revisao, caminho_armazenamento, tipo_mime,
                        tamanho_bytes, imovel_id, enviado_por)
VALUES ('00000000-0000-4000-8000-000000000711', 'contrato.pdf', true, 'supabase://docs/contrato.pdf',
        'application/pdf', 1024, '00000000-0000-4000-8000-000000000702',
        '00000000-0000-4000-8000-000000000701')
ON CONFLICT DO NOTHING;

-- Positivo: documento classificado, vinculado a movimentação.
INSERT INTO documentos (id, nome_arquivo, tipo, data_documento, pendente_revisao,
                        caminho_armazenamento, tipo_mime, tamanho_bytes, movimentacao_id, enviado_por)
VALUES ('00000000-0000-4000-8000-000000000712', 'comprovante.jpg', 'COMPROVANTE', '2025-03-01',
        false, 'supabase://docs/comprovante.jpg', 'image/jpeg', 2048,
        '00000000-0000-4000-8000-000000000705', '00000000-0000-4000-8000-000000000701')
ON CONFLICT DO NOTHING;

-- Positivo: vínculo com recibo de caução no livro de caução.
INSERT INTO caucao_movimentacoes (id, contrato_id, tipo, valor, data, documento_id, criado_por)
VALUES ('00000000-0000-4000-8000-000000000713', '00000000-0000-4000-8000-000000000704',
        'DEPOSITO', 1000.00, '2025-01-01', '00000000-0000-4000-8000-000000000711',
        '00000000-0000-4000-8000-000000000701')
ON CONFLICT DO NOTHING;

-- Positivo: reforma.
INSERT INTO reformas (id, imovel_id, data_reforma, descricao, valor, criado_por)
VALUES ('00000000-0000-4000-8000-000000000714', '00000000-0000-4000-8000-000000000702',
        '2025-02-01', 'Pintura da fachada', 500.00, '00000000-0000-4000-8000-000000000701')
ON CONFLICT DO NOTHING;

-- Negativos de documento.
DO $$
BEGIN
  BEGIN
    INSERT INTO documentos (nome_arquivo, caminho_armazenamento, tipo_mime, tamanho_bytes, enviado_por)
    VALUES ('sem_vinculo.pdf', 'supabase://docs/x.pdf', 'application/pdf', 10,
            '00000000-0000-4000-8000-000000000701');
    RAISE EXCEPTION 'aceitou documento sem nenhum vínculo';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO documentos (nome_arquivo, caminho_armazenamento, tipo_mime, tamanho_bytes,
                            imovel_id, enviado_por)
    VALUES ('grande.pdf', 'supabase://docs/g.pdf', 'application/pdf', 10485761,
            '00000000-0000-4000-8000-000000000702', '00000000-0000-4000-8000-000000000701');
    RAISE EXCEPTION 'aceitou documento acima de 10 MB';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO documentos (nome_arquivo, caminho_armazenamento, tipo_mime, tamanho_bytes,
                            imovel_id, enviado_por)
    VALUES ('texto.txt', 'supabase://docs/t.txt', 'text/plain', 10,
            '00000000-0000-4000-8000-000000000702', '00000000-0000-4000-8000-000000000701');
    RAISE EXCEPTION 'aceitou MIME fora de PDF/JPG/PNG';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO documentos (nome_arquivo, tipo, pendente_revisao, caminho_armazenamento, tipo_mime,
                            tamanho_bytes, imovel_id, enviado_por)
    VALUES ('classificado.pdf', 'CONTRATO', false, 'supabase://docs/c.pdf', 'application/pdf', 10,
            '00000000-0000-4000-8000-000000000702', '00000000-0000-4000-8000-000000000701');
    RAISE EXCEPTION 'aceitou classificado sem data_documento';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO documentos (nome_arquivo, data_documento, pendente_revisao, caminho_armazenamento,
                            tipo_mime, tamanho_bytes, imovel_id, enviado_por)
    VALUES ('classificado.pdf', '2025-01-01', false, 'supabase://docs/c.pdf', 'application/pdf', 10,
            '00000000-0000-4000-8000-000000000702', '00000000-0000-4000-8000-000000000701');
    RAISE EXCEPTION 'aceitou classificado sem tipo';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
END $$;

-- Negativos de reforma.
DO $$
BEGIN
  BEGIN
    INSERT INTO reformas (imovel_id, data_reforma, descricao, valor)
    VALUES ('00000000-0000-4000-8000-000000000702', '2025-02-01', 'X', 0);
    RAISE EXCEPTION 'aceitou reforma com valor zero';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO reformas (imovel_id, data_reforma, descricao, valor)
    VALUES ('00000000-0000-4000-8000-000000000702', '2025-02-01', '   ', 10);
    RAISE EXCEPTION 'aceitou reforma com descrição vazia';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
END $$;
