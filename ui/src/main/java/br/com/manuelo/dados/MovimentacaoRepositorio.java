package br.com.manuelo.dados;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import br.com.manuelo.Modelos;

@Repository
public class MovimentacaoRepositorio {

    public record Resumo(BigDecimal entradas, BigDecimal saidas, BigDecimal saldo, int quantidade) {
    }

    private static final String SELECT_EXTRATO = """
            SELECT mv.data_movimentacao,
                   mv.descricao,
                   mv.categoria::text AS categoria,
                   CASE
                     WHEN mv.tipo = 'ENTRADA' THEN mv.valor
                     WHEN mv.tipo = 'SAIDA' THEN -mv.valor
                     WHEN mv.tipo = 'ESTORNO' AND o.tipo = 'ENTRADA' THEN -mv.valor
                     WHEN mv.tipo = 'ESTORNO' AND o.tipo = 'SAIDA' THEN mv.valor
                     ELSE mv.valor
                   END AS valor_assinado
              FROM movimentacoes mv
              LEFT JOIN movimentacoes o ON o.id = mv.movimentacao_original_id
            """;

    private final JdbcClient jdbc;
    private final SessaoUsuario sessao;

    public MovimentacaoRepositorio(JdbcTemplate jdbcTemplate, SessaoUsuario sessao) {
        this.jdbc = JdbcClient.create(jdbcTemplate);
        this.sessao = sessao;
    }

    public List<Modelos.Movimentacao> listar(int limite) {
        return jdbc.sql(SELECT_EXTRATO + " ORDER BY mv.data_movimentacao DESC, mv.criado_em DESC LIMIT :limite")
                .param("limite", limite)
                .query((rs, row) -> new Modelos.Movimentacao(
                        rs.getObject("data_movimentacao", LocalDate.class),
                        rs.getString("descricao"),
                        Dominio.categoria(rs.getString("categoria")),
                        rs.getBigDecimal("valor_assinado")))
                .list();
    }

    public List<Modelos.Movimentacao> extrato(LocalDate inicio, LocalDate fim, int limite) {
        return jdbc.sql(SELECT_EXTRATO
                + " WHERE mv.data_movimentacao BETWEEN :inicio AND :fim"
                + " ORDER BY mv.data_movimentacao DESC, mv.criado_em DESC LIMIT :limite")
                .param("inicio", inicio)
                .param("fim", fim)
                .param("limite", limite)
                .query((rs, row) -> new Modelos.Movimentacao(
                        rs.getObject("data_movimentacao", LocalDate.class),
                        rs.getString("descricao"),
                        Dominio.categoria(rs.getString("categoria")),
                        rs.getBigDecimal("valor_assinado")))
                .list();
    }

    public Resumo resumo(LocalDate inicio, LocalDate fim) {
        return jdbc.sql("SELECT * FROM fn_resumo_periodo(:inicio, :fim)")
                .param("inicio", inicio)
                .param("fim", fim)
                .query((rs, row) -> new Resumo(
                        rs.getBigDecimal("total_entradas"),
                        rs.getBigDecimal("total_saidas"),
                        rs.getBigDecimal("saldo"),
                        rs.getInt("qtd_registros")))
                .single();
    }

    public void registrar(boolean entrada, String categoriaUi, String contratoId, BigDecimal valor,
            LocalDate data, String descricao, String formaUi) {
        if (valor == null || valor.signum() <= 0) {
            throw new IllegalArgumentException("Informe um valor maior que zero.");
        }
        if (descricao == null || descricao.isBlank()) {
            throw new IllegalArgumentException("Informe a descrição do lançamento.");
        }
        LocalDate quando = data == null ? LocalDate.now() : data;
        UUID usuario = UUID.fromString(sessao.id());

        String categoria;
        String origem = null;
        UUID contrato = null;
        UUID imovel = null;
        LocalDate competencia = null;

        if (entrada) {
            if ("Aluguel".equals(categoriaUi)) {
                if (contratoId == null || contratoId.isBlank()) {
                    throw new IllegalArgumentException("Selecione o contrato do aluguel.");
                }
                contrato = UUID.fromString(contratoId);
                imovel = UUID.fromString(jdbc.sql("SELECT imovel_id::text FROM contratos WHERE id = :id")
                        .param("id", contrato)
                        .query(String.class)
                        .single());
                categoria = "ALUGUEL";
                competencia = quando.withDayOfMonth(1);
            } else {
                categoria = "OUTRAS_ENTRADAS";
                origem = categoriaUi == null || categoriaUi.isBlank() ? "Outras entradas" : categoriaUi;
            }
        } else {
            switch (categoriaUi == null ? "" : categoriaUi) {
                case "Manutenção" -> categoria = "MANUTENCAO";
                case "Tributos" -> categoria = "TRIBUTOS";
                default -> {
                    categoria = "OUTRAS_SAIDAS";
                    origem = categoriaUi == null || categoriaUi.isBlank() ? "Outras saídas" : categoriaUi;
                }
            }
        }

        jdbc.sql("""
                INSERT INTO movimentacoes (tipo, categoria, valor, data_movimentacao, descricao,
                                           forma_pagamento, imovel_id, responsavel_id, contrato_id,
                                           competencia, origem_descricao)
                VALUES (CAST(:tipo AS tipo_movimentacao), CAST(:categoria AS categoria_movimentacao),
                        :valor, :data, :descricao, CAST(:forma AS forma_pagamento),
                        :imovel, :usuario, :contrato, :competencia, :origem)
                """)
                .param("tipo", entrada ? "ENTRADA" : "SAIDA")
                .param("categoria", categoria)
                .param("valor", valor)
                .param("data", quando)
                .param("descricao", descricao.trim())
                .param("forma", Dominio.formaPagamentoEnum(formaUi))
                .param("imovel", imovel)
                .param("usuario", usuario)
                .param("contrato", contrato)
                .param("competencia", competencia)
                .param("origem", origem)
                .update();
    }
}
