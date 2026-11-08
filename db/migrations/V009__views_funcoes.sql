-- V009: derivados (views e funções) do ManuElo.
-- Não criam tabelas: extrato, resumo, situação de contratos/imóveis, home, reformas,
-- documentos e saldo de caução.

-- Resumo financeiro por período. Estornos contam na data do estorno (R09).
CREATE OR REPLACE FUNCTION fn_resumo_periodo(p_inicio date, p_fim date)
RETURNS TABLE (
  total_entradas numeric(14,2),
  total_saidas   numeric(14,2),
  saldo          numeric(14,2),
  qtd_registros  integer,
  total_estornos numeric(14,2)
)
LANGUAGE plpgsql
AS $$
#variable_conflict use_column
BEGIN
  IF p_inicio IS NULL OR p_fim IS NULL OR p_inicio > p_fim THEN
    RAISE EXCEPTION 'período inválido: % a %', p_inicio, p_fim USING ERRCODE = '23514';
  END IF;

  RETURN QUERY
  WITH m AS (
    SELECT mv.tipo, mv.valor, o.tipo AS tipo_original
      FROM movimentacoes mv
      LEFT JOIN movimentacoes o ON o.id = mv.movimentacao_original_id
     WHERE mv.data_movimentacao BETWEEN p_inicio AND p_fim
  )
  SELECT
    COALESCE(SUM(CASE
      WHEN tipo = 'ENTRADA' THEN valor
      WHEN tipo = 'ESTORNO' AND tipo_original = 'ENTRADA' THEN -valor
      ELSE 0 END), 0)::numeric(14,2),
    COALESCE(SUM(CASE
      WHEN tipo = 'SAIDA' THEN valor
      WHEN tipo = 'ESTORNO' AND tipo_original = 'SAIDA' THEN -valor
      ELSE 0 END), 0)::numeric(14,2),
    COALESCE(SUM(CASE
      WHEN tipo = 'ENTRADA' THEN valor
      WHEN tipo = 'SAIDA' THEN -valor
      WHEN tipo = 'ESTORNO' AND tipo_original = 'ENTRADA' THEN -valor
      WHEN tipo = 'ESTORNO' AND tipo_original = 'SAIDA' THEN valor
      ELSE 0 END), 0)::numeric(14,2),
    COUNT(*)::integer,
    COALESCE(SUM(CASE WHEN tipo = 'ESTORNO' THEN valor ELSE 0 END), 0)::numeric(14,2)
  FROM m;
END;
$$;

-- Extrato detalhado por período, com filtro opcional de tipo.
CREATE OR REPLACE FUNCTION fn_extrato_periodo(
  p_inicio date,
  p_fim date,
  p_filtro filtro_tipo_movimentacao DEFAULT NULL
)
RETURNS TABLE (
  movimentacao_id  uuid,
  data             date,
  tipo             tipo_movimentacao,
  categoria        categoria_movimentacao,
  valor            numeric(14,2),
  descricao        varchar,
  forma_pagamento  forma_pagamento,
  imovel_id        uuid,
  contrato_id      uuid,
  competencia      date,
  origem_descricao varchar,
  favorecido       varchar,
  estorno_de       uuid
)
LANGUAGE plpgsql
AS $$
#variable_conflict use_column
BEGIN
  IF p_inicio IS NULL OR p_fim IS NULL OR p_inicio > p_fim THEN
    RAISE EXCEPTION 'período inválido: % a %', p_inicio, p_fim USING ERRCODE = '23514';
  END IF;

  RETURN QUERY
  SELECT mv.id, mv.data_movimentacao, mv.tipo, mv.categoria, mv.valor, mv.descricao,
         mv.forma_pagamento, mv.imovel_id, mv.contrato_id, mv.competencia,
         mv.origem_descricao, mv.favorecido, mv.movimentacao_original_id
    FROM movimentacoes mv
    LEFT JOIN movimentacoes o ON o.id = mv.movimentacao_original_id
   WHERE mv.data_movimentacao BETWEEN p_inicio AND p_fim
     AND (
       p_filtro IS NULL
       OR (p_filtro = 'ENTRADA'
           AND (mv.tipo = 'ENTRADA' OR (mv.tipo = 'ESTORNO' AND o.tipo = 'ENTRADA')))
       OR (p_filtro = 'SAIDA'
           AND (mv.tipo = 'SAIDA' OR (mv.tipo = 'ESTORNO' AND o.tipo = 'SAIDA')))
     )
   ORDER BY mv.data_movimentacao, mv.criado_em, mv.id;
END;
$$;

-- Atraso de um contrato: competências vencidas e ainda em aberto (R05).
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
  venc AS (
    SELECT m.competencia,
           (m.competencia
            + (LEAST(c.dia_vencimento::int,
                     EXTRACT(DAY FROM (m.competencia + interval '1 month - 1 day'))::int) - 1)
           )::date AS vencimento
      FROM meses m
      CROSS JOIN c
  ),
  abertas AS (
    SELECT v.competencia,
           v.vencimento,
           c.valor_aluguel - COALESCE(p.valor_pago, 0) AS deficit
      FROM venc v
      CROSS JOIN c
      LEFT JOIN LATERAL (
        SELECT COALESCE(SUM(t.valor), 0) AS valor_pago
          FROM (
            SELECT mv.valor
              FROM movimentacoes mv
             WHERE mv.tipo = 'ENTRADA'
               AND mv.categoria = 'ALUGUEL'
               AND mv.contrato_id = c.id
               AND mv.competencia = v.competencia
            UNION ALL
            SELECT -e.valor
              FROM movimentacoes e
              JOIN movimentacoes o ON o.id = e.movimentacao_original_id
             WHERE e.tipo = 'ESTORNO'
               AND o.tipo = 'ENTRADA'
               AND o.categoria = 'ALUGUEL'
               AND o.contrato_id = c.id
               AND o.competencia = v.competencia
          ) t
      ) p ON true
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

-- Situação de um contrato: validade e atraso em uma data de referência.
CREATE OR REPLACE FUNCTION fn_contrato_situacao(
  p_contrato_id uuid,
  p_referencia date DEFAULT current_date
)
RETURNS TABLE (
  contrato_id              uuid,
  dias_para_vencer         integer,
  situacao_validade        text,
  atrasado                 boolean,
  dias_atraso              integer,
  valor_em_atraso          numeric(14,2),
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
  cfg AS (
    SELECT COALESCE(
      (SELECT valor::int FROM configuracoes WHERE chave = 'dias_alerta_vencimento'), 7
    ) AS janela
  ),
  atraso AS (
    SELECT * FROM fn_contrato_atraso(p_contrato_id, p_referencia)
  )
  SELECT c.id,
         CASE WHEN c.fim IS NULL THEN NULL ELSE (c.fim - p_referencia)::int END,
         CASE
           WHEN c.fim IS NOT NULL AND c.fim < p_referencia THEN 'VENCIDO'
           WHEN c.fim IS NOT NULL AND c.fim <= p_referencia + cfg.janela THEN 'A_VENCER'
           ELSE 'VIGENTE'
         END,
         COALESCE(a.qtd_competencias_abertas, 0) > 0,
         COALESCE(a.dias_atraso, 0),
         COALESCE(a.valor_em_atraso, 0)::numeric(14,2),
         COALESCE(a.qtd_competencias_abertas, 0)
    FROM c
    CROSS JOIN cfg
    LEFT JOIN atraso a ON true;
END;
$$;

CREATE OR REPLACE VIEW vw_contratos_situacao AS
SELECT c.id                         AS contrato_id,
       c.imovel_id,
       c.inquilino_id,
       c.fiador_id,
       c.inicio,
       c.fim,
       c.valor_aluguel,
       c.dia_vencimento,
       c.acompanhar_pagamento_desde,
       i.nome                       AS inquilino_nome,
       i.documento                  AS inquilino_documento,
       i.email                      AS inquilino_email,
       (im.logradouro || ', ' || im.numero) AS imovel_endereco,
       f.nome                       AS fiador_nome,
       s.dias_para_vencer,
       s.situacao_validade,
       s.atrasado,
       s.dias_atraso,
       s.valor_em_atraso,
       s.qtd_competencias_abertas
  FROM contratos c
  JOIN inquilinos i ON i.id = c.inquilino_id
  JOIN imoveis im ON im.id = c.imovel_id
  LEFT JOIN fiadores f ON f.id = c.fiador_id
  CROSS JOIN LATERAL fn_contrato_situacao(c.id, current_date) s;

CREATE OR REPLACE VIEW vw_contratos_atrasados AS
SELECT * FROM vw_contratos_situacao WHERE atrasado;

CREATE OR REPLACE VIEW vw_contratos_a_vencer AS
SELECT * FROM vw_contratos_situacao WHERE situacao_validade = 'A_VENCER';

CREATE OR REPLACE VIEW vw_imoveis_situacao AS
WITH vigente AS (
  SELECT DISTINCT ON (c.imovel_id)
         c.imovel_id, c.id AS contrato_id, c.inquilino_id
    FROM contratos c
   WHERE c.inicio <= current_date
     AND (c.fim IS NULL OR c.fim >= current_date)
   ORDER BY c.imovel_id, c.inicio DESC
)
SELECT im.*,
       CASE WHEN v.contrato_id IS NOT NULL THEN 'ALUGADO' ELSE 'VAGO' END AS situacao_contrato,
       (im.status = 'EM_MANUTENCAO') AS em_manutencao,
       CASE
         WHEN im.status = 'EM_MANUTENCAO' THEN 'EM_MANUTENCAO'
         WHEN v.contrato_id IS NOT NULL THEN 'ALUGADO'
         ELSE 'VAGO'
       END AS status_exibicao,
       v.contrato_id   AS contrato_atual_id,
       v.inquilino_id  AS inquilino_atual_id,
       i.nome          AS inquilino_atual_nome
  FROM imoveis im
  LEFT JOIN vigente v ON v.imovel_id = im.id
  LEFT JOIN inquilinos i ON i.id = v.inquilino_id;

CREATE OR REPLACE VIEW vw_home_indicadores AS
SELECT
  (SELECT COUNT(*) FROM imoveis)::integer AS imoveis_cadastrados,
  (SELECT COUNT(*) FROM vw_imoveis_situacao WHERE situacao_contrato = 'ALUGADO')::integer
    AS imoveis_alugados,
  (SELECT COUNT(*) FROM vw_imoveis_situacao WHERE situacao_contrato = 'VAGO')::integer
    AS imoveis_vagos,
  (SELECT COUNT(*) FROM vw_imoveis_situacao WHERE em_manutencao)::integer
    AS imoveis_em_manutencao,
  (SELECT COUNT(*) FROM vw_contratos_situacao WHERE situacao_validade = 'A_VENCER')::integer
    AS contratos_proximos_vencimento,
  (SELECT COUNT(*) FROM vw_contratos_situacao WHERE atrasado)::integer
    AS contratos_atrasados,
  (SELECT COUNT(*) FROM vw_contratos_situacao
    WHERE atrasado
      AND dias_atraso >= COALESCE(
        (SELECT valor::int FROM configuracoes WHERE chave = 'dias_atraso_critico'), 7
      ))::integer AS contratos_atrasados_criticos;

CREATE OR REPLACE VIEW vw_reformas_historico AS
SELECT r.*,
       (im.logradouro || ', ' || im.numero) AS imovel_endereco,
       resp.nome AS responsavel_periodo
  FROM reformas r
  JOIN imoveis im ON im.id = r.imovel_id
  LEFT JOIN LATERAL (
    SELECT inq.nome
      FROM contratos c
      JOIN inquilinos inq ON inq.id = c.inquilino_id
     WHERE c.imovel_id = r.imovel_id
       AND c.inicio <= r.data_reforma
       AND (c.fim IS NULL OR c.fim >= r.data_reforma)
     ORDER BY c.inicio DESC
     LIMIT 1
  ) resp ON true;

CREATE OR REPLACE VIEW vw_documentos_lista AS
SELECT d.*,
       COALESCE(direto.nome, contrato_inq.nome, imovel_inq.nome) AS inquilino_nome,
       CASE
         WHEN d.pendente_revisao THEN 'PENDENTE'
         WHEN d.tipo = 'CONTRATO' THEN 'CONTRATO'
         WHEN d.tipo = 'COMPROVANTE' THEN 'COMPROVANTE'
         ELSE d.tipo::text
       END AS etiqueta,
       mv.tipo::text AS tipo_financeiro
  FROM documentos d
  LEFT JOIN inquilinos direto ON direto.id = d.inquilino_id
  LEFT JOIN contratos ct ON ct.id = d.contrato_id
  LEFT JOIN inquilinos contrato_inq ON contrato_inq.id = ct.inquilino_id
  LEFT JOIN LATERAL (
    SELECT i.nome
      FROM contratos c2
      JOIN inquilinos i ON i.id = c2.inquilino_id
     WHERE c2.imovel_id = d.imovel_id
       AND c2.inicio <= current_date
       AND (c2.fim IS NULL OR c2.fim >= current_date)
     ORDER BY c2.inicio DESC
     LIMIT 1
  ) imovel_inq ON true
  LEFT JOIN movimentacoes mv ON mv.id = d.movimentacao_id;

CREATE OR REPLACE VIEW vw_saldo_caucao AS
SELECT c.id AS contrato_id,
       c.imovel_id,
       c.inquilino_id,
       COALESCE(SUM(CASE WHEN cm.tipo = 'DEPOSITO' THEN cm.valor ELSE 0 END), 0)::numeric(14,2)
         AS total_depositos,
       COALESCE(SUM(CASE WHEN cm.tipo = 'ABATIMENTO_ALUGUEL' THEN cm.valor ELSE 0 END), 0)::numeric(14,2)
         AS total_abatimentos,
       COALESCE(SUM(CASE WHEN cm.tipo = 'DEVOLUCAO' THEN cm.valor ELSE 0 END), 0)::numeric(14,2)
         AS total_devolucoes,
       COALESCE(SUM(CASE WHEN cm.tipo = 'DEPOSITO' THEN cm.valor ELSE -cm.valor END), 0)::numeric(14,2)
         AS saldo
  FROM contratos c
  LEFT JOIN caucao_movimentacoes cm ON cm.contrato_id = c.id
 GROUP BY c.id, c.imovel_id, c.inquilino_id;

CREATE OR REPLACE VIEW vw_movimentacoes_recentes AS
SELECT mv.*,
       (im.logradouro || ', ' || im.numero) AS imovel_endereco,
       inq.nome AS inquilino_nome
  FROM movimentacoes mv
  LEFT JOIN imoveis im ON im.id = mv.imovel_id
  LEFT JOIN contratos c ON c.id = mv.contrato_id
  LEFT JOIN inquilinos inq ON inq.id = c.inquilino_id
 ORDER BY mv.criado_em DESC, mv.data_movimentacao DESC
 LIMIT 20;
