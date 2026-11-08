-- V005: movimentações financeiras (imutáveis por trigger na V007).

CREATE TABLE movimentacoes (
  id                        uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tipo                      tipo_movimentacao NOT NULL,
  categoria                 categoria_movimentacao,
  valor                     numeric(14,2) NOT NULL,
  data_movimentacao         date NOT NULL,
  descricao                 varchar(255) NOT NULL,
  forma_pagamento           forma_pagamento,
  imovel_id                 uuid REFERENCES imoveis (id) ON DELETE RESTRICT,
  responsavel_id            uuid NOT NULL REFERENCES usuarios (id) ON DELETE RESTRICT,
  contrato_id               uuid REFERENCES contratos (id) ON DELETE RESTRICT,
  competencia               date,
  origem_descricao          varchar(255),
  favorecido                varchar(160),
  movimentacao_original_id  uuid REFERENCES movimentacoes (id) ON DELETE RESTRICT,
  justificativa             varchar(500),
  corrige_movimentacao_id   uuid REFERENCES movimentacoes (id) ON DELETE RESTRICT,
  criado_em                 timestamptz NOT NULL DEFAULT now(),

  CONSTRAINT uq_movimentacoes_original UNIQUE (movimentacao_original_id),
  CONSTRAINT chk_movimentacoes_valor CHECK (valor > 0),
  CONSTRAINT chk_movimentacoes_descricao CHECK (length(btrim(descricao)) > 0),

  CONSTRAINT chk_movimentacoes_tipo_categoria CHECK (
    (tipo = 'ESTORNO' AND categoria IS NULL)
    OR (tipo = 'ENTRADA' AND categoria IS NOT NULL
        AND categoria IN ('ALUGUEL', 'OUTRAS_ENTRADAS'))
    OR (tipo = 'SAIDA' AND categoria IS NOT NULL
        AND categoria IN ('MANUTENCAO', 'TRIBUTOS', 'ADMINISTRATIVO', 'OUTRAS_SAIDAS'))
  ),

  CONSTRAINT chk_movimentacoes_forma CHECK (tipo = 'ESTORNO' OR forma_pagamento IS NOT NULL),
  CONSTRAINT chk_movimentacoes_caucao_so_aluguel CHECK (
    forma_pagamento IS DISTINCT FROM 'CAUCAO' OR categoria = 'ALUGUEL'
  ),

  CONSTRAINT chk_movimentacoes_aluguel_contrato CHECK (
    categoria IS DISTINCT FROM 'ALUGUEL' OR contrato_id IS NOT NULL
  ),
  CONSTRAINT chk_movimentacoes_aluguel_imovel CHECK (
    categoria IS DISTINCT FROM 'ALUGUEL' OR imovel_id IS NOT NULL
  ),
  CONSTRAINT chk_movimentacoes_aluguel_competencia CHECK (
    categoria IS DISTINCT FROM 'ALUGUEL'
    OR (competencia IS NOT NULL AND competencia = date_trunc('month', competencia)::date)
  ),

  CONSTRAINT chk_movimentacoes_outras_entradas_origem CHECK (
    categoria IS DISTINCT FROM 'OUTRAS_ENTRADAS'
    OR (origem_descricao IS NOT NULL AND length(btrim(origem_descricao)) > 0)
  ),

  CONSTRAINT chk_movimentacoes_favorecido_so_saida CHECK (tipo = 'SAIDA' OR favorecido IS NULL),

  CONSTRAINT chk_movimentacoes_estorno CHECK (
    (tipo = 'ESTORNO'
      AND movimentacao_original_id IS NOT NULL
      AND justificativa IS NOT NULL
      AND length(btrim(justificativa)) > 0)
    OR
    (tipo <> 'ESTORNO'
      AND movimentacao_original_id IS NULL
      AND justificativa IS NULL)
  ),

  CONSTRAINT chk_movimentacoes_corrige_so_nao_estorno CHECK (
    corrige_movimentacao_id IS NULL OR tipo <> 'ESTORNO'
  )
);

CREATE INDEX ix_movimentacoes_data ON movimentacoes (data_movimentacao);
CREATE INDEX ix_movimentacoes_tipo_categoria ON movimentacoes (tipo, categoria);
CREATE INDEX ix_movimentacoes_contrato ON movimentacoes (contrato_id);
CREATE INDEX ix_movimentacoes_imovel ON movimentacoes (imovel_id);
CREATE INDEX ix_movimentacoes_competencia ON movimentacoes (competencia);
CREATE INDEX ix_movimentacoes_responsavel ON movimentacoes (responsavel_id);
CREATE INDEX ix_movimentacoes_corrige ON movimentacoes (corrige_movimentacao_id);

ALTER TABLE caucao_movimentacoes
  ADD CONSTRAINT fk_caucao_movimentacao
  FOREIGN KEY (movimentacao_id) REFERENCES movimentacoes (id) ON DELETE RESTRICT;
