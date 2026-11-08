-- V008: relatórios insert-only (com composição exata) e configurações.

CREATE TABLE relatorios (
  id                        uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  nome                      varchar(160) NOT NULL,
  tipo                      tipo_relatorio NOT NULL,
  data_referencia           date NOT NULL,
  periodo_inicio            date,
  periodo_fim               date,
  filtro_tipo_movimentacao  filtro_tipo_movimentacao,
  janela_dias               integer,
  total_entradas            numeric(14,2),
  total_saidas              numeric(14,2),
  saldo                     numeric(14,2),
  qtd_registros             integer NOT NULL,
  caminho_arquivo           varchar(500) NOT NULL,
  gerado_por                uuid NOT NULL REFERENCES usuarios (id) ON DELETE RESTRICT,
  gerado_em                 timestamptz NOT NULL DEFAULT now(),

  CONSTRAINT chk_relatorios_nome CHECK (length(btrim(nome)) > 0),
  CONSTRAINT chk_relatorios_caminho CHECK (length(btrim(caminho_arquivo)) > 0),
  CONSTRAINT chk_relatorios_qtd CHECK (qtd_registros >= 0),
  CONSTRAINT chk_relatorios_movimentacao CHECK (
    tipo <> 'MOVIMENTACAO'
    OR (periodo_inicio IS NOT NULL
        AND periodo_fim IS NOT NULL
        AND periodo_inicio <= periodo_fim
        AND total_entradas IS NOT NULL
        AND total_saidas IS NOT NULL
        AND saldo IS NOT NULL)
  ),
  CONSTRAINT chk_relatorios_contratos CHECK (
    tipo = 'MOVIMENTACAO'
    OR (periodo_inicio IS NULL
        AND periodo_fim IS NULL
        AND filtro_tipo_movimentacao IS NULL
        AND total_entradas IS NULL
        AND total_saidas IS NULL
        AND saldo IS NULL)
  ),
  CONSTRAINT chk_relatorios_janela CHECK (
    (tipo = 'CONTRATOS_A_VENCER' AND janela_dias IS NOT NULL AND janela_dias > 0)
    OR (tipo <> 'CONTRATOS_A_VENCER' AND janela_dias IS NULL)
  ),
  CONSTRAINT chk_relatorios_filtro CHECK (
    tipo = 'MOVIMENTACAO' OR filtro_tipo_movimentacao IS NULL
  ),
  CONSTRAINT chk_relatorios_totais_nao_negativos CHECK (
    (total_entradas IS NULL OR total_entradas >= 0)
    AND (total_saidas IS NULL OR total_saidas >= 0)
  )
);

CREATE INDEX ix_relatorios_tipo ON relatorios (tipo);
CREATE INDEX ix_relatorios_gerado_em ON relatorios (gerado_em);
CREATE INDEX ix_relatorios_data_referencia ON relatorios (data_referencia);

CREATE TRIGGER trg_relatorios_insert_only
BEFORE UPDATE OR DELETE ON relatorios
FOR EACH ROW EXECUTE FUNCTION fn_bloquear_alteracao();

CREATE TABLE relatorio_movimentacoes (
  relatorio_id    uuid NOT NULL REFERENCES relatorios (id) ON DELETE RESTRICT,
  movimentacao_id uuid NOT NULL REFERENCES movimentacoes (id) ON DELETE RESTRICT,
  ordem           integer NOT NULL,
  CONSTRAINT pk_relatorio_movimentacoes PRIMARY KEY (relatorio_id, movimentacao_id),
  CONSTRAINT uq_relatorio_movimentacoes_ordem UNIQUE (relatorio_id, ordem),
  CONSTRAINT chk_relatorio_movimentacoes_ordem CHECK (ordem > 0)
);

CREATE INDEX ix_relatorio_movimentacoes_mov ON relatorio_movimentacoes (movimentacao_id);

CREATE TRIGGER trg_relatorio_movimentacoes_insert_only
BEFORE UPDATE OR DELETE ON relatorio_movimentacoes
FOR EACH ROW EXECUTE FUNCTION fn_bloquear_alteracao();

CREATE TABLE relatorio_contratos (
  relatorio_id uuid NOT NULL REFERENCES relatorios (id) ON DELETE RESTRICT,
  contrato_id  uuid NOT NULL REFERENCES contratos (id) ON DELETE RESTRICT,
  ordem        integer NOT NULL,
  situacao     situacao_relatorio_contrato NOT NULL,
  dias         integer,
  CONSTRAINT pk_relatorio_contratos PRIMARY KEY (relatorio_id, contrato_id),
  CONSTRAINT uq_relatorio_contratos_ordem UNIQUE (relatorio_id, ordem),
  CONSTRAINT chk_relatorio_contratos_ordem CHECK (ordem > 0),
  CONSTRAINT chk_relatorio_contratos_dias CHECK (dias IS NULL OR dias >= 0),
  CONSTRAINT chk_relatorio_contratos_dias_obrigatorios CHECK (
    situacao = 'EXISTENTE' OR dias IS NOT NULL
  )
);

CREATE INDEX ix_relatorio_contratos_contrato ON relatorio_contratos (contrato_id);

CREATE TRIGGER trg_relatorio_contratos_insert_only
BEFORE UPDATE OR DELETE ON relatorio_contratos
FOR EACH ROW EXECUTE FUNCTION fn_bloquear_alteracao();

CREATE TABLE configuracoes (
  chave          varchar(100) PRIMARY KEY,
  valor          varchar(500) NOT NULL,
  descricao      varchar(255),
  criado_em      timestamptz NOT NULL DEFAULT now(),
  atualizado_por uuid REFERENCES usuarios (id) ON DELETE RESTRICT,
  atualizado_em  timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT chk_configuracoes_chave CHECK (length(btrim(chave)) > 0),
  CONSTRAINT chk_configuracoes_valor CHECK (length(btrim(valor)) > 0)
);

CREATE TRIGGER trg_configuracoes_atualizado_em
BEFORE UPDATE ON configuracoes
FOR EACH ROW EXECUTE FUNCTION fn_atualizar_atualizado_em();

INSERT INTO configuracoes (chave, valor, descricao)
VALUES
  ('dias_alerta_vencimento', '7', 'Dias de antecedência para contratos próximos do vencimento'),
  ('dias_atraso_critico', '7', 'Dias de atraso a partir dos quais o contrato é crítico')
ON CONFLICT (chave) DO NOTHING;
