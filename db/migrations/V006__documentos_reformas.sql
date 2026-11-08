-- V006: documentos (com vínculos e classificação) e histórico de reformas.
-- Antecipada em relação aos triggers de movimentação: o comprovante de SAÍDA
-- consulta esta tabela (DEC-14).

CREATE TABLE documentos (
  id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  nome_arquivo         varchar(200) NOT NULL,
  tipo                 tipo_documento,
  data_documento       date,
  pendente_revisao     boolean NOT NULL DEFAULT true,
  caminho_armazenamento varchar(500) NOT NULL,
  tipo_mime            varchar(100) NOT NULL,
  tamanho_bytes        bigint NOT NULL,
  movimentacao_id      uuid REFERENCES movimentacoes (id) ON DELETE RESTRICT,
  imovel_id            uuid REFERENCES imoveis (id) ON DELETE RESTRICT,
  inquilino_id         uuid REFERENCES inquilinos (id) ON DELETE RESTRICT,
  contrato_id          uuid REFERENCES contratos (id) ON DELETE RESTRICT,
  enviado_por          uuid NOT NULL REFERENCES usuarios (id) ON DELETE RESTRICT,
  enviado_em           timestamptz NOT NULL DEFAULT now(),
  atualizado_por       uuid REFERENCES usuarios (id) ON DELETE RESTRICT,
  atualizado_em        timestamptz,
  CONSTRAINT chk_documentos_nome_nao_vazio CHECK (length(btrim(nome_arquivo)) > 0),
  CONSTRAINT chk_documentos_caminho_nao_vazio CHECK (length(btrim(caminho_armazenamento)) > 0),
  CONSTRAINT chk_documentos_tamanho CHECK (tamanho_bytes > 0 AND tamanho_bytes <= 10485760),
  CONSTRAINT chk_documentos_mime CHECK (
    tipo_mime IN ('application/pdf', 'image/jpeg', 'image/png')
  ),
  CONSTRAINT chk_documentos_classificacao CHECK (
    pendente_revisao OR (tipo IS NOT NULL AND data_documento IS NOT NULL)
  ),
  CONSTRAINT chk_documentos_vinculo CHECK (
    movimentacao_id IS NOT NULL OR imovel_id IS NOT NULL
    OR inquilino_id IS NOT NULL OR contrato_id IS NOT NULL
  )
);

CREATE INDEX ix_documentos_movimentacao ON documentos (movimentacao_id);
CREATE INDEX ix_documentos_imovel ON documentos (imovel_id);
CREATE INDEX ix_documentos_inquilino ON documentos (inquilino_id);
CREATE INDEX ix_documentos_contrato ON documentos (contrato_id);
CREATE INDEX ix_documentos_enviado_em ON documentos (enviado_em);
CREATE INDEX ix_documentos_nome_trgm ON documentos USING gin (nome_arquivo gin_trgm_ops);

CREATE TRIGGER trg_documentos_atualizado_em
BEFORE UPDATE ON documentos
FOR EACH ROW EXECUTE FUNCTION fn_atualizar_atualizado_em();

ALTER TABLE caucao_movimentacoes
  ADD CONSTRAINT fk_caucao_documento
  FOREIGN KEY (documento_id) REFERENCES documentos (id) ON DELETE RESTRICT;

CREATE TABLE reformas (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  imovel_id       uuid NOT NULL REFERENCES imoveis (id) ON DELETE RESTRICT,
  data_reforma    date NOT NULL,
  descricao       varchar(500) NOT NULL,
  valor           numeric(14,2) NOT NULL,
  movimentacao_id uuid REFERENCES movimentacoes (id) ON DELETE RESTRICT,
  criado_por      uuid REFERENCES usuarios (id) ON DELETE RESTRICT,
  criado_em       timestamptz NOT NULL DEFAULT now(),
  atualizado_por  uuid REFERENCES usuarios (id) ON DELETE RESTRICT,
  atualizado_em   timestamptz,
  CONSTRAINT chk_reformas_descricao CHECK (length(btrim(descricao)) > 0),
  CONSTRAINT chk_reformas_valor CHECK (valor > 0)
);

CREATE INDEX ix_reformas_imovel ON reformas (imovel_id);
CREATE INDEX ix_reformas_data ON reformas (data_reforma);

CREATE TRIGGER trg_reformas_atualizado_em
BEFORE UPDATE ON reformas
FOR EACH ROW EXECUTE FUNCTION fn_atualizar_atualizado_em();
