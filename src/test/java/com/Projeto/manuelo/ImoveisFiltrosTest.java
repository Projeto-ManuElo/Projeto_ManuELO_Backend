package com.Projeto.manuelo;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import br.com.manuelo.DemoData;
import br.com.manuelo.dados.ContratoRepositorio;
import br.com.manuelo.dados.ImovelRepositorio;

/**
 * A-20: filtros de /imoveis executados no banco e contrato vigente por imóvel.
 *
 * Vermelho proposital: {@code ImovelRepositorio.listar(String, String, String, String)} e
 * {@code ContratoRepositorio.buscarVigentePorImovel(String)} ainda não existem; o
 * contrato esperado é o do brief (null/blank = sem filtro; tipo/status por rótulo;
 * endereço casa logradouro/numero/bairro; inquilino casa nome; case-insensitive).
 *
 * Roda contra o Postgres real do perfil de teste (sem mock) e cada método faz rollback.
 * Os dados usam um marcador único para não depender do estado/seed do banco.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ImoveisFiltrosTest {

	static final String MARCADOR = "QA-FILTROS-" + UUID.randomUUID();

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private ImovelRepositorio imovelRepositorio;

	@Autowired
	private ContratoRepositorio contratoRepositorio;

	private UUID imovelCasa;
	private UUID imovelApartamento;
	private UUID imovelComercial;
	private UUID inquilinoId;
	private UUID contratoVigenteId;
	private String nomeInquilino;
	private String bairroCasa;
	private String logradouroApartamento;

	@BeforeEach
	void inserirCenario() {
		imovelCasa = UUID.randomUUID();
		imovelApartamento = UUID.randomUUID();
		imovelComercial = UUID.randomUUID();
		inquilinoId = UUID.randomUUID();
		contratoVigenteId = UUID.randomUUID();

		bairroCasa = "Bairro Alfa " + MARCADOR;
		logradouroApartamento = "Avenida Beta " + MARCADOR;

		inserirImovel(imovelCasa, "Rua Alfa " + MARCADOR, "101", bairroCasa, "CASA", "VAGO");
		inserirImovel(imovelApartamento, logradouroApartamento, "202",
				"Bairro Beta " + MARCADOR, "APARTAMENTO", "ALUGADO");
		inserirImovel(imovelComercial, "Travessa Gama " + MARCADOR, "303",
				"Bairro Gama " + MARCADOR, "COMERCIAL", "EM_MANUTENCAO");

		nomeInquilino = "Inquilino QA " + MARCADOR;
		jdbcTemplate.update("""
				INSERT INTO inquilinos (id, nome, documento)
				VALUES (?, ?, ?)
				""", inquilinoId, nomeInquilino, documentoUnico());

		LocalDate inicio = LocalDate.now().minusDays(15);
		jdbcTemplate.update("""
				INSERT INTO contratos (id, imovel_id, inquilino_id, inicio, fim, valor_aluguel,
				                       dia_vencimento, acompanhar_pagamento_desde)
				VALUES (?, ?, ?, ?, NULL, ?, ?, ?)
				""", contratoVigenteId, imovelApartamento, inquilinoId, inicio,
				new BigDecimal("1500.00"), (short) 10, inicio);
	}

	@Test
	void semFiltrosTrazOsTresImoveisInseridos() {
		List<DemoData.Imovel> imoveis = imovelRepositorio.listar(null, null, null, null);

		assertThat(imoveis)
				.as("listar(null, null, null, null) não pode esconder imóveis")
				.extracting(DemoData.Imovel::id)
				.contains(imovelCasa.toString(), imovelApartamento.toString(), imovelComercial.toString());
	}

	@Test
	void filtraPorEnderecoUsandoBairroELogradouro() {
		assertThat(imovelRepositorio.listar(bairroCasa.toLowerCase(Locale.ROOT), null, null, null))
				.as("endereço deve casar o bairro, sem diferenciar maiúsculas/minúsculas")
				.extracting(DemoData.Imovel::id)
				.containsExactly(imovelCasa.toString());

		assertThat(imovelRepositorio.listar(logradouroApartamento, null, null, null))
				.as("endereço deve casar o logradouro")
				.extracting(DemoData.Imovel::id)
				.containsExactly(imovelApartamento.toString());
	}

	@Test
	void filtraPorTipoStatusEInquilino() {
		assertThat(imovelRepositorio.listar(MARCADOR, "Casa", null, null))
				.as("tipo recebe o rótulo exibido na tela")
				.extracting(DemoData.Imovel::id)
				.containsExactly(imovelCasa.toString());

		assertThat(imovelRepositorio.listar(MARCADOR, null, "Alugado", null))
				.as("status recebe o rótulo exibido na tela")
				.extracting(DemoData.Imovel::id)
				.containsExactly(imovelApartamento.toString());

		assertThat(imovelRepositorio.listar(MARCADOR, null, null, nomeInquilino.toLowerCase(Locale.ROOT)))
				.as("inquilino casa pelo nome do contrato vigente, sem diferenciar maiúsculas/minúsculas")
				.extracting(DemoData.Imovel::id)
				.containsExactly(imovelApartamento.toString());
	}

	@Test
	void combinacaoDeTipoEStatusSemCorrespondenciaRetornaVazio() {
		assertThat(imovelRepositorio.listar(MARCADOR, "Casa", "Alugado", null))
				.as("a Casa foi inserida como Vago: combinação não deve retornar nada")
				.isEmpty();
	}

	@Test
	void buscaContratoVigenteDoImovelAlugado() {
		ContratoRepositorio.Detalhe vigente =
				contratoRepositorio.buscarVigentePorImovel(imovelApartamento.toString());

		assertThat(vigente).as("o imóvel alugado tem contrato vigente").isNotNull();
		assertThat(vigente.id()).isEqualTo(contratoVigenteId.toString());
		assertThat(vigente.imovelId()).isEqualTo(imovelApartamento.toString());
		assertThat(vigente.inquilino()).isEqualTo(nomeInquilino);
	}

	@Test
	void buscaContratoVigenteDeImovelSemContratoRetornaNull() {
		assertThat(contratoRepositorio.buscarVigentePorImovel(imovelCasa.toString()))
				.as("imóvel sem contrato não pode devolver um Detalhe")
				.isNull();
	}

	private void inserirImovel(UUID id, String logradouro, String numero, String bairro,
			String tipo, String status) {
		jdbcTemplate.update("""
				INSERT INTO imoveis (id, logradouro, numero, complemento, bairro, cidade, uf, cep,
				                     tipo, status)
				VALUES (?, ?, ?, NULL, ?, 'Aracaju', 'SE', NULL,
				        CAST(? AS tipo_imovel), CAST(? AS status_imovel))
				""", id, logradouro, numero, bairro, tipo, status);
	}

	private String documentoUnico() {
		long valor = Math.abs(UUID.randomUUID().getMostSignificantBits()) % 100_000_000_000L;
		return String.format("%011d", valor);
	}
}
