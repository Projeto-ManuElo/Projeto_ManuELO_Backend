package com.Projeto.manuelo.servico;

import java.math.BigDecimal;
import java.time.LocalDate;
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

@Service
public class ContratoCadastroService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+$");
    private static final String MENSAGEM_SOBREPOSICAO =
            "Já existe contrato para este imóvel no período informado.";

    public record Dados(String imovelId, String endereco, String nome, String cpf, String email,
            String telefone, String nomeFiador, String documentoFiador, String emailFiador,
            String telefoneFiador, boolean acaoJudicial, String acaoJudicialDescricao,
            BigDecimal aluguel, int diaVencimento, LocalDate vencimento, BigDecimal caucao,
            String contratoCaminho) {
    }

    private final JdbcClient jdbc;

    public ContratoCadastroService(JdbcTemplate jdbcTemplate) {
        this.jdbc = JdbcClient.create(jdbcTemplate);
    }

    @Transactional
    public String criar(Dados dados) {
        if (dados == null) {
            throw new IllegalArgumentException("Dados do contrato são obrigatórios.");
        }

        String nome = textoObrigatorio(dados.nome(), "Informe o nome do inquilino.");
        String documento = somenteDigitos(dados.cpf());
        validarDocumento(documento, "do inquilino");
        String email = normalizarEmail(dados.email(), "E-mail do inquilino inválido.");
        String telefone = normalizarTelefone(dados.telefone());

        String nomeFiador = textoOpcional(dados.nomeFiador());
        String documentoFiador = somenteDigitos(dados.documentoFiador());
        String emailFiador = normalizarEmail(dados.emailFiador(), "E-mail do fiador inválido.");
        String telefoneFiador = normalizarTelefone(dados.telefoneFiador());
        if (!documentoFiador.isEmpty()) {
            validarDocumento(documentoFiador, "do fiador");
            if (nomeFiador == null) {
                throw new IllegalArgumentException("Informe o nome do fiador.");
            }
        } else if (nomeFiador != null || emailFiador != null || telefoneFiador != null) {
            throw new IllegalArgumentException("Informe o CPF/CNPJ do fiador.");
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

        boolean acaoJudicial = dados.acaoJudicial();
        String acaoJudicialDescricao = textoOpcional(dados.acaoJudicialDescricao());
        if (acaoJudicial && acaoJudicialDescricao == null) {
            throw new IllegalArgumentException(
                    "Informe a descrição da ação judicial.");
        }
        if (!acaoJudicial) {
            acaoJudicialDescricao = null;
        }

        LocalDate inicio = LocalDate.now();
        LocalDate fim = dados.vencimento() == null ? inicio.plusMonths(12) : dados.vencimento();
        if (fim.isBefore(inicio)) {
            throw new IllegalArgumentException(
                    "O vencimento não pode ser anterior ao início do contrato.");
        }

        UUID ator = ator();
        UUID imovelId = resolverImovel(dados.imovelId(), dados.endereco(), ator);

        if (existeSobreposicao(imovelId, inicio, fim)) {
            throw new IllegalStateException(MENSAGEM_SOBREPOSICAO);
        }

        UUID inquilinoId = resolverInquilino(nome, documento, email, telefone, ator);
        UUID fiadorId = resolverFiador(nomeFiador, documentoFiador, emailFiador, telefoneFiador,
                ator);

        UUID contratoId = UUID.randomUUID();
        try {
            jdbc.sql("""
                    INSERT INTO contratos (id, imovel_id, inquilino_id, fiador_id, inicio, fim,
                                           valor_aluguel, dia_vencimento,
                                           acompanhar_pagamento_desde, possui_acao_judicial,
                                           acao_judicial_descricao, criado_por)
                    VALUES (:id, :imovel, :inquilino, :fiador, :inicio, :fim,
                            :aluguel, :dia, :inicio, :acaoJudicial, :acaoDescricao, :ator)
                    """)
                    .param("id", contratoId)
                    .param("imovel", imovelId)
                    .param("inquilino", inquilinoId)
                    .param("fiador", fiadorId)
                    .param("inicio", inicio)
                    .param("fim", fim)
                    .param("aluguel", aluguel)
                    .param("dia", diaVencimento)
                    .param("acaoJudicial", acaoJudicial)
                    .param("acaoDescricao", acaoJudicialDescricao)
                    .param("ator", ator)
                    .update();
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalStateException(MENSAGEM_SOBREPOSICAO, ex);
        }

        if (caucao.signum() > 0) {
            jdbc.sql("""
                    INSERT INTO caucao_movimentacoes (contrato_id, tipo, valor, data, criado_por)
                    VALUES (:contrato, 'DEPOSITO'::tipo_caucao, :valor, :data, :ator)
                    """)
                    .param("contrato", contratoId)
                    .param("valor", caucao)
                    .param("data", inicio)
                    .param("ator", ator)
                    .update();
        }

        return contratoId.toString();
    }

    private UUID resolverImovel(String imovelIdInformado, String endereco, UUID ator) {
        if (imovelIdInformado != null && !imovelIdInformado.isBlank()) {
            UUID imovelId = uuid(imovelIdInformado, "Identificador do imóvel inválido.");
            if (!existeImovel(imovelId)) {
                throw new IllegalArgumentException("Imóvel não encontrado: " + imovelId);
            }
            return imovelId;
        }

        String texto = textoObrigatorio(endereco, "Informe o imóvel ou o endereço do imóvel.");
        String[] partes = separarEndereco(texto);
        String logradouro = partes[0];
        String numero = partes[1];
        String complemento = partes[2];
        String bairro = partes[3];

        Optional<UUID> existente = jdbc.sql("""
                SELECT id
                  FROM imoveis
                 WHERE lower(logradouro) = lower(:logradouro)
                   AND lower(numero) = lower(:numero)
                   AND lower(COALESCE(complemento, '')) = lower(:complemento)
                   AND lower(bairro) = lower(:bairro)
                 LIMIT 1
                """)
                .param("logradouro", logradouro)
                .param("numero", numero)
                .param("complemento", complemento == null ? "" : complemento)
                .param("bairro", bairro)
                .query(UUID.class)
                .optional();
        if (existente.isPresent()) {
            return existente.get();
        }

        UUID imovelId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO imoveis (id, logradouro, numero, complemento, bairro, cidade, uf,
                                     tipo, status, criado_por)
                VALUES (:id, :logradouro, :numero, :complemento, :bairro, 'Aracaju', 'SE',
                        'APARTAMENTO', 'VAGO', :ator)
                """)
                .param("id", imovelId)
                .param("logradouro", logradouro)
                .param("numero", numero)
                .param("complemento", complemento)
                .param("bairro", bairro)
                .param("ator", ator)
                .update();
        return imovelId;
    }

    private UUID resolverInquilino(String nome, String documento, String email, String telefone,
            UUID ator) {
        Optional<UUID> porDocumento = jdbc.sql(
                "SELECT id FROM inquilinos WHERE documento = :documento")
                .param("documento", documento)
                .query(UUID.class)
                .optional();

        Optional<UUID> existente = porDocumento;
        if (existente.isEmpty() && email != null) {
            existente = jdbc.sql(
                    "SELECT id FROM inquilinos WHERE lower(email) = lower(:email)")
                    .param("email", email)
                    .query(UUID.class)
                    .optional();
        }

        if (existente.isPresent()) {
            UUID inquilinoId = existente.get();
            jdbc.sql("""
                    UPDATE inquilinos
                       SET nome = :nome, email = :email, telefone = :telefone,
                           atualizado_por = :ator
                     WHERE id = :id
                    """)
                    .param("nome", nome)
                    .param("email", email)
                    .param("telefone", telefone)
                    .param("ator", ator)
                    .param("id", inquilinoId)
                    .update();
            return inquilinoId;
        }

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
        return inquilinoId;
    }

    private UUID resolverFiador(String nome, String documento, String email, String telefone,
            UUID ator) {
        if (documento.isEmpty()) {
            return null;
        }

        Optional<UUID> existente = jdbc.sql(
                "SELECT id FROM fiadores WHERE documento = :documento")
                .param("documento", documento)
                .query(UUID.class)
                .optional();

        if (existente.isPresent()) {
            UUID fiadorId = existente.get();
            jdbc.sql("""
                    UPDATE fiadores
                       SET nome = :nome, email = :email, telefone = :telefone,
                           atualizado_por = :ator
                     WHERE id = :id
                    """)
                    .param("nome", nome)
                    .param("email", email)
                    .param("telefone", telefone)
                    .param("ator", ator)
                    .param("id", fiadorId)
                    .update();
            return fiadorId;
        }

        UUID fiadorId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO fiadores (id, nome, documento, email, telefone, criado_por)
                VALUES (:id, :nome, :documento, :email, :telefone, :ator)
                """)
                .param("id", fiadorId)
                .param("nome", nome)
                .param("documento", documento)
                .param("email", email)
                .param("telefone", telefone)
                .param("ator", ator)
                .update();
        return fiadorId;
    }

    private boolean existeImovel(UUID imovelId) {
        return jdbc.sql("SELECT 1 FROM imoveis WHERE id = :id")
                .param("id", imovelId)
                .query(Integer.class)
                .optional()
                .isPresent();
    }

    private boolean existeSobreposicao(UUID imovelId, LocalDate inicio, LocalDate fim) {
        Integer total = jdbc.sql("""
                SELECT count(*)
                  FROM contratos
                 WHERE imovel_id = :imovel
                   AND inicio <= :fim
                   AND COALESCE(fim, 'infinity'::date) >= :inicio
                """)
                .param("imovel", imovelId)
                .param("inicio", inicio)
                .param("fim", fim)
                .query(Integer.class)
                .single();
        return total != null && total > 0;
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

    private String[] separarEndereco(String endereco) {
        String texto = endereco == null ? "" : endereco.trim();
        String[] blocos = texto.split("\\s+-\\s+");
        String primeiro = blocos.length == 0 ? texto : blocos[0];
        String logradouro = primeiro;
        String numero = "S/N";
        int virgula = primeiro.lastIndexOf(',');
        if (virgula > 0) {
            logradouro = primeiro.substring(0, virgula).trim();
            numero = primeiro.substring(virgula + 1).trim();
            if (numero.isEmpty()) {
                numero = "S/N";
            }
        }
        String complemento = blocos.length >= 3 ? blocos[1].trim() : null;
        if (complemento != null && complemento.isEmpty()) {
            complemento = null;
        }
        String bairro = blocos.length >= 2 ? blocos[blocos.length - 1].trim() : "Centro";
        if (bairro.isEmpty()) {
            bairro = "Centro";
        }
        return new String[] { logradouro.isEmpty() ? texto : logradouro, numero, complemento,
                bairro };
    }
}
