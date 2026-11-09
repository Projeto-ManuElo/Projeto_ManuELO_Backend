-- Seed fictício do ManuElo. Nenhum dado real.
-- Prefixos de UUID reservados: 30.. imóveis, 31.. inquilinos, 32.. fiadores,
-- 33.. contratos, 34.. usuários, 35.. movimentações, 36.. documentos, 37.. caução.
-- Gera 124 imóveis, 110 contratos e 3040 movimentações, com atrasos e estornos.

-- Usuários fictícios (hash inválido de propósito; não é credencial real).
INSERT INTO usuarios (id, nome, email, senha_hash, perfil, status, aprovado_por, aprovado_em)
VALUES
  ('34000000-0000-4000-8000-000000000001', 'Administrador Fictício', 'seed.admin@example.test',
   '$2a$10$SEEDNAOECREDENCIALREAL000000000000000000000000000000000000', 'ADMINISTRADOR',
   'ATIVO', '34000000-0000-4000-8000-000000000001', now()),
  ('34000000-0000-4000-8000-000000000002', 'Colaborador Fictício', 'seed.colaborador@example.test',
   '$2a$10$SEEDNAOECREDENCIALREAL000000000000000000000000000000000000', 'COLABORADOR',
   'ATIVO', '34000000-0000-4000-8000-000000000001', now())
ON CONFLICT DO NOTHING;

-- 124 imóveis.
INSERT INTO imoveis (id, logradouro, numero, complemento, bairro, cidade, uf, cep, tipo, status, criado_por)
SELECT ('30000000-0000-4000-8000-' || lpad(n::text, 12, '0'))::uuid,
       'Rua Fictícia ' || n,
       n::text,
       CASE WHEN n % 5 = 0 THEN 'Bloco ' || ((n % 9) + 1) END,
       (ARRAY['Centro', 'Atalaia', 'Farol', 'Coroa do Meio', 'Jardins'])[1 + (n % 5)],
       'Aracaju',
       'SE',
       CASE WHEN n % 3 = 0 THEN lpad((49000000 + n)::text, 8, '0') END,
       (ARRAY['CASA', 'APARTAMENTO', 'COMERCIAL', 'TERRENO']::tipo_imovel[])[1 + (n % 4)],
       CASE
         WHEN n <= 110 THEN 'ALUGADO'::status_imovel
         WHEN n <= 119 THEN 'VAGO'::status_imovel
         ELSE 'EM_MANUTENCAO'::status_imovel
       END,
       '34000000-0000-4000-8000-000000000001'
  FROM generate_series(1, 124) AS n
ON CONFLICT DO NOTHING;

-- 120 inquilinos.
INSERT INTO inquilinos (id, nome, documento, email, criado_por)
SELECT ('31000000-0000-4000-8000-' || lpad(n::text, 12, '0'))::uuid,
       'Inquilino Fictício ' || n,
       lpad((80000000000 + n)::text, 11, '0'),
       'inquilino' || n || '@example.test',
       '34000000-0000-4000-8000-000000000001'
  FROM generate_series(1, 120) AS n
ON CONFLICT DO NOTHING;

-- 60 fiadores.
INSERT INTO fiadores (id, nome, documento, email, telefone, criado_por)
SELECT ('32000000-0000-4000-8000-' || lpad(n::text, 12, '0'))::uuid,
       'Fiador Fictício ' || n,
       lpad((82000000000 + n)::text, 11, '0'),
       'fiador' || n || '@example.test',
       lpad((7990000000 + n)::text, 10, '0'),
       '34000000-0000-4000-8000-000000000001'
  FROM generate_series(1, 60) AS n
ON CONFLICT DO NOTHING;

-- 110 contratos (um por imóvel; os 10 últimos são os atrasados/vencidos).
INSERT INTO contratos (id, imovel_id, inquilino_id, fiador_id, inicio, fim, valor_aluguel,
                       dia_vencimento, acompanhar_pagamento_desde, possui_acao_judicial,
                       acao_judicial_descricao, criado_por)
SELECT ('33000000-0000-4000-8000-' || lpad(n::text, 12, '0'))::uuid,
       ('30000000-0000-4000-8000-' || lpad(n::text, 12, '0'))::uuid,
       ('31000000-0000-4000-8000-' || lpad(((n - 1) % 120 + 1)::text, 12, '0'))::uuid,
       CASE WHEN n % 2 = 0
            THEN ('32000000-0000-4000-8000-' || lpad(((n - 1) % 60 + 1)::text, 12, '0'))::uuid
       END,
       (date_trunc('month', current_date) - interval '18 months')::date,
       CASE
         WHEN n BETWEEN 101 AND 105 THEN (current_date + interval '3 days')::date
         WHEN n BETWEEN 106 AND 110 THEN (current_date - interval '5 days')::date
       END,
       (800 + (n % 7) * 100)::numeric(14, 2),
       (5 + (n % 20))::smallint,
       (date_trunc('month', current_date) - interval '17 months')::date,
       (n % 37 = 0),
       CASE WHEN n % 37 = 0 THEN 'Ação judicial fictícia do contrato ' || n END,
       '34000000-0000-4000-8000-000000000001'
  FROM generate_series(1, 110) AS n
ON CONFLICT DO NOTHING;

-- 1620 aluguéis pagos (contratos 1..90, 18 competências).
INSERT INTO movimentacoes (id, tipo, categoria, valor, data_movimentacao, descricao,
                           forma_pagamento, imovel_id, responsavel_id, contrato_id, competencia)
SELECT ('35000000-0000-4000-8000-' || lpad((((n - 1) * 18) + m + 1)::text, 12, '0'))::uuid,
       'ENTRADA'::tipo_movimentacao,
       'ALUGUEL'::categoria_movimentacao,
       c.valor_aluguel,
       LEAST(
         ((date_trunc('month', current_date) - (m || ' months')::interval) + interval '4 days')::date,
         current_date
       ),
       'Aluguel ' || to_char(date_trunc('month', current_date) - (m || ' months')::interval, 'MM/YYYY'),
       (ARRAY['PIX', 'BOLETO', 'TRANSFERENCIA', 'DINHEIRO']::forma_pagamento[])[1 + ((n + m) % 4)],
       c.imovel_id,
       '34000000-0000-4000-8000-000000000001',
       c.id,
       (date_trunc('month', current_date) - (m || ' months')::interval)::date
  FROM generate_series(1, 90) AS n
 CROSS JOIN generate_series(0, 17) AS m
  JOIN contratos c
    ON c.id = ('33000000-0000-4000-8000-' || lpad(n::text, 12, '0'))::uuid
ON CONFLICT DO NOTHING;

-- 120 aluguéis de contratos parcialmente pagos (91..100, competências 6..17).
INSERT INTO movimentacoes (id, tipo, categoria, valor, data_movimentacao, descricao,
                           forma_pagamento, imovel_id, responsavel_id, contrato_id, competencia)
SELECT ('35100000-0000-4000-8000-' || lpad((((n - 91) * 12) + (m - 6) + 1)::text, 12, '0'))::uuid,
       'ENTRADA'::tipo_movimentacao,
       'ALUGUEL'::categoria_movimentacao,
       c.valor_aluguel,
       LEAST(
         ((date_trunc('month', current_date) - (m || ' months')::interval) + interval '4 days')::date,
         current_date
       ),
       'Aluguel ' || to_char(date_trunc('month', current_date) - (m || ' months')::interval, 'MM/YYYY'),
       (ARRAY['PIX', 'BOLETO', 'TRANSFERENCIA', 'DINHEIRO']::forma_pagamento[])[1 + ((n + m) % 4)],
       c.imovel_id,
       '34000000-0000-4000-8000-000000000001',
       c.id,
       (date_trunc('month', current_date) - (m || ' months')::interval)::date
  FROM generate_series(91, 100) AS n
 CROSS JOIN generate_series(6, 17) AS m
  JOIN contratos c
    ON c.id = ('33000000-0000-4000-8000-' || lpad(n::text, 12, '0'))::uuid
ON CONFLICT DO NOTHING;

-- 400 outras entradas.
INSERT INTO movimentacoes (id, tipo, categoria, valor, data_movimentacao, descricao,
                           forma_pagamento, responsavel_id, origem_descricao)
SELECT ('35200000-0000-4000-8000-' || lpad(n::text, 12, '0'))::uuid,
       'ENTRADA'::tipo_movimentacao,
       'OUTRAS_ENTRADAS'::categoria_movimentacao,
       (50 + (n % 500))::numeric(14, 2),
       current_date - (n % 540),
       'Outras entradas fictícias ' || n,
       (ARRAY['PIX', 'BOLETO', 'TRANSFERENCIA', 'DINHEIRO']::forma_pagamento[])[1 + (n % 4)],
       '34000000-0000-4000-8000-000000000001',
       'Origem fictícia ' || n
  FROM generate_series(1, 400) AS n
ON CONFLICT DO NOTHING;

-- 860 saídas.
INSERT INTO movimentacoes (id, tipo, categoria, valor, data_movimentacao, descricao,
                           forma_pagamento, imovel_id, responsavel_id, favorecido)
SELECT ('35300000-0000-4000-8000-' || lpad(n::text, 12, '0'))::uuid,
       'SAIDA'::tipo_movimentacao,
       (ARRAY['MANUTENCAO', 'TRIBUTOS', 'ADMINISTRATIVO', 'OUTRAS_SAIDAS']::categoria_movimentacao[])[1 + (n % 4)],
       (100 + (n % 900))::numeric(14, 2),
       current_date - (n % 540),
       'Despesa fictícia ' || n,
       (ARRAY['PIX', 'BOLETO', 'TRANSFERENCIA', 'DINHEIRO']::forma_pagamento[])[1 + (n % 4)],
       ('30000000-0000-4000-8000-' || lpad(((n - 1) % 124 + 1)::text, 12, '0'))::uuid,
       '34000000-0000-4000-8000-000000000001',
       'Fornecedor Fictício ' || n
  FROM generate_series(1, 860) AS n
ON CONFLICT DO NOTHING;

-- Comprovante para cada saída (R04).
INSERT INTO documentos (id, nome_arquivo, caminho_armazenamento, tipo_mime, tamanho_bytes,
                        movimentacao_id, enviado_por, pendente_revisao)
SELECT ('36000000-0000-4000-8000-' || lpad(n::text, 12, '0'))::uuid,
       'comprovante-' || n || '.pdf',
       'supabase://seed/comprovante-' || n || '.pdf',
       (ARRAY['application/pdf', 'image/jpeg', 'image/png'])[1 + (n % 3)],
       1000 + n,
       ('35300000-0000-4000-8000-' || lpad(n::text, 12, '0'))::uuid,
       '34000000-0000-4000-8000-000000000001',
       true
  FROM generate_series(1, 860) AS n
ON CONFLICT DO NOTHING;

-- Caução depositada para todos os 110 contratos.
INSERT INTO caucao_movimentacoes (id, contrato_id, tipo, valor, data, criado_por)
SELECT ('37000000-0000-4000-8000-' || lpad(n::text, 12, '0'))::uuid,
       c.id,
       'DEPOSITO'::tipo_caucao,
       (c.valor_aluguel * 2)::numeric(14, 2),
       c.inicio,
       '34000000-0000-4000-8000-000000000001'
  FROM generate_series(1, 110) AS n
  JOIN contratos c
    ON c.id = ('33000000-0000-4000-8000-' || lpad(n::text, 12, '0'))::uuid
ON CONFLICT DO NOTHING;

-- 10 abatimentos de caução (entrada ALUGUEL com forma CAUCAO + livro de caução).
INSERT INTO movimentacoes (id, tipo, categoria, valor, data_movimentacao, descricao,
                           forma_pagamento, imovel_id, responsavel_id, contrato_id, competencia)
SELECT ('35600000-0000-4000-8000-' || lpad(n::text, 12, '0'))::uuid,
       'ENTRADA'::tipo_movimentacao,
       'ALUGUEL'::categoria_movimentacao,
       200.00::numeric(14, 2),
       current_date,
       'Aluguel pago com caução (fictício)',
       'CAUCAO'::forma_pagamento,
       c.imovel_id,
       '34000000-0000-4000-8000-000000000001',
       c.id,
       date_trunc('month', current_date)::date
  FROM generate_series(1, 10) AS n
  JOIN contratos c
    ON c.id = ('33000000-0000-4000-8000-' || lpad(n::text, 12, '0'))::uuid
ON CONFLICT DO NOTHING;

INSERT INTO caucao_movimentacoes (id, contrato_id, tipo, valor, data, movimentacao_id, criado_por)
SELECT ('37100000-0000-4000-8000-' || lpad(n::text, 12, '0'))::uuid,
       ('33000000-0000-4000-8000-' || lpad(n::text, 12, '0'))::uuid,
       'ABATIMENTO_ALUGUEL'::tipo_caucao,
       200.00::numeric(14, 2),
       current_date,
       ('35600000-0000-4000-8000-' || lpad(n::text, 12, '0'))::uuid,
       '34000000-0000-4000-8000-000000000001'
  FROM generate_series(1, 10) AS n
ON CONFLICT DO NOTHING;

-- 20 estornos de aluguel (administrador, mesmo valor).
WITH alvo AS (
  SELECT id, valor, data_movimentacao,
         row_number() OVER (ORDER BY id) AS rn
    FROM movimentacoes
   WHERE tipo = 'ENTRADA' AND categoria = 'ALUGUEL'
   ORDER BY id
   LIMIT 20
)
INSERT INTO movimentacoes (id, tipo, valor, data_movimentacao, descricao, responsavel_id,
                           movimentacao_original_id, justificativa)
SELECT ('35400000-0000-4000-8000-' || lpad(rn::text, 12, '0'))::uuid,
       'ESTORNO'::tipo_movimentacao,
       valor,
       data_movimentacao + 1,
       'Estorno fictício de aluguel ' || rn,
       '34000000-0000-4000-8000-000000000001',
       id,
       'Estorno de dados fictícios'
  FROM alvo
ON CONFLICT DO NOTHING;

-- 10 estornos de saída (administrador, mesmo valor).
WITH alvo AS (
  SELECT id, valor, data_movimentacao,
         row_number() OVER (ORDER BY id) AS rn
    FROM movimentacoes
   WHERE tipo = 'SAIDA'
   ORDER BY id
   LIMIT 10
)
INSERT INTO movimentacoes (id, tipo, valor, data_movimentacao, descricao, responsavel_id,
                           movimentacao_original_id, justificativa)
SELECT ('35500000-0000-4000-8000-' || lpad(rn::text, 12, '0'))::uuid,
       'ESTORNO'::tipo_movimentacao,
       valor,
       data_movimentacao + 1,
       'Estorno fictício de saída ' || rn,
       '34000000-0000-4000-8000-000000000001',
       id,
       'Estorno de dados fictícios'
  FROM alvo
ON CONFLICT DO NOTHING;
