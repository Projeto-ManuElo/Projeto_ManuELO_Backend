-- T13: telefone do inquilino (V012).

-- Positivo: telefone em dígitos é aceito e o UPDATE carimba atualizado_em.
DO $$
DECLARE
  v_antes timestamptz;
  v_depois timestamptz;
BEGIN
  UPDATE inquilinos
     SET telefone = '79999990000'
   WHERE id = '31000000-0000-4000-8000-000000000001';

  SELECT atualizado_em INTO v_depois
    FROM inquilinos WHERE id = '31000000-0000-4000-8000-000000000001';
  IF v_depois IS NULL THEN
    RAISE EXCEPTION 'trigger de auditoria não carimbou atualizado_em no telefone';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM inquilinos
     WHERE id = '31000000-0000-4000-8000-000000000001'
       AND telefone = '79999990000'
  ) THEN
    RAISE EXCEPTION 'telefone válido não foi gravado';
  END IF;
END $$;

-- Negativos: telefone com letras, curto demais e longo demais.
DO $$
BEGIN
  BEGIN
    UPDATE inquilinos SET telefone = 'abc' WHERE id = '31000000-0000-4000-8000-000000000002';
    RAISE EXCEPTION 'aceitou telefone com letras';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    UPDATE inquilinos SET telefone = '123' WHERE id = '31000000-0000-4000-8000-000000000002';
    RAISE EXCEPTION 'aceitou telefone curto';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    UPDATE inquilinos SET telefone = '799999900001' WHERE id = '31000000-0000-4000-8000-000000000002';
    RAISE EXCEPTION 'aceitou telefone longo';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
END $$;

-- Positivo: NULL continua permitido.
UPDATE inquilinos SET telefone = NULL WHERE id = '31000000-0000-4000-8000-000000000003';
