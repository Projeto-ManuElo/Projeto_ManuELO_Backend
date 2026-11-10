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
    SELECT tablename FROM pg_tables
     WHERE schemaname = 'public'
       AND tablename <> 'flyway_schema_history'
     ORDER BY tablename
  LOOP
    EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', t.tablename);
    EXECUTE format('DROP POLICY IF EXISTS manuelo_app_all ON public.%I', t.tablename);
    EXECUTE format(
      'CREATE POLICY manuelo_app_all ON public.%I FOR ALL TO manuelo_app USING (true) WITH CHECK (true)',
      t.tablename
    );
  END LOOP;
END $$;