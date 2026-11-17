package com.Projeto.manuelo;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import br.com.manuelo.DemoData;
import br.com.manuelo.dados.ReformaRepositorio;
import br.com.manuelo.dados.SessaoUsuario;

/**
 * A-22: histórico de reformas isolado por imóvel e responsável derivado do contrato
 * vigente na data da reforma (vw_reformas_historico).
 *
 * Roda contra o Postgres real do perfil de teste (sem mock). Cada método faz rollback:
 * os imóveis, inquilinos, contratos e reformas usam um marcador único para não depender
 * do estado/seed do banco. O admin autenticado é o {@code manuelo.admin.*} do perfil
 * de teste ({@code qa@manuelo.local / qa-admin-123}), criado pelo AdminInicial no boot.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReformasHistoricoTest {

	private static final String MARCADOR = "QA-A22-" + UUID.randomUUID().toString().substring(0, 8);

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private AuthenticationManager authenticationManager;

	@Value("${manuelo.admin.email}")
	private String adminEmail;

	@Value("${manuelo.admin.senha}")
	private String adminSenha;

	private ReformaRepositorio repositorio;

	private UUID imovel1;
	private UUID imovel2;
	private String inquilino1;
	private String inquilino2;
	private String descricao1;
	private String descricao2;
	private LocalDate data1;
	private LocalDate data2;

	@BeforeEach
	void prepararCenario() {
		autenticarAdmin();
		repositorio = new ReformaRepositorio(jdbc, new SessaoUsuario());

		imovel1 = UUID.randomUUID();
		imovel2 = UUID.randomUUID();
		inquilino1 = "Inquilino A22 Alfa " + MARCADOR;
		inquilino2 = "Inquilino A22 Beta " + MARCADOR;

		inserirImovel(imovel1, "Avenida QA A22 Alfa " + MARCADOR, "10");
		inserirImovel(imovel2, "Avenida QA A22 Beta " + MARCADOR, "20");

		UUID inquilinoId1 = inserirInquilino(inquilino1);
		UUID inquilinoId2 = inserirInquilino(inquilino2);

		LocalDate inicioContrato = LocalDate.now().minusMonths(6);
		inserirContrato(imovel1, inquilinoId1, inicioContrato);
		inserirContrato(imovel2, inquilinoId2, inicioContrato);

		data1 = LocalDate.now().minusMonths(3);
		data2 = LocalDate.now().minusMonths(2);
		descricao1 = "Troca de telhado " + MARCADOR;
		descricao2 = "Pintura interna " + MARCADOR;

		repositorio.salvar(imovel1.toString(), data1, descricao1, new BigDecimal("1200.50"));
		repositorio.salvar(imovel2.toString(), data2, descricao2, new BigDecimal("850.00"));
	}

	@AfterEach
	void limparContexto() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void listarTrazSomenteAsReformasDoImovelSelecionado() {
		List<DemoData.Reforma> reformas1 = repositorio.listar(imovel1.toString());
		assertThat(reformas1)
				.as("o imóvel 1 não pode ver a reforma do imóvel 2")
				.hasSize(1);

		DemoData.Reforma reforma1 = reformas1.get(0);
		assertThat(reforma1.data()).isEqualTo(data1);
		assertThat(reforma1.descricao()).isEqualTo(descricao1);
		assertThat(reforma1.valor()).isEqualByComparingTo("1200.50");
		assertThat(reforma1.proprietario())
				.as("responsável do período deve ser o inquilino do contrato vigente na data")
				.isEqualTo(inquilino1);

		List<DemoData.Reforma> reformas2 = repositorio.listar(imovel2.toString());
		assertThat(reformas2)
				.as("o imóvel 2 não pode ver a reforma do imóvel 1")
				.hasSize(1);

		DemoData.Reforma reforma2 = reformas2.get(0);
		assertThat(reforma2.data()).isEqualTo(data2);
		assertThat(reforma2.descricao()).isEqualTo(descricao2);
		assertThat(reforma2.valor()).isEqualByComparingTo("850.00");
		assertThat(reforma2.proprietario())
				.as("responsável do período deve ser o inquilino do contrato vigente na data")
				.isEqualTo(inquilino2);
	}

	@Test
	void listarImoveisTrazOsDoisOrdenadosPorEnderecoComProprietarioAtual() {
		List<ReformaRepositorio.ImovelInfo> imoveis = repositorio.listarImoveis();

		List<ReformaRepositorio.ImovelInfo> nossos = imoveis.stream()
				.filter(info -> info.id().equals(imovel1.toString())
						|| info.id().equals(imovel2.toString()))
				.toList();

		assertThat(nossos)
				.as("os dois imóveis devem aparecer ordenados por endereço (Alfa antes de Beta)")
				.extracting(ReformaRepositorio.ImovelInfo::id)
				.containsExactly(imovel1.toString(), imovel2.toString());

		assertThat(nossos.get(0).endereco()).contains("Avenida QA A22 Alfa " + MARCADOR);
		assertThat(nossos.get(0).proprietario()).isEqualTo(inquilino1);
		assertThat(nossos.get(1).endereco()).contains("Avenida QA A22 Beta " + MARCADOR);
		assertThat(nossos.get(1).proprietario()).isEqualTo(inquilino2);
	}

	private void autenticarAdmin() {
		Authentication autenticacao = authenticationManager.authenticate(
				new UsernamePasswordAuthenticationToken(adminEmail, adminSenha));
		SecurityContextHolder.getContext().setAuthentication(autenticacao);
	}

	private void inserirImovel(UUID id, String logradouro, String numero) {
		jdbc.update("""
				INSERT INTO imoveis (id, logradouro, numero, complemento, bairro, cidade, uf, cep,
				                     tipo, status)
				VALUES (?, ?, ?, NULL, ?, 'Aracaju', 'SE', NULL,
				        'CASA'::tipo_imovel, 'ALUGADO'::status_imovel)
				""", id, logradouro, numero, "Bairro QA A22 " + MARCADOR);
	}

	private UUID inserirInquilino(String nome) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO inquilinos (id, nome, documento)
				VALUES (?, ?, ?)
				""", id, nome, documentoUnico());
		return id;
	}

	private void inserirContrato(UUID imovelId, UUID inquilinoId, LocalDate inicio) {
		jdbc.update("""
				INSERT INTO contratos (id, imovel_id, inquilino_id, inicio, fim, valor_aluguel,
				                       dia_vencimento, acompanhar_pagamento_desde)
				VALUES (?, ?, ?, ?, NULL, ?, ?, ?)
				""", UUID.randomUUID(), imovelId, inquilinoId, inicio,
				new BigDecimal("1500.00"), (short) 10, inicio);
	}

	private String documentoUnico() {
		long valor = Math.abs(UUID.randomUUID().getMostSignificantBits()) % 100_000_000_000L;
		return String.format("%011d", valor);
	}
}
