-- T11: RLS, revokes e privilégios mínimos da role manuelo_app.

-- Toda tabela do schema public tem RLS ligado.
DO $$
DECLARE
  sem_rls text;
BEGIN
  SELECT string_agg(c.relname, ', ' ORDER BY c.relname)
    INTO sem_rls
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
   WHERE n.nspname = 'public'
     AND c.relkind = 'r'
     AND NOT c.relrowsecurity;
  IF sem_rls IS NOT NULL THEN
    RAISE EXCEPTION 'tabelas sem RLS: %', sem_rls;
  END IF;
END $$;

-- anon/authenticated: sem privilégio algum nas tabelas.
DO $$
BEGIN
  IF has_table_privilege('anon', 'public.usuarios', 'SELECT') THEN
    RAISE EXCEPTION 'anon ainda tem SELECT em usuarios';
  END IF;
  IF has_table_privilege('anon', 'public.movimentacoes', 'SELECT') THEN
    RAISE EXCEPTION 'anon ainda tem SELECT em movimentacoes';
  END IF;
  IF has_table_privilege('authenticated', 'public.usuarios', 'SELECT') THEN
    RAISE EXCEPTION 'authenticated ainda tem SELECT em usuarios';
  END IF;
END $$;

-- manuelo_app: privilégio mínimo esperado.
DO $$
BEGIN
  IF NOT has_table_privilege('manuelo_app', 'public.usuarios', 'SELECT') THEN
    RAISE EXCEPTION 'manuelo_app sem SELECT em usuarios';
  END IF;
  IF NOT has_table_privilege('manuelo_app', 'public.usuarios', 'INSERT') THEN
    RAISE EXCEPTION 'manuelo_app sem INSERT em usuarios';
  END IF;
  IF NOT has_table_privilege('manuelo_app', 'public.usuarios', 'UPDATE') THEN
    RAISE EXCEPTION 'manuelo_app sem UPDATE em usuarios';
  END IF;
  IF has_table_privilege('manuelo_app', 'public.usuarios', 'DELETE') THEN
    RAISE EXCEPTION 'manuelo_app não deveria ter DELETE em usuarios';
  END IF;
  IF NOT has_table_privilege('manuelo_app', 'public.movimentacoes', 'INSERT') THEN
    RAISE EXCEPTION 'manuelo_app sem INSERT em movimentacoes';
  END IF;
  IF has_table_privilege('manuelo_app', 'public.movimentacoes', 'UPDATE') THEN
    RAISE EXCEPTION 'manuelo_app não deveria ter UPDATE em movimentacoes';
  END IF;
  IF has_table_privilege('manuelo_app', 'public.movimentacoes', 'DELETE') THEN
    RAISE EXCEPTION 'manuelo_app não deveria ter DELETE em movimentacoes';
  END IF;
  IF has_table_privilege('manuelo_app', 'public.configuracoes', 'UPDATE') THEN
    NULL;
  ELSE
    RAISE EXCEPTION 'manuelo_app deveria poder atualizar configuracoes';
  END IF;
END $$;

-- manuelo_app acessa dados (RLS com política permissiva para a role).
DO $$
DECLARE
  v_qtd integer;
BEGIN
  SET LOCAL ROLE manuelo_app;
  SELECT count(*) INTO v_qtd FROM usuarios;
  SELECT count(*) INTO v_qtd FROM vw_home_indicadores;
  RESET ROLE;
END $$;

-- anon e authenticated são barrados.
DO $$
BEGIN
  BEGIN
    SET LOCAL ROLE anon;
    PERFORM count(*) FROM usuarios;
    RESET ROLE;
    RAISE EXCEPTION 'anon conseguiu ler usuarios';
  EXCEPTION WHEN insufficient_privilege THEN
    RESET ROLE;
  END;

  BEGIN
    SET LOCAL ROLE authenticated;
    PERFORM count(*) FROM movimentacoes;
    RESET ROLE;
    RAISE EXCEPTION 'authenticated conseguiu ler movimentacoes';
  EXCEPTION WHEN insufficient_privilege THEN
    RESET ROLE;
  END;
END $$;

-- manuelo_app não apaga nada.
DO $$
BEGIN
  BEGIN
    SET LOCAL ROLE manuelo_app;
    DELETE FROM movimentacoes
     WHERE id = '00000000-0000-4000-8000-000000001011';
    RESET ROLE;
    RAISE EXCEPTION 'manuelo_app conseguiu DELETE em movimentacoes';
  EXCEPTION WHEN insufficient_privilege THEN
    RESET ROLE;
  END;
END $$;
