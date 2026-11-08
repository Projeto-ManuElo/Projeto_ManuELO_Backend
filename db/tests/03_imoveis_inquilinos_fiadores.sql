-- T04: imóveis, inquilinos, fiadores, checks, unicidade e auditoria.

INSERT INTO imoveis (id, logradouro, numero, bairro, cidade, uf, cep, tipo, status)
VALUES
  ('00000000-0000-4000-8000-000000000401', 'Rua das Flores', '100', 'Centro', 'Aracaju', 'SE',
   '49010-000', 'CASA', 'VAGO'),
  ('00000000-0000-4000-8000-000000000402', 'Avenida Beira Mar', '200', 'Atalaia', 'Aracaju', 'SE',
   NULL, 'APARTAMENTO', 'ALUGADO')
ON CONFLICT DO NOTHING;

INSERT INTO inquilinos (id, nome, documento, email)
VALUES ('00000000-0000-4000-8000-000000000411', 'T04 Inquilino', '90000000001',
        't04.inquilino@example.test')
ON CONFLICT DO NOTHING;

INSERT INTO fiadores (id, nome, documento, email, telefone)
VALUES ('00000000-0000-4000-8000-000000000421', 'T04 Fiador', '90000000002',
        't04.fiador@example.test', '79999990000')
ON CONFLICT DO NOTHING;

-- Positivo: auditoria temporal é carimbada no UPDATE.
DO $$
DECLARE
  v_antes timestamptz;
  v_depois timestamptz;
BEGIN
  SELECT atualizado_em INTO v_antes FROM imoveis WHERE id = '00000000-0000-4000-8000-000000000401';
  IF v_antes IS NOT NULL THEN
    RAISE EXCEPTION 'imóvel recém-criado já tinha atualizado_em';
  END IF;

  UPDATE imoveis SET complemento = 'Casa 1' WHERE id = '00000000-0000-4000-8000-000000000401';
  SELECT atualizado_em INTO v_depois FROM imoveis WHERE id = '00000000-0000-4000-8000-000000000401';
  IF v_depois IS NULL THEN
    RAISE EXCEPTION 'trigger de auditoria não carimbou atualizado_em';
  END IF;
END $$;

-- Negativos de imóvel.
DO $$
BEGIN
  BEGIN
    INSERT INTO imoveis (logradouro, numero, bairro, cidade, uf, tipo)
    VALUES ('Rua X', '1', 'Centro', 'Aracaju', 'se', 'CASA');
    RAISE EXCEPTION 'aceitou UF minúscula';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO imoveis (logradouro, numero, bairro, cidade, cep, tipo)
    VALUES ('Rua X', '1', 'Centro', 'Aracaju', '49010', 'CASA');
    RAISE EXCEPTION 'aceitou CEP inválido';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO imoveis (logradouro, numero, bairro, cidade, tipo)
    VALUES ('   ', '1', 'Centro', 'Aracaju', 'CASA');
    RAISE EXCEPTION 'aceitou logradouro vazio';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
END $$;

-- O default de UF é SE: a inserção sem UF acima falhou por outro motivo? Garante o default.
INSERT INTO imoveis (logradouro, numero, bairro, cidade, tipo)
VALUES ('Rua Sem UF', '10', 'Centro', 'Aracaju', 'TERRENO');
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM imoveis WHERE logradouro = 'Rua Sem UF' AND uf = 'SE'
  ) THEN
    RAISE EXCEPTION 'default de UF não aplicado';
  END IF;
END $$;

-- Negativos de inquilino.
DO $$
BEGIN
  BEGIN
    INSERT INTO inquilinos (nome, documento) VALUES ('T04 Doc Curto', '123');
    RAISE EXCEPTION 'aceitou documento curto';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO inquilinos (nome, documento) VALUES ('T04 Duplicado', '90000000001');
    RAISE EXCEPTION 'aceitou documento duplicado';
  EXCEPTION WHEN unique_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO inquilinos (nome, documento, email) VALUES ('T04 Email', '90000000003', 'invalido');
    RAISE EXCEPTION 'aceitou e-mail inválido';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
END $$;

-- Negativos de fiador.
DO $$
BEGIN
  BEGIN
    INSERT INTO fiadores (nome, documento) VALUES ('T04 Fiador Duplicado', '90000000002');
    RAISE EXCEPTION 'aceitou documento duplicado de fiador';
  EXCEPTION WHEN unique_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO fiadores (nome, documento, telefone)
    VALUES ('T04 Fiador Telefone', '90000000004', 'abc');
    RAISE EXCEPTION 'aceitou telefone inválido';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
END $$;
