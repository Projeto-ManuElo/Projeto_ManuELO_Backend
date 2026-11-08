-- V004: contratos (com dia de vencimento e sem sobreposição) e livro-razão de caução.

CREATE TABLE contratos (
  id                          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  imovel_id                   uuid NOT NULL REFERENCES imoveis (id) ON DELETE RESTRICT,
  inquilino_id                uuid NOT NULL REFERENCES inquilinos (id) ON DELETE RESTRICT,
  fiador_id                   uuid REFERENCES fiadores (id) ON DELETE RESTRICT,
  inicio                      date NOT NULL,
  fim                         date,
  valor_aluguel               numeric(14,2) NOT NULL,
  dia_vencimento              smallint NOT NULL,
  acompanhar_pagamento_desde  date NOT NULL,
  possui_acao_judicial        boolean NOT NULL DEFAULT false,
  acao_judicial_descricao     varchar(500),
  criado_por                  uuid REFERENCES usuarios (id) ON DELETE RESTRICT,
  criado_em                   timestamptz NOT NULL DEFAULT now(),
  atualizado_por              uuid REFERENCES usuarios (id) ON DELETE RESTRICT,
  atualizado_em               timestamptz,
  CONSTRAINT chk_contratos_valor CHECK (valor_aluguel > 0),
  CONSTRAINT chk_contratos_dia_vencimento CHECK (dia_vencimento BETWEEN 1 AND 31),
  CONSTRAINT chk_contratos_periodo CHECK (fim IS NULL OR fim >= inicio),
  CONSTRAINT chk_contratos_acompanhar_desde CHECK (acompanhar_pagamento_desde >= inicio),
  CONSTRAINT chk_contratos_acao_judicial CHECK (
    NOT possui_acao_judicial
    OR (acao_judicial_descricao IS NOT NULL AND length(btrim(acao_judicial_descricao)) > 0)
  )
);

-- Um imóvel não pode ter dois contratos com períodos sobrepostos.
ALTER TABLE contratos
  ADD CONSTRAINT ex_contratos_sem_sobreposicao
  EXCLUDE USING gist (
    imovel_id WITH =,
    daterange(inicio, CASE WHEN fim IS NULL THEN NULL ELSE fim + 1 END, '[)') WITH &&
  );

CREATE INDEX ix_contratos_imovel ON contratos (imovel_id);
CREATE INDEX ix_contratos_inquilino ON contratos (inquilino_id);
CREATE INDEX ix_contratos_fiador ON contratos (fiador_id);
CREATE INDEX ix_contratos_fim ON contratos (fim);
CREATE INDEX ix_contratos_acompanhar ON contratos (acompanhar_pagamento_desde);

CREATE TRIGGER trg_contratos_atualizado_em
BEFORE UPDATE ON contratos
FOR EACH ROW EXECUTE FUNCTION fn_atualizar_atualizado_em();

CREATE TABLE caucao_movimentacoes (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  contrato_id     uuid NOT NULL REFERENCES contratos (id) ON DELETE RESTRICT,
  tipo            tipo_caucao NOT NULL,
  valor           numeric(14,2) NOT NULL,
  data            date NOT NULL,
  movimentacao_id uuid,
  documento_id    uuid,
  criado_por      uuid NOT NULL REFERENCES usuarios (id) ON DELETE RESTRICT,
  criado_em       timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT chk_caucao_valor CHECK (valor > 0),
  CONSTRAINT chk_caucao_abatimento_com_movimentacao CHECK (
    tipo <> 'ABATIMENTO_ALUGUEL' OR movimentacao_id IS NOT NULL
  )
);

CREATE INDEX ix_caucao_contrato ON caucao_movimentacoes (contrato_id);
CREATE INDEX ix_caucao_data ON caucao_movimentacoes (data);
CREATE INDEX ix_caucao_movimentacao ON caucao_movimentacoes (movimentacao_id);
CREATE INDEX ix_caucao_documento ON caucao_movimentacoes (documento_id);
