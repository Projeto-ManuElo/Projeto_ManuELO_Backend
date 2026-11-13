package com.Projeto.manuelo.servico;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.Projeto.manuelo.dominio.enums.PerfilUsuario;
import com.Projeto.manuelo.seguranca.UsuarioDetalhes;

/**
 * A-32: pagamento de aluguel transacional (R05/R07, DEC-05). A movimentação
 * ENTRADA/ALUGUEL e, quando aplicável, o abatimento da caução são gravados na
 * mesma transação, com lock do contrato para evitar saldo negativo concorrente.
 */
@Service
public class PagamentoAluguelService {

    private static final DateTimeFormatter MES_ANO =
            DateTimeFormatter.ofPattern("MM/yyyy", Locale.forLanguageTag("pt-BR"));

    private final JdbcClient jdbc;

    public PagamentoAluguelService(JdbcTemplate jdbcTemplate) {
        this.jdbc = JdbcClient.create(jdbcTemplate);
    }

    @Transactional
    public void registrar(String contratoId, BigDecimal valor, String forma, boolean caucao) {
        registrar(contratoId, LocalDate.now(), valor, forma, caucao);
    }

    @Transactional
    public void registrar(String contratoId, LocalDate competencia, BigDecimal valor,
                          String forma, boolean caucao) {
        if (valor == null || valor.signum() <= 0) {
            throw new IllegalArgumentException("Informe um valor maior que zero.");
        }

        LocalDate competenciaMes = competencia == null
                ? LocalDate.now().withDayOfMonth(1)
                : competencia.withDayOfMonth(1);
        if (competenciaMes.isAfter(LocalDate.now().withDayOfMonth(1))) {
            throw new IllegalArgumentException("Competência não pode ser futura.");
        }

        UsuarioDetalhes ator = ator();
        UUID contrato = contrato(contratoId);

        ContratoRef dados = jdbc.sql("""
                SELECT imovel_id, acompanhar_pagamento_desde
                  FROM contratos
                 WHERE id = :id
                   FOR UPDATE
                """)
                .param("id", contrato)
                .query((rs, rowNum) -> new ContratoRef(
                        rs.getObject("imovel_id", UUID.class),
                        rs.getObject("acompanhar_pagamento_desde", LocalDate.class)))
                .optional()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Contrato não encontrado: " + contratoId));

        if (dados.acompanharPagamentoDesde() != null
                && competenciaMes.isBefore(dados.acompanharPagamentoDesde().withDayOfMonth(1))) {
            throw new IllegalArgumentException(
                    "Competência anterior ao início do acompanhamento.");
        }

        UUID imovel = dados.imovelId();

        if (caucao) {
            BigDecimal saldo = jdbc.sql("""
                    SELECT COALESCE(saldo, 0)
                      FROM vw_saldo_caucao
                     WHERE contrato_id = :id
                    """)
                    .param("id", contrato)
                    .query(BigDecimal.class)
                    .optional()
                    .orElse(BigDecimal.ZERO);
            if (saldo.compareTo(valor) < 0) {
                throw new IllegalStateException("Saldo de caução insuficiente.");
            }
        }

        boolean jaPaga = jdbc.sql("""
                SELECT 1
                  FROM movimentacoes m
                 WHERE m.contrato_id = :contrato
                   AND m.tipo = 'ENTRADA'
                   AND m.categoria = 'ALUGUEL'
                   AND m.competencia = :competencia
                   AND NOT EXISTS (
                     SELECT 1
                       FROM movimentacoes e
                      WHERE e.movimentacao_original_id = m.id
                        AND e.tipo = 'ESTORNO'
                   )
                """)
                .param("contrato", contrato)
                .param("competencia", competenciaMes)
                .query(Integer.class)
                .optional()
                .isPresent();
        if (jaPaga) {
            throw new IllegalStateException("Esta competência já foi paga para o contrato.");
        }

        LocalDate hoje = LocalDate.now();
        String formaEnum = caucao ? "CAUCAO" : formaEnum(forma);
        String descricao = "Aluguel " + MES_ANO.format(competenciaMes)
                + (caucao ? " (caução)" : "");

        UUID movimentacao = UUID.fromString(jdbc.sql("""
                INSERT INTO movimentacoes (tipo, categoria, valor, data_movimentacao, descricao,
                                           forma_pagamento, imovel_id, responsavel_id, contrato_id,
                                           competencia)
                VALUES ('ENTRADA', 'ALUGUEL', :valor, :data, :descricao,
                        CAST(:forma AS forma_pagamento),
                        :imovel, :responsavel, :contrato,
                        :competencia)
                RETURNING id::text
                """)
                .param("valor", valor)
                .param("data", hoje)
                .param("descricao", descricao)
                .param("forma", formaEnum)
                .param("imovel", imovel)
                .param("responsavel", ator.getId())
                .param("contrato", contrato)
                .param("competencia", competenciaMes)
                .query(String.class)
                .single());

        if (caucao) {
            jdbc.sql("""
                    INSERT INTO caucao_movimentacoes (contrato_id, tipo, valor, data,
                                                      movimentacao_id, criado_por)
                    VALUES (:contrato, 'ABATIMENTO_ALUGUEL', :valor, :data,
                            :movimentacao, :responsavel)
                    """)
                    .param("contrato", contrato)
                    .param("valor", valor)
                    .param("data", hoje)
                    .param("movimentacao", movimentacao)
                    .param("responsavel", ator.getId())
                    .update();
        }
    }

    private UsuarioDetalhes ator() {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao == null
                || !(autenticacao.getPrincipal() instanceof UsuarioDetalhes detalhes)
                || detalhes.getId() == null) {
            throw new IllegalStateException("Usuário autenticado obrigatório");
        }
        PerfilUsuario perfil = detalhes.getPerfil();
        if (perfil != PerfilUsuario.ADMINISTRADOR && perfil != PerfilUsuario.COLABORADOR) {
            throw new AccessDeniedException(
                    "Apenas administradores e colaboradores podem registrar pagamentos.");
        }
        return detalhes;
    }

    private UUID contrato(String contratoId) {
        if (contratoId == null || contratoId.isBlank()) {
            throw new IllegalArgumentException("Informe o contrato.");
        }
        try {
            return UUID.fromString(contratoId.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Identificador do contrato inválido.", ex);
        }
    }

    private record ContratoRef(UUID imovelId, LocalDate acompanharPagamentoDesde) {
    }

    private String formaEnum(String forma) {
        if (forma == null || forma.isBlank()) {
            return "PIX";
        }
        return switch (forma.trim().toLowerCase(Locale.ROOT)) {
            case "boleto" -> "BOLETO";
            case "dinheiro" -> "DINHEIRO";
            case "transferência", "transferencia" -> "TRANSFERENCIA";
            case "caução", "caucao" -> "CAUCAO";
            case "pix" -> "PIX";
            default -> "PIX";
        };
    }
}
