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
