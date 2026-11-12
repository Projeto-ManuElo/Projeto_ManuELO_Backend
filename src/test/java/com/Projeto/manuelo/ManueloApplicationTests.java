package com.Projeto.manuelo;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Sobe o contexto completo contra o Postgres real do juiz (sem Testcontainers/docker).
 * Também confirma que o DataSource abre conexão e que o schema do Flyway está presente.
 */
@SpringBootTest
@ActiveProfiles("test")
class ManueloApplicationTests {

	@Autowired
	private DataSource dataSource;

	@Test
	void contextLoadsComBancoReal() throws Exception {
		try (var conexao = dataSource.getConnection()) {
			assertThat(conexao.isValid(5)).isTrue();
		}
		var jdbc = new JdbcTemplate(dataSource);
		Integer tabelas = jdbc.queryForObject(
				"SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name IN "
						+ "('usuarios', 'imoveis', 'contratos', 'movimentacoes', 'documentos')",
				Integer.class);
		assertThat(tabelas).isEqualTo(5);
	}

}
