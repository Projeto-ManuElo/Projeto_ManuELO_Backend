package com.Projeto.manuelo;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
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
import br.com.manuelo.dados.ContratoRepositorio;

/**
 * A-30: filtros de /contratos executados no banco (nome, CPF, imóvel, validade e situação),
 * incluindo CPF mascarado e escape de LIKE para {@code %} e {@code _}.
 *
 * Os três contratos do cenário cobrem as três situações da view
 * {@code vw_contratos_situacao} / {@code fn_contrato_situacao}:
 * <ul>
 *   <li>ativo vigente: fim distante, sem competência aberta;</li>
 *   <li>a vencer: fim dentro da janela de alerta (padrão 7 dias);</li>
 *   <li>atrasado: fim no passado e competência antiga em aberto.</li>
 * </ul>
 *
 * Roda contra o Postgres real do perfil de teste (sem mock) e cada método faz rollback.
 * O marcador aleatório evita depender de qualquer seed ou de outro teste.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ContratosFiltrosTest {

	private static final String MARCADOR = "QA-CONTRATOS-" + UUID.randomUUID();
	private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private ContratoRepositorio contratoRepositorio;

	@Autowired
	private AuthenticationManager authenticationManager;

	@Value("${manuelo.admin.email}")
	private String adminEmail;

	@Value("${manuelo.admin.senha}")
	private String adminSenha;

	private UUID imovelAtivo;
	private UUID imovelAVencer;
	private UUID imovelAtrasado;
	private UUID contratoAtivo;
	private UUID contratoAVencer;
	private UUID contratoAtrasado;
	private String nomeAtivo;
	private String nomeAVencer;
	private String nomeAtrasado;
	private String logradouroAtivo;
	private String logradouroAVencer;
	private String logradouroAtrasado;
	private String documentoAtivo;

	@BeforeEach
	void autenticarAdminEInserirCenario() {
		Authentication autenticacao = authenticationManager.authenticate(
				new UsernamePasswordAuthenticationToken(adminEmail, adminSenha));
		SecurityContextHolder.getContext().setAuthentication(autenticacao);

		LocalDate hoje = jdbc.queryForObject("SELECT current_date", Date.class).toLocalDate();

		imovelAtivo = UUID.randomUUID();
		imovelAVencer = UUID.randomUUID();
		imovelAtrasado = UUID.randomUUID();
		contratoAtivo = UUID.randomUUID();
		contratoAVencer = UUID.randomUUID();
		contratoAtrasado = UUID.randomUUID();

		nomeAtivo = "Alfa QA " + MARCADOR;
		nomeAVencer = "Beta QA " + MARCADOR;
		nomeAtrasado = "Gama QA " + MARCADOR;

		logradouroAtivo = "Rua Alfa " + MARCADOR;
		logradouroAVencer = "Avenida Beta " + MARCADOR;
		logradouroAtrasado = "Travessa Gama " + MARCADOR;

		documentoAtivo = documentoUnico();
		String documentoAVencer = documentoUnico();
		String documentoAtrasado = documentoUnico();

		inserirImovel(imovelAtivo, logradouroAtivo, "101", "Bairro Alfa " + MARCADOR,
				"APARTAMENTO", "ALUGADO");
		inserirImovel(imovelAVencer, logradouroAVencer, "202", "Bairro Beta " + MARCADOR,
				"CASA", "ALUGADO");
		inserirImovel(imovelAtrasado, logradouroAtrasado, "303", "Bairro Gama " + MARCADOR,
				"COMERCIAL", "ALUGADO");

		inserirInquilino(UUID.randomUUID(), nomeAtivo, documentoAtivo);
		inserirInquilino(UUID.randomUUID(), nomeAVencer, documentoAVencer);
		inserirInquilino(UUID.randomUUID(), nomeAtrasado, documentoAtrasado);

		inserirContrato(contratoAtivo, imovelAtivo, documentoAtivo,
				hoje.minusMonths(1), hoje.plusMonths(6), hoje.withDayOfMonth(1), (short) 31);
		inserirContrato(contratoAVencer, imovelAVencer, documentoAVencer,
				hoje.minusMonths(1), hoje.plusDays(3), hoje.withDayOfMonth(1), (short) 31);
		inserirContrato(contratoAtrasado, imovelAtrasado, documentoAtrasado,
				hoje.minusMonths(3).withDayOfMonth(1), hoje.minusDays(10),
				hoje.minusMonths(2).withDayOfMonth(1), (short) 5);
	}

	@AfterEach
	void limparContextoDeSeguranca() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void semFiltrosTrazOsTresContratos() {
		assertThat(ids(contratoRepositorio.listar(null, null, null, null, null)))
				.as("listar sem filtro não pode esconder os contratos inseridos")
				.contains(contratoAtivo.toString(), contratoAVencer.toString(),
						contratoAtrasado.toString());
	}

	@Test
	void filtraNomeParcialSemDiferenciarMaiusculas() {
		assertThat(ids(contratoRepositorio.listar(nomeAtivo.toLowerCase(Locale.ROOT),
				null, null, null, null)))
				.as("nome parcial deve casar somente o contrato do 'Alfa', sem diferenciar caixa")
				.containsExactly(contratoAtivo.toString());
	}

	@Test
	void filtraCpfComMascaraESemMascaraComOMesmoResultado() {
		List<DemoData.Contrato> semMascara =
				contratoRepositorio.listar(null, documentoAtivo, null, null, null);
		List<DemoData.Contrato> comMascara =
				contratoRepositorio.listar(null, mascararCpf(documentoAtivo), null, null, null);

		assertThat(ids(semMascara)).as("CPF só com dígitos isola o contrato")
				.containsExactly(contratoAtivo.toString());
		assertThat(ids(comMascara)).as("CPF mascarado isola o mesmo contrato")
				.containsExactly(contratoAtivo.toString());
		assertThat(ids(comMascara)).isEqualTo(ids(semMascara));
	}

	@Test
	void filtraImovelParcialSemDiferenciarMaiusculas() {
		assertThat(ids(contratoRepositorio.listar(null, null,
				("avenida beta " + MARCADOR).toLowerCase(Locale.ROOT), null, null)))
				.as("endereço parcial deve casar somente o imóvel do 'Beta'")
				.containsExactly(contratoAVencer.toString());
	}

	@Test
	void filtraValidadeNoFormatoDiaMesAno() {
		LocalDate fimAtivo = jdbc.queryForObject(
				"SELECT fim FROM contratos WHERE id = ?", Date.class, contratoAtivo).toLocalDate();

		assertThat(ids(contratoRepositorio.listar(null, null, null, DATA.format(fimAtivo), null)))
				.as("validade dd/MM/yyyy deve isolar pelo fim do contrato")
				.containsExactly(contratoAtivo.toString());
	}

	@Test
	void filtraSituacaoCoerenteComAView() {
		List<DemoData.Contrato> ativos =
				contratoRepositorio.listar(MARCADOR, null, null, null, "Ativo");
		List<DemoData.Contrato> aVencer =
				contratoRepositorio.listar(MARCADOR, null, null, null, "A vencer");
		List<DemoData.Contrato> atrasados =
				contratoRepositorio.listar(MARCADOR, null, null, null, "Atrasado");

		assertThat(ids(ativos)).as("'Ativo' deve trazer só o contrato vigente sem atraso")
				.containsExactly(contratoAtivo.toString());
		assertThat(ids(aVencer)).as("'A vencer' deve trazer só o contrato na janela de alerta")
				.containsExactly(contratoAVencer.toString());
		assertThat(ids(atrasados)).as("'Atrasado' deve trazer só o contrato com competência aberta")
				.containsExactly(contratoAtrasado.toString());

		assertThat(ativos).extracting(DemoData.Contrato::situacao).containsOnly("Ativo");
		assertThat(aVencer).extracting(DemoData.Contrato::situacao).containsOnly("A vencer");
		assertThat(atrasados).extracting(DemoData.Contrato::situacao).containsOnly("Atrasado");
	}

	@Test
	void percentEUnderscoreNaoViramCuringaNoFiltro() {
		assertThat(contratoRepositorio.listar("Alfa%QA", null, null, null, null))
				.as("'%' literal não pode casar o nome 'Alfa QA' como se fosse curinga")
				.isEmpty();
		assertThat(contratoRepositorio.listar("Alfa_QA", null, null, null, null))
				.as("'_' literal não pode casar o espaço de 'Alfa QA' como se fosse curinga")
				.isEmpty();
	}

	private List<String> ids(List<DemoData.Contrato> contratos) {
		return contratos.stream().map(DemoData.Contrato::id).toList();
	}

	private void inserirImovel(UUID id, String logradouro, String numero, String bairro,
			String tipo, String status) {
		jdbc.update("""
				INSERT INTO imoveis (id, logradouro, numero, complemento, bairro, cidade, uf, cep,
				                     tipo, status)
				VALUES (?, ?, ?, NULL, ?, 'Aracaju', 'SE', NULL,
				        CAST(? AS tipo_imovel), CAST(? AS status_imovel))
				""", id, logradouro, numero, bairro, tipo, status);
	}

	private void inserirInquilino(UUID id, String nome, String documento) {
		jdbc.update("""
				INSERT INTO inquilinos (id, nome, documento)
				VALUES (?, ?, ?)
				""", id, nome, documento);
	}

	private void inserirContrato(UUID id, UUID imovel, String documento,
			LocalDate inicio, LocalDate fim, LocalDate acompanharDesde, short diaVencimento) {
		UUID inquilino = jdbc.queryForObject(
				"SELECT id FROM inquilinos WHERE documento = ?", UUID.class, documento);
		jdbc.update("""
				INSERT INTO contratos (id, imovel_id, inquilino_id, inicio, fim, valor_aluguel,
				                       dia_vencimento, acompanhar_pagamento_desde)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?)
				""", id, imovel, inquilino, inicio, fim, new BigDecimal("1500.00"),
				diaVencimento, acompanharDesde);
	}

	private static String mascararCpf(String documento) {
		return documento.substring(0, 3) + "." + documento.substring(3, 6) + "."
				+ documento.substring(6, 9) + "-" + documento.substring(9);
	}

	private static String documentoUnico() {
		long valor = (UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE)
				% 100_000_000_000L;
		return String.format("%011d", valor);
	}
}
