package br.com.manuelo.dados;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import br.com.manuelo.DemoData;

@Repository
public class ContratoRepositorio {

    public record Detalhe(String id, String inquilino, String documento, String email,
            String endereco, String situacao, BigDecimal aluguel, LocalDate inicio, LocalDate fim,
            int diaVencimento, boolean acaoJudicial, String acaoJudicialDescricao,
            String fiadorNome, String fiadorDocumento, String fiadorEmail, String fiadorTelefone,
            BigDecimal caucaoDepositos, BigDecimal caucaoSaldo, String imovelId) {
    }

    public record Opcao(String id, String rotulo) {
    }

    private static final DateTimeFormatter MES_ANO = DateTimeFormatter.ofPattern("MM/yyyy",
            Locale.forLanguageTag("pt-BR"));

    private final JdbcClient jdbc;
    private final SessaoUsuario sessao;

    public ContratoRepositorio(JdbcTemplate jdbcTemplate, SessaoUsuario sessao) {
        this.jdbc = JdbcClient.create(jdbcTemplate);
        this.sessao = sessao;
    }

    public List<DemoData.Contrato> listar() {
        return jdbc.sql("""
                SELECT contrato_id::text AS id, inquilino_nome, inquilino_documento, inquilino_email,
                       imovel_endereco, valor_aluguel, fim, atrasado, situacao_validade,
                       COALESCE(dias_para_vencer, 0) AS dias
                  FROM vw_contratos_situacao
                 ORDER BY fim NULLS LAST, inquilino_nome
                """)
                .query((rs, row) -> new DemoData.Contrato(
                        rs.getString("id"),
                        rs.getString("inquilino_nome"),
                        Dominio.formatarDocumento(rs.getString("inquilino_documento")),
                        rs.getString("inquilino_email"),
                        rs.getObject("fim", LocalDate.class),
                        rs.getInt("dias"),
                        rs.getString("imovel_endereco"),
                        Dominio.situacaoContrato(rs.getBoolean("atrasado"),
                                rs.getString("situacao_validade")),
                        rs.getBigDecimal("valor_aluguel")))
                .list();
    }

    public List<Opcao> opcoes() {
        return jdbc.sql("""
                SELECT c.id::text AS id,
                       i.nome || ' — ' || im.logradouro || ', ' || im.numero AS rotulo
                  FROM contratos c
                  JOIN inquilinos i ON i.id = c.inquilino_id
                  JOIN imoveis im ON im.id = c.imovel_id
                 WHERE c.inicio <= current_date AND (c.fim IS NULL OR c.fim >= current_date)
                 ORDER BY i.nome
                """)
                .query((rs, row) -> new Opcao(rs.getString("id"), rs.getString("rotulo")))
                .list();
    }

    public Detalhe buscar(String id) {
        List<Detalhe> encontrados = jdbc.sql("""
                SELECT v.contrato_id::text AS id, v.inquilino_nome, v.inquilino_documento,
                       v.inquilino_email, v.imovel_endereco, v.valor_aluguel, v.inicio, v.fim,
                       v.dia_vencimento, v.atrasado, v.situacao_validade, v.imovel_id::text AS imovel_id,
                       c.possui_acao_judicial, c.acao_judicial_descricao,
                       f.nome AS fiador_nome, f.documento AS fiador_documento,
                       f.email AS fiador_email, f.telefone AS fiador_telefone,
                       COALESCE(sc.total_depositos, 0) AS caucao_depositos,
                       COALESCE(sc.saldo, 0) AS caucao_saldo
                  FROM vw_contratos_situacao v
                  JOIN contratos c ON c.id = v.contrato_id
                  LEFT JOIN fiadores f ON f.id = v.fiador_id
                  LEFT JOIN vw_saldo_caucao sc ON sc.contrato_id = v.contrato_id
                 WHERE v.contrato_id = :id
                """)
                .param("id", UUID.fromString(id))
                .query((rs, row) -> mapear(rs))
                .list();
        return encontrados.isEmpty() ? null : encontrados.get(0);
    }

    public Detalhe buscarVigentePorImovel(String imovelId) {
        if (imovelId == null || imovelId.isBlank()) {
            return null;
        }
        List<Detalhe> encontrados = jdbc.sql("""
                SELECT v.contrato_id::text AS id, v.inquilino_nome, v.inquilino_documento,
                       v.inquilino_email, v.imovel_endereco, v.valor_aluguel, v.inicio, v.fim,
                       v.dia_vencimento, v.atrasado, v.situacao_validade, v.imovel_id::text AS imovel_id,
                       c.possui_acao_judicial, c.acao_judicial_descricao,
                       f.nome AS fiador_nome, f.documento AS fiador_documento,
                       f.email AS fiador_email, f.telefone AS fiador_telefone,
                       COALESCE(sc.total_depositos, 0) AS caucao_depositos,
                       COALESCE(sc.saldo, 0) AS caucao_saldo
                  FROM vw_contratos_situacao v
                  JOIN contratos c ON c.id = v.contrato_id
                  LEFT JOIN fiadores f ON f.id = v.fiador_id
                  LEFT JOIN vw_saldo_caucao sc ON sc.contrato_id = v.contrato_id
                 WHERE v.imovel_id = :id
                   AND v.inicio <= current_date
                   AND (v.fim IS NULL OR v.fim >= current_date)
                 ORDER BY v.inicio DESC
                 LIMIT 1
                """)
                .param("id", UUID.fromString(imovelId))
                .query((rs, row) -> mapear(rs))
                .list();
        return encontrados.isEmpty() ? null : encontrados.get(0);
    }

    private Detalhe mapear(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Detalhe(
                rs.getString("id"),
                rs.getString("inquilino_nome"),
                Dominio.formatarDocumento(rs.getString("inquilino_documento")),
                rs.getString("inquilino_email"),
                rs.getString("imovel_endereco"),
                Dominio.situacaoContrato(rs.getBoolean("atrasado"),
                        rs.getString("situacao_validade")),
                rs.getBigDecimal("valor_aluguel"),
                rs.getObject("inicio", LocalDate.class),
                rs.getObject("fim", LocalDate.class),
                rs.getInt("dia_vencimento"),
                rs.getBoolean("possui_acao_judicial"),
                rs.getString("acao_judicial_descricao"),
                rs.getString("fiador_nome"),
                rs.getString("fiador_documento") == null ? null
                        : Dominio.formatarDocumento(rs.getString("fiador_documento")),
                rs.getString("fiador_email"),
                rs.getString("fiador_telefone") == null ? null
                        : Dominio.formatarTelefone(rs.getString("fiador_telefone")),
                rs.getBigDecimal("caucao_depositos"),
                rs.getBigDecimal("caucao_saldo"),
                rs.getString("imovel_id"));
    }

    public String criar(br.com.manuelo.ContratoDialog.DadosContrato dados) {
        String documento = Dominio.soDigitos(dados.cpf());
        if (documento.length() != 11 && documento.length() != 14) {
            throw new IllegalArgumentException("CPF/CNPJ deve ter 11 ou 14 dígitos.");
        }
        String nome = dados.nome() == null ? "" : dados.nome().trim();
        if (nome.isEmpty()) {
            throw new IllegalArgumentException("Informe o nome do inquilino.");
        }
        String email = dados.email() == null || dados.email().isBlank() ? null : dados.email().trim();
        UUID usuario = UUID.fromString(sessao.id());
        UUID inquilinoId = resolverInquilino(nome, documento, email, usuario);
        UUID imovelId = resolverImovel(dados.endereco(), usuario);

        LocalDate inicio = LocalDate.now();
        LocalDate fim = dados.vencimento() == null ? inicio.plusMonths(12) : dados.vencimento();
        if (fim.isBefore(inicio)) {
            throw new IllegalArgumentException("O vencimento não pode ser anterior ao início do contrato.");
        }
        if (dados.aluguel() == null || dados.aluguel().signum() <= 0) {
            throw new IllegalArgumentException("Informe um valor de aluguel maior que zero.");
        }
        int dia = Math.max(1, Math.min(31, fim.getDayOfMonth()));

        try {
            return jdbc.sql("""
                    INSERT INTO contratos (imovel_id, inquilino_id, inicio, fim, valor_aluguel,
                                           dia_vencimento, acompanhar_pagamento_desde, criado_por)
                    VALUES (:imovel, :inquilino, :inicio, :fim, :valor, :dia, :inicio, :usuario)
                    RETURNING id::text
                    """)
                    .param("imovel", imovelId)
                    .param("inquilino", inquilinoId)
                    .param("inicio", inicio)
                    .param("fim", fim)
                    .param("valor", dados.aluguel())
                    .param("dia", dia)
                    .param("usuario", usuario)
                    .query(String.class)
                    .single();
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalStateException(
                    "Já existe contrato para este imóvel no período informado.");
        }
    }

    private UUID resolverInquilino(String nome, String documento, String email, UUID usuario) {
        List<String> porDocumento = jdbc.sql("SELECT id::text FROM inquilinos WHERE documento = :documento")
                .param("documento", documento)
                .query(String.class)
                .list();
        if (!porDocumento.isEmpty()) {
            return UUID.fromString(porDocumento.get(0));
        }
        if (email != null) {
            List<String> porEmail = jdbc.sql("SELECT id::text FROM inquilinos WHERE lower(email) = lower(:email)")
                    .param("email", email)
                    .query(String.class)
                    .list();
            if (!porEmail.isEmpty()) {
                return UUID.fromString(porEmail.get(0));
            }
        }
        return UUID.fromString(jdbc.sql("""
                INSERT INTO inquilinos (nome, documento, email, criado_por)
                VALUES (:nome, :documento, :email, :usuario)
                RETURNING id::text
                """)
                .param("nome", nome)
                .param("documento", documento)
                .param("email", email)
                .param("usuario", usuario)
                .query(String.class)
                .single());
    }

    private UUID resolverImovel(String endereco, UUID usuario) {
        String texto = endereco == null || endereco.isBlank() ? "Endereço não informado" : endereco.trim();
        List<String> encontrados = jdbc.sql("""
                SELECT id::text FROM imoveis
                 WHERE lower(logradouro) = lower(:endereco)
                    OR lower(logradouro || ', ' || numero) = lower(:endereco)
                    OR lower(logradouro || ', ' || numero || ' - ' || bairro) = lower(:endereco)
                 LIMIT 1
                """)
                .param("endereco", texto)
                .query(String.class)
                .list();
        if (!encontrados.isEmpty()) {
            return UUID.fromString(encontrados.get(0));
        }
        String[] partes = ImovelRepositorio.separarEndereco(texto);
        return UUID.fromString(jdbc.sql("""
                INSERT INTO imoveis (logradouro, numero, complemento, bairro, cidade, uf, tipo, status,
                                     criado_por)
                VALUES (:logradouro, :numero, :complemento, :bairro, 'Aracaju', 'SE',
                        'APARTAMENTO', 'VAGO', :usuario)
                RETURNING id::text
                """)
                .param("logradouro", partes[0])
                .param("numero", partes[1])
                .param("complemento", partes[2])
                .param("bairro", partes[3])
                .param("usuario", usuario)
                .query(String.class)
                .single());
    }

    public void registrarPagamento(String contratoId, BigDecimal valor, String forma, boolean caucao) {
        if (valor == null || valor.signum() <= 0) {
            throw new IllegalArgumentException("Informe um valor maior que zero.");
        }
        UUID contrato = UUID.fromString(contratoId);
        UUID usuario = UUID.fromString(sessao.id());
        String imovelId = jdbc.sql("SELECT imovel_id::text FROM contratos WHERE id = :id")
                .param("id", contrato)
                .query(String.class)
                .single();

        if (caucao) {
            BigDecimal saldo = jdbc.sql("SELECT COALESCE(saldo, 0) FROM vw_saldo_caucao WHERE contrato_id = :id")
                    .param("id", contrato)
                    .query(BigDecimal.class)
                    .single();
            if (saldo.compareTo(valor) < 0) {
                throw new IllegalStateException("Saldo de caução insuficiente.");
            }
        }

        LocalDate hoje = LocalDate.now();
        LocalDate competencia = hoje.withDayOfMonth(1);
        String formaEnum = caucao ? "CAUCAO" : Dominio.formaPagamentoEnum(forma);
        String descricao = "Aluguel " + MES_ANO.format(hoje) + (caucao ? " (caução)" : "");

        UUID movimentacao = UUID.fromString(jdbc.sql("""
                INSERT INTO movimentacoes (tipo, categoria, valor, data_movimentacao, descricao,
                                           forma_pagamento, imovel_id, responsavel_id, contrato_id,
                                           competencia)
                VALUES ('ENTRADA', 'ALUGUEL', :valor, :data, :descricao,
                        CAST(:forma AS forma_pagamento),
                        :imovel, :usuario, :contrato, :competencia)
                RETURNING id::text
                """)
                .param("valor", valor)
                .param("data", hoje)
                .param("descricao", descricao)
                .param("forma", formaEnum)
                .param("imovel", UUID.fromString(imovelId))
                .param("usuario", usuario)
                .param("contrato", contrato)
                .param("competencia", competencia)
                .query(String.class)
                .single());

        if (caucao) {
            jdbc.sql("""
                    INSERT INTO caucao_movimentacoes (contrato_id, tipo, valor, data,
                                                      movimentacao_id, criado_por)
                    VALUES (:contrato, 'ABATIMENTO_ALUGUEL', :valor, :data, :movimentacao, :usuario)
                    """)
                    .param("contrato", contrato)
                    .param("valor", valor)
                    .param("data", hoje)
                    .param("movimentacao", movimentacao)
                    .param("usuario", usuario)
                    .update();
        }
    }
}
