package com.Projeto.manuelo.servico;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.Projeto.manuelo.seguranca.UsuarioDetalhes;

/**
 * A-21: cadastro transacional de inquilino. Um único envio cria inquilino, fiador
 * (quando informado), contrato vigente e depósito de caução na mesma transação do
 * chamador; qualquer falha desfaz tudo. O ator vem do SecurityContext (DEC-I11).
 */
@Service
public class InquilinoCadastroService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+$");

    public record Dados(String inquilinoId, String nome, String documento, String email,
            String telefone, String nomeFiador, String documentoFiador, String emailFiador,
            String imovelId, BigDecimal aluguel, int diaVencimento, BigDecimal caucao,
            String reciboCaminho) {
    }

    private final JdbcClient jdbc;

    public InquilinoCadastroService(JdbcTemplate jdbcTemplate) {
        this.jdbc = JdbcClient.create(jdbcTemplate);
    }

    @Transactional
    public String salvar(Dados dados) {
        if (dados == null) {
            throw new IllegalArgumentException("Dados do inquilino são obrigatórios.");
        }

        String nome = textoObrigatorio(dados.nome(), "Informe o nome do inquilino.");
        String documento = somenteDigitos(dados.documento());
        validarDocumento(documento, "do inquilino");
        String email = normalizarEmail(dados.email(), "E-mail do inquilino inválido.");
        String telefone = normalizarTelefone(dados.telefone());

        String nomeFiador = textoOpcional(dados.nomeFiador());
        String documentoFiador = somenteDigitos(dados.documentoFiador());
        String emailFiador = normalizarEmail(dados.emailFiador(), "E-mail do fiador inválido.");
        if (!documentoFiador.isEmpty()) {
            validarDocumento(documentoFiador, "do fiador");
            if (nomeFiador == null) {
                throw new IllegalArgumentException("Informe o nome do fiador.");
            }
        } else if (nomeFiador != null || emailFiador != null) {
            throw new IllegalArgumentException("Informe o CPF/CNPJ do fiador.");
        }

        UUID ator = ator();

        if (dados.inquilinoId() != null && !dados.inquilinoId().isBlank()) {
            UUID inquilinoId = uuid(dados.inquilinoId(), "Identificador do inquilino inválido.");
            try {
                int atualizados = jdbc.sql("""
                        UPDATE inquilinos
                           SET nome = :nome, documento = :documento, email = :email,
                               telefone = :telefone, atualizado_por = :ator
                         WHERE id = :id
                        """)
                        .param("nome", nome)
                        .param("documento", documento)
                        .param("email", email)
                        .param("telefone", telefone)
                        .param("ator", ator)
                        .param("id", inquilinoId)
                        .update();
                if (atualizados == 0) {
                    throw new IllegalArgumentException("Inquilino não encontrado: " + inquilinoId);
                }
                return inquilinoId.toString();
            } catch (DataIntegrityViolationException ex) {
                throw new IllegalStateException(
                        "Não foi possível atualizar o inquilino: " + causa(ex), ex);
            }
        }

        BigDecimal aluguel = dados.aluguel();
        if (aluguel == null || aluguel.signum() <= 0) {
            throw new IllegalArgumentException("Informe um valor de aluguel maior que zero.");
        }
        int diaVencimento = dados.diaVencimento();
        if (diaVencimento < 1 || diaVencimento > 31) {
            throw new IllegalArgumentException("O dia de vencimento deve estar entre 1 e 31.");
        }
        BigDecimal caucao = dados.caucao() == null ? BigDecimal.ZERO : dados.caucao();
        if (caucao.signum() < 0) {
            throw new IllegalArgumentException("O valor da caução não pode ser negativo.");
        }
        UUID imovelId = uuid(dados.imovelId(), "Selecione o imóvel do contrato.");

        try {
            if (!existeImovel(imovelId)) {
                throw new IllegalArgumentException("Imóvel não encontrado: " + imovelId);
            }
            if (existeContratoVigente(imovelId)) {
                throw new IllegalStateException("Já existe contrato vigente para este imóvel.");
            }

            Optional<UUID> fiadorExistente = documentoFiador.isEmpty()
                    ? Optional.empty()
                    : buscarFiadorPorDocumento(documentoFiador);

            UUID inquilinoId = UUID.randomUUID();
            jdbc.sql("""
                    INSERT INTO inquilinos (id, nome, documento, email, telefone, criado_por)
                    VALUES (:id, :nome, :documento, :email, :telefone, :ator)
                    """)
                    .param("id", inquilinoId)
                    .param("nome", nome)
                    .param("documento", documento)
                    .param("email", email)
                    .param("telefone", telefone)
                    .param("ator", ator)
                    .update();

            UUID fiadorId = null;
            if (!documentoFiador.isEmpty()) {
                if (fiadorExistente.isPresent()) {
                    fiadorId = fiadorExistente.get();
                    jdbc.sql("""
                            UPDATE fiadores
                               SET nome = :nome, email = :email, atualizado_por = :ator
                             WHERE id = :id
                            """)
                            .param("nome", nomeFiador)
                            .param("email", emailFiador)
                            .param("ator", ator)
                            .param("id", fiadorId)
                            .update();
                } else {
                    fiadorId = UUID.randomUUID();
                    jdbc.sql("""
                            INSERT INTO fiadores (id, nome, documento, email, criado_por)
                            VALUES (:id, :nome, :documento, :email, :ator)
                            """)
                            .param("id", fiadorId)
                            .param("nome", nomeFiador)
                            .param("documento", documentoFiador)
                            .param("email", emailFiador)
                            .param("ator", ator)
                            .update();
                }
            }

            UUID contratoId = UUID.randomUUID();
            jdbc.sql("""
                    INSERT INTO contratos (id, imovel_id, inquilino_id, fiador_id, inicio, fim,
                                           valor_aluguel, dia_vencimento,
                                           acompanhar_pagamento_desde, possui_acao_judicial,
                                           criado_por)
                    VALUES (:id, :imovel, :inquilino, :fiador, current_date, NULL,
                            :aluguel, :dia, current_date, false, :ator)
                    """)
                    .param("id", contratoId)
                    .param("imovel", imovelId)
                    .param("inquilino", inquilinoId)
                    .param("fiador", fiadorId)
                    .param("aluguel", aluguel)
                    .param("dia", diaVencimento)
                    .param("ator", ator)
                    .update();

            if (caucao.signum() > 0) {
                jdbc.sql("""
                        INSERT INTO caucao_movimentacoes (contrato_id, tipo, valor, data,
                                                          criado_por)
                        VALUES (:contrato, 'DEPOSITO'::tipo_caucao, :valor, current_date, :ator)
                        """)
                        .param("contrato", contratoId)
                        .param("valor", caucao)
                        .param("ator", ator)
                        .update();
            }

            return inquilinoId.toString();
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalStateException(
                    "Não foi possível concluir o cadastro do inquilino: " + causa(ex), ex);
        }
    }

    private boolean existeImovel(UUID imovelId) {
        return jdbc.sql("SELECT 1 FROM imoveis WHERE id = :id")
                .param("id", imovelId)
                .query(Integer.class)
                .optional()
                .isPresent();
    }

    private boolean existeContratoVigente(UUID imovelId) {
        Integer vigentes = jdbc.sql("""
                SELECT count(*)
                  FROM contratos
                 WHERE imovel_id = :imovel
                   AND inicio <= current_date
                   AND (fim IS NULL OR fim >= current_date)
                """)
                .param("imovel", imovelId)
                .query(Integer.class)
                .single();
        return vigentes != null && vigentes > 0;
    }

    private Optional<UUID> buscarFiadorPorDocumento(String documento) {
        return jdbc.sql("SELECT id FROM fiadores WHERE documento = :documento")
                .param("documento", documento)
                .query(UUID.class)
                .optional();
    }

    private UUID ator() {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao == null
                || !(autenticacao.getPrincipal() instanceof UsuarioDetalhes detalhes)
                || detalhes.getId() == null) {
            throw new IllegalStateException("Usuário autenticado obrigatório");
        }
        return detalhes.getId();
    }

    private String textoObrigatorio(String valor, String mensagem) {
        String texto = textoOpcional(valor);
        if (texto == null) {
            throw new IllegalArgumentException(mensagem);
        }
        return texto;
    }

    private String textoOpcional(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim();
    }

    private String normalizarEmail(String valor, String mensagem) {
        String email = textoOpcional(valor);
        if (email == null) {
            return null;
        }
        if (!EMAIL.matcher(email).matches()) {
            throw new IllegalArgumentException(mensagem);
        }
        return email;
    }

    private String normalizarTelefone(String valor) {
        String telefone = somenteDigitos(valor);
        if (telefone.isEmpty()) {
            return null;
        }
        if (telefone.length() != 10 && telefone.length() != 11) {
            throw new IllegalArgumentException("Telefone deve ter 10 ou 11 dígitos.");
        }
        return telefone;
    }

    private String somenteDigitos(String valor) {
        return valor == null ? "" : valor.replaceAll("\\D", "");
    }

    private void validarDocumento(String documento, String sujeito) {
        if (documento.length() != 11 && documento.length() != 14) {
            throw new IllegalArgumentException(
                    "CPF/CNPJ " + sujeito + " deve ter 11 ou 14 dígitos.");
        }
    }

    private UUID uuid(String valor, String mensagem) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(mensagem);
        }
        try {
            return UUID.fromString(valor.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(mensagem, ex);
        }
    }

    private String causa(DataIntegrityViolationException ex) {
        Throwable causa = ex.getMostSpecificCause();
        String mensagem = causa == null ? null : causa.getMessage();
        return mensagem == null || mensagem.isBlank() ? "violação de integridade" : mensagem;
    }
}
