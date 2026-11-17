package br.com.manuelo.dados;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class HomeRepositorio {

    public record Indicadores(int cadastrados, int alugados, int vagos, int manutencao, int aVencer,
            int atrasados, int atrasadosCriticos) {
    }

    private final JdbcClient jdbc;

    public HomeRepositorio(JdbcTemplate jdbcTemplate) {
        this.jdbc = JdbcClient.create(jdbcTemplate);
    }

    public Indicadores indicadores() {
        return jdbc.sql("SELECT * FROM vw_home_indicadores")
                .query((rs, row) -> new Indicadores(
                        rs.getInt("imoveis_cadastrados"),
                        rs.getInt("imoveis_alugados"),
                        rs.getInt("imoveis_vagos"),
                        rs.getInt("imoveis_em_manutencao"),
                        rs.getInt("contratos_proximos_vencimento"),
                        rs.getInt("contratos_atrasados"),
                        rs.getInt("contratos_atrasados_criticos")))
                .single();
    }
}
