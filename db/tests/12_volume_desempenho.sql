-- T13: desempenho das views com 124 imóveis e 3040 movimentações do seed.
-- Limite: cada consulta deve responder em menos de 2 segundos.

DO $$
DECLARE
  t0 timestamptz;
  ms numeric;
  i integer;
  total numeric := 0;
BEGIN
  PERFORM * FROM vw_home_indicadores;
  PERFORM * FROM vw_contratos_situacao;
  PERFORM * FROM vw_contratos_atrasados;
  PERFORM * FROM vw_contratos_a_vencer;
  PERFORM * FROM fn_extrato_periodo(
    (current_date - interval '18 months')::date, current_date
  );

  t0 := clock_timestamp();
  PERFORM * FROM vw_home_indicadores;
  ms := extract(epoch FROM clock_timestamp() - t0) * 1000;
  IF ms >= 2000 THEN
    RAISE EXCEPTION 'vw_home_indicadores demorou % ms', round(ms, 1);
  END IF;

  t0 := clock_timestamp();
  PERFORM * FROM vw_contratos_situacao;
  ms := extract(epoch FROM clock_timestamp() - t0) * 1000;
  IF ms >= 2000 THEN
    RAISE EXCEPTION 'vw_contratos_situacao demorou % ms', round(ms, 1);
  END IF;

  t0 := clock_timestamp();
  PERFORM * FROM vw_contratos_atrasados;
  ms := extract(epoch FROM clock_timestamp() - t0) * 1000;
  IF ms >= 2000 THEN
    RAISE EXCEPTION 'vw_contratos_atrasados demorou % ms', round(ms, 1);
  END IF;

  t0 := clock_timestamp();
  PERFORM * FROM vw_contratos_a_vencer;
  ms := extract(epoch FROM clock_timestamp() - t0) * 1000;
  IF ms >= 2000 THEN
    RAISE EXCEPTION 'vw_contratos_a_vencer demorou % ms', round(ms, 1);
  END IF;

  t0 := clock_timestamp();
  PERFORM * FROM fn_extrato_periodo(
    (current_date - interval '18 months')::date, current_date
  );
  ms := extract(epoch FROM clock_timestamp() - t0) * 1000;
  IF ms >= 2000 THEN
    RAISE EXCEPTION 'fn_extrato_periodo demorou % ms', round(ms, 1);
  END IF;

  FOR i IN 1..5 LOOP
    t0 := clock_timestamp();
    PERFORM * FROM vw_home_indicadores;
    total := total + extract(epoch FROM clock_timestamp() - t0) * 1000;
  END LOOP;
  IF total / 5 >= 2000 THEN
    RAISE EXCEPTION 'média de vw_home_indicadores foi % ms', round(total / 5, 1);
  END IF;

  IF (SELECT count(*) FROM movimentacoes
       WHERE responsavel_id = '34000000-0000-4000-8000-000000000001') <> 3040 THEN
    RAISE EXCEPTION 'volume do seed mudou; atualize o teste de desempenho';
  END IF;
  IF (SELECT count(*) FROM imoveis
       WHERE id::text LIKE '30000000-0000-4000-8000-%') <> 124 THEN
    RAISE EXCEPTION 'quantidade de imóveis do seed mudou';
  END IF;
END $$;
