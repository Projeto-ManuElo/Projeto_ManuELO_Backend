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
