package com.Projeto.manuelo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import com.Projeto.manuelo.servico.MovimentacaoFinanceiraService;
import com.Projeto.manuelo.servico.RelatorioService;
import com.Projeto.manuelo.servico.RelatorioService.Arquivo;
import com.Projeto.manuelo.servico.RelatorioService.ResumoRelatorio;

/**
 * A-70/A-71: os 4 PDFs, a composição insert-only de relatórios e o download,
 * contra o Postgres do perfil de teste. Prova que cada PDF começa com
 * {@code %PDF-}, que a movimentação grava período/totais/filtro e a composição
 * exata, que os contratos gravam situação/dias (com janela de 7 dias a vencer)
 * e que gerar/baixar/listar são exclusivos do ADMINISTRADOR (DEC-08, R09).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RelatorioServiceTest {

	private static final String MARCADOR = "QA-A70-" + UUID.randomUUID().toString().substring(0, 8);
	private static final byte[] COMPROVANTE =
			("%PDF-1.4\n% QA A-70 comprovante\n%%EOF\n").getBytes(StandardCharsets.UTF_8);
	private static final Path DIRETORIO = criarDiretorioTemporario();
	private static final String SENHA_COLABORADOR = "qa-colab-123";

	@DynamicPropertySource
	static void armazenamento(DynamicPropertyRegistry registry) {
		registry.add("manuelo.storage.dir", DIRETORIO::toString);
	}

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private AuthenticationManager authenticationManager;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private RelatorioService relatorios;

	@Autowired
	private MovimentacaoFinanceiraService movimentacoes;

	@Value("${manuelo.admin.email}")
	private String adminEmail;

	@Value("${manuelo.admin.senha}")
	private String adminSenha;

	private UUID adminId;
	private String colaboradorEmail;

	@BeforeEach
	void prepararAtores() {
		autenticarAdmin();
		adminId = jdbc.queryForObject("SELECT id FROM usuarios WHERE lower(email) = lower(?)",
				UUID.class, adminEmail);

		colaboradorEmail = MARCADOR + "-colab@manuelo.local";
		jdbc.update("""
				INSERT INTO usuarios (id, nome, email, senha_hash, perfil, status,
				                      aprovado_por, aprovado_em)
				VALUES (?, 'QA A70 Colaborador', ?, ?, 'COLABORADOR'::perfil_usuario,
				        'ATIVO'::status_usuario, ?, now())
				""", UUID.randomUUID(), colaboradorEmail,
				passwordEncoder.encode(SENHA_COLABORADOR), adminId);
	}

	@AfterEach
	void limparContexto() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void geraOsQuatroTiposComPdfBaixavelValido() {
		LocalDate hoje = LocalDate.now();
		criarContrato(hoje.minusMonths(4).withDayOfMonth(1), hoje.plusMonths(6), hoje, 5);
		criarContrato(hoje.minusMonths(2).withDayOfMonth(1), hoje.plusMonths(6),
				hoje.minusMonths(2).withDayOfMonth(1), 5);
		criarContrato(hoje.minusMonths(2).withDayOfMonth(1), hoje.plusDays(3), hoje, 5);

		String existentes = relatorios.gerar("Contratos existentes", null, null, null);
		String atrasados = relatorios.gerar("Contratos atrasados", null, null, null);
		String aVencer = relatorios.gerar("Contratos a vencer", null, null, null);
		String movimentacao = relatorios.gerar("Movimentação", hoje.withDayOfMonth(1), hoje,
				"Todos");

		assertThat(relatorio(existentes).get("tipo")).isEqualTo("CONTRATOS_EXISTENTES");
		assertThat(relatorio(atrasados).get("tipo")).isEqualTo("CONTRATOS_ATRASADOS");
		assertThat(relatorio(aVencer).get("tipo")).isEqualTo("CONTRATOS_A_VENCER");
		assertThat(relatorio(movimentacao).get("tipo")).isEqualTo("MOVIMENTACAO");

		for (String id : List.of(existentes, atrasados, aVencer, movimentacao)) {
			Arquivo arquivo = relatorios.baixar(id);
			assertThat(arquivo.mime()).as("mime do PDF").isEqualTo("application/pdf");
			assertThat(arquivo.nome()).as("nome do arquivo").endsWithIgnoringCase(".pdf");
			assertThat(arquivo.conteudo()).as("PDF não pode ser vazio")
					.hasSizeGreaterThan(100);
			String inicio = new String(arquivo.conteudo(), 0, 5, StandardCharsets.US_ASCII);
			assertThat(inicio).as("todo download começa com %PDF-").isEqualTo("%PDF-");
			assertThat(new String(arquivo.conteudo(), StandardCharsets.ISO_8859_1))
					.as("PDF deve ter %%EOF").contains("%%EOF");
		}
	}

	@Test
	void relatorioDeMovimentacaoGravaPeriodoTotaisFiltroEComposicaoExata() {
		LocalDate inicio = LocalDate.of(2003, 4, 1);
		LocalDate fim = LocalDate.of(2003, 4, 30);
		LocalDate diaEntrada1 = LocalDate.of(2003, 4, 5);
		LocalDate diaEntrada2 = LocalDate.of(2003, 4, 10);
		LocalDate diaSaida = LocalDate.of(2003, 4, 20);

		String entrada1 = movimentacoes.registrarEntrada("Doação", null,
				new BigDecimal("100.00"), diaEntrada1, "Entrada A " + MARCADOR, "PIX");
		String entrada2 = movimentacoes.registrarEntrada("Doação", null,
				new BigDecimal("50.00"), diaEntrada2, "Entrada B " + MARCADOR, "PIX");
		String saida = movimentacoes.registrarSaida("MANUTENCAO", new BigDecimal("30.00"),
				diaSaida, "Saída C " + MARCADOR, "PIX", "Fornecedor " + MARCADOR,
				"comprovante-" + MARCADOR + ".pdf", "application/pdf", COMPROVANTE);

		String todos = relatorios.gerar("Movimentação", inicio, fim, "Todos");
		Map<String, Object> linhaTodos = relatorio(todos);
		assertThat(linhaTodos.get("tipo")).isEqualTo("MOVIMENTACAO");
		assertThat(((java.sql.Date) linhaTodos.get("periodo_inicio")).toLocalDate())
				.isEqualTo(inicio);
		assertThat(((java.sql.Date) linhaTodos.get("periodo_fim")).toLocalDate()).isEqualTo(fim);
		assertThat(linhaTodos.get("filtro")).as("Todos não grava filtro").isNull();
		assertThat(linhaTodos.get("janela_dias")).isNull();
		assertThat((BigDecimal) linhaTodos.get("total_entradas")).isEqualByComparingTo("150.00");
		assertThat((BigDecimal) linhaTodos.get("total_saidas")).isEqualByComparingTo("30.00");
		assertThat((BigDecimal) linhaTodos.get("saldo")).isEqualByComparingTo("120.00");
		assertThat(((Number) linhaTodos.get("qtd_registros")).intValue()).isEqualTo(3);
		assertThat((UUID) linhaTodos.get("gerado_por")).isEqualTo(adminId);
		assertThat(composicaoMovimentacoes(todos))
				.containsExactly(UUID.fromString(entrada1), UUID.fromString(entrada2),
						UUID.fromString(saida));

		String entradas = relatorios.gerar("Movimentação", inicio, fim, "Entrada");
		Map<String, Object> linhaEntradas = relatorio(entradas);
		assertThat(linhaEntradas.get("filtro")).isEqualTo("ENTRADA");
		assertThat((BigDecimal) linhaEntradas.get("total_entradas"))
				.isEqualByComparingTo("150.00");
		assertThat((BigDecimal) linhaEntradas.get("total_saidas")).isEqualByComparingTo("0");
		assertThat((BigDecimal) linhaEntradas.get("saldo")).isEqualByComparingTo("150.00");
		assertThat(((Number) linhaEntradas.get("qtd_registros")).intValue()).isEqualTo(2);
		assertThat(composicaoMovimentacoes(entradas))
				.containsExactly(UUID.fromString(entrada1), UUID.fromString(entrada2));

		String saidas = relatorios.gerar("Movimentação", inicio, fim, "Saída");
		Map<String, Object> linhaSaidas = relatorio(saidas);
		assertThat(linhaSaidas.get("filtro")).isEqualTo("SAIDA");
		assertThat((BigDecimal) linhaSaidas.get("total_entradas")).isEqualByComparingTo("0");
		assertThat((BigDecimal) linhaSaidas.get("total_saidas")).isEqualByComparingTo("30.00");
		assertThat((BigDecimal) linhaSaidas.get("saldo")).isEqualByComparingTo("-30.00");
		assertThat(((Number) linhaSaidas.get("qtd_registros")).intValue()).isEqualTo(1);
		assertThat(composicaoMovimentacoes(saidas))
				.containsExactly(UUID.fromString(saida));
	}

	@Test
	void relatorioDeContratosAtrasadosGravaSituacaoEDiasDaView() {
		LocalDate hoje = LocalDate.now();
		UUID contrato = criarContrato(hoje.minusMonths(2).withDayOfMonth(1),
				hoje.plusMonths(6), hoje.minusMonths(2).withDayOfMonth(1), 5);
		Map<String, Object> situacao = situacaoContrato(contrato);
		assertThat(situacao.get("atrasado")).isEqualTo(true);
		int diasAtraso = ((Number) situacao.get("dias_atraso")).intValue();
		assertThat(diasAtraso).isPositive();

		String id = relatorios.gerar("Contratos atrasados", null, null, null);

		Map<String, Object> linha = relatorio(id);
		assertThat(linha.get("tipo")).isEqualTo("CONTRATOS_ATRASADOS");
		assertThat(linha.get("janela_dias")).isNull();
		assertThat(linha.get("periodo_inicio")).isNull();
		assertThat(linha.get("periodo_fim")).isNull();
		assertThat(linha.get("total_entradas")).isNull();
		assertThat(linha.get("total_saidas")).isNull();
		assertThat(linha.get("saldo")).isNull();

		List<Map<String, Object>> composicao = composicaoContratos(id);
		assertThat(composicao).singleElement().satisfies(item -> {
			assertThat(item.get("contrato_id")).isEqualTo(contrato);
			assertThat(item.get("situacao")).isEqualTo("ATRASADO");
			assertThat(((Number) item.get("dias")).intValue()).isEqualTo(diasAtraso);
		});
	}

	@Test
	void relatorioDeContratosExistentesGravaSituacaoExistenteSemDias() {
		LocalDate hoje = LocalDate.now();
		UUID contrato = criarContrato(hoje.minusMonths(4).withDayOfMonth(1),
				hoje.plusMonths(6), hoje.plusMonths(1).withDayOfMonth(1), 5);
		assertThat(situacaoContrato(contrato).get("atrasado")).isEqualTo(false);

		String id = relatorios.gerar("Contratos existentes", null, null, null);

		Map<String, Object> linha = relatorio(id);
		assertThat(linha.get("tipo")).isEqualTo("CONTRATOS_EXISTENTES");
		assertThat(linha.get("periodo_inicio")).isNull();
		assertThat(linha.get("periodo_fim")).isNull();
		assertThat(linha.get("total_entradas")).isNull();
		assertThat(linha.get("total_saidas")).isNull();
		assertThat(linha.get("saldo")).isNull();

		List<Map<String, Object>> composicao = composicaoContratos(id);
		assertThat(composicao).singleElement().satisfies(item -> {
			assertThat(item.get("contrato_id")).isEqualTo(contrato);
			assertThat(item.get("situacao")).isEqualTo("EXISTENTE");
			assertThat(item.get("dias")).as("EXISTENTE não exige dias").isNull();
		});
	}

	@Test
	void relatorioDeContratosAVencerGravaJanelaDeSeteDiasEPeriodoTotaisNulos() {
		Integer janelaConfigurada = jdbc.queryForObject(
				"SELECT valor::int FROM configuracoes WHERE chave = 'dias_alerta_vencimento'",
				Integer.class);
		assertThat(janelaConfigurada).as("config de referência").isEqualTo(7);

		LocalDate hoje = LocalDate.now();
		UUID contrato = criarContrato(hoje.minusMonths(2).withDayOfMonth(1),
				hoje.plusDays(3), hoje, 5);
		Map<String, Object> situacao = situacaoContrato(contrato);
		assertThat(situacao.get("situacao_validade")).isEqualTo("A_VENCER");
		int diasParaVencer = ((Number) situacao.get("dias_para_vencer")).intValue();
		assertThat(diasParaVencer).isEqualTo(3);

		String id = relatorios.gerar("Contratos a vencer", null, null, null);

		Map<String, Object> linha = relatorio(id);
		assertThat(linha.get("tipo")).isEqualTo("CONTRATOS_A_VENCER");
		assertThat(((Number) linha.get("janela_dias")).intValue()).isEqualTo(7);
		assertThat(linha.get("periodo_inicio")).as("contratos não têm período").isNull();
		assertThat(linha.get("periodo_fim")).isNull();
		assertThat(linha.get("filtro")).isNull();
		assertThat(linha.get("total_entradas")).isNull();
		assertThat(linha.get("total_saidas")).isNull();
		assertThat(linha.get("saldo")).isNull();

		List<Map<String, Object>> composicao = composicaoContratos(id);
		assertThat(composicao).singleElement().satisfies(item -> {
			assertThat(item.get("contrato_id")).isEqualTo(contrato);
			assertThat(item.get("situacao")).isEqualTo("A_VENCER");
			assertThat(((Number) item.get("dias")).intValue()).isEqualTo(diasParaVencer);
		});
	}

	@Test
	void colaboradorNaoPodeGerarBaixarNemListarRecentes() {
		String existente = relatorios.gerar("Contratos existentes", null, null, null);
		int relatoriosAntes = contar("SELECT count(*) FROM relatorios");

		autenticarColaborador();

		assertThatThrownBy(() -> relatorios.gerar("Contratos atrasados", null, null, null))
				.as("A-71/DEC-08: COLABORADOR não gera relatório")
				.isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> relatorios.baixar(existente))
				.as("A-71: COLABORADOR não baixa PDF")
				.isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> relatorios.listarRecentes(10))
				.as("A-71: COLABORADOR não lista relatórios")
				.isInstanceOf(AccessDeniedException.class);

		assertThat(contar("SELECT count(*) FROM relatorios"))
				.as("tentativa negada não grava relatório").isEqualTo(relatoriosAntes);
	}

	@Test
	void listarRecentesTrazOsGeradosDoMaisNovoParaOMaisAntigoELimita() {
		LocalDate hoje = LocalDate.now();
		String primeiro = relatorios.gerar("Contratos existentes", null, null, null);
		String segundo = relatorios.gerar("Contratos atrasados", null, null, null);
		String terceiro = relatorios.gerar("Movimentação", hoje.withDayOfMonth(1), hoje,
				"Todos");

		List<ResumoRelatorio> recentes = relatorios.listarRecentes(10);
		assertThat(recentes).extracting(ResumoRelatorio::id)
				.contains(primeiro, segundo, terceiro);
		assertThat(recentes).isSortedAccordingTo(
				Comparator.comparing(ResumoRelatorio::geradoEm)
						.thenComparing(ResumoRelatorio::id)
						.reversed());
		assertThat(recentes).allSatisfy(item -> {
			assertThat(item.nome()).isNotBlank();
			assertThat(item.geradoEm()).isBeforeOrEqualTo(OffsetDateTime.now());
			assertThat(item.qtd()).isGreaterThanOrEqualTo(0);
		});

		List<ResumoRelatorio> dois = relatorios.listarRecentes(2);
		assertThat(dois).as("listarRecentes respeita o limite")
				.containsExactlyElementsOf(recentes.subList(0, 2));
		assertThat(relatorios.listarRecentes(0)).isEmpty();
	}

	private void autenticarAdmin() {
		Authentication autenticacao = authenticationManager.authenticate(
				new UsernamePasswordAuthenticationToken(adminEmail, adminSenha));
		SecurityContextHolder.getContext().setAuthentication(autenticacao);
	}

	private void autenticarColaborador() {
		Authentication autenticacao = authenticationManager.authenticate(
				new UsernamePasswordAuthenticationToken(colaboradorEmail, SENHA_COLABORADOR));
		SecurityContextHolder.getContext().setAuthentication(autenticacao);
	}

	private Map<String, Object> relatorio(String id) {
		return jdbc.queryForMap("""
				SELECT tipo::text AS tipo, data_referencia, periodo_inicio, periodo_fim,
				       filtro_tipo_movimentacao::text AS filtro, janela_dias,
				       total_entradas, total_saidas, saldo, qtd_registros, gerado_por
				  FROM relatorios
				 WHERE id = ?
				""", UUID.fromString(id));
	}

	private List<UUID> composicaoMovimentacoes(String relatorioId) {
		return jdbc.query("""
				SELECT movimentacao_id
				  FROM relatorio_movimentacoes
				 WHERE relatorio_id = ?
				 ORDER BY ordem
				""", (rs, rowNum) -> rs.getObject("movimentacao_id", UUID.class),
				UUID.fromString(relatorioId));
	}

	private List<Map<String, Object>> composicaoContratos(String relatorioId) {
		return jdbc.queryForList("""
				SELECT contrato_id, ordem, situacao::text AS situacao, dias
				  FROM relatorio_contratos
				 WHERE relatorio_id = ?
				 ORDER BY ordem
				""", UUID.fromString(relatorioId));
	}

	private Map<String, Object> situacaoContrato(UUID contratoId) {
		return jdbc.queryForMap("""
				SELECT situacao_validade, atrasado, dias_atraso, dias_para_vencer
				  FROM vw_contratos_situacao
				 WHERE contrato_id = ?
				""", contratoId);
	}

	private UUID criarContrato(LocalDate inicio, LocalDate fim, LocalDate acompanharDesde,
			int diaVencimento) {
		UUID imovelId = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO imoveis (id, logradouro, numero, complemento, bairro, cidade, uf,
				                     tipo, status)
				VALUES (?, ?, '10', NULL, ?, 'Aracaju', 'SE',
				        'APARTAMENTO'::tipo_imovel, 'ALUGADO'::status_imovel)
				""", imovelId, "Rua QA A70 " + MARCADOR + " "
				+ imovelId.toString().substring(0, 8), "Bairro " + MARCADOR);

		UUID inquilinoId = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO inquilinos (id, nome, documento)
				VALUES (?, ?, ?)
				""", inquilinoId, "Inquilino " + MARCADOR + " "
				+ inquilinoId.toString().substring(0, 8), documentoUnico());

		UUID contratoId = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO contratos (id, imovel_id, inquilino_id, inicio, fim, valor_aluguel,
				                       dia_vencimento, acompanhar_pagamento_desde, criado_por)
				VALUES (?, ?, ?, ?, ?, 1000.00, ?, ?, ?)
				""", contratoId, imovelId, inquilinoId, inicio, fim, diaVencimento,
				acompanharDesde, adminId);
		return contratoId;
	}

	private int contar(String sql, Object... argumentos) {
		Integer total = jdbc.queryForObject(sql, Integer.class, argumentos);
		return total == null ? 0 : total;
	}

	private static String documentoUnico() {
		long valor = (UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE)
				% 100_000_000_000L;
		return String.format("%011d", valor);
	}

	private static Path criarDiretorioTemporario() {
		try {
			return Files.createTempDirectory("manuelo-storage-qa-a70-").toAbsolutePath();
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}
}
