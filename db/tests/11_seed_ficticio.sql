-- T12: integridade e volume do seed fictício.

DO $$
DECLARE
  admin uuid := '34000000-0000-4000-8000-000000000001';
  n integer;
  r record;
BEGIN
  SELECT count(*) INTO n FROM imoveis
   WHERE id::text LIKE '30000000-0000-4000-8000-%';
  IF n <> 124 THEN RAISE EXCEPTION 'seed deveria ter 124 imóveis, veio %', n; END IF;

  SELECT count(*) INTO n FROM inquilinos
   WHERE id::text LIKE '31000000-0000-4000-8000-%';
  IF n <> 120 THEN RAISE EXCEPTION 'seed deveria ter 120 inquilinos, veio %', n; END IF;

  SELECT count(*) INTO n FROM fiadores
   WHERE id::text LIKE '32000000-0000-4000-8000-%';
  IF n <> 60 THEN RAISE EXCEPTION 'seed deveria ter 60 fiadores, veio %', n; END IF;

  SELECT count(*) INTO n FROM contratos
   WHERE id::text LIKE '33000000-0000-4000-8000-%';
  IF n <> 110 THEN RAISE EXCEPTION 'seed deveria ter 110 contratos, veio %', n; END IF;

  SELECT count(*) INTO n FROM movimentacoes WHERE responsavel_id = admin;
  IF n <> 3040 THEN RAISE EXCEPTION 'seed deveria ter 3040 movimentações, veio %', n; END IF;

  SELECT count(*) INTO n FROM movimentacoes
   WHERE responsavel_id = admin AND tipo = 'ESTORNO';
  IF n <> 30 THEN RAISE EXCEPTION 'seed deveria ter 30 estornos, veio %', n; END IF;

  SELECT count(*) INTO n FROM movimentacoes
   WHERE responsavel_id = admin AND tipo = 'SAIDA';
  IF n <> 860 THEN RAISE EXCEPTION 'seed deveria ter 860 saídas, veio %', n; END IF;

  SELECT count(*) INTO n FROM movimentacoes
   WHERE responsavel_id = admin AND tipo = 'ENTRADA';
  IF n <> 2150 THEN RAISE EXCEPTION 'seed deveria ter 2150 entradas, veio %', n; END IF;

  SELECT count(*) INTO n FROM documentos WHERE enviado_por = admin;
  IF n <> 860 THEN RAISE EXCEPTION 'seed deveria ter 860 documentos, veio %', n; END IF;
END $$;

-- Toda saída do seed tem comprovante (R04).
DO $$
DECLARE
  n integer;
BEGIN
  SELECT count(*) INTO n
    FROM movimentacoes m
   WHERE m.responsavel_id = '34000000-0000-4000-8000-000000000001'
     AND m.tipo = 'SAIDA'
     AND NOT EXISTS (SELECT 1 FROM documentos d WHERE d.movimentacao_id = m.id);
  IF n <> 0 THEN RAISE EXCEPTION '% saídas do seed sem comprovante', n; END IF;
END $$;

-- Estornos com mesmo valor e com original válido.
DO $$
DECLARE
  n integer;
BEGIN
  SELECT count(*) INTO n
    FROM movimentacoes e
    JOIN movimentacoes o ON o.id = e.movimentacao_original_id
   WHERE e.responsavel_id = '34000000-0000-4000-8000-000000000001'
     AND e.tipo = 'ESTORNO'
     AND (e.valor <> o.valor OR o.tipo = 'ESTORNO');
  IF n <> 0 THEN RAISE EXCEPTION '% estornos com valor/origem inválidos', n; END IF;
END $$;

-- Caução: 110 depósitos, 10 abatimentos e saldo nunca negativo.
DO $$
DECLARE
  n integer;
BEGIN
  SELECT count(*) INTO n FROM caucao_movimentacoes
   WHERE id::text LIKE '37000000-0000-4000-8000-%';
  IF n <> 110 THEN RAISE EXCEPTION 'seed deveria ter 110 depósitos de caução, veio %', n; END IF;

  SELECT count(*) INTO n FROM caucao_movimentacoes
   WHERE id::text LIKE '37100000-0000-4000-8000-%';
  IF n <> 10 THEN RAISE EXCEPTION 'seed deveria ter 10 abatimentos de caução, veio %', n; END IF;

  SELECT count(*) INTO n FROM vw_saldo_caucao s
   WHERE s.contrato_id::text LIKE '33000000-0000-4000-8000-%'
     AND s.saldo < 0;
  IF n <> 0 THEN RAISE EXCEPTION '% contratos do seed com saldo de caução negativo', n; END IF;
END $$;

-- Atrasos e a vencer aparecem nas views com o volume do seed.
DO $$
DECLARE
  n integer;
  home record;
BEGIN
  SELECT count(*) INTO n FROM vw_contratos_atrasados
   WHERE contrato_id::text LIKE '33000000-0000-4000-8000-%';
  IF n < 1 THEN RAISE EXCEPTION 'seed deveria produzir contratos atrasados'; END IF;

  SELECT count(*) INTO n FROM vw_contratos_a_vencer
   WHERE contrato_id::text LIKE '33000000-0000-4000-8000-%';
  IF n < 1 THEN RAISE EXCEPTION 'seed deveria produzir contratos a vencer'; END IF;

  SELECT * INTO home FROM vw_home_indicadores;
  IF home.imoveis_cadastrados < 124 THEN
    RAISE EXCEPTION 'home deveria contar ao menos 124 imóveis: %', row_to_json(home);
  END IF;
  IF home.contratos_atrasados < 1 THEN
    RAISE EXCEPTION 'home deveria contar contrato atrasado: %', row_to_json(home);
  END IF;
END $$;
