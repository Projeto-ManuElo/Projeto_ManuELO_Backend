package com.Projeto.manuelo.servico;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Consultas de dados dos relatórios. Não conhece PDF; devolve records imutáveis.
 */
final class RelatorioDados {

    record ContratoLinha(String contratoId, String inquilino, String documento,
            String imovel, LocalDate inicio, LocalDate fim, String situacaoValidade,
            boolean atrasado, Integer diasAtraso, Integer diasParaVencer,
            BigDecimal valorAluguel, BigDecimal valorEmAtraso) {
    }

    record MovimentoLinha(String id, String tipo, String tipoOriginal, String categoria,
            String categoriaOriginal, BigDecimal valor, BigDecimal valorAssinado, LocalDate data,
            String descricao, String forma, String origem, String favorecido, String imovel,
            String inquilino, String responsavel, String justificativa, String descricaoOriginal,
            boolean comprovante) {
    }

    private final JdbcClient jdbc;

    RelatorioDados(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    List<ContratoLinha> contratos(String condicao, String ordem) {
        return jdbc.sql("""
                SELECT contrato_id, inquilino_nome, inquilino_documento, imovel_endereco,
                       inicio, fim, situacao_validade, atrasado, dias_atraso, dias_para_vencer,
                       valor_aluguel, valor_em_atraso
                  FROM vw_contratos_situacao
                """ + " " + condicao + " ORDER BY " + ordem)
                .query((rs, rowNum) -> new ContratoLinha(
                        rs.getObject("contrato_id", UUID.class).toString(),
                        rs.getString("inquilino_nome"),
                        rs.getString("inquilino_documento"),
                        rs.getString("imovel_endereco"),
                        rs.getObject("inicio", LocalDate.class),
                        rs.getObject("fim", LocalDate.class),
                        rs.getString("situacao_validade"),
                        rs.getBoolean("atrasado"),
                        rs.getObject("dias_atraso", Integer.class),
                        rs.getObject("dias_para_vencer", Integer.class),
                        rs.getBigDecimal("valor_aluguel"),
                        rs.getBigDecimal("valor_em_atraso")))
                .list();
    }

    List<MovimentoLinha> movimentacoes(LocalDate inicio, LocalDate fim, String filtro) {
        StringBuilder condicoes = new StringBuilder(
                " WHERE mv.data_movimentacao BETWEEN :inicio AND :fim");
        if ("ENTRADA".equals(filtro)) {
            condicoes.append(" AND (mv.tipo = 'ENTRADA'"
                    + " OR (mv.tipo = 'ESTORNO' AND o.tipo = 'ENTRADA'))");
        } else if ("SAIDA".equals(filtro)) {
            condicoes.append(" AND (mv.tipo = 'SAIDA'"
                    + " OR (mv.tipo = 'ESTORNO' AND o.tipo = 'SAIDA'))");
        }
        return jdbc.sql("""
                SELECT mv.id,
                       mv.tipo::text AS tipo,
                       o.tipo::text AS tipo_original,
                       mv.categoria::text AS categoria,
                       o.categoria::text AS categoria_original,
                       mv.valor,
                       CASE
                         WHEN mv.tipo = 'ENTRADA' THEN mv.valor
                         WHEN mv.tipo = 'SAIDA' THEN -mv.valor
                         WHEN mv.tipo = 'ESTORNO' AND o.tipo = 'ENTRADA' THEN -mv.valor
                         WHEN mv.tipo = 'ESTORNO' AND o.tipo = 'SAIDA' THEN mv.valor
                         ELSE mv.valor
                       END AS valor_assinado,
                       mv.data_movimentacao,
                       mv.descricao,
                       mv.forma_pagamento::text AS forma,
                       mv.origem_descricao,
                       mv.favorecido,
                       im.logradouro || ', ' || im.numero AS imovel,
                       inq.nome AS inquilino,
                       u.nome AS responsavel,
                       mv.justificativa,
                       o.descricao AS descricao_original,
                       EXISTS (
                         SELECT 1 FROM documentos d
                          WHERE d.movimentacao_id = mv.id
                            AND d.tipo = 'COMPROVANTE'
                       ) AS comprovante
                  FROM movimentacoes mv
                  LEFT JOIN movimentacoes o ON o.id = mv.movimentacao_original_id
                  LEFT JOIN imoveis im ON im.id = mv.imovel_id
                  LEFT JOIN contratos c ON c.id = mv.contrato_id
                  LEFT JOIN inquilinos inq ON inq.id = c.inquilino_id
                  JOIN usuarios u ON u.id = mv.responsavel_id
                """ + condicoes + """
                 ORDER BY mv.data_movimentacao, mv.criado_em, mv.id
                """)
                .param("inicio", inicio)
                .param("fim", fim)
                .query((rs, rowNum) -> new MovimentoLinha(
                        rs.getObject("id", UUID.class).toString(),
                        rs.getString("tipo"),
                        rs.getString("tipo_original"),
                        rs.getString("categoria"),
                        rs.getString("categoria_original"),
                        rs.getBigDecimal("valor"),
                        rs.getBigDecimal("valor_assinado"),
                        rs.getObject("data_movimentacao", LocalDate.class),
                        rs.getString("descricao"),
                        rs.getString("forma"),
                        rs.getString("origem_descricao"),
                        rs.getString("favorecido"),
                        rs.getString("imovel"),
                        rs.getString("inquilino"),
                        rs.getString("responsavel"),
                        rs.getString("justificativa"),
                        rs.getString("descricao_original"),
                        rs.getBoolean("comprovante")))
                .list();
    }

    int janelaDias() {
        return jdbc.sql("""
                SELECT valor::int
                  FROM configuracoes
                 WHERE chave = 'dias_alerta_vencimento'
                """)
                .query(Integer.class)
                .optional()
                .orElse(7);
    }
}
