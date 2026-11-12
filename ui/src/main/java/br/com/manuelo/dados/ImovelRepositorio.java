package br.com.manuelo.dados;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import br.com.manuelo.DemoData;

@Repository
public class ImovelRepositorio {

    public record Dados(String id, String logradouro, String numero, String complemento, String bairro,
            String cidade, String uf, String cep, String tipo, String status) {
    }

    public record Opcao(String id, String rotulo) {
    }

    private final JdbcClient jdbc;
    private final SessaoUsuario sessao;

    public ImovelRepositorio(JdbcTemplate jdbcTemplate, SessaoUsuario sessao) {
        this.jdbc = JdbcClient.create(jdbcTemplate);
        this.sessao = sessao;
    }

    public List<DemoData.Imovel> listar() {
        return jdbc.sql("""
                SELECT v.id::text AS id, v.logradouro, v.numero, v.complemento, v.bairro,
                       v.cidade, v.uf, v.cep, v.tipo::text AS tipo,
                       v.status_exibicao, v.inquilino_atual_nome,
                       COALESCE(cv.valor_aluguel, 0) AS aluguel
                  FROM vw_imoveis_situacao v
                  LEFT JOIN LATERAL (
                    SELECT c.valor_aluguel
                      FROM contratos c
                     WHERE c.imovel_id = v.id
                       AND c.inicio <= current_date
                       AND (c.fim IS NULL OR c.fim >= current_date)
                     ORDER BY c.inicio DESC
                     LIMIT 1
                  ) cv ON true
                 ORDER BY v.logradouro, v.numero
                """)
                .query((rs, row) -> {
                    String observacoes = rs.getString("cidade") + "/" + rs.getString("uf");
                    String cep = rs.getString("cep");
                    if (cep != null && !cep.isBlank()) {
                        observacoes += " • CEP " + cep;
                    }
                    return new DemoData.Imovel(
                            rs.getString("id"),
                            rs.getString("logradouro"),
                            rs.getString("numero"),
                            rs.getString("complemento"),
                            rs.getString("bairro"),
                            rs.getString("cidade"),
                            rs.getString("uf"),
                            cep,
                            Dominio.tipoImovel(rs.getString("tipo")),
                            Dominio.statusImovel(rs.getString("status_exibicao")),
                            rs.getString("inquilino_atual_nome"),
                            rs.getBigDecimal("aluguel"),
                            observacoes);
                })
                .list();
    }

    public List<Opcao> opcoes() {
        return jdbc.sql("""
                SELECT id::text AS id,
                       logradouro || ', ' || numero AS rotulo
                  FROM imoveis
                 ORDER BY logradouro, numero
                """)
                .query((rs, row) -> new Opcao(rs.getString("id"), rs.getString("rotulo")))
                .list();
    }

    public void salvar(Dados dados) {
        String cidade = vazioPara(dados.cidade(), "Aracaju");
        String uf = vazioPara(dados.uf(), "SE").toUpperCase(Locale.ROOT);
        String cep = dados.cep() != null && dados.cep().matches("^[0-9]{5}-?[0-9]{3}$")
                ? dados.cep()
                : null;
        String complemento = vazioParaNulo(dados.complemento());
        String tipo = Dominio.tipoImovelEnum(dados.tipo());
        String status = Dominio.statusImovelEnum(dados.status());
        UUID usuario = sessao.id() == null ? null : UUID.fromString(sessao.id());

        try {
            if (dados.id() == null || dados.id().isBlank()) {
                jdbc.sql("""
                        INSERT INTO imoveis (logradouro, numero, complemento, bairro, cidade, uf, cep,
                                             tipo, status, criado_por)
                        VALUES (:logradouro, :numero, :complemento, :bairro, :cidade, :uf, :cep,
                                CAST(:tipo AS tipo_imovel), CAST(:status AS status_imovel), :usuario)
                        """)
                        .param("logradouro", dados.logradouro().trim())
                        .param("numero", vazioPara(dados.numero(), "S/N"))
                        .param("complemento", complemento)
                        .param("bairro", vazioPara(dados.bairro(), "Centro"))
                        .param("cidade", cidade)
                        .param("uf", uf)
                        .param("cep", cep)
                        .param("tipo", tipo)
                        .param("status", status)
                        .param("usuario", usuario)
                        .update();
            } else {
                jdbc.sql("""
                        UPDATE imoveis
                           SET logradouro = :logradouro, numero = :numero, complemento = :complemento,
                               bairro = :bairro, cidade = :cidade, uf = :uf, cep = :cep,
                               tipo = CAST(:tipo AS tipo_imovel),
                               status = CAST(:status AS status_imovel), atualizado_por = :usuario
                         WHERE id = :id
                        """)
                        .param("logradouro", dados.logradouro().trim())
                        .param("numero", vazioPara(dados.numero(), "S/N"))
                        .param("complemento", complemento)
                        .param("bairro", vazioPara(dados.bairro(), "Centro"))
                        .param("cidade", cidade)
                        .param("uf", uf)
                        .param("cep", cep)
                        .param("tipo", tipo)
                        .param("status", status)
                        .param("usuario", usuario)
                        .param("id", UUID.fromString(dados.id()))
                        .update();
            }
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalStateException("Não foi possível salvar o imóvel: " + ex.getMostSpecificCause().getMessage());
        }
    }

    private String vazioPara(String valor, String padrao) {
        return valor == null || valor.isBlank() ? padrao : valor.trim();
    }

    private String vazioParaNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    public static String[] separarEndereco(String endereco) {
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
        String bairro = blocos.length >= 2 ? blocos[blocos.length - 1].trim() : "Centro";
        return new String[] { logradouro.isEmpty() ? texto : logradouro, numero, complemento, bairro };
    }
}
