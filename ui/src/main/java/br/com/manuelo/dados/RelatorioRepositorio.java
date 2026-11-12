package br.com.manuelo.dados;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class RelatorioRepositorio {

    private final JdbcClient jdbc;
    private final SessaoUsuario sessao;

    public RelatorioRepositorio(JdbcTemplate jdbcTemplate, SessaoUsuario sessao) {
        this.jdbc = JdbcClient.create(jdbcTemplate);
        this.sessao = sessao;
    }

    public List<List<String>> linhasContratos(String tipoUi) {
        boolean atrasados = "Inadimplência".equals(tipoUi);
        String sql = atrasados
                ? """
                  SELECT inquilino_nome, imovel_endereco, dias_atraso, valor_em_atraso
                    FROM vw_contratos_situacao WHERE atrasado ORDER BY dias_atraso DESC
                  """
                : """
                  SELECT inquilino_nome, imovel_endereco, COALESCE(dias_para_vencer, 0), valor_aluguel
                    FROM vw_contratos_situacao ORDER BY fim NULLS LAST, inquilino_nome
                  """;
        return jdbc.sql(sql)
                .query((rs, row) -> List.of(
                        rs.getString("inquilino_nome"),
                        rs.getString("imovel_endereco"),
                        atrasados ? rs.getInt("dias_atraso") + " dias de atraso" : "Situação do contrato",
                        "R$ " + rs.getBigDecimal(atrasados ? "valor_em_atraso" : "valor_aluguel")
                                .toPlainString().replace(".", ",")))
                .list();
    }

    public void registrar(String tipoUi, LocalDate inicio, LocalDate fim,
            MovimentacaoRepositorio.Resumo resumo, int qtdRegistros, String caminho) {
        String tipo = switch (tipoUi) {
            case "Movimentação financeira" -> "MOVIMENTACAO";
            case "Inadimplência" -> "CONTRATOS_ATRASADOS";
            default -> "CONTRATOS_EXISTENTES";
        };
        UUID id = UUID.randomUUID();
        UUID usuario = UUID.fromString(sessao.id());
        String nome = "Relatório de " + tipoUi.toLowerCase(java.util.Locale.ROOT) + " - "
                + LocalDate.now();

        if ("MOVIMENTACAO".equals(tipo)) {
            jdbc.sql("""
                    INSERT INTO relatorios (id, nome, tipo, data_referencia, periodo_inicio,
                                            periodo_fim, total_entradas, total_saidas, saldo,
                                            qtd_registros, caminho_arquivo, gerado_por)
                    VALUES (:id, :nome, 'MOVIMENTACAO', current_date, :inicio, :fim,
                            :entradas, :saidas, :saldo, :qtd, :caminho, :usuario)
                    """)
                    .param("id", id)
                    .param("nome", nome)
                    .param("inicio", inicio)
                    .param("fim", fim)
                    .param("entradas", resumo.entradas())
                    .param("saidas", resumo.saidas())
                    .param("saldo", resumo.saldo())
                    .param("qtd", resumo.quantidade())
                    .param("caminho", caminho)
                    .param("usuario", usuario)
                    .update();

            jdbc.sql("""
                    INSERT INTO relatorio_movimentacoes (relatorio_id, movimentacao_id, ordem)
                    SELECT :id, id,
                           (row_number() OVER (ORDER BY data_movimentacao, criado_em, id))::int
                      FROM movimentacoes
                     WHERE data_movimentacao BETWEEN :inicio AND :fim
                    """)
                    .param("id", id)
                    .param("inicio", inicio)
                    .param("fim", fim)
                    .update();
            return;
        }

        jdbc.sql("""
                INSERT INTO relatorios (id, nome, tipo, data_referencia, qtd_registros,
                                        caminho_arquivo, gerado_por)
                VALUES (:id, :nome, CAST(:tipo AS tipo_relatorio), current_date, :qtd, :caminho, :usuario)
                """)
                .param("id", id)
                .param("nome", nome)
                .param("tipo", tipo)
                .param("qtd", qtdRegistros)
                .param("caminho", caminho)
                .param("usuario", usuario)
                .update();

        if ("CONTRATOS_ATRASADOS".equals(tipo)) {
            jdbc.sql("""
                    INSERT INTO relatorio_contratos (relatorio_id, contrato_id, ordem, situacao, dias)
                    SELECT :id, contrato_id,
                           (row_number() OVER (ORDER BY dias_atraso DESC, contrato_id))::int,
                           'ATRASADO', dias_atraso
                      FROM vw_contratos_situacao
                     WHERE atrasado
                    """)
                    .param("id", id)
                    .update();
        } else {
            jdbc.sql("""
                    INSERT INTO relatorio_contratos (relatorio_id, contrato_id, ordem, situacao)
                    SELECT :id, contrato_id,
                           (row_number() OVER (ORDER BY contrato_id))::int, 'EXISTENTE'
                      FROM vw_contratos_situacao
                    """)
                    .param("id", id)
                    .update();
        }
    }
}
