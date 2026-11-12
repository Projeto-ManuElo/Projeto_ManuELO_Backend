-- V012: telefone do inquilino.
-- O formulário de cadastro de inquilino já coleta o telefone; a tabela não tinha a coluna.
-- Formato normalizado em dígitos, como em fiadores (DEC-16).

ALTER TABLE inquilinos
  ADD COLUMN telefone varchar(20);

ALTER TABLE inquilinos
  ADD CONSTRAINT chk_inquilinos_telefone CHECK (
    telefone IS NULL OR telefone ~ '^[0-9]{10,11}$'
  );
