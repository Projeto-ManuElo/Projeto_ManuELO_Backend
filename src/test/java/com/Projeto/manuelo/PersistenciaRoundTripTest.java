package com.Projeto.manuelo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import br.com.manuelo.Modelos;
import br.com.manuelo.dados.ImovelRepositorio;
import br.com.manuelo.dados.SessaoUsuario;

/**
 * Prova de persistência (etapa 7 do juiz): cria um imóvel pelo repositório real,
 * derruba o contexto Spring, sobe outro contexto e lê o registro de volta do Postgres.
 *
 * Ainda não existe serviço de imóveis (A-02/A-03 pendentes); por isso usamos o
 * repositório JdbcClient disponível. Não há mock: todas as operações vão ao banco.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PersistenciaRoundTripTest {

	static final String MARCADOR = "QA-" + UUID.randomUUID();
	static final String LOGRADOURO = "Rua Persistencia " + MARCADOR;

	static ApplicationContext contextoDaEscrita;
	static ApplicationContext contextoDaLeitura;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private ApplicationContext contexto;

	@Test
	@Order(1)
	@DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
	void gravaRegistroNoBanco() {
		contextoDaEscrita = contexto;

		ImovelRepositorio repositorio = new ImovelRepositorio(jdbcTemplate, new SessaoUsuario());
		repositorio.salvar(new ImovelRepositorio.Dados(
				null, LOGRADOURO, "100", null, "Centro", "Aracaju", "SE", "49000-000",
				"Casa", "Vago"));

		Integer gravados = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM imoveis WHERE logradouro = ?", Integer.class, LOGRADOURO);
		assertThat(gravados).isEqualTo(1);
	}

	@Test
	@Order(2)
	void leRegistroAposReiniciarContexto() {
		contextoDaLeitura = contexto;

		assertThat(contextoDaLeitura)
				.as("o contexto Spring precisa ter sido reiniciado entre a escrita e a leitura")
				.isNotSameAs(contextoDaEscrita);

		ImovelRepositorio repositorio = new ImovelRepositorio(jdbcTemplate, new SessaoUsuario());
		List<Modelos.Imovel> imoveis = repositorio.listar();

		assertThat(imoveis)
				.as("o imóvel gravado antes do reinício deve continuar no Postgres")
				.anyMatch(imovel -> LOGRADOURO.equals(imovel.logradouro()));
	}

}
