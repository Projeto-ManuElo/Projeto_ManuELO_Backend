-- V011: desempenho das views com volume (T13).
-- Substitui fn_contrato_atraso por agregação única por competência e evita
-- recalcular vw_contratos_situacao três vezes em vw_home_indicadores.

CREATE OR REPLACE FUNCTION fn_contrato_atraso(
  p_contrato_id uuid,
  p_referencia date DEFAULT current_date
)
RETURNS TABLE (
  contrato_id            uuid,
  competencia_mais_antiga date,
  dias_atraso            integer,
  valor_em_atraso        numeric(14,2),
  qtd_competencias_abertas integer
)
LANGUAGE plpgsql
AS $$
#variable_conflict use_column
BEGIN
  IF p_referencia IS NULL THEN
    RAISE EXCEPTION 'data de referência obrigatória' USING ERRCODE = '23514';
  END IF;

  RETURN QUERY
  WITH c AS (
    SELECT * FROM contratos WHERE id = p_contrato_id
  ),
  meses AS (
    SELECT gs::date AS competencia
      FROM c
      CROSS JOIN LATERAL generate_series(
        date_trunc('month', c.acompanhar_pagamento_desde),
        date_trunc('month', p_referencia),
        interval '1 month'
      ) AS gs
  ),
  limites AS (
    SELECT min(competencia) AS inicio, max(competencia) AS fim FROM meses
  ),
  venc AS (
    SELECT m.competencia,
           (m.competencia
            + (LEAST(c.dia_vencimento::int,
                     EXTRACT(DAY FROM (m.competencia + interval '1 month - 1 day'))::int) - 1)
           )::date AS vencimento
      FROM meses m
      CROSS JOIN c
  ),
  pagos AS (
    SELECT mv.competencia, SUM(mv.valor) AS valor
      FROM movimentacoes mv
      CROSS JOIN limites l
     WHERE mv.tipo = 'ENTRADA'
       AND mv.categoria = 'ALUGUEL'
       AND mv.contrato_id = p_contrato_id
       AND mv.competencia BETWEEN l.inicio AND l.fim
     GROUP BY mv.competencia
    UNION ALL
    SELECT o.competencia, -SUM(e.valor) AS valor
      FROM movimentacoes e
      JOIN movimentacoes o ON o.id = e.movimentacao_original_id
      CROSS JOIN limites l
     WHERE e.tipo = 'ESTORNO'
       AND o.tipo = 'ENTRADA'
       AND o.categoria = 'ALUGUEL'
       AND o.contrato_id = p_contrato_id
       AND o.competencia BETWEEN l.inicio AND l.fim
     GROUP BY o.competencia
  ),
  pago_comp AS (
    SELECT competencia, SUM(valor) AS valor FROM pagos GROUP BY competencia
  ),
  abertas AS (
    SELECT v.competencia,
           v.vencimento,
           c.valor_aluguel - COALESCE(p.valor, 0) AS deficit
      FROM venc v
      CROSS JOIN c
      LEFT JOIN pago_comp p ON p.competencia = v.competencia
     WHERE v.vencimento < p_referencia
       AND (c.fim IS NULL OR v.vencimento <= c.fim)
  )
  SELECT c.id,
         (SELECT min(a.vencimento) FROM abertas a WHERE a.deficit > 0),
         COALESCE((SELECT (p_referencia - min(a.vencimento))::int
                     FROM abertas a WHERE a.deficit > 0), 0),
         COALESCE((SELECT SUM(a.deficit) FROM abertas a WHERE a.deficit > 0), 0)::numeric(14,2),
         COALESCE((SELECT COUNT(*) FROM abertas a WHERE a.deficit > 0), 0)::integer
    FROM c;
END;
$$;

CREATE OR REPLACE VIEW vw_home_indicadores AS
WITH s AS MATERIALIZED (
  SELECT * FROM vw_contratos_situacao
),
i AS MATERIALIZED (
  SELECT * FROM vw_imoveis_situacao
)
SELECT
  (SELECT COUNT(*) FROM i)::integer AS imoveis_cadastrados,
  (SELECT COUNT(*) FROM i WHERE situacao_contrato = 'ALUGADO')::integer
    AS imoveis_alugados,
  (SELECT COUNT(*) FROM i WHERE situacao_contrato = 'VAGO')::integer
    AS imoveis_vagos,
  (SELECT COUNT(*) FROM i WHERE em_manutencao)::integer
    AS imoveis_em_manutencao,
  (SELECT COUNT(*) FROM s WHERE situacao_validade = 'A_VENCER')::integer
    AS contratos_proximos_vencimento,
  (SELECT COUNT(*) FROM s WHERE atrasado)::integer
    AS contratos_atrasados,
  (SELECT COUNT(*) FROM s
    WHERE atrasado
      AND dias_atraso >= COALESCE(
        (SELECT valor::int FROM configuracoes WHERE chave = 'dias_atraso_critico'), 7
      ))::integer AS contratos_atrasados_criticos;
