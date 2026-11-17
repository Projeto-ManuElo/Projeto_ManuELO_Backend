package com.Projeto.manuelo.servico;

import java.math.BigDecimal;
import java.time.LocalDate;
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
 * A-32/L-28: livro-razão da caução (R07). Depósito e devolução são gravados em
 * {@code caucao_movimentacoes}; a devolução nunca pode exceder o saldo atual em
 * {@code vw_saldo_caucao}. O contrato é travado para serializar operações
 * concorrentes e a recusa não grava nada.
 */
@Service
@Transactional
public class CaucaoService {

    private final JdbcClient jdbc;

    public CaucaoService(JdbcTemplate jdbcTemplate) {
        this.jdbc = JdbcClient.create(jdbcTemplate);
    }

    public String depositar(String contratoId, BigDecimal valor, LocalDate data) {
        validarValor(valor);
        LocalDate quando = dataNaoFutura(data);
        UsuarioDetalhes ator = ator();
        UUID contrato = contratoTravado(contratoId);

        return jdbc.sql("""
                INSERT INTO caucao_movimentacoes (contrato_id, tipo, valor, data, criado_por)
                VALUES (:contrato, 'DEPOSITO'::tipo_caucao, :valor, :data, :ator)
                RETURNING id::text
                """)
                .param("contrato", contrato)
                .param("valor", valor)
                .param("data", quando)
                .param("ator", ator.getId())
                .query(String.class)
                .single();
    }

    public String devolver(String contratoId, BigDecimal valor, LocalDate data) {
        validarValor(valor);
        LocalDate quando = dataNaoFutura(data);
        UsuarioDetalhes ator = ator();
        UUID contrato = contratoTravado(contratoId);

        BigDecimal saldo = jdbc.sql("""
                SELECT COALESCE(saldo, 0)
                  FROM vw_saldo_caucao
                 WHERE contrato_id = :id
                """)
                .param("id", contrato)
                .query(BigDecimal.class)
                .optional()
                .orElse(BigDecimal.ZERO);
        if (valor.compareTo(saldo) > 0) {
            throw new IllegalStateException("Saldo de caução insuficiente.");
        }

        return jdbc.sql("""
                INSERT INTO caucao_movimentacoes (contrato_id, tipo, valor, data, criado_por)
                VALUES (:contrato, 'DEVOLUCAO'::tipo_caucao, :valor, :data, :ator)
                RETURNING id::text
                """)
                .param("contrato", contrato)
                .param("valor", valor)
                .param("data", quando)
                .param("ator", ator.getId())
                .query(String.class)
                .single();
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
                    "Apenas administradores e colaboradores podem movimentar caução.");
        }
        return detalhes;
    }

    private UUID contratoTravado(String contratoId) {
        UUID contrato = contrato(contratoId);
        boolean existe = jdbc.sql("SELECT 1 FROM contratos WHERE id = :id FOR UPDATE")
                .param("id", contrato)
                .query(Integer.class)
                .optional()
                .isPresent();
        if (!existe) {
            throw new IllegalArgumentException("Contrato não encontrado: " + contratoId);
        }
        return contrato;
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

    private void validarValor(BigDecimal valor) {
        if (valor == null || valor.signum() <= 0) {
            throw new IllegalArgumentException("Informe um valor maior que zero.");
        }
    }

    private LocalDate dataNaoFutura(LocalDate data) {
        LocalDate quando = data == null ? LocalDate.now() : data;
        if (quando.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("A data não pode ser futura.");
        }
        return quando;
    }
}
