-- ARQUIVO GERADO por scripts/gerar-bundle-supabase.sh. Nao edite a mao.
-- Cole no SQL Editor do Supabase (Database > SQL Editor) e execute.
-- Schema v3: 13 tabelas, 9 views e 12 funcoes (V001..V011).
-- Se for usar Flyway depois, aplique em um banco limpo (ver db/supabase/README.md).

BEGIN;
CREATE SCHEMA IF NOT EXISTS extensions;
SET search_path = public, extensions;

-- ===== V001__extensoes_enums_auditoria.sql =====
-- V001: extensões, tipos enumerados e função de auditoria temporal.

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS btree_gist;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TYPE perfil_usuario AS ENUM ('ADMINISTRADOR', 'COLABORADOR');
CREATE TYPE status_usuario AS ENUM ('PENDENTE_APROVACAO', 'ATIVO', 'INATIVO');

CREATE TYPE tipo_imovel AS ENUM ('APARTAMENTO', 'CASA', 'COMERCIAL', 'TERRENO');
CREATE TYPE status_imovel AS ENUM ('ALUGADO', 'VAGO', 'EM_MANUTENCAO');

CREATE TYPE tipo_movimentacao AS ENUM ('ENTRADA', 'SAIDA', 'ESTORNO');
CREATE TYPE categoria_movimentacao AS ENUM (
  'ALUGUEL', 'OUTRAS_ENTRADAS', 'MANUTENCAO', 'TRIBUTOS', 'ADMINISTRATIVO', 'OUTRAS_SAIDAS'
);
CREATE TYPE forma_pagamento AS ENUM ('PIX', 'BOLETO', 'TRANSFERENCIA', 'DINHEIRO', 'CAUCAO');

CREATE TYPE tipo_caucao AS ENUM ('DEPOSITO', 'ABATIMENTO_ALUGUEL', 'DEVOLUCAO');

CREATE TYPE tipo_documento AS ENUM (
  'CONTRATO', 'COMPROVANTE', 'NOTA_FISCAL', 'DOCUMENTO_CONTABIL', 'CADASTRO'
);

CREATE TYPE tipo_relatorio AS ENUM (
  'CONTRATOS_EXISTENTES', 'CONTRATOS_ATRASADOS', 'CONTRATOS_A_VENCER', 'MOVIMENTACAO'
);
CREATE TYPE filtro_tipo_movimentacao AS ENUM ('ENTRADA', 'SAIDA');
CREATE TYPE situacao_relatorio_contrato AS ENUM ('EXISTENTE', 'ATRASADO', 'A_VENCER');

-- Toda tabela editável usa este trigger para carimbar atualizado_em.
CREATE OR REPLACE FUNCTION fn_atualizar_atualizado_em()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  NEW.atualizado_em := now();
  RETURN NEW;
END;
$$;


-- ===== V002__usuarios_tokens.sql =====
-- V002: usuários (com aprovação) e tokens de redefinição de senha.

CREATE TABLE usuarios (
  id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  nome             varchar(160) NOT NULL,
  email            varchar(160) NOT NULL,
  senha_hash       varchar(255) NOT NULL,
  perfil           perfil_usuario NOT NULL DEFAULT 'COLABORADOR',
  status           status_usuario NOT NULL DEFAULT 'PENDENTE_APROVACAO',
  aprovado_por     uuid REFERENCES usuarios (id) ON DELETE RESTRICT,
  aprovado_em      timestamptz,
  ultimo_acesso_em timestamptz,
  criado_por       uuid REFERENCES usuarios (id) ON DELETE RESTRICT,
  criado_em        timestamptz NOT NULL DEFAULT now(),
  atualizado_por   uuid REFERENCES usuarios (id) ON DELETE RESTRICT,
  atualizado_em    timestamptz,
  CONSTRAINT chk_usuarios_nome_nao_vazio CHECK (length(btrim(nome)) > 0),
  CONSTRAINT chk_usuarios_email_formato CHECK (email ~ '^[^@[:space:]]+@[^@[:space:]]+$'),
  CONSTRAINT chk_usuarios_senha_hash_tamanho CHECK (length(senha_hash) >= 20),
  CONSTRAINT chk_usuarios_aprovacao_coerente CHECK ((aprovado_por IS NULL) = (aprovado_em IS NULL)),
  CONSTRAINT chk_usuarios_ativo_aprovado CHECK (status <> 'ATIVO' OR aprovado_em IS NOT NULL),
  CONSTRAINT chk_usuarios_pendente_sem_aprovacao CHECK (
    status <> 'PENDENTE_APROVACAO' OR (aprovado_por IS NULL AND aprovado_em IS NULL)
  )
);

CREATE UNIQUE INDEX uq_usuarios_email ON usuarios (lower(email));
CREATE INDEX ix_usuarios_status ON usuarios (status);
CREATE INDEX ix_usuarios_perfil ON usuarios (perfil);
CREATE INDEX ix_usuarios_nome_trgm ON usuarios USING gin (nome gin_trgm_ops);

CREATE TRIGGER trg_usuarios_atualizado_em
BEFORE UPDATE ON usuarios
FOR EACH ROW EXECUTE FUNCTION fn_atualizar_atualizado_em();

CREATE TABLE tokens_redefinicao_senha (
  id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  usuario_id uuid NOT NULL REFERENCES usuarios (id) ON DELETE RESTRICT,
  token_hash varchar(255) NOT NULL,
  expira_em  timestamptz NOT NULL,
  usado_em   timestamptz,
  criado_em  timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT uq_tokens_redefinicao_hash UNIQUE (token_hash),
  CONSTRAINT chk_tokens_hash_tamanho CHECK (length(token_hash) >= 32),
  CONSTRAINT chk_tokens_expira_depois_criacao CHECK (expira_em > criado_em),
  CONSTRAINT chk_tokens_uso_coerente CHECK (usado_em IS NULL OR usado_em >= criado_em)
);

CREATE INDEX ix_tokens_redefinicao_usuario ON tokens_redefinicao_senha (usuario_id);

-- Uso único: depois de marcado como usado, o token não pode ser reutilizado nem alterado.
CREATE OR REPLACE FUNCTION fn_token_redefinicao_uso_unico()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF TG_OP = 'DELETE' THEN
    RAISE EXCEPTION 'tokens de redefinição não podem ser apagados' USING ERRCODE = '55000';
  END IF;

  IF OLD.usado_em IS NOT NULL THEN
    RAISE EXCEPTION 'token de redefinição já utilizado' USING ERRCODE = '55000';
  END IF;

  IF NEW.usuario_id IS DISTINCT FROM OLD.usuario_id
     OR NEW.token_hash IS DISTINCT FROM OLD.token_hash
     OR NEW.expira_em IS DISTINCT FROM OLD.expira_em
     OR NEW.criado_em IS DISTINCT FROM OLD.criado_em THEN
    RAISE EXCEPTION 'campos imutáveis do token de redefinição foram alterados' USING ERRCODE = '55000';
  END IF;

  RETURN NEW;
END;
$$;

CREATE TRIGGER trg_tokens_redefinicao_uso_unico
BEFORE UPDATE OR DELETE ON tokens_redefinicao_senha
FOR EACH ROW EXECUTE FUNCTION fn_token_redefinicao_uso_unico();


-- ===== V003__imoveis_inquilinos_fiadores.sql =====
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


-- ===== V004__contratos_caucao.sql =====
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


-- ===== V005__movimentacoes.sql =====
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


-- ===== V006__documentos_reformas.sql =====
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


-- ===== V007__regras_movimentacoes_caucao.sql =====
-- V007: integridade das movimentações e da caução.
-- Regras R01, R02, R03 (imóvel do contrato), R04 (comprovante), R07 (caução).

-- Imutabilidade: movimentações e caução nunca são editadas nem apagadas (R01).
CREATE OR REPLACE FUNCTION fn_bloquear_alteracao()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  RAISE EXCEPTION 'tabela % é imutável: operação % proibida', TG_TABLE_NAME, TG_OP
    USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER trg_movimentacoes_imutavel
BEFORE UPDATE OR DELETE ON movimentacoes
FOR EACH ROW EXECUTE FUNCTION fn_bloquear_alteracao();

CREATE TRIGGER trg_caucao_imutavel
BEFORE UPDATE OR DELETE ON caucao_movimentacoes
FOR EACH ROW EXECUTE FUNCTION fn_bloquear_alteracao();

-- Estorno (R01) e correção (R02) e coerência de imóvel do aluguel (R03).
CREATE OR REPLACE FUNCTION fn_movimentacao_regras()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
  v_original   movimentacoes%ROWTYPE;
  v_perfil     perfil_usuario;
  v_imovel     uuid;
BEGIN
  IF NEW.tipo = 'ESTORNO' THEN
    SELECT perfil INTO v_perfil FROM usuarios WHERE id = NEW.responsavel_id;
    IF v_perfil IS DISTINCT FROM 'ADMINISTRADOR' THEN
      RAISE EXCEPTION 'somente administrador pode estornar movimentação'
        USING ERRCODE = '23514';
    END IF;

    SELECT * INTO v_original FROM movimentacoes WHERE id = NEW.movimentacao_original_id;
    IF NOT FOUND THEN
      RAISE EXCEPTION 'movimentação original do estorno não existe'
        USING ERRCODE = '23514';
    END IF;

    IF v_original.tipo = 'ESTORNO' THEN
      RAISE EXCEPTION 'não é permitido estornar um estorno'
        USING ERRCODE = '23514';
    END IF;

    IF NEW.valor <> v_original.valor THEN
      RAISE EXCEPTION 'estorno deve ter o mesmo valor da movimentação original'
        USING ERRCODE = '23514';
    END IF;

    IF EXISTS (
      SELECT 1 FROM movimentacoes m
       WHERE m.movimentacao_original_id = NEW.movimentacao_original_id
    ) THEN
      RAISE EXCEPTION 'movimentação original já possui estorno'
        USING ERRCODE = '23514';
    END IF;

  ELSE
    IF NEW.categoria = 'ALUGUEL' THEN
      SELECT imovel_id INTO v_imovel FROM contratos WHERE id = NEW.contrato_id;
      IF v_imovel IS DISTINCT FROM NEW.imovel_id THEN
        RAISE EXCEPTION 'imóvel da entrada de aluguel difere do imóvel do contrato'
          USING ERRCODE = '23514';
      END IF;
    END IF;

    IF NEW.corrige_movimentacao_id IS NOT NULL THEN
      SELECT perfil INTO v_perfil FROM usuarios WHERE id = NEW.responsavel_id;
      IF v_perfil IS DISTINCT FROM 'ADMINISTRADOR' THEN
        RAISE EXCEPTION 'somente administrador pode corrigir movimentação'
          USING ERRCODE = '23514';
      END IF;
    END IF;
  END IF;

  RETURN NEW;
END;
$$;

CREATE TRIGGER trg_movimentacoes_regras
BEFORE INSERT ON movimentacoes
FOR EACH ROW EXECUTE FUNCTION fn_movimentacao_regras();

-- Correção só é válida se a original já foi estornada (R02).
CREATE OR REPLACE FUNCTION fn_movimentacao_correcao()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF NEW.corrige_movimentacao_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM movimentacoes m
     WHERE m.tipo = 'ESTORNO'
       AND m.movimentacao_original_id = NEW.corrige_movimentacao_id
  ) THEN
    RAISE EXCEPTION 'movimentação corrigida exige estorno prévio da original'
      USING ERRCODE = '23514';
  END IF;
  RETURN NEW;
END;
$$;

CREATE CONSTRAINT TRIGGER trg_movimentacao_correcao
AFTER INSERT ON movimentacoes
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION fn_movimentacao_correcao();

-- Toda SAÍDA precisa de ao menos um comprovante ao fim da transação (R04).
CREATE OR REPLACE FUNCTION fn_movimentacao_comprovante_saida()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF NEW.tipo = 'SAIDA' AND NOT EXISTS (
    SELECT 1 FROM documentos d WHERE d.movimentacao_id = NEW.id
  ) THEN
    RAISE EXCEPTION 'saída % exige comprovante até o fim da transação', NEW.id
      USING ERRCODE = '23514';
  END IF;
  RETURN NEW;
END;
$$;

CREATE CONSTRAINT TRIGGER trg_movimentacao_comprovante_saida
AFTER INSERT ON movimentacoes
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION fn_movimentacao_comprovante_saida();

-- Forma CAUCAO só existe com o registro correspondente no livro de caução (R07).
CREATE OR REPLACE FUNCTION fn_movimentacao_caucao_vinculada()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF NEW.tipo <> 'ESTORNO'
     AND NEW.forma_pagamento = 'CAUCAO'
     AND NOT EXISTS (
       SELECT 1 FROM caucao_movimentacoes c WHERE c.movimentacao_id = NEW.id
     ) THEN
    RAISE EXCEPTION 'movimentação com forma CAUCAO exige registro de abatimento de caução'
      USING ERRCODE = '23514';
  END IF;
  RETURN NEW;
END;
$$;

CREATE CONSTRAINT TRIGGER trg_movimentacao_caucao_vinculada
AFTER INSERT ON movimentacoes
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION fn_movimentacao_caucao_vinculada();

-- Caução: abatimento coerente com a movimentação e saldo nunca negativo (R07).
CREATE OR REPLACE FUNCTION fn_caucao_regras()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
  v_mov    movimentacoes%ROWTYPE;
  v_saldo  numeric(14,2);
  v_delta  numeric(14,2);
BEGIN
  IF NEW.tipo = 'ABATIMENTO_ALUGUEL' THEN
    SELECT * INTO v_mov FROM movimentacoes WHERE id = NEW.movimentacao_id;
    IF NOT FOUND
       OR v_mov.tipo <> 'ENTRADA'
       OR v_mov.categoria <> 'ALUGUEL'
       OR v_mov.forma_pagamento <> 'CAUCAO' THEN
      RAISE EXCEPTION 'abatimento de caução exige entrada de aluguel com forma CAUCAO'
        USING ERRCODE = '23514';
    END IF;
    IF v_mov.contrato_id IS DISTINCT FROM NEW.contrato_id THEN
      RAISE EXCEPTION 'abatimento de caução deve usar o contrato da movimentação'
        USING ERRCODE = '23514';
    END IF;
    IF v_mov.valor <> NEW.valor THEN
      RAISE EXCEPTION 'abatimento de caução deve ter o valor da movimentação'
        USING ERRCODE = '23514';
    END IF;
  END IF;

  SELECT COALESCE(SUM(CASE WHEN tipo = 'DEPOSITO' THEN valor ELSE -valor END), 0)
    INTO v_saldo
    FROM caucao_movimentacoes
   WHERE contrato_id = NEW.contrato_id;

  v_delta := CASE WHEN NEW.tipo = 'DEPOSITO' THEN NEW.valor ELSE -NEW.valor END;

  IF v_saldo + v_delta < 0 THEN
    RAISE EXCEPTION 'saldo de caução não pode ficar negativo'
      USING ERRCODE = '23514';
  END IF;

  RETURN NEW;
END;
$$;

CREATE TRIGGER trg_caucao_regras
BEFORE INSERT ON caucao_movimentacoes
FOR EACH ROW EXECUTE FUNCTION fn_caucao_regras();


-- ===== V008__relatorios_configuracoes.sql =====
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


-- ===== V009__views_funcoes.sql =====
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


-- ===== V010__seguranca_rls.sql =====
-- V010: segurança no estilo Supabase.
-- RLS ligado em todas as tabelas; anon/authenticated sem acesso; role manuelo_app
-- com privilégio mínimo (sem DELETE e sem UPDATE nas tabelas imutáveis).

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'manuelo_app') THEN
    CREATE ROLE manuelo_app NOLOGIN;
  END IF;
END $$;

-- anon/authenticated não acessam o schema public (a aplicação usa JDBC direto).
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
    EXECUTE 'REVOKE ALL ON SCHEMA public FROM anon';
    EXECUTE 'REVOKE ALL ON ALL TABLES IN SCHEMA public FROM anon';
    EXECUTE 'REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM anon';
    EXECUTE 'REVOKE ALL ON ALL FUNCTIONS IN SCHEMA public FROM anon';
  END IF;

  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
    EXECUTE 'REVOKE ALL ON SCHEMA public FROM authenticated';
    EXECUTE 'REVOKE ALL ON ALL TABLES IN SCHEMA public FROM authenticated';
    EXECUTE 'REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM authenticated';
    EXECUTE 'REVOKE ALL ON ALL FUNCTIONS IN SCHEMA public FROM authenticated';
  END IF;
END $$;

GRANT USAGE ON SCHEMA public TO manuelo_app;
REVOKE CREATE ON SCHEMA public FROM manuelo_app;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO manuelo_app;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO manuelo_app;

-- Escrita apenas nas tabelas editáveis/inseríveis; DELETE nunca é concedido.
GRANT INSERT ON
  usuarios,
  tokens_redefinicao_senha,
  imoveis,
  inquilinos,
  fiadores,
  contratos,
  caucao_movimentacoes,
  movimentacoes,
  documentos,
  reformas,
  relatorios,
  relatorio_movimentacoes,
  relatorio_contratos
TO manuelo_app;

GRANT UPDATE ON
  usuarios,
  tokens_redefinicao_senha,
  imoveis,
  inquilinos,
  fiadores,
  contratos,
  documentos,
  reformas,
  configuracoes
TO manuelo_app;

GRANT INSERT ON configuracoes TO manuelo_app;

-- RLS em todas as tabelas, com política permissiva só para a role da aplicação.
DO $$
DECLARE
  t record;
BEGIN
  FOR t IN
    SELECT tablename FROM pg_tables WHERE schemaname = 'public' ORDER BY tablename
  LOOP
    EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', t.tablename);
    EXECUTE format('DROP POLICY IF EXISTS manuelo_app_all ON public.%I', t.tablename);
    EXECUTE format(
      'CREATE POLICY manuelo_app_all ON public.%I FOR ALL TO manuelo_app USING (true) WITH CHECK (true)',
      t.tablename
    );
  END LOOP;
END $$;


-- ===== V011__desempenho_views.sql =====
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


COMMIT;
