package br.com.manuelo.dados;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    public record DadosEdicao(String imovelId, BigDecimal aluguel, int diaVencimento,
            String nomeFiador, String documentoFiador, String emailFiador, BigDecimal caucao) {
    }

    private final JdbcClient jdbc;
    private final SessaoUsuario sessao;

    public InquilinoRepositorio(JdbcTemplate jdbcTemplate, SessaoUsuario sessao) {
        this.jdbc = JdbcClient.create(jdbcTemplate);
        this.sessao = sessao;
    }

    public List<Modelos.Inquilino> listar() {
        return listar(null, null, null);
    }

    public List<Modelos.Inquilino> listar(String nome, String cpf, String email) {
        StringBuilder sql = new StringBuilder("""
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
                 WHERE 1 = 1
                """);
        Map<String, Object> params = new LinkedHashMap<>();

        if (nome != null && !nome.isBlank()) {
            sql.append(" AND lower(i.nome) LIKE lower(:nome) ESCAPE '\\'");
            params.put("nome", "%" + like(nome.trim()) + "%");
        }
        String cpfDigitos = cpf == null ? "" : cpf.replaceAll("\\D", "");
        if (!cpfDigitos.isEmpty()) {
            sql.append(" AND regexp_replace(i.documento, '\\D', '', 'g') LIKE :cpf");
            params.put("cpf", "%" + cpfDigitos + "%");
        }
        if (email != null && !email.isBlank()) {
            sql.append(" AND lower(i.email) LIKE lower(:email) ESCAPE '\\'");
            params.put("email", "%" + like(email.trim()) + "%");
        }
        sql.append(" ORDER BY i.nome");

        return jdbc.sql(sql.toString())
                .params(params)
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

    public DadosEdicao detalheEdicao(String inquilinoId) {
        if (inquilinoId == null || inquilinoId.isBlank()) {
            return null;
        }
        return jdbc.sql("""
                SELECT cv.imovel_id::text AS imovel_id, cv.valor_aluguel,
                       cv.dia_vencimento, f.nome AS fiador_nome, f.documento AS fiador_documento,
                       f.email AS fiador_email, COALESCE(sc.saldo, 0) AS saldo
                  FROM inquilinos i
                  LEFT JOIN LATERAL (
                    SELECT c.id, c.imovel_id, c.fiador_id, c.valor_aluguel, c.dia_vencimento
                      FROM contratos c
                     WHERE c.inquilino_id = i.id
                       AND c.inicio <= current_date
                       AND (c.fim IS NULL OR c.fim >= current_date)
                     ORDER BY c.inicio DESC
                     LIMIT 1
                  ) cv ON true
                  LEFT JOIN fiadores f ON f.id = cv.fiador_id
                  LEFT JOIN vw_saldo_caucao sc ON sc.contrato_id = cv.id
                 WHERE i.id = :id
                """)
                .param("id", UUID.fromString(inquilinoId))
                .query((rs, row) -> new DadosEdicao(
                        rs.getString("imovel_id"),
                        rs.getBigDecimal("valor_aluguel"),
                        rs.getInt("dia_vencimento"),
                        rs.getString("fiador_nome"),
                        rs.getString("fiador_documento") == null ? null
                                : Dominio.formatarDocumento(rs.getString("fiador_documento")),
                        rs.getString("fiador_email"),
                        rs.getBigDecimal("saldo")))
                .optional()
                .orElse(null);
    }

    public String contratoVigenteId(String inquilinoId) {
        if (inquilinoId == null || inquilinoId.isBlank()) {
            return null;
        }
        return jdbc.sql("""
                SELECT c.id::text
                  FROM contratos c
                 WHERE c.inquilino_id = :id
                   AND c.inicio <= current_date
                   AND (c.fim IS NULL OR c.fim >= current_date)
                 ORDER BY c.inicio DESC
                 LIMIT 1
                """)
                .param("id", UUID.fromString(inquilinoId))
                .query(String.class)
                .optional()
                .orElse(null);
    }

    private static String like(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
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
