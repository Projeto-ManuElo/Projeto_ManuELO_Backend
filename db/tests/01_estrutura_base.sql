-- T02: extensões, enums e função de auditoria.

DO $$
DECLARE
  faltando text;
BEGIN
  SELECT string_agg(e, ', ')
    INTO faltando
    FROM unnest(ARRAY['pgcrypto', 'btree_gist', 'pg_trgm']) AS e
   WHERE NOT EXISTS (SELECT 1 FROM pg_extension x WHERE x.extname = e);
  IF faltando IS NOT NULL THEN
    RAISE EXCEPTION 'extensões ausentes: %', faltando;
  END IF;
END $$;

DO $$
DECLARE
  caso record;
BEGIN
  FOR caso IN
    SELECT * FROM (VALUES
      ('perfil_usuario', 'ADMINISTRADOR,COLABORADOR'),
      ('status_usuario', 'PENDENTE_APROVACAO,ATIVO,INATIVO'),
      ('tipo_imovel', 'APARTAMENTO,CASA,COMERCIAL,TERRENO'),
      ('status_imovel', 'ALUGADO,VAGO,EM_MANUTENCAO'),
      ('tipo_movimentacao', 'ENTRADA,SAIDA,ESTORNO'),
      ('categoria_movimentacao', 'ALUGUEL,OUTRAS_ENTRADAS,MANUTENCAO,TRIBUTOS,ADMINISTRATIVO,OUTRAS_SAIDAS'),
      ('forma_pagamento', 'PIX,BOLETO,TRANSFERENCIA,DINHEIRO,CAUCAO'),
      ('tipo_caucao', 'DEPOSITO,ABATIMENTO_ALUGUEL,DEVOLUCAO'),
      ('tipo_documento', 'CONTRATO,COMPROVANTE,NOTA_FISCAL,DOCUMENTO_CONTABIL,CADASTRO'),
      ('tipo_relatorio', 'CONTRATOS_EXISTENTES,CONTRATOS_ATRASADOS,CONTRATOS_A_VENCER,MOVIMENTACAO'),
      ('filtro_tipo_movimentacao', 'ENTRADA,SAIDA'),
      ('situacao_relatorio_contrato', 'EXISTENTE,ATRASADO,A_VENCER')
    ) AS t(nome, rotulos)
  LOOP
    IF NOT EXISTS (
      SELECT 1
        FROM pg_type ty
        JOIN pg_enum en ON en.enumtypid = ty.oid
       WHERE ty.typname = caso.nome
      HAVING string_agg(en.enumlabel, ',' ORDER BY en.enumsortorder) = caso.rotulos
    ) THEN
      RAISE EXCEPTION 'enum % com rótulos inesperados (esperado %)', caso.nome, caso.rotulos;
    END IF;
  END LOOP;
END $$;

DO $$
BEGIN
  IF to_regprocedure('fn_atualizar_atualizado_em()') IS NULL THEN
    RAISE EXCEPTION 'função fn_atualizar_atualizado_em() ausente';
  END IF;
END $$;

-- Negativo: rótulo inválido deve ser rejeitado pelo enum.
DO $$
BEGIN
  BEGIN
    PERFORM 'INEXISTENTE'::perfil_usuario;
    RAISE EXCEPTION 'enum perfil_usuario aceitou rótulo inválido';
  EXCEPTION WHEN invalid_text_representation THEN
    NULL;
  END;
END $$;
