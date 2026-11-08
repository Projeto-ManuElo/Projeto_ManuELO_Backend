-- V003: imóveis, inquilinos e fiadores, com índices de busca.

CREATE TABLE imoveis (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  logradouro      varchar(200) NOT NULL,
  numero          varchar(20) NOT NULL,
  complemento     varchar(100),
  bairro          varchar(100) NOT NULL,
  cidade          varchar(100) NOT NULL,
  uf              char(2) NOT NULL DEFAULT 'SE',
  cep             varchar(9),
  tipo            tipo_imovel NOT NULL,
  status          status_imovel NOT NULL DEFAULT 'VAGO',
  criado_por      uuid REFERENCES usuarios (id) ON DELETE RESTRICT,
  criado_em       timestamptz NOT NULL DEFAULT now(),
  atualizado_por  uuid REFERENCES usuarios (id) ON DELETE RESTRICT,
  atualizado_em   timestamptz,
  CONSTRAINT chk_imoveis_logradouro_nao_vazio CHECK (length(btrim(logradouro)) > 0),
  CONSTRAINT chk_imoveis_numero_nao_vazio CHECK (length(btrim(numero)) > 0),
  CONSTRAINT chk_imoveis_bairro_nao_vazio CHECK (length(btrim(bairro)) > 0),
  CONSTRAINT chk_imoveis_cidade_nao_vazio CHECK (length(btrim(cidade)) > 0),
  CONSTRAINT chk_imoveis_uf CHECK (uf ~ '^[A-Z]{2}$'),
  CONSTRAINT chk_imoveis_cep CHECK (cep IS NULL OR cep ~ '^[0-9]{5}-?[0-9]{3}$')
);

CREATE INDEX ix_imoveis_status ON imoveis (status);
CREATE INDEX ix_imoveis_tipo ON imoveis (tipo);
CREATE INDEX ix_imoveis_logradouro_trgm ON imoveis USING gin (logradouro gin_trgm_ops);
CREATE INDEX ix_imoveis_bairro_trgm ON imoveis USING gin (bairro gin_trgm_ops);
CREATE INDEX ix_imoveis_cidade_trgm ON imoveis USING gin (cidade gin_trgm_ops);

CREATE TRIGGER trg_imoveis_atualizado_em
BEFORE UPDATE ON imoveis
FOR EACH ROW EXECUTE FUNCTION fn_atualizar_atualizado_em();

CREATE TABLE inquilinos (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  nome            varchar(160) NOT NULL,
  documento       varchar(18) NOT NULL,
  email           varchar(160),
  criado_por      uuid REFERENCES usuarios (id) ON DELETE RESTRICT,
  criado_em       timestamptz NOT NULL DEFAULT now(),
  atualizado_por  uuid REFERENCES usuarios (id) ON DELETE RESTRICT,
  atualizado_em   timestamptz,
  CONSTRAINT uq_inquilinos_documento UNIQUE (documento),
  CONSTRAINT chk_inquilinos_nome_nao_vazio CHECK (length(btrim(nome)) > 0),
  CONSTRAINT chk_inquilinos_documento CHECK (documento ~ '^([0-9]{11}|[0-9]{14})$'),
  CONSTRAINT chk_inquilinos_email CHECK (
    email IS NULL OR email ~ '^[^@[:space:]]+@[^@[:space:]]+$'
  )
);

CREATE INDEX ix_inquilinos_nome_trgm ON inquilinos USING gin (nome gin_trgm_ops);
CREATE INDEX ix_inquilinos_documento_trgm ON inquilinos USING gin (documento gin_trgm_ops);
CREATE INDEX ix_inquilinos_email ON inquilinos (lower(email));

CREATE TRIGGER trg_inquilinos_atualizado_em
BEFORE UPDATE ON inquilinos
FOR EACH ROW EXECUTE FUNCTION fn_atualizar_atualizado_em();

CREATE TABLE fiadores (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  nome            varchar(160) NOT NULL,
  documento       varchar(18) NOT NULL,
  email           varchar(160),
  telefone        varchar(20),
  criado_por      uuid REFERENCES usuarios (id) ON DELETE RESTRICT,
  criado_em       timestamptz NOT NULL DEFAULT now(),
  atualizado_por  uuid REFERENCES usuarios (id) ON DELETE RESTRICT,
  atualizado_em   timestamptz,
  CONSTRAINT uq_fiadores_documento UNIQUE (documento),
  CONSTRAINT chk_fiadores_nome_nao_vazio CHECK (length(btrim(nome)) > 0),
  CONSTRAINT chk_fiadores_documento CHECK (documento ~ '^([0-9]{11}|[0-9]{14})$'),
  CONSTRAINT chk_fiadores_email CHECK (
    email IS NULL OR email ~ '^[^@[:space:]]+@[^@[:space:]]+$'
  ),
  CONSTRAINT chk_fiadores_telefone CHECK (telefone IS NULL OR telefone ~ '^[0-9]{10,11}$')
);

CREATE INDEX ix_fiadores_nome_trgm ON fiadores USING gin (nome gin_trgm_ops);
CREATE INDEX ix_fiadores_documento_trgm ON fiadores USING gin (documento gin_trgm_ops);

CREATE TRIGGER trg_fiadores_atualizado_em
BEFORE UPDATE ON fiadores
FOR EACH ROW EXECUTE FUNCTION fn_atualizar_atualizado_em();
