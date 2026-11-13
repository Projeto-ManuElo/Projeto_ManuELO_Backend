package br.com.manuelo.dados;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import br.com.manuelo.Modelos;

@Repository
public class InquilinoRepositorio {

    public record Dados(String id, String nome, String documento, String email, String telefone) {
    }

    public record Opcao(String id, String rotulo) {
    }

    private final JdbcClient jdbc;
    private final SessaoUsuario sessao;

    public InquilinoRepositorio(JdbcTemplate jdbcTemplate, SessaoUsuario sessao) {
        this.jdbc = JdbcClient.create(jdbcTemplate);
        this.sessao = sessao;
    }

    public List<Modelos.Inquilino> listar() {
        return jdbc.sql("""
                SELECT i.id::text AS id, i.nome, i.documento, i.email, i.telefone,
                       cv.id AS contrato_id, cv.imovel_id,
                       im.logradouro, im.numero, im.tipo::text AS imovel_tipo,
                       sc.saldo
                  FROM inquilinos i
                  LEFT JOIN LATERAL (
                    SELECT c.id, c.imovel_id
                      FROM contratos c
                     WHERE c.inquilino_id = i.id
                       AND c.inicio <= current_date
                       AND (c.fim IS NULL OR c.fim >= current_date)
                     ORDER BY c.inicio DESC
                     LIMIT 1
                  ) cv ON true
                  LEFT JOIN imoveis im ON im.id = cv.imovel_id
                  LEFT JOIN vw_saldo_caucao sc ON sc.contrato_id = cv.id
                 ORDER BY i.nome
                """)
                .query((rs, row) -> {
                    String logradouro = rs.getString("logradouro");
                    String endereco = logradouro == null ? ""
                            : logradouro + ", " + rs.getString("numero");
                    String imovel = rs.getString("imovel_tipo") == null ? ""
                            : Dominio.tipoImovel(rs.getString("imovel_tipo"));
                    String status = rs.getString("contrato_id") == null ? "Sem contrato" : "Alugado";
                    BigDecimal caucao = rs.getBigDecimal("saldo") == null ? BigDecimal.ZERO
                            : rs.getBigDecimal("saldo");
                    return new Modelos.Inquilino(rs.getString("id"), rs.getString("nome"),
                            Dominio.formatarDocumento(rs.getString("documento")),
                            rs.getString("email") == null ? "" : rs.getString("email"),
                            rs.getString("telefone") == null ? ""
                                    : Dominio.formatarTelefone(rs.getString("telefone")),
                            endereco, imovel, status, caucao);
                })
                .list();
    }

    public List<Opcao> opcoes() {
        return jdbc.sql("SELECT id::text AS id, nome AS rotulo FROM inquilinos ORDER BY nome")
                .query((rs, row) -> new Opcao(rs.getString("id"), rs.getString("rotulo")))
                .list();
    }

    public void salvar(Dados dados) {
        String documento = Dominio.soDigitos(dados.documento());
        if (documento.length() != 11 && documento.length() != 14) {
            throw new IllegalArgumentException("CPF/CNPJ deve ter 11 ou 14 dígitos.");
        }
        String telefone = Dominio.soDigitos(dados.telefone());
        if (!telefone.isEmpty() && telefone.length() != 10 && telefone.length() != 11) {
            throw new IllegalArgumentException("Telefone deve ter 10 ou 11 dígitos.");
        }
        String email = dados.email() == null || dados.email().isBlank() ? null : dados.email().trim();
        UUID usuario = sessao.id() == null ? null : UUID.fromString(sessao.id());

        try {
            if (dados.id() == null || dados.id().isBlank()) {
                jdbc.sql("""
                        INSERT INTO inquilinos (nome, documento, email, telefone, criado_por)
                        VALUES (:nome, :documento, :email, :telefone, :usuario)
                        """)
                        .param("nome", dados.nome().trim())
                        .param("documento", documento)
                        .param("email", email)
                        .param("telefone", telefone.isEmpty() ? null : telefone)
                        .param("usuario", usuario)
                        .update();
            } else {
                jdbc.sql("""
                        UPDATE inquilinos
                           SET nome = :nome, documento = :documento, email = :email,
                               telefone = :telefone, atualizado_por = :usuario
                         WHERE id = :id
                        """)
                        .param("nome", dados.nome().trim())
                        .param("documento", documento)
                        .param("email", email)
                        .param("telefone", telefone.isEmpty() ? null : telefone)
                        .param("usuario", usuario)
                        .param("id", UUID.fromString(dados.id()))
                        .update();
            }
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalStateException("Não foi possível salvar o inquilino: "
                    + ex.getMostSpecificCause().getMessage());
        }
    }
}
