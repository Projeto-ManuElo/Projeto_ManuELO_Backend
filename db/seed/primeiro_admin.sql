-- Cria o primeiro administrador sem gravar segredo no repositório.
-- Uso:
--   psql "$DATABASE_URL" \
--     -v nome="Fulano" -v email="fulano@dominio" -v senha_hash='<bcrypt>' \
--     -f db/seed/primeiro_admin.sql
-- O hash deve vir do backend (BCrypt), nunca em texto puro.
\set ON_ERROR_STOP on

INSERT INTO usuarios (nome, email, senha_hash, perfil, status)
VALUES (:'nome', :'email', :'senha_hash', 'ADMINISTRADOR', 'PENDENTE_APROVACAO')
ON CONFLICT DO NOTHING;

UPDATE usuarios
   SET perfil = 'ADMINISTRADOR',
       status = 'ATIVO',
       aprovado_por = id,
       aprovado_em = now()
 WHERE email = :'email'
   AND status = 'PENDENTE_APROVACAO';
