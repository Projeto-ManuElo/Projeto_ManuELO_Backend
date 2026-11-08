-- T03: usuários, aprovação e tokens de redefinição.

INSERT INTO usuarios (id, nome, email, senha_hash, perfil, status)
VALUES
  ('00000000-0000-4000-8000-000000000301', 'T03 Pendente', 't03.pendente@example.test',
   '$2a$10$abcdefghijklmnopqrstuvABCDEFGHIJKLMNOPQRSTUVWXYZ012345', 'COLABORADOR', 'PENDENTE_APROVACAO')
ON CONFLICT DO NOTHING;

INSERT INTO usuarios (id, nome, email, senha_hash, perfil, status, aprovado_por, aprovado_em)
VALUES ('00000000-0000-4000-8000-000000000302', 'T03 Admin', 't03.admin@example.test',
        '$2a$10$abcdefghijklmnopqrstuvABCDEFGHIJKLMNOPQRSTUVWXYZ012345', 'ADMINISTRADOR', 'ATIVO',
        '00000000-0000-4000-8000-000000000302', now())
ON CONFLICT DO NOTHING;

-- Positivo: aprovação preenche os dois campos e o status ATIVO é aceito.
DO $$
DECLARE
  v_aprovado timestamptz;
BEGIN
  SELECT aprovado_em INTO v_aprovado
    FROM usuarios WHERE id = '00000000-0000-4000-8000-000000000302';
  IF v_aprovado IS NULL THEN
    RAISE EXCEPTION 'usuário ATIVO de teste sem aprovado_em';
  END IF;
END $$;

-- Negativos de usuário.
DO $$
BEGIN
  BEGIN
    INSERT INTO usuarios (nome, email, senha_hash, status, aprovado_por)
    VALUES ('T03 Sem Data', 't03.sem.data@example.test',
            '$2a$10$abcdefghijklmnopqrstuvABCDEFGHIJKLMNOPQRSTUVWXYZ012345', 'ATIVO',
            '00000000-0000-4000-8000-000000000302');
    RAISE EXCEPTION 'aceitou aprovação sem aprovado_em';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO usuarios (nome, email, senha_hash, status)
    VALUES ('T03 Ativo', 't03.ativo.sem.aprovacao@example.test',
            '$2a$10$abcdefghijklmnopqrstuvABCDEFGHIJKLMNOPQRSTUVWXYZ012345', 'ATIVO');
    RAISE EXCEPTION 'aceitou ATIVO sem aprovação';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO usuarios (nome, email, senha_hash, status, aprovado_por, aprovado_em)
    VALUES ('T03 Pendente Aprovado', 't03.pendente.aprovado@example.test',
            '$2a$10$abcdefghijklmnopqrstuvABCDEFGHIJKLMNOPQRSTUVWXYZ012345',
            'PENDENTE_APROVACAO', '00000000-0000-4000-8000-000000000302', now());
    RAISE EXCEPTION 'aceitou pendente com aprovação';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO usuarios (nome, email, senha_hash)
    VALUES ('T03 Email Ruim', 'sem-arroba', '$2a$10$abcdefghijklmnopqrstuvABCDEFGHIJKLMNOPQRSTUVWXYZ012345');
    RAISE EXCEPTION 'aceitou e-mail inválido';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO usuarios (nome, email, senha_hash)
    VALUES ('   ', 't03.nome.vazio@example.test',
            '$2a$10$abcdefghijklmnopqrstuvABCDEFGHIJKLMNOPQRSTUVWXYZ012345');
    RAISE EXCEPTION 'aceitou nome vazio';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO usuarios (nome, email, senha_hash)
    VALUES ('T03 Duplicado', 'T03.ADMIN@example.test',
            '$2a$10$abcdefghijklmnopqrstuvABCDEFGHIJKLMNOPQRSTUVWXYZ012345');
    RAISE EXCEPTION 'aceitou e-mail duplicado com caixa diferente';
  EXCEPTION WHEN unique_violation THEN NULL;
  END;
END $$;

-- Tokens: positivos e negativos.
INSERT INTO tokens_redefinicao_senha (id, usuario_id, token_hash, expira_em)
VALUES (
  '00000000-0000-4000-8000-0000000003a1',
  '00000000-0000-4000-8000-000000000302',
  repeat('a', 64),
  now() + interval '1 hour'
)
ON CONFLICT DO NOTHING;

UPDATE tokens_redefinicao_senha
   SET usado_em = now()
 WHERE id = '00000000-0000-4000-8000-0000000003a1'
   AND usado_em IS NULL;

DO $$
BEGIN
  BEGIN
    UPDATE tokens_redefinicao_senha
       SET usado_em = now()
     WHERE id = '00000000-0000-4000-8000-0000000003a1';
    RAISE EXCEPTION 'aceitou reutilização de token';
  EXCEPTION WHEN SQLSTATE '55000' THEN NULL;
  END;

  BEGIN
    INSERT INTO tokens_redefinicao_senha (usuario_id, token_hash, expira_em)
    VALUES ('00000000-0000-4000-8000-000000000302', repeat('b', 64), now() - interval '1 hour');
    RAISE EXCEPTION 'aceitou token expirado antes da criação';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO tokens_redefinicao_senha (usuario_id, token_hash, expira_em)
    VALUES ('00000000-0000-4000-8000-000000000302', 'curto', now() + interval '1 hour');
    RAISE EXCEPTION 'aceitou hash de token curto';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO tokens_redefinicao_senha (usuario_id, token_hash, expira_em)
    VALUES ('00000000-0000-4000-8000-000000000302', repeat('a', 64), now() + interval '1 hour');
    RAISE EXCEPTION 'aceitou hash de token duplicado';
  EXCEPTION WHEN unique_violation THEN NULL;
  END;
END $$;
