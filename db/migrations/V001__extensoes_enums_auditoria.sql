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
