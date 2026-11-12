package br.com.manuelo.dados;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import br.com.manuelo.DemoData;

@Repository
public class ReformaRepositorio {

    public record ImovelInfo(String id, String endereco, String proprietario) {
    }

    private final JdbcClient jdbc;
    private final SessaoUsuario sessao;

    public ReformaRepositorio(JdbcTemplate jdbcTemplate, SessaoUsuario sessao) {
        this.jdbc = JdbcClient.create(jdbcTemplate);
        this.sessao = sessao;
    }

    public ImovelInfo imovelPadrao() {
        List<ImovelInfo> encontrados = jdbc.sql("""
                SELECT im.id::text AS id,
                       im.logradouro || ', ' || im.numero AS endereco,
                       (SELECT iq.nome
                          FROM contratos c
                          JOIN inquilinos iq ON iq.id = c.inquilino_id
                         WHERE c.imovel_id = im.id
                           AND c.inicio <= current_date
                           AND (c.fim IS NULL OR c.fim >= current_date)
                         ORDER BY c.inicio DESC
                         LIMIT 1) AS proprietario
                  FROM imoveis im
                 ORDER BY im.logradouro, im.numero
                 LIMIT 1
                """)
                .query((rs, row) -> new ImovelInfo(rs.getString("id"), rs.getString("endereco"),
                        rs.getString("proprietario")))
                .list();
        return encontrados.isEmpty() ? null : encontrados.get(0);
    }

    public List<DemoData.Reforma> listar(String imovelId) {
        return jdbc.sql("""
                SELECT data_reforma, descricao, valor,
                       COALESCE(responsavel_periodo, 'Sem contrato vigente') AS proprietario
                  FROM vw_reformas_historico
                 WHERE imovel_id = :imovel
                 ORDER BY data_reforma DESC
                """)
                .param("imovel", UUID.fromString(imovelId))
                .query((rs, row) -> new DemoData.Reforma(
                        rs.getObject("data_reforma", LocalDate.class),
                        rs.getString("descricao"),
                        rs.getString("proprietario"),
                        rs.getBigDecimal("valor")))
                .list();
    }

    public void salvar(String imovelId, LocalDate data, String descricao, BigDecimal valor) {
        if (descricao == null || descricao.isBlank()) {
            throw new IllegalArgumentException("Informe a descrição da reforma.");
        }
        if (valor == null || valor.signum() <= 0) {
            throw new IllegalArgumentException("Informe um valor maior que zero.");
        }
        jdbc.sql("""
                INSERT INTO reformas (imovel_id, data_reforma, descricao, valor, criado_por)
                VALUES (:imovel, :data, :descricao, :valor, :usuario)
                """)
                .param("imovel", UUID.fromString(imovelId))
                .param("data", data == null ? LocalDate.now() : data)
                .param("descricao", descricao.trim())
                .param("valor", valor)
                .param("usuario", UUID.fromString(sessao.id()))
                .update();
    }
}
